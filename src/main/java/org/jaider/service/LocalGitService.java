package org.jaider.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

// import org.slf4j.Logger; // Future: Add logging
// import org.slf4j.LoggerFactory; // Future: Add logging

public class LocalGitService implements GitService {

    // private static final Logger logger = LoggerFactory.getLogger(LocalGitService.class); // Future
    private static final long GIT_TIMEOUT_SECONDS = 30;

    private static class CommandResult {
        final int exitCode;
        final String output;
        final String error;

        CommandResult(int exitCode, String output, String error) {
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
        }

        boolean isSuccess() {
            return exitCode == 0;
        }
    }

    private CommandResult executeGitCommand(File workingDirectory, List<String> commandParts) {
        // logger.info("Executing Git command: {} in directory: {}", String.join(" ", commandParts), workingDirectory.getAbsolutePath()); // Future
        System.out.println("LocalGitService: Executing Git command: " + String.join(" ", commandParts) + " in directory: " + workingDirectory.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(commandParts);
        pb.directory(workingDirectory);

        StringBuilder stdOutput = new StringBuilder();
        StringBuilder stdError = new StringBuilder();
        int exitCode = -1;

        try {
            Process process = pb.start();

            try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                String line;
                while ((line = outReader.readLine()) != null) {
                    stdOutput.append(line).append(System.lineSeparator());
                }
                while ((line = errReader.readLine()) != null) {
                    stdError.append(line).append(System.lineSeparator());
                }
            }

            if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                // logger.warn("Git command timed out: {}", String.join(" ", commandParts)); // Future
                System.err.println("LocalGitService: Git command timed out: " + String.join(" ", commandParts));
                process.destroyForcibly();
                return new CommandResult(-1, stdOutput.toString(), stdError.toString() + "\nError: Git command timed out.");
            }
            exitCode = process.exitValue();
            // logger.info("Git command finished with exit code {}. Output: '{}', Error: '{}'", exitCode, stdOutput, stdError); // Future
             System.out.println("LocalGitService: Git command finished with exit code " + exitCode + ". Output: '" + stdOutput + "', Error: '" + stdError + "'");

        } catch (IOException e) {
            // logger.error("IOException during Git command execution: {}. Command: {}", e.getMessage(), String.join(" ", commandParts), e); // Future
            System.err.println("LocalGitService: IOException during Git command: " + e.getMessage());
            return new CommandResult(-1, stdOutput.toString(), stdError.toString() + "\nError: IOException - " + e.getMessage());
        } catch (InterruptedException e) {
            // logger.warn("Git command execution interrupted: {}. Command: {}", e.getMessage(), String.join(" ", commandParts), e); // Future
             System.err.println("LocalGitService: Git command interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return new CommandResult(-1, stdOutput.toString(), stdError.toString() + "\nError: Interrupted - " + e.getMessage());
        }
        return new CommandResult(exitCode, stdOutput.toString(), stdError.toString());
    }

    @Override
    public boolean applyDiff(File projectRoot, String filePath, String diffContent) {
        // logger.info("Attempting to apply diff to file: {} in project root: {}", filePath, projectRoot.getAbsolutePath()); // Future
        System.out.println("LocalGitService: Attempting to apply diff to file: " + filePath + " in project root: " + projectRoot.getAbsolutePath());

        File tempDiffFile = null;
        try {
            // 1. Create a temporary file for the diff content
            tempDiffFile = File.createTempFile("jaider_diff_", ".patch", projectRoot); // Create in project root for simpler paths if git requires
            Path tempDiffPath = tempDiffFile.toPath();
            Files.writeString(tempDiffPath, diffContent, StandardOpenOption.WRITE);
            // logger.debug("Diff content written to temporary file: {}", tempDiffPath.toString()); // Future
            System.out.println("LocalGitService: Diff content written to temporary file: " + tempDiffPath.toString());

            // 2. Check if the diff applies cleanly (optional but good practice)
            // `git apply --check --verbose <patch_file_path>`
            // The path to the patch file should be relative to the projectRoot or absolute.
            List<String> checkCommand = new ArrayList<>(Arrays.asList("git", "apply", "--check", "--verbose", tempDiffPath.toString()));
            CommandResult checkResult = executeGitCommand(projectRoot, checkCommand);
            if (!checkResult.isSuccess()) {
                // logger.error("Git apply --check failed for diff on {}. Exit code: {}. Error: {}", filePath, checkResult.exitCode, checkResult.error); // Future
                System.err.println("LocalGitService: Git apply --check failed for diff on " + filePath + ". Error: " + checkResult.error + checkResult.output);
                return false;
            }
            // logger.info("Git apply --check successful for diff on {}", filePath); // Future
            System.out.println("LocalGitService: Git apply --check successful for diff on " + filePath);

            // 3. Apply the diff
            // `git apply --verbose <patch_file_path>`
            List<String> applyCommand = new ArrayList<>(Arrays.asList("git", "apply", "--verbose", tempDiffPath.toString()));
            CommandResult applyResult = executeGitCommand(projectRoot, applyCommand);

            if (!applyResult.isSuccess()) {
                // logger.error("Git apply failed for diff on {}. Exit code: {}. Error: {}", filePath, applyResult.exitCode, applyResult.error); // Future
                System.err.println("LocalGitService: Git apply failed for diff on " + filePath + ". Error: " + applyResult.error + applyResult.output);
                // Attempt to reverse the patch if it partially applied, though git apply usually doesn't do partials without specific flags
                // List<String> reverseCommand = new ArrayList<>(Arrays.asList("git", "apply", "--reverse", tempDiffPath.toString()));
                // executeGitCommand(projectRoot, reverseCommand); // Best effort reverse
                return false;
            }
            // logger.info("Git apply successful for diff on {}", filePath); // Future
            System.out.println("LocalGitService: Git apply successful for diff on " + filePath);
            return true;

        } catch (IOException e) {
            // logger.error("IOException during applyDiff operation for {}: {}", filePath, e.getMessage(), e); // Future
            System.err.println("LocalGitService: IOException during applyDiff for " + filePath + ": " + e.getMessage());
            return false;
        } finally {
            if (tempDiffFile != null && !tempDiffFile.delete()) {
                // logger.warn("Failed to delete temporary diff file: {}", tempDiffFile.getAbsolutePath()); // Future
                 System.err.println("LocalGitService: Failed to delete temporary diff file: " + tempDiffFile.getAbsolutePath());
            }
        }
    }

