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
class HelpCommandTest {

    @Mock
    private AppContext appContext;

    @Mock
    private JaiderModel model;

    @InjectMocks
    private HelpCommand helpCommand;

    @BeforeEach
    void setUp() {
        when(appContext.model()).thenReturn(model);
    }

    @Test
    void execute_shouldLogHelpTextToModel() {
        helpCommand.execute(null); // Argument is not used

        ArgumentCaptor<String> helpTextCaptor = ArgumentCaptor.forClass(String.class);
        verify(model).logUser(helpTextCaptor.capture());

        String capturedHelpText = helpTextCaptor.getValue();
        assertNotNull(capturedHelpText);
        assertTrue(capturedHelpText.contains("Available commands:"), "Help text should list available commands.");
        assertTrue(capturedHelpText.contains("/add <file_path_1> [file_path_2] ..."), "Help text should contain /add command.");
        assertTrue(capturedHelpText.contains("/ask <question>"), "Help text should contain /ask command.");
        assertTrue(capturedHelpText.contains("/commit <message>"), "Help text should contain /commit command.");
        assertTrue(capturedHelpText.contains("/config"), "Help text should contain /config command.");
        assertTrue(capturedHelpText.contains("/editconf"), "Help text should contain /editconf command.");
        assertTrue(capturedHelpText.contains("/exit"), "Help text should contain /exit command.");
        assertTrue(capturedHelpText.contains("/help"), "Help text should contain /help command.");
        assertTrue(capturedHelpText.contains("/index"), "Help text should contain /index command.");
        assertTrue(capturedHelpText.contains("/mode <agent_name>"), "Help text should contain /mode command.");
        assertTrue(capturedHelpText.contains("/reset"), "Help text should contain /reset command.");
        assertTrue(capturedHelpText.contains("/undo <file_path>"), "Help text should contain /undo command.");
        // Add more assertions if specific phrasing or other commands are critical
    }
}
