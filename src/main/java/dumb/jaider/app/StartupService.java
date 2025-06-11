package dumb.jaider.app;

import dev.langchain4j.data.message.AiMessage;
import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.service.BuildManagerService;
import dumb.jaider.service.GitService;
import dumb.jaider.service.RestartService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
// Add other necessary java.io or java.nio imports as needed

public class StartupService {

    private final JaiderModel model;
    private final Config config;
    private final BuildManagerService buildManagerService; // New field
    private final GitService gitService;                 // New field
    private final RestartService restartService;           // New field

    private static final Logger logger = LoggerFactory.getLogger(StartupService.class); // New logger field
    private static final String SENTINEL_FILE_NAME = "self_update_pending_validation.json";
    private static final int MAX_ROLLBACK_ATTEMPTS = 2; // Defined constant

    public StartupService(JaiderModel model, Config config, BuildManagerService buildManagerService, GitService gitService, RestartService restartService) {
        this.model = model;
        this.config = config;
        this.buildManagerService = buildManagerService;
        this.gitService = gitService;
        this.restartService = restartService;
    }

    /**
     * Performs startup checks for self-update validation.
     * @return true if normal startup should proceed, false if a restart has been triggered (e.g., after rollback).
     */
    public boolean performStartupValidationChecks() {
        // Ensure model and model.dir are not null before proceeding
        if (model == null || model.dir == null) {
            logger.error("CRITICAL: JaiderModel or its directory is null. Cannot perform startup checks.");
            // Depending on application structure, this might be a fatal error.
            // For now, we allow startup to proceed, but this state should ideally not occur.
            return true;
        }

        Path jaiderDir = model.dir.resolve(".jaider");
        Path sentinelFile = jaiderDir.resolve(SENTINEL_FILE_NAME);

        if (Files.exists(sentinelFile)) {
            model.addLog(AiMessage.from("[StartupService] Pending self-update validation sentinel file found: " + sentinelFile.toString()));
            logger.info("Pending self-update validation sentinel file found: {}", sentinelFile.toString());

            try {
                String content = Files.readString(sentinelFile);
                JSONObject sentinelData = new JSONObject(content);

                String filePath = sentinelData.optString("filePath", "Unknown file");
                String commitMessage = sentinelData.optString("commitMessage", "N/A");
                long timestamp = sentinelData.optLong("timestamp", 0); // TODO: Use this timestamp for display or logic
                int attempt = sentinelData.optInt("attempt", 1);

                model.addLog(AiMessage.from(
                    String.format("[StartupService] Validating update for: %s, Commit: '%s', Attempt: %d", filePath, commitMessage, attempt)
                ));
                logger.info("Validating update for: {}, Commit: '{}', Attempt: {}", filePath, commitMessage, attempt);

                String runCommand = config.runCommand;

                if (runCommand != null && !runCommand.trim().isEmpty()) {
                    model.addLog(AiMessage.from("[StartupService] Executing validation command: " + runCommand));
                    logger.info("Executing validation command: {}", runCommand);

                    // Assuming buildManagerService.executeMavenCommand can handle generic commands
                    // or a new method like executeCommand(String[] cmd, File dir) is available.
                    // The command string needs to be split into an array.
                    BuildManagerService.BuildResult validationResult = this.buildManagerService.executeMavenCommand(runCommand.split("\\s+"), model.dir.toFile()); // Corrected

                    if (validationResult.isSuccess()) {
                        model.addLog(AiMessage.from("[StartupService] Validation command successful for update to: " + filePath));
                        logger.info("Validation command successful for update to: {}", filePath);
                        try {
                            Files.delete(sentinelFile);
                            logger.info("Sentinel file deleted successfully.");
                        } catch (Exception eDel) {
                            model.addLog(AiMessage.from("[StartupService] Error deleting sentinel file: " + eDel.getMessage()));
                            logger.warn("Error deleting sentinel file: {}", eDel.getMessage(), eDel);
                        }
                        return true; // Proceed with normal startup
                    } else {
                        model.addLog(AiMessage.from("[StartupService] VALIDATION FAILED for update to: " + filePath + ". Exit Code: " + validationResult.getExitCode() + ". Output:\n" + validationResult.getOutput()));
                        logger.error("VALIDATION FAILED for update to: {}. Exit Code: {}. Output:\n{}", filePath, validationResult.getExitCode(), validationResult.getOutput());

                        int newAttempt = attempt + 1;
                        if (newAttempt > MAX_ROLLBACK_ATTEMPTS) {
                            model.addLog(AiMessage.from("[StartupService] CRITICAL: Maximum rollback attempts (" + MAX_ROLLBACK_ATTEMPTS + ") reached for update to: " + filePath + ". Manual intervention required. Sentinel file will be deleted."));
                            logger.error("CRITICAL: Maximum rollback attempts ({}) reached for update to: {}. Manual intervention required. Sentinel file will be deleted.", MAX_ROLLBACK_ATTEMPTS, filePath);
                            try {
                                Files.delete(sentinelFile);
                                logger.info("Sentinel file deleted after max rollback attempts.");
                            } catch (Exception eDel) {
                                model.addLog(AiMessage.from("[StartupService] Error deleting sentinel file after max attempts: " + eDel.getMessage()));
                                logger.warn("Error deleting sentinel file after max attempts: {}", eDel.getMessage(), eDel);
                            }
                            return true; // Proceed with normal startup, but problem is logged
                        } else {
                            model.addLog(AiMessage.from(String.format("[StartupService] Attempting rollback %d of %d for update to: %s", newAttempt, MAX_ROLLBACK_ATTEMPTS, filePath)));
                            logger.info("Attempting rollback {} of {} for update to: {}", newAttempt, MAX_ROLLBACK_ATTEMPTS, filePath);

                            String commitHashFromSentinel = sentinelData.optString("commitHash", null);
                            boolean revertSuccess;
                            if (commitHashFromSentinel != null && !commitHashFromSentinel.isBlank()) {
                                logger.info("Attempting to revert committed update using commit hash: {}", commitHashFromSentinel);
                                revertSuccess = this.gitService.revertLastCommittedUpdate(model.dir.toFile(), commitHashFromSentinel); // Corrected
                            } else {
                                logger.warn("No commitHash found in sentinel for update to {}. Attempting file-based revert.", filePath);
                                revertSuccess = this.gitService.revertChanges(model.dir.toFile(), filePath); // Corrected
                            }

                            if (revertSuccess) {
                                model.addLog(AiMessage.from("[StartupService] Code revert successful for " + filePath + ". Recompiling project."));
                                logger.info("Code revert successful for {}. Recompiling project.", filePath);

                                BuildManagerService.BuildResult compileResult = this.buildManagerService.compileProject(model);
                                model.addLog(AiMessage.from("[StartupService] Re-compile after revert. Success: " + compileResult.isSuccess() + ". Output:\n" + compileResult.getOutput()));
                                logger.info("Re-compile after revert for {}. Success: {}. Output:\n{}", filePath, compileResult.isSuccess(), compileResult.getOutput());

                                if (compileResult.isSuccess()) {
                                    BuildManagerService.BuildResult packageResult = this.buildManagerService.packageProject(model);
                                    model.addLog(AiMessage.from("[StartupService] Re-package after revert. Success: " + packageResult.isSuccess() + ". Output:\n" + packageResult.getOutput()));
                                    logger.info("Re-package after revert for {}. Success: {}. Output:\n{}", filePath, packageResult.isSuccess(), packageResult.getOutput());

                                    // Update Sentinel File for next attempt
                                    try {
                                        JSONObject newSentinelData = new JSONObject();
                                        newSentinelData.put("filePath", filePath);
                                        newSentinelData.put("commitMessage", commitMessage); // Original commit message
                                        newSentinelData.put("timestamp", System.currentTimeMillis());
                                        newSentinelData.put("attempt", newAttempt);
                                        // Preserve commitHash in sentinel if it was used for revert, or if it existed before
                                        if (commitHashFromSentinel != null && !commitHashFromSentinel.isBlank()) {
                                            newSentinelData.put("commitHash", commitHashFromSentinel);
                                        }
                                        Files.writeString(sentinelFile, newSentinelData.toString(2));
                                        model.addLog(AiMessage.from("[StartupService] Sentinel file updated for attempt " + newAttempt + "."));
                                        logger.info("Sentinel file updated for attempt {}.", newAttempt);
                                    } catch (Exception eWriteSentinel) {
                                        model.addLog(AiMessage.from("[StartupService] CRITICAL: Failed to write updated sentinel file: " + eWriteSentinel.getMessage() + ". Deleting sentinel to avoid loop. Manual check needed."));
                                        logger.error("CRITICAL: Failed to write updated sentinel file: {}. Deleting sentinel to avoid loop. Manual check needed.", eWriteSentinel.getMessage(), eWriteSentinel);
                                        try { Files.deleteIfExists(sentinelFile); } catch (Exception eDel) { logger.error("Failed to delete sentinel in critical path after failing to write updated sentinel: {}", eDel.getMessage(), eDel);}
                                        return true; // Avoid potential restart loop if sentinel cannot be updated
                                    }

                                    model.addLog(AiMessage.from("[StartupService] Rebuild after rollback successful. Triggering restart."));
                                    logger.info("Rebuild after rollback successful for {}. Triggering restart.", filePath);
                                    this.restartService.restartApplication(model.getOriginalArgs());
                                    return false; // Restart triggered
                                } else {
                                    model.addLog(AiMessage.from("[StartupService] CRITICAL: Code revert was successful, BUT PROJECT FAILED TO COMPILE after revert for " + filePath + ". Manual intervention likely required. Sentinel file deleted."));
                                    logger.error("CRITICAL: Code revert was successful, BUT PROJECT FAILED TO COMPILE after revert for {}. Manual intervention likely required. Sentinel file deleted.", filePath);
                                    try {
                                        Files.delete(sentinelFile);
                                    } catch (Exception eDel) {
                                        model.addLog(AiMessage.from("[StartupService] Error deleting sentinel file after failed re-compile: " + eDel.getMessage()));
                                        logger.warn("Error deleting sentinel file after failed re-compile for {}: {}", filePath, eDel.getMessage(), eDel);
                                    }
                                    return true;
                                }
                            } else {
                                model.addLog(AiMessage.from("[StartupService] CRITICAL: Rollback attempt (git revert) FAILED for " + filePath + ". Manual intervention required. Sentinel file deleted."));
                                logger.error("CRITICAL: Rollback attempt (git revert) FAILED for {}. Manual intervention required. Sentinel file deleted.", filePath);
                                try {
                                    Files.delete(sentinelFile);
                                } catch (Exception eDel) {
                                    model.addLog(AiMessage.from("[StartupService] Error deleting sentinel file after failed revert: " + eDel.getMessage()));
                                    logger.warn("Error deleting sentinel file after failed revert for {}: {}", filePath, eDel.getMessage(), eDel);
                                }
                                return true;
                            }
                        }
                    }
                } else {
                    model.addLog(AiMessage.from("[StartupService] No validation command configured (runCommand is empty). Assuming update is valid for: " + filePath));
                    logger.info("No validation command configured (runCommand is empty). Assuming update is valid for: {}", filePath);
                    try {
                        Files.delete(sentinelFile);
                        logger.info("Sentinel file deleted (no validation command).");
                    } catch (Exception eDel) {
                        model.addLog(AiMessage.from("[StartupService] Error deleting sentinel file (no validation command): " + eDel.getMessage()));
                        logger.warn("Error deleting sentinel file (no validation command) for {}: {}", filePath, eDel.getMessage(), eDel);
                    }
                    return true; // Proceed with normal startup
                }
            } catch (Exception e) {
                model.addLog(AiMessage.from("[StartupService] CRITICAL: Error reading or parsing sentinel file: " + e.getMessage() + ". Proceeding with normal startup, but validation was skipped."));
                logger.error("CRITICAL: Error reading or parsing sentinel file: {}. Proceeding with normal startup, but validation was skipped.", e.getMessage(), e);
                // Optionally delete corrupted sentinel file? For now, leave it for inspection.
                return true; // Proceed with normal startup despite error
            }
        } else {
            // No sentinel file, normal startup
            model.addLog(AiMessage.from("[StartupService] No self-update sentinel file found. Proceeding with normal startup."));
            logger.info("No self-update sentinel file found. Proceeding with normal startup.");
            return true;
        }
    }
}
