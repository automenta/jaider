package dumb.jaider.commands;

import dev.langchain4j.data.message.AiMessage;
import dumb.jaider.app.App;
import dumb.jaider.model.JaiderModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AddCommandTest {

    @Mock
    private AppContext mockAppContext; // Removed duplicate
    @Spy
    private final JaiderModel mockJaiderModel = new JaiderModel("null");
    @Mock
    private App mockApp;

    private AddCommand addCommand; // Removed @InjectMocks

    @BeforeEach
    void setUp() {
        addCommand = new AddCommand(); // Manually instantiate
        // Standard mocking behavior for AppContext
        when(mockAppContext.model()).thenReturn(mockJaiderModel);
        // when(mockAppContext.app()).thenReturn(mockApp); // Moved to specific tests
        // Clear the files set on the spied/real JaiderModel before each test
        mockJaiderModel.files.clear(); // Removed incorrect assignment, clear is enough for Spy
    }

    @Test
    void testExecute_nullArgs_showsUsage() {
        addCommand.execute(null, mockAppContext);
        verify(mockJaiderModel).addLog(AiMessage.from("Usage: /add <file1> [file2] ..."));
        verify(mockApp, never()).updateTokenCountPublic();
    }

    @Test
    void testExecute_emptyArgsString_showsUsage() {
        addCommand.execute("   ", mockAppContext); // Test with whitespace only
        verify(mockJaiderModel).addLog(AiMessage.from("Usage: /add <file1> [file2] ..."));
        verify(mockApp, never()).updateTokenCountPublic();
    }

    @Test
    void testExecute_withValidArgs_addsFilesToModelAndUpdatesTokenCount() {
        var projectRoot = Paths.get("/test/project");
        // Use doReturn().when() for spy, or ensure getDir() is not final if it's a real method call on spy
        doReturn(projectRoot).when(mockJaiderModel).getDir();
        when(mockAppContext.app()).thenReturn(mockApp); // Stubbing moved here


        var args = "fileOne.txt subdir/fileTwo.java";
        addCommand.execute(args, mockAppContext);

        assertTrue(mockJaiderModel.files.contains(projectRoot.resolve("fileOne.txt")));
        assertTrue(mockJaiderModel.files.contains(projectRoot.resolve("subdir/fileTwo.java")));
        assertEquals(2, mockJaiderModel.files.size());

        verify(mockApp).updateTokenCountPublic();
        verify(mockJaiderModel).addLog(AiMessage.from("Added to context: fileOne.txt, subdir/fileTwo.java"));
    }

    @Test
    void testExecute_withAlreadyAddedFile_doesNotAddDuplicatesAndLogsAppropriately() {
        var projectRoot = Paths.get("/test/project");
        var file1Path = projectRoot.resolve("file1.txt");

        // mockJaiderModel.files is already cleared in setUp
        mockJaiderModel.files.add(file1Path); // Pre-add file1.txt

        doReturn(projectRoot).when(mockJaiderModel).getDir();
        when(mockAppContext.app()).thenReturn(mockApp); // Stubbing moved here

        var args = "file1.txt newFile.css"; // file1.txt is a duplicate
        addCommand.execute(args, mockAppContext);

        assertTrue(mockJaiderModel.files.contains(file1Path));
        assertTrue(mockJaiderModel.files.contains(projectRoot.resolve("newFile.css")));
        assertEquals(2, mockJaiderModel.files.size(), "Set should contain two unique files.");

        verify(mockApp).updateTokenCountPublic(); // Called because newFile.css was added
        verify(mockJaiderModel).addLog(AiMessage.from("Added to context: file1.txt, newFile.css"));
        // If AddCommand is supposed to log about duplicates separately, that verify would go here.
        // Current AddCommand implementation doesn't explicitly log duplicates, just the final list.
    }
}
