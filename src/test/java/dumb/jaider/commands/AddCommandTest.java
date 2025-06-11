package dumb.jaider.commands;

import dev.langchain4j.data.message.AiMessage;
import dumb.jaider.app.App;
import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.ui.UI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddCommandTest {

    @Mock
    private AppContext appContext;
    @Spy // Use Spy for JaiderModel to have real fields initialized
    private final JaiderModel model = new JaiderModel(Paths.get("target/test-project-add"));
    @Mock
    private Config config;
    @Mock
    private UI ui;
    @Mock
    private App app;

    @InjectMocks
    private AddCommand addCommand;

    @TempDir
    Path tempDir; // JUnit 5 temporary directory for testing file operations

    @BeforeEach
    void setUp() {
        // Ensure the test project directory exists
        try {
            Files.createDirectories(model.dir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create test project directory", e);
        }

        when(appContext.model()).thenReturn(model); // model is now a spy
        // when(appContext.getConfig()).thenReturn(config); // Unnecessary stub
        // when(appContext.getUi()).thenReturn(ui); // Unnecessary stub
        // when(appContext.getAppInstance()).thenReturn(app); // Moved to specific tests
    }

    @Test
    void execute_nullArguments_shouldLogUsage() {
        addCommand.execute(null, appContext);
        verify(model).addLog(argThat(msg -> {
            if (msg instanceof AiMessage) return ((AiMessage) msg).text().equals("[Jaider] Usage: /add <file1> [file2] ...");
            return false;
        }));
        verify(app, never()).updateTokenCountPublic();
    }

    @Test
    void execute_blankArgument_shouldLogUsage() {
        addCommand.execute("   ", appContext);
        verify(model).addLog(argThat(msg -> {
            if (msg instanceof AiMessage) return ((AiMessage) msg).text().equals("[Jaider] Usage: /add <file1> [file2] ...");
            return false;
        }));
        verify(app, never()).updateTokenCountPublic();
    }

    @Test
    void execute_oneValidFilePath_shouldAddFileAndLogSuccess() {
        when(appContext.app()).thenReturn(app); // Added here
        String filePathStr = "src/main/java/dumb/jaider/Test.java"; // Relative to projectDir
        Path expectedPath = model.dir.resolve(filePathStr);

        addCommand.execute(filePathStr, appContext);

        assertTrue(model.files.contains(expectedPath.normalize()));
        verify(app).updateTokenCountPublic();
        verify(model).addLog(argThat(msg -> {
            // Command logs the input string, not the resolved path for the success message content part
            if (msg instanceof AiMessage) return ((AiMessage) msg).text().equals("[Jaider] Added files to context: " + filePathStr);
            return false;
        }));
    }

    @Test
    void execute_multipleValidFilePaths_shouldAddAllFilesAndLogSuccess() {
        when(appContext.app()).thenReturn(app); // Added here
        String filePathStr1 = "src/main/java/dumb/jaider/Test1.java";
        String filePathStr2 = "docs/README.md";
        Path expectedPath1 = model.dir.resolve(filePathStr1);
        Path expectedPath2 = model.dir.resolve(filePathStr2);
        String expectedLogMessage = "[Jaider] Added files to context: " + filePathStr1 + ", " + filePathStr2;


        addCommand.execute(filePathStr1 + " " + filePathStr2, appContext);

        assertTrue(model.files.contains(expectedPath1.normalize()));
        assertTrue(model.files.contains(expectedPath2.normalize()));
        verify(app, times(1)).updateTokenCountPublic(); // Called once after all files
        verify(model).addLog(argThat(msg -> { // Called once with all files
            if (msg instanceof AiMessage) return ((AiMessage) msg).text().equals(expectedLogMessage);
            return false;
        }));
    }

    @Test
    void execute_pathOutsideProject_shouldNotAddAndLogWarning() {
        when(appContext.app()).thenReturn(app); // Added here
        // Use a temporary directory for a controlled "outside" path
        Path outsideFile = tempDir.resolve("outside.txt");
        try {
            Files.writeString(outsideFile, "content");
        } catch (IOException e) {
            fail("Could not create temp file for test");
        }
        String absolutePathStr = outsideFile.toAbsolutePath().toString();

        addCommand.execute(absolutePathStr, appContext);
        Path expectedAddedPath = Paths.get(absolutePathStr).normalize();
        assertTrue(model.files.contains(expectedAddedPath));
        verify(app).updateTokenCountPublic(); // It would still update tokens
        verify(model).addLog(argThat(msg -> {
            if (msg instanceof AiMessage) {
                String actualText = ((AiMessage) msg).text();
                // The AddCommand logs the exact string provided if it's a single argument
                String expectedText = "[Jaider] Added files to context: " + absolutePathStr;
                // System.out.println("DEBUG execute_pathOutsideProject_shouldNotAddAndLogWarning: \n  absolutePathStr (expected in log) = " + absolutePathStr + "\n  Actual Logged Msg Content = " + actualText);
                return actualText.equals(expectedText); // Using .equals() as it should be an exact match.
            }
            return false;
        }));
    }
}
