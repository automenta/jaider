package dumb.jaider.demo;

import dumb.jaider.integration.ProjectManager;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

// Definition of DemoRunner.ExecutionResult is needed by ProjectManager
// so this file is created first, even if ProjectManager doesn't exist yet.

public class DemoRunner {

    private final ProjectManager projectManager;
    private final String demoIdentifier; // Used to create a unique project directory name
    private Path projectPath;

    public DemoRunner(String demoNameSuffix) {
        // This identifier will be part of the temporary project directory name
        this.demoIdentifier = "JaiderDemo_" + demoNameSuffix;
        // Assumes ProjectManager will be created with a constructor that accepts this identifier
        // to manage a specific project directory.
        this.projectManager = new ProjectManager(this.demoIdentifier);
    }

    /**
     * Prepares the temporary project directory.
     */
    public void setupProject() throws Exception {
        projectManager.createTemporaryProject(); // ProjectManager uses its internal identifier
        this.projectPath = projectManager.getProjectDir();
        if (this.projectPath == null) {
            throw new IllegalStateException("Project path was not set by ProjectManager after creation.");
        }
        // Ensure standard source directory exists
        Path srcMainJava = projectPath.resolve("src").resolve("main").resolve("java");
        if (!java.nio.file.Files.exists(srcMainJava)) {
            java.nio.file.Files.createDirectories(srcMainJava);
        }
        Path targetClasses = projectPath.resolve("target").resolve("classes");
        if (!java.nio.file.Files.exists(targetClasses)) {
            java.nio.file.Files.createDirectories(targetClasses);
        }
    }

    /**
     * Writes a source file to the project.
     * The path should be relative to "src/main/java".
     * Example: "com/example/Main.java"
     */
    public void addSourceFile(String relativeFilePath, String content) throws Exception {
        if (this.projectPath == null) {
            throw new IllegalStateException("Project has not been set up. Call setupProject() first.");
        }
        Path fullPath = projectPath.resolve("src/main/java").resolve(relativeFilePath);
        // writeFile in ProjectManager should handle creating parent directories for the file if they don't exist.
        projectManager.writeFile(fullPath.getParent(), fullPath.getFileName().toString(), content);
    }

    /**
     * Compiles the project.
     * @return true if compilation was successful, false otherwise.
     */
    public boolean compileProject() throws Exception {
        if (this.projectPath == null) {
            throw new IllegalStateException("Project has not been set up. Call setupProject() first.");
        }
        try {
            return projectManager.compileProject().get(); // Wait for async compilation
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Compilation failed: " + e.getMessage());
            e.printStackTrace(); // For more detailed logs during testing
            return false;
        }
    }

    /**
     * Runs the compiled project.
     * @param mainClassName The fully qualified name of the main class to execute.
     * @return An object containing stdout and stderr from the execution.
     */
    public ExecutionResult runProject(String mainClassName) throws Exception {
        if (this.projectPath == null) {
            throw new IllegalStateException("Project has not been set up. Call setupProject() first.");
        }
        return projectManager.runMainClass(mainClassName);
    }

    /**
     * Cleans up the temporary project.
     */
    public void cleanupProject() throws Exception {
        projectManager.cleanupProject();
    }

    /**
     * Represents the result of executing a process.
     */
    public static class ExecutionResult {
        public final String stdout;
        public final String stderr;
        public final int exitCode;

        public ExecutionResult(String stdout, String stderr, int exitCode) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
        }

        @Override
        public String toString() {
            return "ExecutionResult{" +
                   "exitCode=" + exitCode +
                   ", stdout='" + stdout + '\'' +
                   ", stderr='" + stderr + '\'' +
                   '}';
        }
    }
}
