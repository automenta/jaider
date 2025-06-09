package dumb.jaider.agents;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.agent.tool.ToolSpecification; // Corrected import
import dumb.jaider.tools.StandardTools; // Though not used by AskAgent, AbstractAgent takes it
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AskAgentTest {

    @Mock
    ChatLanguageModel chatLanguageModel;
    @Mock
    ChatMemory chatMemory;
    @Mock
    StandardTools standardTools; // Mocked, but AskAgent doesn't use tools from it

    private AskAgent askAgent;

    @BeforeEach
    void setUp() {
        // StandardTools is passed to AbstractAgent's constructor, so it needs to be provided
        askAgent = new AskAgent(chatLanguageModel, chatMemory, standardTools);
    }

    @Test
    void name_shouldReturnAsk() {
        assertEquals("Ask", askAgent.name());
    }

    @Test
    void getTools_shouldReturnEmptySet() {
        Set<ToolSpecification> actualTools = askAgent.getTools(); // getTools() from AbstractAgent
        assertTrue(actualTools.isEmpty(), "AskAgent should have no tools.");
        // Verify standardTools is not called for getTools for AskAgent specifically
        verifyNoInteractions(standardTools);
    }

    @Test
    void constructor_systemPrompt_verificationAttempt() {
        // Similar to ArchitectAgentTest, direct verification of the system prompt
        // passed to AiServices.builder() is difficult with Mockito alone.
        // We verify the constant.
        String expectedSystemMessageStart = "You are the Ask Agent.";
        assertTrue(AskAgent.SYSTEM_MESSAGE.startsWith(expectedSystemMessageStart),
                "SYSTEM_MESSAGE constant should match expected start.");
    }

    @Test
    void act_shouldInvokeChatLanguageModel() {
        List<dev.langchain4j.data.message.ChatMessage> messages = Collections.singletonList(
                dev.langchain4j.data.message.UserMessage.from("Test query for AskAgent")
        );
        Response<dev.langchain4j.data.message.AiMessage> mockResponse = Response.from(
                dev.langchain4j.data.message.AiMessage.from("AskAgent test response")
        );

        when(chatLanguageModel.generate(anyList())).thenReturn(mockResponse);

        String response = askAgent.act(messages);

        assertEquals("AskAgent test response", response);
        verify(chatLanguageModel).generate(messages); // Verifies the core interaction
    }
}
