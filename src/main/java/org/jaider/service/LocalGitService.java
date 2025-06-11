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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalGitService implements GitService {

    private static final Logger logger = LoggerFactory.getLogger(LocalGitService.class);
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
        logger.info("Executing Git command: {} in directory: {}", String.join(" ", commandParts), workingDirectory.getAbsolutePath());

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
                logger.warn("Git command timed out: {}", String.join(" ", commandParts));
                process.destroyForcibly();
                return new CommandResult(-1, stdOutput.toString(), stdError.toString() + "\nError: Git command timed out.");
            }
            exitCode = process.exitValue();
            logger.debug("Git command finished with exit code {}. Output: '{}', Error: '{}'", exitCode, stdOutput.toString().trim(), stdError.toString().trim());

        } catch (IOException e) {
            logger.error("IOException during Git command execution: {}. Command: {}", e.getMessage(), String.join(" ", commandParts), e);
            return new CommandResult(-1, stdOutput.toString(), stdError.toString() + "\nError: IOException - " + e.getMessage());
        } catch (InterruptedException e) {
            logger.warn("Git command execution interrupted: {}. Command: {}", e.getMessage(), String.join(" ", commandParts), e);
            Thread.currentThread().interrupt();
            return new CommandResult(-1, stdOutput.toString(), stdError.toString() + "\nError: Interrupted - " + e.getMessage());
        }
        return new CommandResult(exitCode, stdOutput.toString(), stdError.toString());
    }

    @Override
    public boolean applyDiff(File projectRoot, String filePath, String diffContent) {
        logger.info("Attempting to apply diff to file: {} in project root: {}", filePath, projectRoot.getAbsolutePath());

        File tempDiffFile = null;
        try {
            // 1. Create a temporary file for the diff content
            tempDiffFile = File.createTempFile("jaider_diff_", ".patch", projectRoot); // Create in project root for simpler paths if git requires
            Path tempDiffPath = tempDiffFile.toPath();
            Files.writeString(tempDiffPath, diffContent, StandardOpenOption.WRITE);
            logger.debug("Diff content written to temporary file: {}", tempDiffPath.toString());

            // 2. Check if the diff applies cleanly (optional but good practice)
            List<String> checkCommand = new ArrayList<>(Arrays.asList("git", "apply", "--check", "--verbose", tempDiffPath.toString()));
            CommandResult checkResult = executeGitCommand(projectRoot, checkCommand);
            if (!checkResult.isSuccess()) {
                logger.error("Git apply --check failed for diff on {}. Exit code: {}. Error: {}. Output: {}", filePath, checkResult.exitCode, checkResult.error, checkResult.output);
                return false;
            }
            logger.info("Git apply --check successful for diff on {}", filePath);

            // 3. Apply the diff
            List<String> applyCommand = new ArrayList<>(Arrays.asList("git", "apply", "--verbose", tempDiffPath.toString()));
            CommandResult applyResult = executeGitCommand(projectRoot, applyCommand);

            if (!applyResult.isSuccess()) {
                logger.error("Git apply failed for diff on {}. Exit code: {}. Error: {}. Output: {}", filePath, applyResult.exitCode, applyResult.error, applyResult.output);
                return false;
            }
            logger.info("Git apply successful for diff on {}", filePath);
            return true;

        } catch (IOException e) {
            logger.error("IOException during applyDiff operation for {}: {}", filePath, e.getMessage(), e);
            return false;
        } finally {
            if (tempDiffFile != null && !tempDiffFile.delete()) {
                logger.warn("Failed to delete temporary diff file: {}", tempDiffFile.getAbsolutePath());
            }
        }
    }

    @Override
    public boolean revertChanges(File projectRoot, String filePath) {
        logger.info("Attempting to revert {} to its state at HEAD~1 in project root: {}", filePath, projectRoot.getAbsolutePath());

        // 1. Checkout the file from the commit before HEAD
        List<String> checkoutCommand = new ArrayList<>(Arrays.asList("git", "checkout", "HEAD~1", "--", filePath));
        CommandResult checkoutResult = executeGitCommand(projectRoot, checkoutCommand);

        if (!checkoutResult.isSuccess()) {
            logger.error("Failed to checkout {} from HEAD~1. Exit code: {}. Error: {}. Output: {}", filePath, checkoutResult.exitCode, checkoutResult.error, checkoutResult.output);
            return false;
        }
        logger.info("Successfully checked out {} from HEAD~1.", filePath);

        // 2. Stage the reverted file
        List<String> addCommand = new ArrayList<>(Arrays.asList("git", "add", filePath));
        CommandResult addResult = executeGitCommand(projectRoot, addCommand);
        if (!addResult.isSuccess()) {
            logger.error("Failed to stage reverted file {}. Exit code: {}. Error: {}. Output: {}", filePath, addResult.exitCode, addResult.error, addResult.output);
            return false;
        }
        logger.info("Successfully staged reverted file {}.", filePath);

        // 3. Commit the revert
        String commitMessage = "Rollback: Reverted changes to " + filePath + " due to failed validation";
        List<String> commitCommand = new ArrayList<>(Arrays.asList("git", "commit", "-m", commitMessage));
        CommandResult commitResult = executeGitCommand(projectRoot, commitCommand);

        if (!commitResult.isSuccess()) {
            logger.error("Failed to commit reverted file {}. Exit code: {}. Error: {}. Output: {}", filePath, commitResult.exitCode, commitResult.error, commitResult.output);
            return false;
        }
        logger.info("Successfully committed revert of {}.", filePath);
        return true;
    }

    @Override
    public String commitChanges(File projectRoot, String filePath, String commitMessage) {
        logger.info("Attempting to commit changes for file: {} with message: '{}' in project root: {}", filePath, commitMessage, projectRoot.getAbsolutePath());

        // 1. Stage the file: `git add <filePath>`
        List<String> addCommand = new ArrayList<>(Arrays.asList("git", "add", filePath));
        CommandResult addResult = executeGitCommand(projectRoot, addCommand);
        if (!addResult.isSuccess()) {
            logger.error("Failed to stage file {} for commit. Exit code: {}. Error: {}. Output: {}", filePath, addResult.exitCode, addResult.error, addResult.output);
            return null;
        }
        logger.info("File {} staged successfully.", filePath);

        // 2. Commit the staged changes: `git commit -m "<commitMessage>"`
        List<String> commitCommand = new ArrayList<>(Arrays.asList("git", "commit", "-m", commitMessage));
        CommandResult commitResult = executeGitCommand(projectRoot, commitCommand);

        if (!commitResult.isSuccess()) {
            logger.error("Failed to commit changes for {} with message '{}'. Exit code: {}. Error: {}. Output: {}", filePath, commitMessage, commitResult.exitCode, commitResult.error, commitResult.output);
            return null;
        }
        // logger.info("Successfully committed changes for {} with message '{}'", filePath, commitMessage); // Logged below with hash

        // 3. Get the commit hash
        List<String> revParseCommand = new ArrayList<>(Arrays.asList("git", "rev-parse", "HEAD"));
        CommandResult revParseResult = executeGitCommand(projectRoot, revParseCommand);
        if (revParseResult.isSuccess() && revParseResult.output != null && !revParseResult.output.trim().isEmpty()) {
            String commitHash = revParseResult.output.trim();
            logger.info("Successfully committed changes for {} with message '{}'. Commit hash: {}", filePath, commitMessage, commitHash);
            return commitHash; // Return the commit hash
        } else {
            logger.error("Committed changes for {} but failed to retrieve commit hash. Error (rev-parse): {}. Output: {}", filePath, revParseResult.error, revParseResult.output);
            return null; // Indicate commit might have happened but hash retrieval failed
        }
    }

    @Override
    public boolean isWorkingDirectoryClean(File projectRoot) {
        logger.info("Checking if working directory is clean in: {}", projectRoot.getAbsolutePath());
        List<String> command = new ArrayList<>(Arrays.asList("git", "status", "--porcelain"));
        CommandResult result = executeGitCommand(projectRoot, command);

        if (!result.isSuccess()) {
            logger.error("Failed to execute 'git status --porcelain'. Exit code: {}. Error: {}. Output: {}",
                         result.exitCode, result.error, result.output);
            // Treat as not clean if status command fails, to be safe
            return false;
        }

        if (result.output == null || result.output.trim().isEmpty()) {
            logger.info("Working directory is clean.");
            return true;
        } else {
            logger.warn("Working directory is not clean. Status output:\n{}", result.output.trim());
            return false;
        }
    }

    @Override
    public boolean revertLastCommittedUpdate(File projectRoot, String commitHashToRevert) {
        if (commitHashToRevert == null || commitHashToRevert.trim().isEmpty()) {
            logger.error("commitHashToRevert cannot be null or empty for revertLastCommittedUpdate.");
            return false;
        }
        logger.info("Attempting to revert commit: {} in project root: {}", commitHashToRevert, projectRoot.getAbsolutePath());

        // Ensure working directory is clean before 'git revert' might be a good idea,
        // but StartupService should call this on a clean state post-restart.
        // For now, proceed directly with revert.

        List<String> revertCommand = new ArrayList<>(Arrays.asList("git", "revert", commitHashToRevert, "--no-edit"));
        CommandResult result = executeGitCommand(projectRoot, revertCommand);

        if (!result.isSuccess()) {
            logger.error("Failed to revert commit {}. Exit code: {}. Error: {}. Output: {}",
                         commitHashToRevert, result.exitCode, result.error, result.output);
        } else {
            logger.info("Successfully reverted commit {}.", commitHashToRevert);
        }
        return result.isSuccess();
    }
}
