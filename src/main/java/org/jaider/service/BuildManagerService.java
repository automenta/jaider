package org.jaider.service;

import dumb.jaider.model.JaiderModel;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
// import org.slf4j.Logger; // Future: Add logging
// import org.slf4j.LoggerFactory; // Future: Add logging

public class BuildManagerService {

    // private static final Logger logger = LoggerFactory.getLogger(BuildManagerService.class); // Future
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
        if (jaiderModel == null || jaiderModel.getDir() == null) {
            // logger.error("JaiderModel or its project directory is null. Cannot compile."); // Future
            System.err.println("BuildManagerService: JaiderModel or its project directory is null. Cannot compile.");
            return new BuildResult(false, "Project directory is not configured.", -1);
        }
        File projectDir = jaiderModel.getDir();
        if (!projectDir.isDirectory()) {
            // logger.error("Project directory does not exist or is not a directory: {}", projectDir.getAbsolutePath()); // Future
            System.err.println("BuildManagerService: Project directory does not exist or is not a directory: " + projectDir.getAbsolutePath());
            return new BuildResult(false, "Project directory is invalid: " + projectDir.getAbsolutePath(), -1);
        }
        return executeMavenCommand(new String[]{"mvn", "compile"}, projectDir);
    }

    public BuildResult packageProject(JaiderModel jaiderModel) {
        if (jaiderModel == null || jaiderModel.getDir() == null) {
            // logger.error("JaiderModel or its project directory is null. Cannot package."); // Future
            System.err.println("BuildManagerService: JaiderModel or its project directory is null. Cannot package.");
            return new BuildResult(false, "Project directory is not configured.", -1);
        }
        File projectDir = jaiderModel.getDir();
        if (!projectDir.isDirectory()) {
            // logger.error("Project directory does not exist or is not a directory: {}", projectDir.getAbsolutePath()); // Future
            System.err.println("BuildManagerService: Project directory does not exist or is not a directory: " + projectDir.getAbsolutePath());
            return new BuildResult(false, "Project directory is invalid: " + projectDir.getAbsolutePath(), -1);
        }
        return executeMavenCommand(new String[]{"mvn", "package"}, projectDir);
    }

    private BuildResult executeMavenCommand(String[] command, File projectDir) {
        // logger.info("Executing Maven command: {} in directory: {}", Arrays.toString(command), projectDir.getAbsolutePath()); // Future
        System.out.println("BuildManagerService: Executing Maven command: " + Arrays.toString(command) + " in directory: " + projectDir.getAbsolutePath());
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
                    // logger.debug("Maven output: {}", line); // Future: Log line-by-line if needed
                }
            }

            boolean completed = process.waitFor(MAVEN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int exitCode;

            if (completed) {
                exitCode = process.exitValue();
                // logger.info("Maven command {} completed with exit code: {}", Arrays.toString(command), exitCode); // Future
                System.out.println("BuildManagerService: Maven command " + Arrays.toString(command) + " completed with exit code: " + exitCode);
            } else {
                // logger.warn("Maven command {} timed out after {} seconds.", Arrays.toString(command), MAVEN_TIMEOUT_SECONDS); // Future
                System.err.println("BuildManagerService: Maven command " + Arrays.toString(command) + " timed out after " + MAVEN_TIMEOUT_SECONDS + " seconds.");
                output.append("\nERROR: Maven command timed out after ").append(MAVEN_TIMEOUT_SECONDS).append(" seconds.");
                process.destroyForcibly(); // Ensure the process is killed
                return new BuildResult(false, output.toString(), -1); // Use a special exit code for timeout
            }

            return new BuildResult(exitCode == 0, output.toString(), exitCode);

        } catch (IOException e) {
            // logger.error("IOException during Maven command execution: {}. Command: {}, Directory: {}", e.getMessage(), Arrays.toString(command), projectDir.getAbsolutePath(), e); // Future
            System.err.println("BuildManagerService: IOException during Maven command execution: " + e.getMessage() + ". Command: " + Arrays.toString(command));
            output.append("\nERROR: IOException occurred: ").append(e.getMessage());
            return new BuildResult(false, output.toString(), -1);
        } catch (InterruptedException e) {
            // logger.warn("Maven command execution was interrupted: {}. Command: {}, Directory: {}", e.getMessage(), Arrays.toString(command), projectDir.getAbsolutePath(), e); // Future
            System.err.println("BuildManagerService: Maven command execution was interrupted: " + e.getMessage() + ". Command: " + Arrays.toString(command));
            Thread.currentThread().interrupt(); // Restore interrupted status
            output.append("\nERROR: Execution interrupted: ").append(e.getMessage());
            return new BuildResult(false, output.toString(), -1);
        }
    }
}
