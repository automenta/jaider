package dumb.jaider.commands;

import dumb.jaider.app.AppContext;
import dumb.jaider.agents.CoderAgent;
import dumb.jaider.app.DependencyInjector;
import dumb.jaider.ui.CommandSender;
import java.util.function.Consumer; // Added import
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
        verify(mockCoderAgent, never()).streamAct(anyString(), any(Consumer.class)); // Added this line
    }

    @Test
    void execute_withArguments_invokesCoderAgentStreaming() {
        String userRequest = "refactor this class";
        String[] requestArgs = {"refactor", "this", "class"};

        // Use doAnswer to simulate the CoderAgent calling the consumer
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("Chunk 1 ");
            consumer.accept("Chunk 2 ");
            consumer.accept("Chunk 3");
            return null; // void method
        }).when(mockCoderAgent).streamAct(eq(userRequest), any(Consumer.class));

        codeCommand.execute(requestArgs, mockCommandSender);

        // Verify initial message
        verify(mockCommandSender).sendMessage("Engaging CoderAgent with request: " + userRequest);

        // Capture messages sent to CommandSender
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        // The first message is "Engaging...", then 3 chunks
        verify(mockCommandSender, times(4)).sendMessage(messageCaptor.capture());

        // Assert that the streamAct method was called on the agent
        verify(mockCoderAgent).streamAct(eq(userRequest), any(Consumer.class));

        // Check the streamed messages (after the initial "Engaging..." message)
        assertEquals("Chunk 1 ", messageCaptor.getAllValues().get(1));
        assertEquals("Chunk 2 ", messageCaptor.getAllValues().get(2));
        assertEquals("Chunk 3", messageCaptor.getAllValues().get(3));

        // Ensure the old act method is no longer called
        verify(mockCoderAgent, never()).act(anyString());
    }

    @Test
    void execute_coderAgentStreamingThrowsException_sendsErrorMessage() {
        String userRequest = "implement feature X";
        String[] requestArgs = {"implement", "feature", "X"};
        String errorMessage = "Something went wrong with streaming";

        // Simulate streamAct throwing an exception
        doThrow(new RuntimeException(errorMessage))
            .when(mockCoderAgent).streamAct(eq(userRequest), any(Consumer.class));

        codeCommand.execute(requestArgs, mockCommandSender);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        // Verify "Engaging..." message and then the error message
        verify(mockCommandSender, times(2)).sendMessage(messageCaptor.capture());

        assertEquals("Engaging CoderAgent with request: " + userRequest, messageCaptor.getAllValues().get(0));
        assertEquals("Error interacting with CoderAgent: " + errorMessage, messageCaptor.getAllValues().get(1));

        // Ensure the old act method is no longer called
        verify(mockCoderAgent, never()).act(anyString());
    }
}
