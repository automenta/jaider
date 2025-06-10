package dumb.jaider.app;

import dumb.jaider.model.JaiderModel;
import dumb.jaider.config.Config;
import dev.langchain4j.data.message.AiMessage; // For logging to model
import org.json.JSONObject; // For parsing sentinel file
import org.jaider.service.BuildManagerService; // New import
import org.jaider.service.GitService;         // New import
import org.jaider.service.RestartService;      // New import

import java.nio.file.Files;
import java.nio.file.Path;
// Add other necessary java.io or java.nio imports as needed

public class StartupService {

    private final JaiderModel model;
    private final Config config;
    private final BuildManagerService buildManagerService; // New field
    private final GitService gitService;                 // New field
    private final RestartService restartService;           // New field

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
        // Ensure model and model.getDir() are not null before proceeding
        if (model == null || model.getDir() == null) {
            System.err.println("[StartupService] CRITICAL: JaiderModel or its directory is null. Cannot perform startup checks.");
            // Depending on application structure, this might be a fatal error.
            // For now, we allow startup to proceed, but this state should ideally not occur.
            return true;
        }

        Path jaiderDir = model.getDir().resolve(".jaider");
        Path sentinelFile = jaiderDir.resolve(SENTINEL_FILE_NAME);

        if (Files.exists(sentinelFile)) {
            model.addLog(AiMessage.from("[StartupService] Pending self-update validation sentinel file found: " + sentinelFile.toString()));
            System.out.println("[StartupService] Pending self-update validation sentinel file found: " + sentinelFile.toString());

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
                System.out.println(String.format("[StartupService] Validating update for: %s, Commit: '%s', Attempt: %d", filePath, commitMessage, attempt));

                String runCommand = config.runCommand;

                if (runCommand != null && !runCommand.trim().isEmpty()) {
                    model.addLog(AiMessage.from("[StartupService] Executing validation command: " + runCommand));
                    System.out.println("[StartupService] Executing validation command: " + runCommand);

                    // Assuming buildManagerService.executeMavenCommand can handle generic commands
                    // or a new method like executeCommand(String[] cmd, File dir) is available.
                    // The command string needs to be split into an array.
                    BuildManagerService.BuildResult validationResult = this.buildManagerService.executeMavenCommand(runCommand.split("\\s+"), model.getDir());

                    if (validationResult.isSuccess()) {
                        model.addLog(AiMessage.from("[StartupService] Validation command successful for update to: " + filePath));
                        System.out.println("[StartupService] Validation command successful for update to: " + filePath);
                        try {
                            Files.delete(sentinelFile);
                            System.out.println("[StartupService] Sentinel file deleted successfully.");
                        } catch (Exception eDel) {
                            model.addLog(AiMessage.from("[StartupService] Error deleting sentinel file: " + eDel.getMessage()));
                            System.err.println("[StartupService] Error deleting sentinel file: " + eDel.getMessage());
                        }
                        return true; // Proceed with normal startup
                    } else {
                        model.addLog(AiMessage.from("[StartupService] VALIDATION FAILED for update to: " + filePath + ". Exit Code: " + validationResult.getExitCode() + ". Output:\n" + validationResult.getOutput()));
                        System.err.println("[StartupService] VALIDATION FAILED for update to: " + filePath + ". Exit Code: " + validationResult.getExitCode() + ". Output:\n" + validationResult.getOutput());

                        int newAttempt = attempt + 1;
                        if (newAttempt > MAX_ROLLBACK_ATTEMPTS) {
                            model.addLog(AiMessage.from("[StartupService] CRITICAL: Maximum rollback attempts (" + MAX_ROLLBACK_ATTEMPTS + ") reached for update to: " + filePath + ". Manual intervention required. Sentinel file will be deleted."));
                            System.err.println("[StartupService] CRITICAL: Maximum rollback attempts (" + MAX_ROLLBACK_ATTEMPTS + ") reached for update to: " + filePath + ". Manual intervention required. Sentinel file will be deleted.");
                            try {
                                Files.delete(sentinelFile);
                                System.out.println("[StartupService] Sentinel file deleted after max rollback attempts.");
                            } catch (Exception eDel) {
                                model.addLog(AiMessage.from("[StartupService] Error deleting sentinel file after max attempts: " + eDel.getMessage()));
                                System.err.println("[StartupService] Error deleting sentinel file after max attempts: " + eDel.getMessage());
                            }
                            return true; // Proceed with normal startup, but problem is logged
                        } else {
                            model.addLog(AiMessage.from(String.format("[StartupService] Attempting rollback %d of %d for update to: %s", newAttempt, MAX_ROLLBACK_ATTEMPTS, filePath)));
                            System.out.println(String.format("[StartupService] Attempting rollback %d of %d for update to: %s", newAttempt, MAX_ROLLBACK_ATTEMPTS, filePath));

                            boolean revertSuccess = this.gitService.revertChanges(model.getDir(), filePath);
                            if (revertSuccess) {
                                model.addLog(AiMessage.from("[StartupService] Code revert successful for " + filePath + ". Recompiling project."));
                                System.out.println("[StartupService] Code revert successful for " + filePath + ". Recompiling project.");

                                BuildManagerService.BuildResult compileResult = this.buildManagerService.compileProject(model);
                                model.addLog(AiMessage.from("[StartupService] Re-compile after revert. Success: " + compileResult.isSuccess() + ". Output:\n" + compileResult.getOutput()));
                                System.out.println("[StartupService] Re-compile after revert. Success: " + compileResult.isSuccess() + ". Output:\n" + compileResult.getOutput());

                                if (compileResult.isSuccess()) {
                                    BuildManagerService.BuildResult packageResult = this.buildManagerService.packageProject(model);
                                    model.addLog(AiMessage.from("[StartupService] Re-package after revert. Success: " + packageResult.isSuccess() + ". Output:\n" + packageResult.getOutput()));
                                    System.out.println("[StartupService] Re-package after revert. Success: " + packageResult.isSuccess() + ". Output:\n" + packageResult.getOutput());

                                    // Update Sentinel File for next attempt
                                    try {
                                        JSONObject newSentinelData = new JSONObject();
                                        newSentinelData.put("filePath", filePath);
                                        newSentinelData.put("commitMessage", commitMessage); // Original commit message
                                        newSentinelData.put("timestamp", System.currentTimeMillis());
                                        newSentinelData.put("attempt", newAttempt);
                                        Files.writeString(sentinelFile, newSentinelData.toString(2));
                                        model.addLog(AiMessage.from("[StartupService] Sentinel file updated for attempt " + newAttempt + "."));
                                        System.out.println("[StartupService] Sentinel file updated for attempt " + newAttempt + ".");
                                    } catch (Exception eWriteSentinel) {
                                        model.addLog(AiMessage.from("[StartupService] CRITICAL: Failed to write updated sentinel file: " + eWriteSentinel.getMessage() + ". Deleting sentinel to avoid loop. Manual check needed."));
                                        System.err.println("[StartupService] CRITICAL: Failed to write updated sentinel file: " + eWriteSentinel.getMessage() + ". Deleting sentinel to avoid loop. Manual check needed.");
                                        try { Files.deleteIfExists(sentinelFile); } catch (Exception eDel) { System.err.println("Failed to delete sentinel in critical path: " + eDel.getMessage());}
                                        return true; // Avoid potential restart loop if sentinel cannot be updated
                                    }

                                    model.addLog(AiMessage.from("[StartupService] Rebuild after rollback successful. Triggering restart."));
                                    System.out.println("[StartupService] Rebuild after rollback successful. Triggering restart.");
                                    this.restartService.restartApplication(model.getOriginalArgs());
                                    return false; // Restart triggered
                                } else {
                                    model.addLog(AiMessage.from("[StartupService] CRITICAL: Code revert was successful, BUT PROJECT FAILED TO COMPILE after revert for " + filePath + ". Manual intervention likely required. Sentinel file deleted."));
                                    System.err.println("[StartupService] CRITICAL: Code revert was successful, BUT PROJECT FAILED TO COMPILE after revert for " + filePath + ". Manual intervention likely required. Sentinel file deleted.");
                                    try {
                                        Files.delete(sentinelFile);
                                    } catch (Exception eDel) {
                                        model.addLog(AiMessage.from("[StartupService] Error deleting sentinel file after failed re-compile: " + eDel.getMessage()));
                                        System.err.println("[StartupService] Error deleting sentinel file after failed re-compile: " + eDel.getMessage());
                                    }
                                    return true;
                                }
                            } else {
                                model.addLog(AiMessage.from("[StartupService] CRITICAL: Rollback attempt (git revert) FAILED for " + filePath + ". Manual intervention required. Sentinel file deleted."));
                                System.err.println("[StartupService] CRITICAL: Rollback attempt (git revert) FAILED for " + filePath + ". Manual intervention required. Sentinel file deleted.");
                                try {
                                    Files.delete(sentinelFile);
                                } catch (Exception eDel) {
                                    model.addLog(AiMessage.from("[StartupService] Error deleting sentinel file after failed revert: " + eDel.getMessage()));
                                    System.err.println("[StartupService] Error deleting sentinel file after failed revert: " + eDel.getMessage());
                                }
                                return true;
                            }
                        }
                    }
                } else {
                    model.addLog(AiMessage.from("[StartupService] No validation command configured (runCommand is empty). Assuming update is valid for: " + filePath));
                    System.out.println("[StartupService] No validation command configured (runCommand is empty). Assuming update is valid for: " + filePath);
                    try {
                        Files.delete(sentinelFile);
                        System.out.println("[StartupService] Sentinel file deleted (no validation command).");
                    } catch (Exception eDel) {
                        model.addLog(AiMessage.from("[StartupService] Error deleting sentinel file (no validation command): " + eDel.getMessage()));
                        System.err.println("[StartupService] Error deleting sentinel file (no validation command): " + eDel.getMessage());
                    }
                    return true; // Proceed with normal startup
                }
            } catch (Exception e) {
                model.addLog(AiMessage.from("[StartupService] CRITICAL: Error reading or parsing sentinel file: " + e.getMessage() + ". Proceeding with normal startup, but validation was skipped."));
                System.err.println("[StartupService] CRITICAL: Error reading or parsing sentinel file: " + e.getMessage() + ". Proceeding with normal startup, but validation was skipped.");
                // Optionally delete corrupted sentinel file? For now, leave it for inspection.
                return true; // Proceed with normal startup despite error
            }
        } else {
            // No sentinel file, normal startup
            model.addLog(AiMessage.from("[StartupService] No self-update sentinel file found. Proceeding with normal startup."));
            System.out.println("[StartupService] No self-update sentinel file found. Proceeding with normal startup.");
            return true;
        }
    }
}
