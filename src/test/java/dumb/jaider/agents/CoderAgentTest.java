package dumb.jaider.agents;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dumb.jaider.tools.StandardTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CoderAgentTest {

    private JaiderAiService mockAiService;
    private ChatLanguageModel mockChatLanguageModel;
    private ChatMemory chatMemory;
    private StandardTools mockStandardTools;
    private CoderAgent coderAgent;

    @BeforeEach
    void setUp() {
        mockAiService = mock(JaiderAiService.class);
        mockChatLanguageModel = mock(ChatLanguageModel.class); // Not directly used by streamAct but needed for constructor
        chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        mockStandardTools = mock(StandardTools.class); // Not directly used by streamAct but needed for constructor

        // Use the test-specific constructor of CoderAgent to inject the mock JaiderAiService
        coderAgent = new CoderAgent(mockChatLanguageModel, chatMemory, mockStandardTools, mockAiService);
    }

    @Test
    void streamAct_callsJaiderAiServiceStreamChat() {
        String userQuery = "test query";
        List<String> receivedChunks = new ArrayList<>();
        Consumer<String> tokenConsumer = receivedChunks::add;

        // Simulate the JaiderAiService calling the consumer
        doAnswer(invocation -> {
            Consumer<String> actualConsumer = invocation.getArgument(1);
            actualConsumer.accept("Streamed chunk 1");
            actualConsumer.accept("Streamed chunk 2");
            return null; // void method
        }).when(mockAiService).streamChat(eq(userQuery), any(Consumer.class));

        coderAgent.streamAct(userQuery, tokenConsumer);

        // Verify that JaiderAiService.streamChat was called correctly
        verify(mockAiService).streamChat(eq(userQuery), any(Consumer.class));

        // Verify that the tokenConsumer received the chunks
        assertEquals(2, receivedChunks.size());
        assertEquals("Streamed chunk 1", receivedChunks.get(0));
        assertEquals("Streamed chunk 2", receivedChunks.get(1));
    }

    @Test
    void streamAct_aiServiceIsNull_sendsErrorMessageToConsumer() {
        // Create an agent instance where the AI service would be null.
        // The CoderAgent's primary constructor would build one, so we need to
        // use a more direct way or ensure the test constructor passes null.
        // For this test, let's imagine a scenario where 'ai' field in AbstractAgent is null.
        // We can achieve this by creating a new CoderAgent with a null JaiderAiService.
        CoderAgent agentWithNullService = new CoderAgent(mockChatLanguageModel, chatMemory, mockStandardTools, null);

        String userQuery = "test query with null service";
        List<String> receivedChunks = new ArrayList<>();
        Consumer<String> tokenConsumer = receivedChunks::add;

        agentWithNullService.streamAct(userQuery, tokenConsumer);

        // Verify that the tokenConsumer received the error message
        assertEquals(1, receivedChunks.size());
        assertEquals("Error: AI service is not available for streaming.", receivedChunks.get(0));
    }
}
