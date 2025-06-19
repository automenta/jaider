package dumb.jaider.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class InteractiveDemoTest {

    private InteractiveDemo demo;
    private Path singleTestTempDir; // Used if @TempDir is not suitable for a specific test's lifecycle

    @BeforeEach
    void setUp() {
        demo = new InteractiveDemo();
        demo.cleanup = true;
        // For tests that don't use @TempDir, we can set up a common temp dir here if needed
        // However, for setup/cleanup test, it's better to let the method create its own
    }

    @Test
    void testSetupAndCleanupTemporaryDirectory() {
        // 1. Call setupTemporaryDirectory()
        assertTrue(demo.setupTemporaryDirectory(), "setupTemporaryDirectory should return true on success");
        Path createdDir = demo.temporaryDirectoryPath;

        // 2. Assert that a directory is created and the path is not null
        assertNotNull(createdDir, "Temporary directory path should not be null after setup");
        assertTrue(Files.exists(createdDir), "Temporary directory should exist after setup");
        assertTrue(Files.isDirectory(createdDir), "Temporary path should be a directory");

        // 3. Call cleanupTemporaryDirectory()
        demo.cleanupTemporaryDirectory();

        // 4. Assert that the directory no longer exists
        assertFalse(Files.exists(createdDir), "Temporary directory should not exist after cleanup");
    }

    @Test
    void testVerifyProjectGeneration_Success(@TempDir Path tempDir) throws IOException {
        // 1. Create a temporary file with some content
        Path testFile = tempDir.resolve("test_project_output.txt");
        Files.write(testFile, Collections.singletonList("Some content"));

        // 2. Call verifyProjectGeneration()
        // The verifyProjectGeneration method in InteractiveDemo prints to System.out/err,
        // which is fine for a demo app but less ideal for pure unit tests.
        // We are primarily interested in its boolean return value.
        assertTrue(demo.verifyProjectGeneration(testFile), "verifyProjectGeneration should return true for a non-empty file");
    }

    @Test
    void testVerifyProjectGeneration_Failure_EmptyFile(@TempDir Path tempDir) throws IOException {
        // 1. Create an empty temporary file
        Path emptyFile = tempDir.resolve("empty_project_output.txt");
        Files.createFile(emptyFile);

        // 2. Call verifyProjectGeneration()
        assertFalse(demo.verifyProjectGeneration(emptyFile), "verifyProjectGeneration should return false for an empty file");
    }

    @Test
    void testVerifyProjectGeneration_Failure_FileNotFound(@TempDir Path tempDir) {
        // 1. Create a non-existent file path
        Path nonExistentFile = tempDir.resolve("non_existent_file.txt");

        // 2. Call verifyProjectGeneration()
        assertFalse(demo.verifyProjectGeneration(nonExistentFile), "verifyProjectGeneration should return false for a non-existent file");
    }

    @Test
    void testVerifyProjectGeneration_Failure_NullPath() {
        // Call verifyProjectGeneration() with a null path
        assertFalse(demo.verifyProjectGeneration(null), "verifyProjectGeneration should return false for a null path");
    }

    // Note: To fully test cleanupTemporaryDirectory, especially its error handling for individual file deletions,
    // would require more complex setup (e.g., mocking Files.delete or creating undeletable files),
    // which is beyond the scope of these initial tests. The current testSetupAndCleanupTemporaryDirectory
    // covers the main success scenario.
}