    @Override
    public boolean revertChanges(File projectRoot, String filePath) {
        // logger.info("Attempting to revert changes for file: {} in project root: {}", filePath, projectRoot.getAbsolutePath()); // Future
        System.out.println("LocalGitService: Attempting to revert changes for file: " + filePath + " in project root: " + projectRoot.getAbsolutePath());

        // `git checkout HEAD -- <filePath>`
        // This command discards changes in the working directory for the specified file.
        // It restores the file to the state it's in at HEAD.
        List<String> revertCommand = new ArrayList<>(Arrays.asList("git", "checkout", "HEAD", "--", filePath));
        CommandResult result = executeGitCommand(projectRoot, revertCommand);

        if (!result.isSuccess()) {
            // logger.error("Failed to revert changes for {}. Exit code: {}. Error: {}", filePath, result.exitCode, result.error); // Future
            System.err.println("LocalGitService: Failed to revert changes for " + filePath + ". Error: " + result.error + result.output);
        } else {
            // logger.info("Successfully reverted changes for {}", filePath); // Future
            System.out.println("LocalGitService: Successfully reverted changes for " + filePath);
        }
        return result.isSuccess();
    }

    @Override
    public boolean commitChanges(File projectRoot, String filePath, String commitMessage) {
        // logger.info("Attempting to commit changes for file: {} with message: '{}' in project root: {}", filePath, commitMessage, projectRoot.getAbsolutePath()); // Future
        System.out.println("LocalGitService: Attempting to commit changes for file: " + filePath + " with message: '" + commitMessage + "' in project root: " + projectRoot.getAbsolutePath());

        // 1. Stage the file: `git add <filePath>`
        List<String> addCommand = new ArrayList<>(Arrays.asList("git", "add", filePath));
        CommandResult addResult = executeGitCommand(projectRoot, addCommand);
        if (!addResult.isSuccess()) {
            // logger.error("Failed to stage file {} for commit. Exit code: {}. Error: {}", filePath, addResult.exitCode, addResult.error); // Future
            System.err.println("LocalGitService: Failed to stage file " + filePath + " for commit. Error: " + addResult.error + addResult.output);
            return false;
        }
        // logger.info("File {} staged successfully.", filePath); // Future
        System.out.println("LocalGitService: File " + filePath + " staged successfully.");

        // 2. Commit the staged changes: `git commit -m "<commitMessage>"`
        List<String> commitCommand = new ArrayList<>(Arrays.asList("git", "commit", "-m", commitMessage));
        CommandResult commitResult = executeGitCommand(projectRoot, commitCommand);

        if (!commitResult.isSuccess()) {
            // logger.error("Failed to commit changes for {} with message '{}'. Exit code: {}. Error: {}", filePath, commitMessage, commitResult.exitCode, commitResult.error); // Future
            System.err.println("LocalGitService: Failed to commit changes for " + filePath + ". Error: " + commitResult.error + commitResult.output);
            // Note: file is staged. User might need to manually unstage or commit.
            return false;
        }
        // logger.info("Successfully committed changes for {} with message '{}'", filePath, commitMessage); // Future
        System.out.println("LocalGitService: Successfully committed changes for " + filePath + " with message '" + commitMessage + "'");
        return true;
    }
}
