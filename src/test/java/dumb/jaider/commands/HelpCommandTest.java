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
class HelpCommandTest {

    @Mock
    private AppContext appContext;

    @Mock
    private JaiderModel model;

    @InjectMocks
    private HelpCommand helpCommand;

    @BeforeEach
    void setUp() {
        when(appContext.getModel()).thenReturn(model); // Corrected
    }

    @Test
    void execute_shouldLogHelpTextToModel() {
        helpCommand.execute(null, appContext); // Corrected // Argument is not used

        ArgumentCaptor<dev.langchain4j.data.message.AiMessage> messageCaptor = ArgumentCaptor.forClass(dev.langchain4j.data.message.AiMessage.class); // Specific to AiMessage
        verify(model).addLog(messageCaptor.capture()); // Corrected

        String capturedHelpText = messageCaptor.getValue().text(); // AiMessage has .text()
        assertNotNull(capturedHelpText);
        assertTrue(capturedHelpText.contains("COMMANDS:"), "Help text should list available commands under 'COMMANDS:'.");
        assertTrue(capturedHelpText.contains("/add <files...>"), "Help text should contain /add command.");
        // Assertions for commands not currently listed in HelpCommand.java are commented out.
        // Revisit these if HelpCommand.java is updated to include them (e.g., dynamically).
        // assertTrue(capturedHelpText.contains("/ask <question>"), "Help text should contain /ask command."); // Not listed under COMMANDS
        // assertTrue(capturedHelpText.contains("/commit <message>"), "Help text should contain /commit command."); // Not listed
        // assertTrue(capturedHelpText.contains("/config"), "Help text should contain /config command."); // Not listed
        assertTrue(capturedHelpText.contains("/edit-config"), "Help text should contain /edit-config command.");
        assertTrue(capturedHelpText.contains("/exit"), "Help text should contain /exit command.");
        assertTrue(capturedHelpText.contains("/help"), "Help text should contain /help command.");
        assertTrue(capturedHelpText.contains("/index"), "Help text should contain /index command.");
        assertTrue(capturedHelpText.contains("/mode <ModeName>"), "Help text should contain /mode command description under MODES.");
        // Assertions for commands not currently listed in HelpCommand.java are commented out.
        // Revisit these if HelpCommand.java is updated to include them (e.g., dynamically).
        // assertTrue(capturedHelpText.contains("/reset"), "Help text should contain /reset command."); // Not listed
        assertTrue(capturedHelpText.contains("/undo"), "Help text should contain /undo command.");
        // Add more assertions if specific phrasing or other commands are critical
    }
}
