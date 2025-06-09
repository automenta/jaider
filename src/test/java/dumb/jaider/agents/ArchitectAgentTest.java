package dumb.jaider.agents;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.agent.tool.ToolSpecification; // Corrected
import dumb.jaider.agents.JaiderAiService; // Corrected
import dumb.jaider.tools.StandardTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.langchain4j.agent.tool.Tool;


import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArchitectAgentTest {

    @Mock
    ChatLanguageModel chatLanguageModel;
    @Mock
    ChatMemory chatMemory;
    @Mock
    StandardTools standardTools;

    // Mocks for JaiderAiService
    // AiServices.Builder is no longer a separate class in this version of Langchain4j
    @Mock
    JaiderAiService mockJaiderAiService;


    private ArchitectAgent architectAgent;

    // Dummy tool for testing StandardTools interaction
    static class ReadOnlyTool {
        @Tool("A read-only tool")
        public String readSomething(String input) { return "read: " + input; }
    }

    @BeforeEach
    void setUp() {
        // It's difficult to intercept AiServices.builder() directly without PowerMock.
        // We will test the inputs to ArchitectAgent constructor and its methods.
        // The actual AiService instance creation within AbstractAgent cannot be easily mocked here.
        // So, for act() method, we'll have to assume that if the ChatLanguageModel is called,
        // it means the AiService was built and invoked.

        // Setup for getTools
        Set<ToolSpecification> readOnlyToolSpecs = Collections.singleton(ToolSpecification.from(new ReadOnlyTool()));
        when(standardTools.getReadOnlyTools()).thenReturn(readOnlyToolSpecs);

        architectAgent = new ArchitectAgent(chatLanguageModel, chatMemory, standardTools);
    }

    @Test
    void name_shouldReturnArchitect() {
        assertEquals("Architect", architectAgent.name());
    }

    @Test
    void getTools_shouldReturnReadOnlyToolsFromStandardTools() {
        Set<ToolSpecification> expectedTools = Collections.singleton(ToolSpecification.from(new ReadOnlyTool()));
        when(standardTools.getReadOnlyTools()).thenReturn(expectedTools); // re-mock for clarity if needed

        Set<ToolSpecification> actualTools = architectAgent.getTools(); // getTools() is directly from AbstractAgent

        assertEquals(expectedTools, actualTools);
        verify(standardTools).getReadOnlyTools();
    }

    @Test
    void constructor_systemPrompt_verificationAttempt() {
        // This test attempts to verify the system prompt indirectly or by assumption.
        // Direct verification of AiServices.builder().systemMessageProvider() is hard.
        // We know ArchitectAgent's constructor calls super(..., SYSTEM_MESSAGE, ...).
        // This test relies on that contract.
        // A more robust test would involve PowerMock or refactoring AbstractAgent.

        // We can't easily capture the SystemMessageProvider lambda passed to AiServices.builder()
        // in AbstractAgent's constructor without PowerMockito or refactoring AbstractAgent.
        // We trust that AbstractAgent correctly uses the systemMessage string passed to its constructor.
        // The best we can do here is to acknowledge the system message string.
        String expectedSystemMessageStart = "You are the Architect Agent.";
        // We cannot assert it here directly. This is more of a conceptual check.
        // If we could mock the AiServices.builder().systemMessageProvider() call, we would.
        // For now, we assume this system message is correctly passed.
        assertTrue(ArchitectAgent.SYSTEM_MESSAGE.startsWith(expectedSystemMessageStart),
                "SYSTEM_MESSAGE constant should match expected start.");
    }


    @Test
    void act_shouldInvokeChatLanguageModel() {
        // We can't mock the 'JaiderAiService internalService' directly as it's created
        // within AbstractAgent via AiServices.builder().
        // However, the 'internalService.chat()' call eventually routes to 'chatLanguageModel.generate()'.
        // So, if chatLanguageModel.generate() is called, it implies act() worked through the service.

        List<dev.langchain4j.data.message.ChatMessage> messages = Collections.singletonList(
                dev.langchain4j.data.message.UserMessage.from("Test query")
        );
        Response<dev.langchain4j.data.message.AiMessage> mockResponse = Response.from(
                dev.langchain4j.data.message.AiMessage.from("Test response")
        );

        // We need to ensure that when internalService.chat(messages) is called,
        // it ultimately results in chatLanguageModel.generate(messages) being called.
        // This is an indirect test. The AiServices framework handles this connection.
        when(chatLanguageModel.generate(anyList())).thenReturn(mockResponse);

        String response = architectAgent.act(messages);

        assertEquals("Test response", response);
        verify(chatLanguageModel).generate(messages); // Verifies the core interaction
    }
}
