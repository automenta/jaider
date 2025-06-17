package dumb.jaider.commands;

import dev.langchain4j.data.message.AiMessage;
import dumb.jaider.agents.Agent;
import dumb.jaider.app.App;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.tools.StandardTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RunCommandTest {

    @Mock
    private AppContext mockAppContext;
    @Mock
    private JaiderModel mockJaiderModel;
    @Mock
    private App mockApp;
    @Mock
    private Agent mockAgent;
    @Mock
    private StandardTools mockStandardTools; // Mock for the specific tool

    @InjectMocks
    private RunCommand runCommand;

    @BeforeEach
    void setUp() {
        when(mockAppContext.model()).thenReturn(mockJaiderModel);
        when(mockAppContext.app()).thenReturn(mockApp);
    }

    @Test
    void testExecute_noActiveAgent_logsError() {
        when(mockApp.getCurrentAgent()).thenReturn(null);

        runCommand.execute("some_args", mockAppContext);

        verify(mockJaiderModel).addLog(AiMessage.from("[RunCommand] Error: No active agent found."));
        verifyNoInteractions(mockStandardTools); // Ensure no tool interaction if no agent
    }

    @Test
    void testExecute_agentHasNoStandardTools_logsError() {
        when(mockApp.getCurrentAgent()).thenReturn(mockAgent);
        // Agent returns an empty set of tools, or a set without StandardTools
        when(mockAgent.tools()).thenReturn(Collections.emptySet());

        runCommand.execute("some_args", mockAppContext);

        verify(mockJaiderModel).addLog(AiMessage.from("[RunCommand] Error: StandardTools not available for the current agent."));
        verifyNoInteractions(mockStandardTools);
    }

    @Test
    void testExecute_agentHasOtherToolsButNotStandardTools_logsError() {
        when(mockApp.getCurrentAgent()).thenReturn(mockAgent);
        var otherTool = new Object(); // A dummy tool instance
        Set<Object> tools = new HashSet<>();
        tools.add(otherTool);
        when(mockAgent.tools()).thenReturn(tools);

        runCommand.execute("some_args", mockAppContext);

        verify(mockJaiderModel).addLog(AiMessage.from("[RunCommand] Error: StandardTools not available for the current agent."));
        verifyNoInteractions(mockStandardTools);
    }


    @Test
    void testExecute_withArgs_executesValidationCommand() {
        when(mockApp.getCurrentAgent()).thenReturn(mockAgent);
        Set<Object> tools = new HashSet<>();
        tools.add(mockStandardTools); // Add the mocked StandardTools
        when(mockAgent.tools()).thenReturn(tools);
        var result = "Validation OK";
        when(mockStandardTools.runValidationCommand(anyString())).thenReturn(result);

        var commandArgs = "test_command_args";
        runCommand.execute(commandArgs, mockAppContext);

        verify(mockJaiderModel).addLog(AiMessage.from("[RunCommand] Executing run command with args: '" + commandArgs + "'"));
        verify(mockStandardTools).runValidationCommand(commandArgs);
        verify(mockJaiderModel).addLog(AiMessage.from("[RunCommand Result]\n" + result));
    }

    @Test
    void testExecute_nullArgs_executesValidationCommandWithEmptyString() {
        when(mockApp.getCurrentAgent()).thenReturn(mockAgent);
        Set<Object> tools = new HashSet<>();
        tools.add(mockStandardTools);
        when(mockAgent.tools()).thenReturn(tools);
        var result = "Validation with empty args OK";
        when(mockStandardTools.runValidationCommand(eq(""))).thenReturn(result);

        runCommand.execute(null, mockAppContext); // Null args

        verify(mockJaiderModel).addLog(AiMessage.from("[RunCommand] Executing run command with args: ''")); // Logs with empty string
        verify(mockStandardTools).runValidationCommand(""); // Called with empty string
        verify(mockJaiderModel).addLog(AiMessage.from("[RunCommand Result]\n" + result));
    }


    @Test
    void testExecute_commandExecutionThrowsException_logsError() {
        when(mockApp.getCurrentAgent()).thenReturn(mockAgent);
        Set<Object> tools = new HashSet<>();
        tools.add(mockStandardTools);
        when(mockAgent.tools()).thenReturn(tools);

        var commandArgs = "test_args_for_exception";
        var exceptionMessage = "Command execution failed badly!";
        doThrow(new RuntimeException(exceptionMessage)).when(mockStandardTools).runValidationCommand(commandArgs);
        // when(mockStandardTools.runValidationCommand(commandArgs)).thenThrow(new RuntimeException(exceptionMessage));


        runCommand.execute(commandArgs, mockAppContext);

        verify(mockJaiderModel).addLog(AiMessage.from("[RunCommand] Executing run command with args: '" + commandArgs + "'"));
        verify(mockStandardTools).runValidationCommand(commandArgs);
        verify(mockJaiderModel).addLog(AiMessage.from("[RunCommand] Error executing validation command: " + exceptionMessage));
    }
}
