package dumb.jaider.agents;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
// Imports for AiMessage, ChatMessage, UserMessage, Response are no longer needed for the revised act test
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// import java.util.Collections; // No longer needed
// import java.util.List; // No longer needed for this test's direct interactions
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
// import static org.mockito.ArgumentMatchers.eq; // Not needed for anyString
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AskAgentTest {

    @Mock
    ChatLanguageModel chatLanguageModel; // Still needed for AbstractAgent constructor
    @Mock
    ChatMemory chatMemory; // Still needed for AbstractAgent constructor
    @Mock
    JaiderAiService jaiderAiServiceMock; // Mock for the service

    private AskAgent askAgent;

    @BeforeEach
    void setUp() {
        // Use the new constructor to inject the mocked JaiderAiService
        askAgent = new AskAgent(chatLanguageModel, chatMemory, jaiderAiServiceMock);
    }

    @Test
    void name_shouldReturnAsk() {
        assertEquals("Ask", askAgent.name());
    }

    @Test
    void getTools_shouldReturnEmptySet() {
        Set<Object> actualTools = askAgent.getTools(); // From AbstractAgent
        assertTrue(actualTools.isEmpty(), "AskAgent should have no tools by default.");
    }

    @Test
    void act_shouldCallAiServiceChat() {
        String testQuery = "Hello, who are you?";
        String expectedResponse = "I am AskAgent, powered by JaiderAiService.";

        // Set up the mock behavior for the JaiderAiService
        when(jaiderAiServiceMock.chat(anyString())).thenReturn(expectedResponse);

        // Execute the act method
        String actualResponse = askAgent.act(testQuery);

        // Verify the response
        assertEquals(expectedResponse, actualResponse);

        // Verify that the 'chat' method of the JaiderAiService was called with the testQuery
        verify(jaiderAiServiceMock).chat(testQuery);
    }
}
