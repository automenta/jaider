package dumb.jaider.commands;

import dumb.jaider.app.AppContext;
import dumb.jaider.agents.CoderAgent;
import dumb.jaider.app.DependencyInjector;
import dumb.jaider.ui.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CodeCommandTest {

    private AppContext mockAppContext;
    private CommandSender mockCommandSender;
    private CoderAgent mockCoderAgent;
    private DependencyInjector mockDependencyInjector;
    private CodeCommand codeCommand;

    @BeforeEach
    void setUp() {
        mockAppContext = mock(AppContext.class);
        mockCommandSender = mock(CommandSender.class);
        mockCoderAgent = mock(CoderAgent.class);
        mockDependencyInjector = mock(DependencyInjector.class);

        // Stubbing AppContext to return the mock DependencyInjector
        when(mockAppContext.getDependencyInjector()).thenReturn(mockDependencyInjector);
        // Stubbing DependencyInjector to return the mock CoderAgent
        when(mockDependencyInjector.getComponent(CoderAgent.class)).thenReturn(mockCoderAgent);

        codeCommand = new CodeCommand(mockAppContext);
    }

    @Test
    void getCommandName() {
        assertEquals("code", codeCommand.getCommandName());
    }

    @Test
    void getHelp() {
        assertEquals("Engages the CoderAgent to perform code modifications based on your request. Usage: /code <your request>", codeCommand.getHelp());
    }

    @Test
    void execute_noArguments_sendsHelpMessage() {
        codeCommand.execute(new String[]{}, mockCommandSender);
        verify(mockCommandSender).sendMessage("Please provide a description of the code changes you want to make. Usage: /code <your request>");
        verify(mockCoderAgent, never()).act(anyString());
    }

    @Test
    void execute_withArguments_invokesCoderAgent() {
        String userRequest = "refactor this class";
        String agentResponse = "Refactoring complete.";
        when(mockCoderAgent.act(userRequest)).thenReturn(agentResponse);

        codeCommand.execute(new String[]{"refactor", "this", "class"}, mockCommandSender);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockCommandSender, times(2)).sendMessage(messageCaptor.capture());

        assertEquals("Engaging CoderAgent with request: " + userRequest, messageCaptor.getAllValues().get(0));
        assertEquals("CoderAgent response: " + agentResponse, messageCaptor.getAllValues().get(1));

        verify(mockCoderAgent).act(userRequest);
    }

    @Test
    void execute_coderAgentThrowsException_sendsErrorMessage() {
        String userRequest = "implement feature X";
        String errorMessage = "Something went wrong";
        when(mockCoderAgent.act(userRequest)).thenThrow(new RuntimeException(errorMessage));

        codeCommand.execute(new String[]{"implement", "feature", "X"}, mockCommandSender);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockCommandSender, times(2)).sendMessage(messageCaptor.capture());

        assertEquals("Engaging CoderAgent with request: " + userRequest, messageCaptor.getAllValues().get(0));
        assertEquals("Error interacting with CoderAgent: " + errorMessage, messageCaptor.getAllValues().get(1));
    }
}
