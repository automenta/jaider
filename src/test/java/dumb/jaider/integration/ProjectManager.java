package dumb.jaider.integration;

import dumb.jaider.service.BuildManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

public class ProjectManager {
    private static final Logger logger = LoggerFactory.getLogger(ProjectManager.class);
    private Path projectDirectory;
    private final BuildManagerService buildManagerService;

    public ProjectManager() {
        this.buildManagerService = new BuildManagerService(); // Using the actual service
    }

    public Path createTemporaryProject(String baseName) throws IOException {
        this.projectDirectory = Files.createTempDirectory(baseName + "_");
        logger.info("Temporary project directory created at: {}", projectDirectory.toAbsolutePath());
        return this.projectDirectory;
    }

    public void cleanupProject() {
        if (projectDirectory != null && Files.exists(projectDirectory)) {
            logger.info("Cleaning up project directory: {}", projectDirectory.toAbsolutePath());
            try (Stream<Path> walk = Files.walk(projectDirectory)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.warn("Failed to delete path during cleanup: {} - {}", path, e.getMessage());
                        }
                    });
                logger.info("Project directory cleaned up successfully.");
            } catch (IOException e) {
                logger.error("Error walking project directory for cleanup: {}", e.getMessage(), e);
            }
        } else {
            logger.info("No project directory to clean up or directory does not exist.");
        }
    }

    public Path saveJavaFile(String packageName, String className, String code) throws IOException {
        if (projectDirectory == null) {
            throw new IllegalStateException("Project directory not initialized. Call createTemporaryProject first.");
        }
        Path packagePath = projectDirectory.resolve("src").resolve("main").resolve("java").resolve(packageName.replace('.', '/'));
        Files.createDirectories(packagePath);
        Path javaFilePath = packagePath.resolve(className + ".java");
        Files.writeString(javaFilePath, code, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        logger.info("Saved Java file to: {}", javaFilePath.toAbsolutePath());
        return javaFilePath;
    }

    public void createPomFile(String pomContent) throws IOException {
        if (projectDirectory == null) {
            throw new IllegalStateException("Project directory not initialized.");
        }
        Path pomPath = projectDirectory.resolve("pom.xml");
        Files.writeString(pomPath, pomContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        logger.info("Created pom.xml at: {}", pomPath.toAbsolutePath());
    }

    public dumb.jaider.model.JaiderModel getJaiderModelForProject() {
        if (projectDirectory == null) {
            throw new IllegalStateException("Project directory not initialized.");
        }
        // Create a minimal JaiderModel instance for BuildManagerService
        // Pass null for UI, as it's not directly used by BuildManagerService compile/test methods
        // However, if other parts of JaiderModel or its dependencies expect a non-null UI, this might need adjustment.
        // For now, assuming BuildManagerService primarily needs the project directory path from JaiderModel.
        // dumb.jaider.ui.UI mockUi = null; // UI instance not directly needed for this constructor
        dumb.jaider.model.JaiderModel model = new dumb.jaider.model.JaiderModel(projectDirectory);
        // model.dir = projectDirectory; // dir is final, set via constructor
        return model;
    }

    public BuildManagerService.BuildResult compile() {
        if (projectDirectory == null) {
            logger.error("Project directory not initialized. Cannot compile.");
            return new BuildManagerService.BuildResult(false, "Project directory not initialized.", -1);
        }
        logger.info("Attempting to compile project in directory: {}", projectDirectory);
        // BuildManagerService expects a JaiderModel with the directory set.
        return buildManagerService.compileProject(getJaiderModelForProject());
    }

    public Path getProjectDir() {
        return projectDirectory;
    }
}
