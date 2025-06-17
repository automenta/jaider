package dumb.jaider.agents;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AskAgentTest {

    @Mock
    ChatModel ChatModel; // Still needed for AbstractAgent constructor
    @Mock
    ChatMemory chatMemory; // Still needed for AbstractAgent constructor
    @Mock
    JaiderAiService jaiderAiServiceMock; // Mock for the service

    private AskAgent askAgent;

    @BeforeEach
    void setUp() {
        // Use the new constructor to inject the mocked JaiderAiService
        askAgent = new AskAgent(ChatModel, chatMemory, jaiderAiServiceMock);
    }

    @Test
    void name_shouldReturnAsk() {
        assertEquals("Ask", askAgent.name());
    }

    @Test
    void tools_shouldReturnEmptySet() {
        var actualTools = askAgent.tools(); // From AbstractAgent
        assertTrue(actualTools.isEmpty(), "AskAgent should have no tools by default.");
    }

    @Test
    void act_shouldCallAiServiceChat() {
        var testQuery = "Hello, who are you?";
        var expectedResponse = "I am AskAgent, powered by JaiderAiService.";

        // Set up the mock behavior for the JaiderAiService
        when(jaiderAiServiceMock.chat(anyString())).thenReturn(expectedResponse);

        // Execute the act method
        var actualResponse = askAgent.act(testQuery);

        // Verify the response
        assertEquals(expectedResponse, actualResponse);

        // Verify that the 'chat' method of the JaiderAiService was called with the testQuery
        verify(jaiderAiServiceMock).chat(testQuery);
    }
}
