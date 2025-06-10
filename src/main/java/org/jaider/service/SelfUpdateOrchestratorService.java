package org.jaider.service;

import dumb.jaider.model.JaiderModel;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.model.StagedUpdate;
import dumb.jaider.ui.UI; // Changed import
import dev.langchain4j.data.message.AiMessage; // Ensure AiMessage is imported

import java.io.File; // Added for project root
import java.util.concurrent.locks.ReentrantLock;
import java.nio.file.Files; // New import
import java.nio.file.Path;   // New import
import org.json.JSONObject; // New import
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SelfUpdateOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(SelfUpdateOrchestratorService.class);

    private volatile StagedUpdate currentStagedUpdate;
    private volatile boolean updateInProgress;
    private final ReentrantLock lock = new ReentrantLock();

    private final JaiderModel jaiderModel;
    private final UI uiService; // Changed type
    private final BuildManagerService buildManagerService;
    private final GitService gitService;
    private final RestartService restartService;

    public SelfUpdateOrchestratorService(
            JaiderModel jaiderModel,
            UI uiService, // Changed type
            BuildManagerService buildManagerService,
            GitService gitService,
            RestartService restartService) {
        this.jaiderModel = jaiderModel;
        this.uiService = uiService;
        this.buildManagerService = buildManagerService;
        this.gitService = gitService;
        this.restartService = restartService;
        this.updateInProgress = false;
    }

    public boolean stageUpdate(StagedUpdate update) {
        lock.lock();
        try {
            if (updateInProgress) {
                logger.warn("Attempted to stage update while another update is already in progress.");
                return false;
            }
            if (this.currentStagedUpdate != null) {
                logger.info("Previous staged update for {} overwritten by update for: {}", this.currentStagedUpdate.getFilePath(), update.getFilePath());
            }
            this.currentStagedUpdate = update;
            logger.info("Update staged for file: {}", update.getFilePath());
            return true;
        } finally {
            lock.unlock();
        }
    }

    public StagedUpdate getPendingUpdate() {
        lock.lock();
        try {
            return currentStagedUpdate;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if an update process (from confirmation to restart) is currently active.
     * @return true if an update is in progress, false otherwise.
     */
    public boolean isUpdateInProgress() {
        lock.lock();
        try {
            return this.updateInProgress;
        } finally {
            lock.unlock();
        }
    }

    public void clearPendingUpdate() {
        lock.lock();
        try {
            if (this.currentStagedUpdate != null) {
                 logger.info("Clearing pending update for file: {}", this.currentStagedUpdate.getFilePath());
                this.currentStagedUpdate = null;
            }
            // This method is primarily for clearing *staged* data if user rejects *before* processing.
            // If called externally while updateInProgress is true, it might lead to inconsistent states.
            // The main flow in processUserApproval should be responsible for clearing updateInProgress.
        } finally {
            lock.unlock();
        }
    }

    public void triggerUserConfirmationProcess() {
        StagedUpdate updateToConfirm;
        lock.lock();
        try {
            if (this.updateInProgress) {
                logger.warn("User confirmation triggered, but an update is already in progress.");
                return;
            }
            updateToConfirm = this.currentStagedUpdate;
            if (updateToConfirm == null) {
                logger.info("User confirmation triggered, but no pending update found.");
                return;
            }
            this.updateInProgress = true;
        } finally {
            lock.unlock();
        }

        logger.info("Triggering user confirmation for update on file: {}", updateToConfirm.getFilePath());
        // Construct title and text for the confirm dialog based on updateToConfirm details
        String title = "Confirm Self-Update";
        String text = String.format(
            "A self-update is proposed for file: %s%nCommit message: %s%n%nDiff:%n---%n%s%n---%n%nDo you want to apply this update?",
            updateToConfirm.getFilePath(),
            updateToConfirm.getCommitMessage(),
            updateToConfirm.getDiffContent()
        );

        uiService.confirm(title, text)
                 .thenAccept(this::processUserApproval);
    }

    private void processUserApproval(boolean userApproved) {
        StagedUpdate updateToApply;
        File projectRoot;

        // Lock critical section for reading shared state and initial validation
        lock.lock();
        try {
            updateToApply = this.currentStagedUpdate;
            projectRoot = (jaiderModel != null) ? jaiderModel.dir.toFile() : null; // Corrected

            if (updateToApply == null) {
                logger.error("User approval processed, but no staged update found. This should not happen if updateInProgress was true.");
                jaiderModel.addLog(AiMessage.from("[Orchestrator] Error: No update found to process."));
                this.updateInProgress = false; // Reset flag
                return;
            }
            if (projectRoot == null || !projectRoot.isDirectory()) {
                logger.error("Project root directory is not configured or invalid. Cannot apply update. Path: {}", projectRoot);
                jaiderModel.addLog(AiMessage.from("[Orchestrator] Error: Project root directory not configured/invalid. Cannot apply update."));
                this.currentStagedUpdate = null; // Clear the problematic update
                this.updateInProgress = false;   // Reset flag
                return;
            }
        } finally {
            lock.unlock();
        }
        // End of initial critical section, updateToApply and projectRoot are now local references.

        if (!userApproved) {
            jaiderModel.addLog(AiMessage.from("[Orchestrator] User rejected self-update for: " + updateToApply.getFilePath()));
            logger.info("User rejected self-update for file: {}", updateToApply.getFilePath());
            lock.lock();
            try {
                this.currentStagedUpdate = null;
                this.updateInProgress = false;
            } finally {
                lock.unlock();
            }
            return;
        }

        jaiderModel.addLog(AiMessage.from("[Orchestrator] User approved self-update. Applying changes for: " + updateToApply.getFilePath()));
        logger.info("User approved self-update. Applying changes for: {}", updateToApply.getFilePath());

        // Check if working directory is clean BEFORE applying diff
        if (!gitService.isWorkingDirectoryClean(projectRoot)) {
            String dirtyRepoMsg = "[Orchestrator] Working directory is not clean. Please commit or stash your changes before attempting a self-update. Aborting self-update.";
            jaiderModel.addLog(AiMessage.from(dirtyRepoMsg)); // For TUI
            logger.warn("Working directory not clean. Aborting self-update for file: {}", updateToApply.getFilePath());

            lock.lock();
            try {
                this.currentStagedUpdate = null;
                this.updateInProgress = false;
            } finally {
                lock.unlock();
            }
            return; // Abort the self-update
        }
        logger.info("Working directory is clean. Proceeding with self-update for file: {}", updateToApply.getFilePath());

        // Step 1: Apply Diff
        boolean diffApplied = gitService.applyDiff(projectRoot, updateToApply.getFilePath(), updateToApply.getDiffContent());
        if (!diffApplied) {
            jaiderModel.addLog(AiMessage.from("[Orchestrator] Failed to apply diff for: " + updateToApply.getFilePath() + ". Update aborted."));
            logger.error("GitService failed to apply diff for file: {}", updateToApply.getFilePath());
            // No revert needed as diff application itself failed.
            lock.lock();
            try {
                // currentStagedUpdate might be kept for inspection or cleared. Clearing for now.
                this.currentStagedUpdate = null;
                this.updateInProgress = false;
            } finally {
                lock.unlock();
            }
            return;
        }
        jaiderModel.addLog(AiMessage.from("[Orchestrator] Diff applied successfully for: " + updateToApply.getFilePath()));
        logger.info("Diff applied successfully for file: {}", updateToApply.getFilePath());

        // Step 2: Compile Project
        BuildManagerService.BuildResult compileResult = buildManagerService.compileProject(jaiderModel);
        if (!compileResult.isSuccess()) {
            jaiderModel.addLog(AiMessage.from("[Orchestrator] Project compilation failed. Output: " + compileResult.getOutput().lines().limit(5).collect(java.util.stream.Collectors.joining("\n")) + "\nAttempting to revert..."));
            logger.error("Project compilation failed after update for file: {}. Output:\n{}", updateToApply.getFilePath(), compileResult.getOutput());

            boolean revertSuccess = gitService.revertChanges(projectRoot, updateToApply.getFilePath());
            if (revertSuccess) {
                jaiderModel.addLog(AiMessage.from("[Orchestrator] Changes successfully reverted for: " + updateToApply.getFilePath()));
                logger.info("Changes successfully reverted for file: {}", updateToApply.getFilePath());
            } else {
                jaiderModel.addLog(AiMessage.from("[Orchestrator] CRITICAL: Failed to revert changes for: " + updateToApply.getFilePath() + " after failed compilation. Manual intervention required."));
                logger.error("CRITICAL - Failed to revert changes for file {} after failed compilation.", updateToApply.getFilePath());
            }
            lock.lock();
            try {
                this.currentStagedUpdate = null;
                this.updateInProgress = false;
            } finally {
                lock.unlock();
            }
            return;
        }
        jaiderModel.addLog(AiMessage.from("[Orchestrator] Project compiled successfully after update."));
        logger.info("Project compiled successfully after update for file: {}", updateToApply.getFilePath());

        // Step 3: Package Project (Optional but recommended)
        BuildManagerService.BuildResult packageResult = buildManagerService.packageProject(jaiderModel);
        if (!packageResult.isSuccess()) {
            jaiderModel.addLog(AiMessage.from("[Orchestrator] WARNING: Project packaging failed. Output: " + packageResult.getOutput().lines().limit(5).collect(java.util.stream.Collectors.joining("\n")) + "\nProceeding with restart."));
            logger.warn("Project packaging failed for file: {}. Output:\n{}", updateToApply.getFilePath(), packageResult.getOutput());
        } else {
            jaiderModel.addLog(AiMessage.from("[Orchestrator] Project packaged successfully."));
            logger.info("Project packaged successfully for file: {}", updateToApply.getFilePath());
        }

        // Step 4: Commit Changes (Optional)
        // For simplicity, let's make this conditional, e.g. based on a flag or always try
        boolean attemptCommit = true; // Could be a configuration
        String commitHash = null; // Initialize commitHash
        if (attemptCommit) {
            commitHash = gitService.commitChanges(projectRoot, updateToApply.getFilePath(), updateToApply.getCommitMessage());
            if (commitHash == null || commitHash.isBlank()) {
                jaiderModel.addLog(AiMessage.from("[Orchestrator] WARNING: Failed to commit changes for: " + updateToApply.getFilePath() + " or could not retrieve commit hash. Update applied and built. Manual commit recommended."));
                logger.warn("Failed to commit changes for {} or retrieve commit hash. Proceeding with restart.", updateToApply.getFilePath());
                // commitHash will remain null or blank
            } else {
                jaiderModel.addLog(AiMessage.from("[Orchestrator] Changes committed: " + updateToApply.getCommitMessage() + " (Hash: " + commitHash + ")"));
                logger.info("Changes committed successfully for {}. Hash: {}", updateToApply.getFilePath(), commitHash);
            }
        }

        // Step 5: Prepare for Restart
        String[] originalArgs = (jaiderModel != null) ? jaiderModel.getOriginalArgs() : new String[]{};
        jaiderModel.addLog(AiMessage.from("[Orchestrator] Update processed. Preparing to restart Jaider..."));
        logger.info("Update processed for file: {}. Preparing to restart application.", updateToApply.getFilePath());

        // Add this block before calling restartService.restartApplication()
        if (jaiderModel != null && jaiderModel.dir != null) { // Corrected
            try {
                Path jaiderDir = jaiderModel.dir.resolve(".jaider"); // Corrected
                Files.createDirectories(jaiderDir); // Ensure .jaider directory exists
                Path sentinelFile = jaiderDir.resolve("self_update_pending_validation.json");

                JSONObject sentinelData = new JSONObject();
                sentinelData.put("filePath", updateToApply.getFilePath());
                sentinelData.put("commitMessage", updateToApply.getCommitMessage());
                sentinelData.put("timestamp", System.currentTimeMillis());
                sentinelData.put("attempt", 1);
                if (commitHash != null && !commitHash.isBlank()) { // Add this check
                    sentinelData.put("commitHash", commitHash);
                }

                Files.writeString(sentinelFile, sentinelData.toString(2)); // Write JSON string
                logger.info("Created sentinel file for post-restart validation: {}", sentinelFile.toString());
                // Replaced uiService.showMessage with jaiderModel.addLog
                jaiderModel.addLog(AiMessage.from("[Orchestrator] Sentinel file for post-restart validation created: " + sentinelFile.toString()));
            } catch (Exception e) {
                logger.error("CRITICAL - Failed to write sentinel file for self-update validation: {}", e.getMessage(), e);
                // Replaced uiService.showError with jaiderModel.addLog
                jaiderModel.addLog(AiMessage.from("[Orchestrator] CRITICAL: Failed to write sentinel file: " + e.getMessage() + ". Post-restart validation may not occur."));
                // Decide if restart should be aborted if sentinel cannot be written. For now, proceed with restart.
            }
        } else {
            logger.error("CRITICAL - JaiderModel or project directory is null. Cannot write sentinel file.");
            // Replaced uiService.showError with jaiderModel.addLog
            jaiderModel.addLog(AiMessage.from("[Orchestrator] CRITICAL: JaiderModel or project directory is null. Cannot write sentinel file."));
        }

        // Step 6: Restart Application
        boolean restartInitiated = restartService.restartApplication(originalArgs);
        if (!restartInitiated) {
            jaiderModel.addLog(AiMessage.from("[Orchestrator] CRITICAL: Failed to initiate application restart. Update applied. Please restart manually."));
            logger.error("CRITICAL - Failed to initiate restart for file {}. Manual restart required.", updateToApply.getFilePath());
            // Even if restart fails, the update is "done". Clear current update and flag.
            lock.lock();
            try {
                this.currentStagedUpdate = null;
                this.updateInProgress = false;
            } finally {
                lock.unlock();
            }
        } else {
            jaiderModel.addLog(AiMessage.from("[Orchestrator] Application restart initiated. Jaider will now shut down."));
            System.out.println("SelfUpdateOrchestratorService: Application restart initiated. System will exit if RestartService doesn't handle it.");
            // If restart service is expected to terminate the current JVM, no further action needed here.
            // If not, System.exit might be called. For now, assume RestartService handles exit.
            // On successful restart, updateInProgress and currentStagedUpdate will be reset in the new instance.
        }
    }
}
