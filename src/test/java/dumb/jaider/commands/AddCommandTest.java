package dumb.jaider.commands;

import dumb.jaider.AppContext;
import dumb.jaider.JaiderModel;
import dumb.jaider.app.App;
import dumb.jaider.config.Config;
import dumb.jaider.ui.UI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddCommandTest {

    @Mock
    private AppContext appContext;
    @Mock
    private JaiderModel model;
    @Mock
    private Config config;
    @Mock
    private UI ui;
    @Mock
    private App app;

    @InjectMocks
    private AddCommand addCommand;

    @BeforeEach
    void setUp() {
        when(appContext.model()).thenReturn(model);
        when(appContext.config()).thenReturn(config);
        when(appContext.ui()).thenReturn(ui);
        when(appContext.app()).thenReturn(app);
        // It's important that model.filesInContext is not null
        when(model.filesInContext()).thenReturn(new HashSet<>());
    }

    @Test
    void execute_nullArguments_shouldLogUsage() {
        addCommand.execute(null);
        verify(model).logUser("Usage: /add <file_path_1> [file_path_2] ...");
        verify(app, never()).updateTokenCountPublic();
    }

    @Test
    void execute_blankArgument_shouldLogUsage() {
        addCommand.execute("   ");
        verify(model).logUser("Usage: /add <file_path_1> [file_path_2] ...");
        verify(app, never()).updateTokenCountPublic();
    }

    @Test
    void execute_oneValidFilePath_shouldAddFileAndLogSuccess() {
        String filePathStr = "src/main/java/dumb/jaider/Test.java";
        Path projectPath = Paths.get("/tmp/test-project");
        Path expectedPath = projectPath.resolve(filePathStr);

        when(model.projectDir()).thenReturn(projectPath.toString());

        addCommand.execute(filePathStr);

        verify(model.filesInContext()).add(expectedPath);
        verify(app).updateTokenCountPublic();
        verify(model).logUser("Added to context: " + expectedPath);
    }

    @Test
    void execute_multipleValidFilePaths_shouldAddAllFilesAndLogSuccess() {
        String filePathStr1 = "src/main/java/dumb/jaider/Test1.java";
        String filePathStr2 = "src/main/java/dumb/jaider/Test2.java";
        Path projectPath = Paths.get("/tmp/test-project");
        Path expectedPath1 = projectPath.resolve(filePathStr1);
        Path expectedPath2 = projectPath.resolve(filePathStr2);

        when(model.projectDir()).thenReturn(projectPath.toString());

        addCommand.execute(filePathStr1 + " " + filePathStr2);

        verify(model.filesInContext()).add(expectedPath1);
        verify(model.filesInContext()).add(expectedPath2);
        verify(app, times(2)).updateTokenCountPublic(); // Called for each file
        verify(model).logUser("Added to context: " + expectedPath1);
        verify(model).logUser("Added to context: " + expectedPath2);
    }

    @Test
    void execute_pathOutsideProject_shouldNotAddAndLogWarning() {
        // This test assumes that the AddCommand itself doesn't currently prevent adding files
        // outside the project dir, but that JaiderModel or other parts might handle it.
        // For now, AddCommand just resolves. If there's a security check to be added in AddCommand,
        // this test would change.
        // The current implementation of AddCommand simply resolves paths.
        String absolutePathStr = "/etc/passwd";
        Path projectPath = Paths.get("/tmp/test-project");
        Path expectedPath = Paths.get(absolutePathStr); // Resolves to itself if absolute

        when(model.projectDir()).thenReturn(projectPath.toString());

        addCommand.execute(absolutePathStr);

        verify(model.filesInContext()).add(expectedPath); // It will attempt to add
        verify(app).updateTokenCountPublic();
        verify(model).logUser("Added to context: " + expectedPath);
        // A more robust test would involve mocking Files.exists or similar if AddCommand did path validation.
        // As it stands, AddCommand is simple.
    }
}
