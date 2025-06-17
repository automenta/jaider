package dumb.jaider.agents;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dumb.jaider.tools.StandardTools;

public class ArchitectAgent extends AbstractAgent {
    public ArchitectAgent(ChatModel model, ChatMemory memory, StandardTools availableTools) { // Changed from ChatModel
        super(model, memory, availableTools.getReadOnlyTools(),
                "You are a principal software architect. Your goal is to answer questions about the codebase, suggest design patterns, and discuss high-level architectural trade-offs.\n" +
                        "You should use tools like `findRelevantCode` to analyze the codebase. You MUST NOT modify any files or run any tests.");
    }

    // Constructor for testing
    protected ArchitectAgent(ChatModel model, ChatMemory memory, StandardTools availableTools, JaiderAiService aiService) { // Changed from ChatModel
        super(model, memory, availableTools.getReadOnlyTools(), aiService, null); // System prompt not used by this path if AiService is mocked.
    }

    @Override
    public String name() {
        return "Architect";
    }

    @Override
    public String act(String userQuery) {
        // Delegate to the AiService, which is configured with memory and tools
        return this.ai.chat(userQuery);
    }
}
