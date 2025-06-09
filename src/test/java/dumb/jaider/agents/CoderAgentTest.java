package dumb.jaider.agents;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.ToolSpecification;
import dumb.jaider.tools.StandardTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoderAgentTest {

    @Mock
    ChatLanguageModel chatLanguageModel;
    @Mock
    ChatMemory chatMemory;
    @Mock
    StandardTools standardTools;

    private CoderAgent coderAgent;

    // Dummy tools for testing StandardTools interaction
    static class ReadWriteTool1 {
        @Tool("A read-write tool 1")
        public String modifySomething(String input) { return "modified1: " + input; }
    }
    static class ReadWriteTool2 {
        @Tool("A read-write tool 2")
        public String modifySomethingElse(String input) { return "modified2: " + input; }
    }


    @BeforeEach
    void setUp() {
        coderAgent = new CoderAgent(chatLanguageModel, chatMemory, standardTools);
    }

    @Test
    void name_shouldReturnCoder() {
        assertEquals("Coder", coderAgent.name());
    }

    @Test
    void getTools_shouldReturnAllToolsFromStandardTools() {
        Set<ToolSpecification> allToolSpecs = new HashSet<>();
        allToolSpecs.add(ToolSpecification.from(new ReadWriteTool1()));
        allToolSpecs.add(ToolSpecification.from(new ReadWriteTool2()));

        when(standardTools.getAllTools()).thenReturn(allToolSpecs);

        Set<ToolSpecification> actualTools = coderAgent.getTools(); // getTools() from AbstractAgent

        assertEquals(allToolSpecs, actualTools);
        verify(standardTools).getAllTools();
    }

    @Test
    void constructor_systemPrompt_verificationAttempt() {
        // Similar to other agent tests, direct verification of the system prompt
        // passed to AiServices.builder() is difficult with Mockito alone.
        // We verify the constant.
        String expectedSystemMessageStart = "You are the Coder Agent.";
        assertTrue(CoderAgent.SYSTEM_MESSAGE.startsWith(expectedSystemMessageStart),
                "SYSTEM_MESSAGE constant should match expected start.");
    }

    @Test
    void act_shouldInvokeChatLanguageModel() {
        List<dev.langchain4j.data.message.ChatMessage> messages = Collections.singletonList(
                dev.langchain4j.data.message.UserMessage.from("Test query for CoderAgent")
        );
        Response<dev.langchain4j.data.message.AiMessage> mockResponse = Response.from(
                dev.langchain4j.data.message.AiMessage.from("CoderAgent test response")
        );

        when(chatLanguageModel.generate(anyList())).thenReturn(mockResponse);

        String response = coderAgent.act(messages);

        assertEquals("CoderAgent test response", response);
        verify(chatLanguageModel).generate(messages); // Verifies the core interaction
    }
}
