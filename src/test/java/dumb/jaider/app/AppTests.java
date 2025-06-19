package dumb.jaider.app;

import dumb.jaider.model.JaiderModel;
import dumb.jaider.ui.UI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AppTests {

    @Mock
    private UI mockUi;

    // Using @Spy for JaiderModel if we want to mock parts of it,
    // but for these tests, a real instance that we can inspect is better.
    // We need to ensure JaiderModel is initialized correctly for App.
    private JaiderModel realModel;

    private App app;

    // Helper to create a temporary valid directory for testing
    private Path createTempDirectory(String prefix) throws IOException {
        return java.nio.file.Files.createTempDirectory(prefix);
    }

    @BeforeEach
    void setUp() throws IOException {
        // Initialize JaiderModel with a default config and a real, temporary path
        Path initialPath = createTempDirectory("initialAppDir");
        realModel = new JaiderModel(initialPath, "TestGlobalConfig");

        // Manually create App instance and inject mocks/spies
        // This is a simplified setup. A full DI setup might be more complex.
        // For App(UI, String...), the String... are originalArgs, can be empty for tests.
        app = new App(mockUi, ""); // Pass mock UI

        // Replace the model in App with our realModel instance
        // This requires a setter or making the field accessible, which is not ideal.
        // For now, we'll assume App uses the model it creates internally,
        // and we'll have to re-initialize App for tests that modify the model's path
        // OR find a way to inject the model.
        // The current App constructor creates its own JaiderModel.
        // Let's re-initialize app with a new model for specific tests if needed,
        // or rely on testing the model that App creates.

        // For the sake of these tests, we will test the App's internally created model.
        // We can access it via app.getModel().
    }

    @Test
    void switchProject_userProvidesValidNewDirectory_shouldUpdateModelAndRedraw() throws IOException {
        Path newValidDir = createTempDirectory("newValidDir");
        String newValidPathString = newValidDir.toString();

        JaiderModel appModel = app.getModel(); // Get the model instance from App

        // Ensure the directory is different before the call
        assertNotEquals(newValidDir.toAbsolutePath(), appModel.getDir().toAbsolutePath());

        when(mockUi.switchProjectDirectory(appModel.getDir().toString()))
                .thenReturn(CompletableFuture.completedFuture(newValidPathString));

        app.switchProject();

        // Wait for CompletableFuture to complete
        CompletableFuture.allOf().join(); // Simple way to wait for async operations if any were chained

        verify(mockUi).switchProjectDirectory(anyString());
        // Verify model's directory was updated
        assertEquals(newValidPathString, appModel.getDir().toString(), "Model directory should be updated to the new path.");
        assertFalse(appModel.isIndexed, "isIndexed should be false after directory switch.");
        assertTrue(appModel.files.isEmpty(), "Model files should be cleared.");
        verify(mockUi).redraw(appModel);

        // Clean up created directory
        java.nio.file.Files.deleteIfExists(newValidDir);
    }

    @Test
    void switchProject_userCancels_shouldNotUpdateModelAndRedraw() throws IOException {
        JaiderModel appModel = app.getModel();
        Path originalPath = appModel.getDir().toAbsolutePath(); // Capture original path

        when(mockUi.switchProjectDirectory(originalPath.toString()))
                .thenReturn(CompletableFuture.completedFuture(null)); // User cancels

        app.switchProject();
        CompletableFuture.allOf().join();

        verify(mockUi).switchProjectDirectory(originalPath.toString());
        // Verify model's directory remains unchanged
        assertEquals(originalPath, appModel.getDir().toAbsolutePath(), "Model directory should remain unchanged if user cancels.");
        verify(mockUi).redraw(appModel); // Redraw happens even on cancel to update status messages
    }

    @Test
    void switchProject_userProvidesEmptyPath_shouldNotUpdateModelAndRedraw() throws IOException {
        JaiderModel appModel = app.getModel();
        Path originalPath = appModel.getDir().toAbsolutePath();

        when(mockUi.switchProjectDirectory(originalPath.toString()))
                .thenReturn(CompletableFuture.completedFuture("")); // User provides empty path

        app.switchProject();
        CompletableFuture.allOf().join();

        verify(mockUi).switchProjectDirectory(originalPath.toString());
        assertEquals(originalPath, appModel.getDir().toAbsolutePath(), "Model directory should remain unchanged for empty path.");
        verify(mockUi).redraw(appModel);
    }

    @Test
    void switchProject_userProvidesInvalidDirectoryPath_shouldNotUpdateModelAndRedraw() throws IOException {
        JaiderModel appModel = app.getModel();
        Path originalPath = appModel.getDir().toAbsolutePath();
        String invalidPathString = "path/that/does/not/exist/and/is/not/a/directory";

        when(mockUi.switchProjectDirectory(originalPath.toString()))
                .thenReturn(CompletableFuture.completedFuture(invalidPathString));

        app.switchProject();
        CompletableFuture.allOf().join();

        verify(mockUi).switchProjectDirectory(originalPath.toString());
        assertEquals(originalPath, appModel.getDir().toAbsolutePath(), "Model directory should remain unchanged for invalid path.");
        assertTrue(appModel.log.stream().anyMatch(logMsg -> logMsg.text().contains("Selected path is not a valid directory")), "Log should contain invalid path message.");
        verify(mockUi).redraw(appModel);
    }


    @Test
    void showGlobalConfigSettings_shouldCallUIShowGlobalConfigurationAndRedraw() {
        JaiderModel appModel = app.getModel();
        when(mockUi.showGlobalConfiguration()).thenReturn(CompletableFuture.completedFuture(null));

        app.showGlobalConfigSettings();
        CompletableFuture.allOf().join();

        verify(mockUi).showGlobalConfiguration();
        assertTrue(appModel.log.stream().anyMatch(logMsg -> logMsg.text().contains("Current global config (from model)")), "Log should show current global config.");
        verify(mockUi).redraw(appModel); // Redraw happens after UI interaction
    }

    @BeforeEach
    void tearDown() throws IOException {
        // Clean up the initial directory created in App's JaiderModel if it's not null
        // This is tricky because App creates its own model.
        // The JaiderModel instance created by App() uses Paths.get("").toAbsolutePath() if not given one.
        // The `realModel`'s initialPath in this test setup is not what App uses unless we can inject it.
        // For robust cleanup, test methods creating directories should clean them up.
        // The initialPath for `realModel` is not used by `app` instance.
        // `app.getModel().getDir()` is the one to clean if it was created by a test.
        // For now, relying on test-specific cleanup for created directories.
    }
}
