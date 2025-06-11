package dumb.jaider.service;

import dumb.jaider.model.JaiderModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class BuildManagerService {

    private static final Logger logger = LoggerFactory.getLogger(BuildManagerService.class);
    private static final long MAVEN_TIMEOUT_SECONDS = 120; // 2 minutes timeout for Maven commands

    public static class BuildResult {
        private final boolean success;
        private final String output;
        private final int exitCode;

        public BuildResult(boolean success, String output, int exitCode) {
            this.success = success;
            this.output = output;
            this.exitCode = exitCode;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getOutput() {
            return output;
        }

        public int getExitCode() {
            return exitCode;
        }
    }

    public BuildManagerService() {
        // Default constructor
    }

    public BuildResult compileProject(JaiderModel jaiderModel) {
        if (jaiderModel == null || jaiderModel.dir == null) {
            logger.error("JaiderModel or its project directory is null. Cannot compile.");
            return new BuildResult(false, "Project directory is not configured.", -1);
        }
        File projectDir = jaiderModel.dir.toFile(); // Corrected
        if (!projectDir.isDirectory()) {
            logger.error("Project directory does not exist or is not a directory: {}", projectDir.getAbsolutePath());
            return new BuildResult(false, "Project directory is invalid: " + projectDir.getAbsolutePath(), -1);
        }
        return executeMavenCommand(new String[]{"mvn", "compile"}, projectDir);
    }

    public BuildResult packageProject(JaiderModel jaiderModel) {
        if (jaiderModel == null || jaiderModel.dir == null) {
            logger.error("JaiderModel or its project directory is null. Cannot package.");
            return new BuildResult(false, "Project directory is not configured.", -1);
        }
        File projectDir = jaiderModel.dir.toFile(); // Corrected
        if (!projectDir.isDirectory()) {
            logger.error("Project directory does not exist or is not a directory: {}", projectDir.getAbsolutePath());
            return new BuildResult(false, "Project directory is invalid: " + projectDir.getAbsolutePath(), -1);
        }
        return executeMavenCommand(new String[]{"mvn", "package"}, projectDir);
    }

    public BuildResult executeMavenCommand(String[] command, File projectDir) { // Changed to public
        logger.info("Executing Maven command: {} in directory: {}", Arrays.toString(command), projectDir.getAbsolutePath());
        StringBuilder output = new StringBuilder();
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(projectDir);
        processBuilder.redirectErrorStream(true); // Merge stderr with stdout

        try {
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                    logger.debug("Maven output: {}", line); // Log line-by-line at DEBUG
                }
            }

            boolean completed = process.waitFor(MAVEN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int exitCode;

            if (completed) {
                exitCode = process.exitValue();
                logger.info("Maven command {} completed with exit code: {}", Arrays.toString(command), exitCode);
            } else {
                logger.warn("Maven command {} timed out after {} seconds.", Arrays.toString(command), MAVEN_TIMEOUT_SECONDS);
                output.append("\nERROR: Maven command timed out after ").append(MAVEN_TIMEOUT_SECONDS).append(" seconds.");
                process.destroyForcibly(); // Ensure the process is killed
                return new BuildResult(false, output.toString(), -1); // Use a special exit code for timeout
            }

            return new BuildResult(exitCode == 0, output.toString(), exitCode);

        } catch (IOException e) {
            logger.error("IOException during Maven command execution: {}. Command: {}, Directory: {}", e.getMessage(), Arrays.toString(command), projectDir.getAbsolutePath(), e);
            output.append("\nERROR: IOException occurred: ").append(e.getMessage());
            return new BuildResult(false, output.toString(), -1);
        } catch (InterruptedException e) {
            logger.warn("Maven command execution was interrupted: {}. Command: {}, Directory: {}", e.getMessage(), Arrays.toString(command), projectDir.getAbsolutePath(), e);
            Thread.currentThread().interrupt(); // Restore interrupted status
            output.append("\nERROR: Execution interrupted: ").append(e.getMessage());
            return new BuildResult(false, output.toString(), -1);
        }
    }
}
