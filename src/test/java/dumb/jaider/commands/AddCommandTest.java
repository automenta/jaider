package dumb.jaider.commands;

import dumb.jaider.app.AppContext;
import dumb.jaider.app.App;
import dumb.jaider.model.JaiderModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class AddCommandTest {

    @Mock
    private AppContext mockAppContext;
    @Mock
    private JaiderModel mockJaiderModel;
    @Mock
    private App mockApp;

    @InjectMocks
    private AddCommand addCommand;

    @BeforeEach
    void setUp() {
        // Standard mocking behavior for AppContext
        when(mockAppContext.getJaiderModel()).thenReturn(mockJaiderModel);
        when(mockAppContext.getApp()).thenReturn(mockApp);
    }

    @Test
    void testExecute_nullArgs_showsUsage() {
        addCommand.execute(null, mockAppContext);
        verify(mockJaiderModel).addLog("Usage: /add <file1> [file2] ...");
        verify(mockApp, never()).updateTokenCountPublic();
    }

    @Test
    void testExecute_emptyArgsString_showsUsage() {
        addCommand.execute("   ", mockAppContext); // Test with whitespace only
        verify(mockJaiderModel).addLog("Usage: /add <file1> [file2] ...");
        verify(mockApp, never()).updateTokenCountPublic();
    }

    @Test
    void testExecute_withValidArgs_addsFilesToModelAndUpdatesTokenCount() {
        Path projectRoot = Paths.get("/test/project");
        when(mockJaiderModel.getDir()).thenReturn(projectRoot);

        Set<Path> modelFiles = new HashSet<>(); // Use a real set to check additions
        when(mockJaiderModel.getFiles()).thenReturn(modelFiles);

        String args = "fileOne.txt subdir/fileTwo.java";
        addCommand.execute(args, mockAppContext);

        assertTrue(modelFiles.contains(projectRoot.resolve("fileOne.txt")));
        assertTrue(modelFiles.contains(projectRoot.resolve("subdir/fileTwo.java")));
        assertEquals(2, modelFiles.size());

        verify(mockApp).updateTokenCountPublic();
        verify(mockJaiderModel).addLog("Added to context: fileOne.txt, subdir/fileTwo.java");
    }

    @Test
    void testExecute_withAlreadyAddedFile_doesNotAddDuplicatesAndLogsAppropriately() {
        Path projectRoot = Paths.get("/test/project");
        Path file1Path = projectRoot.resolve("file1.txt");

        Set<Path> modelFiles = new HashSet<>();
        modelFiles.add(file1Path); // Pre-add file1.txt

        when(mockJaiderModel.getDir()).thenReturn(projectRoot);
        when(mockJaiderModel.getFiles()).thenReturn(modelFiles);

        String args = "file1.txt newFile.css"; // file1.txt is a duplicate
        addCommand.execute(args, mockAppContext);

        assertTrue(modelFiles.contains(file1Path));
        assertTrue(modelFiles.contains(projectRoot.resolve("newFile.css")));
        assertEquals(2, modelFiles.size(), "Set should contain two unique files.");

        verify(mockApp).updateTokenCountPublic(); // Called because newFile.css was added
        // Verify log message for newly added files. AddCommand might log duplicates differently or not at all.
        // Assuming it logs only newly added files or a combined message.
        // If AddCommand is expected to inform about duplicates, that would be another verify.
        verify(mockJaiderModel).addLog(contains("Added to context:"));
        verify(mockJaiderModel).addLog(contains("newFile.css"));
        // Optionally, if it logs about existing files:
        // verify(mockJaiderModel).addLog(contains("file1.txt already in context"));
    }
}
