package dumb.jaider.agents;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dumb.jaider.tools.StandardTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArchitectAgentTest {

    @Mock
    ChatModel ChatModel; // Still needed for AbstractAgent constructor
    @Mock
    ChatMemory chatMemory; // Still needed for AbstractAgent constructor
    @Mock
    StandardTools standardTools; // Still needed for AbstractAgent constructor
    @Mock
    JaiderAiService jaiderAiServiceMock; // Mock for the AiService

    private ArchitectAgent architectAgent;

    // Dummy tool not needed as we're not creating ToolSpecifications directly in this test anymore.
    // static class ReadOnlyTool {
    //     @Tool("A read-only tool")
    //     public String readSomething(String input) { return "read: " + input; }
    // }

    @BeforeEach
    void setUp() {
        // Mock the behavior of standardTools.getReadOnlyTools() *before* ArchitectAgent is constructed,
        // as it's called within the constructor chain.
        // For the general case in setUp, we can return an empty set or a default mock set.
        // Specific tests can override this mock behavior if needed *before* their specific setup.
        when(standardTools.getReadOnlyTools()).thenReturn(Collections.emptySet()); // Default for setUp

        architectAgent = new ArchitectAgent(ChatModel, chatMemory, standardTools, jaiderAiServiceMock);
    }

    @Test
    void name_shouldReturnArchitect() {
        assertEquals("Architect", architectAgent.name());
    }

    @Test
    void tools_shouldReturnWhatWasPassedToConstructor() {
        // ArchitectAgent's constructor passes standardTools.getReadOnlyTools() to AbstractAgent.
        // AbstractAgent.getTools() returns this set.
        // Here, standardTools is a mock. getReadOnlyTools() by default returns null for a mock.
        // So, the set of tools in architectAgent will be whatever getReadOnlyTools() returns.
        // We need to set the specific mock behavior for this test case.
        var expectedToolsSet = Set.of(new Object()); // A dummy set
        when(standardTools.getReadOnlyTools()).thenReturn(expectedToolsSet);

        // ArchitectAgent is already constructed in setUp with standardTools mock.
        // To test a specific return value for getReadOnlyTools for *this* test,
        // we need to ensure the mock is configured *before* the constructor call that matters.
        // The setUp method already called the constructor once.
        // For this test, we will re-initialize to ensure the mock for this test is active during construction.
        // Or, ensure setUp's when() is suitable for most tests, and this one re-mocks then re-constructs.
        // Better: The when() in setUp should be the general case. If a test needs a specific scenario
        // for getReadOnlyTools that affects construction, that test might need its own agent instance.
        // However, getTools() just returns the set. The crucial part is what was passed during construction.

        // Let's stick to re-mocking and re-constructing for this specific test logic to be clear.
        architectAgent = new ArchitectAgent(ChatModel, chatMemory, standardTools, jaiderAiServiceMock); // Re-construct with new mock behavior

        var actualTools = architectAgent.tools();
        assertEquals(expectedToolsSet, actualTools);
        verify(standardTools, times(2)).getReadOnlyTools(); // Called once in setUp, once in re-construction.
    }

    // Removed constructor_systemPrompt_verificationAttempt as SYSTEM_MESSAGE is no longer a public static field

    @Test
    void act_shouldCallAiServiceChat() {
        var testQuery = "What is the architecture of this project?";
        var expectedResponse = "It's a monolith with microservices aspirations.";

        // Set up the mock behavior for the JaiderAiService
        when(jaiderAiServiceMock.chat(anyString())).thenReturn(expectedResponse);

        // Execute the act method (the one taking String)
        var actualResponse = architectAgent.act(testQuery);

        // Verify the response
        assertEquals(expectedResponse, actualResponse);

        // Verify that the 'chat' method of the JaiderAiService was called with the testQuery
        verify(jaiderAiServiceMock).chat(testQuery);
    }
}
