package dumb.jaider.integration;

import dumb.jaider.demo.DemoRunner; // For DemoRunner.ExecutionResult

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProjectManager {

    private static final String BASE_TEMP_DIR = System.getProperty("java.io.tmpdir");
    // It's good practice to have a distinct subdirectory for all demos if many run concurrently.
    private static final Path JAIDER_DEMOS_ROOT_TEMP_DIR = Paths.get(BASE_TEMP_DIR, "JaiderDemos");
    private Path projectDir; // Specific path for this instance's project
    private final String projectIdentifier;


    /**
     * Constructor for ProjectManager.
     * @param projectIdentifier A unique name or identifier for the project, used to create its directory.
     */
    public ProjectManager(String projectIdentifier) {
        this.projectIdentifier = projectIdentifier;
        // projectDir will be set in createTemporaryProject
    }

    /**
     * Creates the temporary project directory.
     * The directory will be named using the projectIdentifier.
     * @throws IOException if the directory cannot be created.
     */
    public void createTemporaryProject() throws IOException {
        if (!Files.exists(JAIDER_DEMOS_ROOT_TEMP_DIR)) {
            Files.createDirectories(JAIDER_DEMOS_ROOT_TEMP_DIR);
        }
        this.projectDir = JAIDER_DEMOS_ROOT_TEMP_DIR.resolve(this.projectIdentifier);
        if (Files.exists(this.projectDir)) {
            // Clean up existing directory if it's there from a previous failed run
            cleanupProjectInternal(this.projectDir);
        }
        Files.createDirectories(this.projectDir);
        // Create basic structure
        Files.createDirectories(this.projectDir.resolve("src/main/java"));
        Files.createDirectories(this.projectDir.resolve("target/classes"));
        System.out.println("ProjectManager: Created temporary project at " + this.projectDir.toAbsolutePath());
    }

    /**
     * @return The root path of the managed project.
     * @throws IllegalStateException if the project directory has not been initialized.
     */
    public Path getProjectDir() {
        if (this.projectDir == null) {
            throw new IllegalStateException("Project directory has not been initialized. Call createTemporaryProject() first.");
        }
        return this.projectDir;
    }

    /**
     * Writes content to a file within the project.
     * @param directory The parent directory for the file, relative to project root.
     * @param fileName The name of the file.
     * @param content The content to write.
     * @throws IOException if writing fails.
     * @throws IllegalStateException if the project directory has not been initialized.
     */
    public void writeFile(Path directory, String fileName, String content) throws IOException {
        if (this.projectDir == null) {
            throw new IllegalStateException("Project directory has not been initialized.");
        }
        // Note: The 'directory' parameter in DemoRunner.addSourceFile is already resolved to an absolute path.
        // Here, we ensure the directory exists before writing.
        Path parentDir = directory; // This is already an absolute path like /tmp/JaiderDemos/DemoX/src/main/java/com/example
        if (!Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        Path filePath = parentDir.resolve(fileName);
        Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("ProjectManager: Wrote file " + filePath.toAbsolutePath());
    }


    /**
     * Compiles the Java source files in the project.
     * Assumes source files are in "src/main/java" and output to "target/classes".
     * @return A CompletableFuture that will complete with true if compilation is successful, false otherwise.
     * @throws IllegalStateException if the project directory has not been initialized.
     */
    public CompletableFuture<Boolean> compileProject() {
        if (this.projectDir == null) {
            throw new IllegalStateException("Project directory has not been initialized.");
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path srcDir = projectDir.resolve("src/main/java");
                Path classesDir = projectDir.resolve("target/classes");

                if (!Files.exists(srcDir) || !Files.isDirectory(srcDir)) {
                    System.err.println("ProjectManager: Source directory not found: " + srcDir);
                    return false;
                }
                 if (!Files.exists(classesDir)) {
                    Files.createDirectories(classesDir);
                }

                List<String> javaFiles;
                try (Stream<Path> walk = Files.walk(srcDir)) {
                    javaFiles = walk
                        .filter(p -> p.toString().endsWith(".java"))
                        .map(Path::toString)
                        .collect(Collectors.toList());
                }

                if (javaFiles.isEmpty()) {
                    System.out.println("ProjectManager: No Java files found to compile in " + srcDir);
                    return true; // No files to compile is not an error for this basic PM
                }

                // Construct javac command
                // Example: javac -d target/classes src/main/java/com/example/MyClass.java ...
                ProcessBuilder pb = new ProcessBuilder();
                List<String> command = new java.util.ArrayList<>();
                command.add("javac");
                command.add("-d");
                command.add(classesDir.toString());
                command.add("-cp"); // Classpath
                command.add(classesDir.toString()); // Add target/classes to classpath for multiple files
                // Add Junit to classpath if needed for tests, but DemoRunner is for main code
                command.addAll(javaFiles);
                pb.command(command);
                pb.directory(projectDir.toFile()); // Run from project root

                System.out.println("ProjectManager: Compiling project in " + projectDir + " with command: " + String.join(" ", command));
                Process process = pb.start();

                String stdout = new String(process.getInputStream().readAllBytes());
                String stderr = new String(process.getErrorStream().readAllBytes());
                int exitCode = process.waitFor();

                if (!stdout.isEmpty()) System.out.println("ProjectManager Compile STDOUT:\n" + stdout);
                if (!stderr.isEmpty()) System.err.println("ProjectManager Compile STDERR:\n" + stderr);

                System.out.println("ProjectManager: Compilation finished with exit code " + exitCode);
                return exitCode == 0;

            } catch (IOException | InterruptedException e) {
                System.err.println("ProjectManager: Error during compilation: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Runs a compiled Java class from the project.
     * @param mainClassName The fully qualified name of the main class (e.g., "com.example.Main").
     * @return DemoRunner.ExecutionResult containing stdout, stderr, and exit code.
     * @throws IOException if an I/O error occurs.
     * @throws InterruptedException if the process is interrupted.
     * @throws IllegalStateException if the project directory has not been initialized.
     */
    public DemoRunner.ExecutionResult runMainClass(String mainClassName) throws IOException, InterruptedException {
        if (this.projectDir == null) {
            throw new IllegalStateException("Project directory has not been initialized.");
        }
        Path classesDir = projectDir.resolve("target/classes");

        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-cp",
                classesDir.toString(),
                mainClassName
        );
        pb.directory(projectDir.toFile());

        System.out.println("ProjectManager: Running " + mainClassName + " in " + projectDir);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes());
        String stderr = new String(process.getErrorStream().readAllBytes());
        int exitCode = process.waitFor();

        System.out.println("ProjectManager: Execution of " + mainClassName + " finished with exit code " + exitCode);
        if (!stdout.isEmpty()) System.out.println("ProjectManager Run STDOUT:\n" + stdout);
        if (!stderr.isEmpty()) System.err.println("ProjectManager Run STDERR:\n" + stderr);

        return new DemoRunner.ExecutionResult(stdout, stderr, exitCode);
    }

    /**
     * Cleans up (deletes) the temporary project directory.
     * @throws IOException if deletion fails.
     */
    public void cleanupProject() throws IOException {
        if (this.projectDir != null && Files.exists(this.projectDir)) {
            System.out.println("ProjectManager: Cleaning up project at " + this.projectDir.toAbsolutePath());
            cleanupProjectInternal(this.projectDir);
            this.projectDir = null; // Reset after cleanup
        } else {
            System.out.println("ProjectManager: No project directory to clean up or already cleaned.");
        }
    }

    private void cleanupProjectInternal(Path pathToBeDeleted) throws IOException {
        try (Stream<Path> walk = Files.walk(pathToBeDeleted)) {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }
}
