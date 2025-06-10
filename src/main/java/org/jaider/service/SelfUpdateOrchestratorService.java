package org.jaider.service;

import dumb.jaider.model.JaiderModel;
import dumb.jaider.model.StagedUpdate;

import java.io.File; // Added for project root
import java.util.concurrent.locks.ReentrantLock;
// import org.slf4j.Logger; // Future: Add logging
// import org.slf4j.LoggerFactory; // Future: Add logging

public class SelfUpdateOrchestratorService {

    // private static final Logger logger = LoggerFactory.getLogger(SelfUpdateOrchestratorService.class); // Future

    private volatile StagedUpdate currentStagedUpdate;
    private volatile boolean updateInProgress;
    private final ReentrantLock lock = new ReentrantLock();

    private final JaiderModel jaiderModel;
    private final UserInterfaceService uiService;
    private final BuildManagerService buildManagerService;
    private final GitService gitService;
    private final RestartService restartService;

    public SelfUpdateOrchestratorService(
            JaiderModel jaiderModel,
            UserInterfaceService uiService,
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
                System.err.println("SelfUpdateOrchestratorService: Attempted to stage update while another update is already in progress.");
                return false;
            }
            if (this.currentStagedUpdate != null) {
                System.out.println("SelfUpdateOrchestratorService: A previous staged update was pending and will be overwritten by update for: " + update.getFilePath());
            }
            this.currentStagedUpdate = update;
            System.out.println("SelfUpdateOrchestratorService: Update staged for file: " + update.getFilePath());
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
                 System.out.println("SelfUpdateOrchestratorService: Clearing pending update for file: " + this.currentStagedUpdate.getFilePath());
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
                System.err.println("SelfUpdateOrchestratorService: User confirmation process triggered, but an update is already in progress.");
                return;
            }
            updateToConfirm = this.currentStagedUpdate;
            if (updateToConfirm == null) {
                System.out.println("SelfUpdateOrchestratorService: User confirmation process triggered, but no pending update found.");
                return;
            }
            this.updateInProgress = true;
        } finally {
            lock.unlock();
        }

        System.out.println("SelfUpdateOrchestratorService: Triggering user confirmation for update on file: " + updateToConfirm.getFilePath());
        String confirmationMessage = String.format(
                "A self-update is proposed for file: %s\nCommit message: %s\n\nDiff:\n---\n%s\n---\n\nDo you want to apply this update? (yes/no)",
                updateToConfirm.getFilePath(),
                updateToConfirm.getCommitMessage(),
                updateToConfirm.getDiffContent()
        );
        uiService.askYesNoQuestion(confirmationMessage, this::processUserApproval);
    }

    private void processUserApproval(boolean userApproved) {
        StagedUpdate updateToApply;
        File projectRoot;

        // Lock critical section for reading shared state and initial validation
        lock.lock();
        try {
            updateToApply = this.currentStagedUpdate;
            projectRoot = (jaiderModel != null) ? jaiderModel.getDir() : null;

            if (updateToApply == null) {
                System.err.println("SelfUpdateOrchestratorService: User approval processed, but no staged update found. This should not happen if updateInProgress was true.");
                uiService.showError("Error: No update found to process.");
                this.updateInProgress = false; // Reset flag
                return;
            }
            if (projectRoot == null || !projectRoot.isDirectory()) {
                System.err.println("SelfUpdateOrchestratorService: Project root directory is not configured or invalid.");
                uiService.showError("Error: Project root directory is not configured or invalid. Cannot apply update.");
                this.currentStagedUpdate = null; // Clear the problematic update
                this.updateInProgress = false;   // Reset flag
                return;
            }
        } finally {
            lock.unlock();
        }
        // End of initial critical section, updateToApply and projectRoot are now local references.

        if (!userApproved) {
            uiService.showMessage("User rejected the self-update for " + updateToApply.getFilePath());
            System.out.println("SelfUpdateOrchestratorService: User rejected self-update for file: " + updateToApply.getFilePath());
            lock.lock();
            try {
                this.currentStagedUpdate = null;
                this.updateInProgress = false;
            } finally {
                lock.unlock();
            }
            return;
        }

        uiService.showMessage("User approved self-update. Applying changes for: " + updateToApply.getFilePath());
        System.out.println("SelfUpdateOrchestratorService: User approved self-update. Applying changes for: " + updateToApply.getFilePath());

        // Step 1: Apply Diff
        boolean diffApplied = gitService.applyDiff(projectRoot, updateToApply.getFilePath(), updateToApply.getDiffContent());
        if (!diffApplied) {
            uiService.showError("Failed to apply diff for " + updateToApply.getFilePath() + ". Update aborted. Check logs for details from GitService.");
            System.err.println("SelfUpdateOrchestratorService: GitService failed to apply diff.");
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
        uiService.showMessage("Diff applied successfully for " + updateToApply.getFilePath());
        System.out.println("SelfUpdateOrchestratorService: Diff applied successfully.");

        // Step 2: Compile Project
        BuildManagerService.BuildResult compileResult = buildManagerService.compileProject(jaiderModel);
        if (!compileResult.isSuccess()) {
            uiService.showError("Failed to compile project after update. Output:\n" + compileResult.getOutput() + "\nAttempting to revert changes...");
            System.err.println("SelfUpdateOrchestratorService: Project compilation failed.");

            boolean revertSuccess = gitService.revertChanges(projectRoot, updateToApply.getFilePath());
            if (revertSuccess) {
                uiService.showMessage("Changes successfully reverted for " + updateToApply.getFilePath());
                System.out.println("SelfUpdateOrchestratorService: Changes reverted successfully.");
            } else {
                uiService.showError("Critical: Failed to revert changes for " + updateToApply.getFilePath() + " after failed compilation. Manual intervention required.");
                System.err.println("SelfUpdateOrchestratorService: CRITICAL - Failed to revert changes.");
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
        uiService.showMessage("Project compiled successfully after update.");
        System.out.println("SelfUpdateOrchestratorService: Project compiled successfully.");

        // Step 3: Package Project (Optional but recommended)
        BuildManagerService.BuildResult packageResult = buildManagerService.packageProject(jaiderModel);
        if (!packageResult.isSuccess()) {
            // This is not necessarily a critical failure if compilation worked.
            // The application might still run from compiled classes.
            uiService.showError("Warning: Failed to package project after update. Output:\n" + packageResult.getOutput() + "\nUpdate was applied and compiled, but packaging failed. Manual check recommended. Proceeding with restart using compiled classes.");
            System.err.println("SelfUpdateOrchestratorService: Project packaging failed. Proceeding as compile was successful.");
        } else {
            uiService.showMessage("Project packaged successfully.");
            System.out.println("SelfUpdateOrchestratorService: Project packaged successfully.");
        }

        // Step 4: Commit Changes (Optional)
        // For simplicity, let's make this conditional, e.g. based on a flag or always try
        boolean attemptCommit = true; // Could be a configuration
        if (attemptCommit) {
            boolean commitSuccess = gitService.commitChanges(projectRoot, updateToApply.getFilePath(), updateToApply.getCommitMessage());
            if (!commitSuccess) {
                uiService.showError("Warning: Failed to commit changes for " + updateToApply.getFilePath() + ". Update is applied and built. Manual commit recommended.");
                System.err.println("SelfUpdateOrchestratorService: Failed to commit changes. Proceeding with restart.");
            } else {
                uiService.showMessage("Changes committed successfully with message: " + updateToApply.getCommitMessage());
                System.out.println("SelfUpdateOrchestratorService: Changes committed successfully.");
            }
        }

        // Step 5: Prepare for Restart
        String[] originalArgs = (jaiderModel != null) ? jaiderModel.getOriginalArgs() : new String[]{};
        uiService.showMessage("Update applied and built. Preparing to restart the application...");
        System.out.println("SelfUpdateOrchestratorService: Preparing to restart application.");

        // Step 6: Restart Application
        boolean restartInitiated = restartService.restartApplication(originalArgs);
        if (!restartInitiated) {
            uiService.showError("Critical: Failed to initiate application restart. The update has been applied and built (and possibly committed). Please restart manually.");
            System.err.println("SelfUpdateOrchestratorService: CRITICAL - Failed to initiate restart. Manual restart required.");
            // Even if restart fails, the update is "done". Clear current update and flag.
            lock.lock();
            try {
                this.currentStagedUpdate = null;
                this.updateInProgress = false;
            } finally {
                lock.unlock();
            }
        } else {
            uiService.showMessage("Application restart initiated. Jaider will now attempt to shut down.");
            System.out.println("SelfUpdateOrchestratorService: Application restart initiated. System will exit if RestartService doesn't handle it.");
            // If restart service is expected to terminate the current JVM, no further action needed here.
            // If not, System.exit might be called. For now, assume RestartService handles exit.
            // On successful restart, updateInProgress and currentStagedUpdate will be reset in the new instance.
        }
    }
}
