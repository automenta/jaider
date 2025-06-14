package dumb.jaider.commands;

import dumb.jaider.app.AppContext;
import dumb.jaider.app.App;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.agents.Agent;
import dumb.jaider.tools.StandardTools; // Assuming StandardTools is the correct class
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
        when(mockAppContext.getJaiderModel()).thenReturn(mockJaiderModel);
        when(mockAppContext.getApp()).thenReturn(mockApp);
    }

    @Test
    void testExecute_noActiveAgent_logsError() {
        when(mockApp.getCurrentAgent()).thenReturn(null);

        runCommand.execute("some_args", mockAppContext);

        verify(mockJaiderModel).addLog("No active agent to run the command.");
        verifyNoInteractions(mockStandardTools); // Ensure no tool interaction if no agent
    }

    @Test
    void testExecute_agentHasNoStandardTools_logsError() {
        when(mockApp.getCurrentAgent()).thenReturn(mockAgent);
        // Agent returns an empty set of tools, or a set without StandardTools
        when(mockAgent.getTools()).thenReturn(Collections.emptySet());

        runCommand.execute("some_args", mockAppContext);

        verify(mockJaiderModel).addLog("StandardTools not available for the current agent.");
        verifyNoInteractions(mockStandardTools);
    }

    @Test
    void testExecute_agentHasOtherToolsButNotStandardTools_logsError() {
        when(mockApp.getCurrentAgent()).thenReturn(mockAgent);
        Object otherTool = new Object(); // A dummy tool instance
        Set<Object> tools = new HashSet<>();
        tools.add(otherTool);
        when(mockAgent.getTools()).thenReturn(tools);

        runCommand.execute("some_args", mockAppContext);

        verify(mockJaiderModel).addLog("StandardTools not available for the current agent.");
        verifyNoInteractions(mockStandardTools);
    }


    @Test
    void testExecute_withArgs_executesValidationCommand() {
        when(mockApp.getCurrentAgent()).thenReturn(mockAgent);
        Set<Object> tools = new HashSet<>();
        tools.add(mockStandardTools); // Add the mocked StandardTools
        when(mockAgent.getTools()).thenReturn(tools);
        when(mockStandardTools.runValidationCommand(anyString())).thenReturn("Validation OK");

        String commandArgs = "test_command_args";
        runCommand.execute(commandArgs, mockAppContext);

        verify(mockJaiderModel).addLog("Executing validation command: " + commandArgs);
        verify(mockStandardTools).runValidationCommand(commandArgs);
        verify(mockJaiderModel).addLog("Validation result: Validation OK");
    }

    @Test
    void testExecute_nullArgs_executesValidationCommandWithEmptyString() {
        when(mockApp.getCurrentAgent()).thenReturn(mockAgent);
        Set<Object> tools = new HashSet<>();
        tools.add(mockStandardTools);
        when(mockAgent.getTools()).thenReturn(tools);
        when(mockStandardTools.runValidationCommand(eq(""))).thenReturn("Validation with empty args OK");

        runCommand.execute(null, mockAppContext); // Null args

        verify(mockJaiderModel).addLog("Executing validation command: "); // Logs with empty string
        verify(mockStandardTools).runValidationCommand(""); // Called with empty string
        verify(mockJaiderModel).addLog("Validation result: Validation with empty args OK");
    }


    @Test
    void testExecute_commandExecutionThrowsException_logsError() {
        when(mockApp.getCurrentAgent()).thenReturn(mockAgent);
        Set<Object> tools = new HashSet<>();
        tools.add(mockStandardTools);
        when(mockAgent.getTools()).thenReturn(tools);

        String commandArgs = "test_args_for_exception";
        String exceptionMessage = "Command execution failed badly!";
        doThrow(new RuntimeException(exceptionMessage)).when(mockStandardTools).runValidationCommand(commandArgs);
        // when(mockStandardTools.runValidationCommand(commandArgs)).thenThrow(new RuntimeException(exceptionMessage));


        runCommand.execute(commandArgs, mockAppContext);

        verify(mockJaiderModel).addLog("Executing validation command: " + commandArgs);
        verify(mockStandardTools).runValidationCommand(commandArgs);
        verify(mockJaiderModel).addLog("Error executing command: " + exceptionMessage);
    }
}
