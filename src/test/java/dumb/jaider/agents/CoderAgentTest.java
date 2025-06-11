package dumb.jaider.agents;

// import dev.langchain4j.agent.tool.Tool; // No longer needed for these direct changes

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dumb.jaider.tools.StandardTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoderAgentTest {

    @Mock
    ChatLanguageModel chatLanguageModel; // Still needed for AbstractAgent constructor
    @Mock
    ChatMemory chatMemory; // Still needed for AbstractAgent constructor
    @Mock
    StandardTools standardTools; // Still needed for AbstractAgent constructor
    @Mock
    JaiderAiService jaiderAiServiceMock; // Mock for the AiService

    private CoderAgent coderAgent;

    // Dummy tools are not needed here anymore as we are not testing StandardTools through CoderAgent's getTools directly in this manner.
    // static class ReadWriteTool1 {
    //     @Tool("A read-write tool 1")
    //     public String modifySomething(String input) { return "modified1: " + input; }
    // }
    // static class ReadWriteTool2 {
    //     @Tool("A read-write tool 2")
    //     public String modifySomethingElse(String input) { return "modified2: " + input; }
    // }

    @BeforeEach
    void setUp() {
        // Stub the getReadOnlyTools method for the mocked standardTools
        // This is crucial because AbstractAgent's constructor calls this method on the passed StandardTools instance.
        // If not stubbed, a mock will return an empty set by default for a method returning a Set.
        when(standardTools.getReadOnlyTools()).thenReturn(Set.of(standardTools));

        // Use the new constructor to inject the mocked JaiderAiService
        coderAgent = new CoderAgent(chatLanguageModel, chatMemory, standardTools.getReadOnlyTools(), jaiderAiServiceMock);
    }

    @Test
    void name_shouldReturnCoder() {
        assertEquals("Coder", coderAgent.name());
    }

    @Test
    void toolsInstanceWrappedInSet() {
        // CoderAgent's constructor passes Set.of(availableTools) to AbstractAgent.
        // AbstractAgent.getTools() returns this set.
        Set<Object> expectedTools = Set.of(standardTools);
        Set<Object> actualTools = coderAgent.tools();

        assertEquals(expectedTools, actualTools, "CoderAgent should be configured with the provided StandardTools instance.");
    }

    // Removed constructor_systemPrompt_verificationAttempt as SYSTEM_MESSAGE is no longer a public static field in CoderAgent

    @Test
    void act_shouldCallAiServiceChat() {
        String testQuery = "Write a python script for me.";
        String expectedResponse = "Okay, I will write that script.";

        // Set up the mock behavior for the JaiderAiService
        when(jaiderAiServiceMock.chat(anyString())).thenReturn(expectedResponse);

        // Execute the act method
        String actualResponse = coderAgent.act(testQuery);

        // Verify the response
        assertEquals(expectedResponse, actualResponse);

        // Verify that the 'chat' method of the JaiderAiService was called with the testQuery
        verify(jaiderAiServiceMock).chat(testQuery);
    }
}
