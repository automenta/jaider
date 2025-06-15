package dumb.jaider.commands;

import dumb.jaider.app.App; // Added import
import dumb.jaider.commands.AppContext;
import dumb.jaider.model.JaiderModel;
import dev.langchain4j.data.message.AiMessage; // Added import
import dev.langchain4j.data.message.ChatMessage;
// UserMessage import is not strictly needed if only AiMessage is used for verification now
// import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import java.util.HashSet; // Added import
import java.util.Arrays; // Added for Arrays.asList
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HelpCommandTest {

    @Mock
    private AppContext mockAppContext;
    @Mock
    private JaiderModel mockJaiderModel;
    @Mock // Added mock for App
    private App mockApp;

    @InjectMocks
    private HelpCommand helpCommand;

    @BeforeEach
    void setUp() {
        when(mockAppContext.model()).thenReturn(mockJaiderModel);
        when(mockAppContext.app()).thenReturn(mockApp); // Stubbing app call
        // Provide a default set of agent names for the tests
        when(mockApp.getAvailableAgentNames()).thenReturn(new HashSet<>(java.util.Arrays.asList("coder", "architect", "ask")));
    }

    @Test
    void testExecute_logsHelpMessageForEachCommand() {
        helpCommand.execute("", mockAppContext); // Argument to execute is typically ignored by HelpCommand

        // Verify the main help title is logged
        verify(mockJaiderModel).addLog(AiMessage.from(HelpCommand.ANSI_BOLD + "Jaider Commands:" + HelpCommand.ANSI_RESET));

        // Verify that addLog is called for each command's help string.
        HelpCommand.COMMANDS_HELP.forEach((cmd, desc) -> {
            verify(mockJaiderModel).addLog(AiMessage.from(HelpCommand.ANSI_BOLD + cmd + HelpCommand.ANSI_RESET + " - " + desc));
        });

        // Verify the "MODES" help block is logged as one AiMessage
        String availableModes = "architect, ask, coder"; // Based on setUp
        String modesHelp = String.format("""

            %sMODES:%s
            Switch modes with %s/mode <ModeName>%s. Available modes: %s.
            - Coder: The default mode for writing and fixing code.
            - Architect: A read-only mode for high-level questions about the codebase.
            - Ask: A simple Q&A mode with no access to your files.
            """, HelpCommand.ANSI_BOLD, HelpCommand.ANSI_RESET, HelpCommand.ANSI_BOLD, HelpCommand.ANSI_RESET, availableModes);
        verify(mockJaiderModel).addLog(AiMessage.from(modesHelp));

        // Total calls: 1 (title) + size of COMMANDS_HELP + 1 (modes block)
        verify(mockJaiderModel, times(1 + HelpCommand.COMMANDS_HELP.size() + 1)).addLog(any(AiMessage.class));
    }

    @Test
    void testExecute_withArgs_behaviorIsSameAsNoArgs() {
        // HelpCommand is expected to ignore any arguments passed to it.
        helpCommand.execute("some arguments that should be ignored", mockAppContext);

        verify(mockJaiderModel).addLog(AiMessage.from(HelpCommand.ANSI_BOLD + "Jaider Commands:" + HelpCommand.ANSI_RESET));

        HelpCommand.COMMANDS_HELP.forEach((cmd, desc) -> {
            verify(mockJaiderModel).addLog(AiMessage.from(HelpCommand.ANSI_BOLD + cmd + HelpCommand.ANSI_RESET + " - " + desc));
        });

        String availableModes = "architect, ask, coder"; // Based on setUp
        String modesHelp = String.format("""

            %sMODES:%s
            Switch modes with %s/mode <ModeName>%s. Available modes: %s.
            - Coder: The default mode for writing and fixing code.
            - Architect: A read-only mode for high-level questions about the codebase.
            - Ask: A simple Q&A mode with no access to your files.
            """, HelpCommand.ANSI_BOLD, HelpCommand.ANSI_RESET, HelpCommand.ANSI_BOLD, HelpCommand.ANSI_RESET, availableModes);
        verify(mockJaiderModel).addLog(AiMessage.from(modesHelp));

        verify(mockJaiderModel, times(1 + HelpCommand.COMMANDS_HELP.size() + 1)).addLog(any(AiMessage.class));
    }
}
