package dumb.jaider.commands;

import dumb.jaider.AppContext;
import dumb.jaider.JaiderModel;
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
        when(appContext.model()).thenReturn(model);
    }

    @Test
    void execute_shouldLogDisabledMessage() {
        undoCommand.execute(null); // Argument is not currently used by UndoCommand

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(model).logUser(messageCaptor.capture());

        String capturedMessage = messageCaptor.getValue();
        assertNotNull(capturedMessage);
        assertEquals("Undo functionality is temporarily disabled. It will be re-enabled in a future version.", capturedMessage);
    }

    @Test
    void execute_withArguments_shouldStillLogDisabledMessage() {
        // Verify that arguments don't change the "disabled" message.
        undoCommand.execute("some/file/path.txt");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(model).logUser(messageCaptor.capture());

        String capturedMessage = messageCaptor.getValue();
        assertNotNull(capturedMessage);
        assertEquals("Undo functionality is temporarily disabled. It will be re-enabled in a future version.", capturedMessage);
    }
}
