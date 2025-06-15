package dumb.jaider.commands;

import dumb.jaider.commands.AppContext;
import dumb.jaider.model.JaiderModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
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

    @InjectMocks
    private HelpCommand helpCommand;

    @BeforeEach
    void setUp() {
        when(mockAppContext.model()).thenReturn(mockJaiderModel);
    }

    @Test
    void testExecute_logsHelpMessageForEachCommand() {
        helpCommand.execute("", mockAppContext); // Argument to execute is typically ignored by HelpCommand

        // Verify the main help title is logged
        verify(mockJaiderModel).addLog(UserMessage.from(HelpCommand.ANSI_BOLD + "Jaider Commands:" + HelpCommand.ANSI_RESET));

        // Verify that addLog is called for each command's help string.
        // This relies on the internal structure of COMMANDS_HELP in HelpCommand.
        // A more robust approach would be to iterate COMMANDS_HELP if it were accessible,
        // but for now, we verify based on the known commands.

        // Example verifications for a few commands:
        verify(mockJaiderModel).addLog(UserMessage.from(HelpCommand.ANSI_BOLD + "/add <file1> [file2] ..." + HelpCommand.ANSI_RESET + " - Add file(s) to the context. Supports glob patterns."));
        verify(mockJaiderModel).addLog(UserMessage.from(HelpCommand.ANSI_BOLD + "/run [args]" + HelpCommand.ANSI_RESET + " - Run the validation command defined in config, passing optional arguments."));
        verify(mockJaiderModel).addLog(UserMessage.from(HelpCommand.ANSI_BOLD + "/editconfig" + HelpCommand.ANSI_RESET + " - Edit the .jaider.json configuration file."));
        // ... add more for other commands if desired, or use a times() verification.

        // Verify based on the number of entries in COMMANDS_HELP
        // Assuming COMMANDS_HELP is a Map or similar structure.
        // If COMMANDS_HELP is private, we count based on the known number of help entries.
        // From the provided HelpCommand snippet, there are 11 command help entries.
        // So, 1 call for the title + 11 calls for each command's help text.
        verify(mockJaiderModel, times(1 + HelpCommand.COMMANDS_HELP.size())).addLog(any(ChatMessage.class));
    }

    @Test
    void testExecute_withArgs_behaviorIsSameAsNoArgs() {
        // HelpCommand is expected to ignore any arguments passed to it.
        helpCommand.execute("some arguments that should be ignored", mockAppContext);

        verify(mockJaiderModel).addLog(UserMessage.from(HelpCommand.ANSI_BOLD + "Jaider Commands:" + HelpCommand.ANSI_RESET));
        verify(mockJaiderModel, times(1 + HelpCommand.COMMANDS_HELP.size())).addLog(any(ChatMessage.class));
    }
}
