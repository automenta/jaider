package dumb.jaider.commands;

import dumb.jaider.commands.AppContext;
import dumb.jaider.model.JaiderModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class UndoCommandTest {

    @Mock
    private AppContext appContext;

    @Mock
    private JaiderModel model;

    @InjectMocks
    private UndoCommand undoCommand;

    @BeforeEach
    void setUp() {
        when(appContext.getModel()).thenReturn(model); // Corrected
    }

    @Test
    void execute_shouldLogDisabledMessage() {
        undoCommand.execute(null, appContext); // Corrected // Argument is not currently used by UndoCommand

        ArgumentCaptor<dev.langchain4j.data.message.AiMessage> messageCaptor = ArgumentCaptor.forClass(dev.langchain4j.data.message.AiMessage.class); // Specific to AiMessage
        verify(model).addLog(messageCaptor.capture()); // Corrected

        String capturedMessage = messageCaptor.getValue().text(); // AiMessage has .text()
        assertNotNull(capturedMessage);
        assertEquals("Undo functionality is temporarily disabled. It will be re-enabled in a future version.", capturedMessage);
    }

    @Test
    void execute_withArguments_shouldStillLogDisabledMessage() {
        // Verify that arguments don't change the "disabled" message.
        undoCommand.execute("some/file/path.txt", appContext); // Corrected

        ArgumentCaptor<dev.langchain4j.data.message.AiMessage> messageCaptor = ArgumentCaptor.forClass(dev.langchain4j.data.message.AiMessage.class); // Specific to AiMessage
        verify(model).addLog(messageCaptor.capture()); // Corrected

        String capturedMessage = messageCaptor.getValue().text(); // AiMessage has .text()
        assertNotNull(capturedMessage);
        assertEquals("Undo functionality is temporarily disabled. It will be re-enabled in a future version.", capturedMessage);
    }
}
