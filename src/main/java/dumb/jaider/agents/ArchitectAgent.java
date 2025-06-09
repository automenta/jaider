package dumb.jaider.agents;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dumb.jaider.tools.StandardTools;

public class ArchitectAgent extends AbstractAgent {
    public ArchitectAgent(ChatLanguageModel model, ChatMemory memory, StandardTools availableTools) {
        super(model, memory, availableTools.getReadOnlyTools(),
                "You are a principal software architect. Your goal is to answer questions about the codebase, suggest design patterns, and discuss high-level architectural trade-offs.\n" +
                        "You should use tools like `findRelevantCode` to analyze the codebase. You MUST NOT modify any files or run any tests.");
    }

    @Override
    public String name() {
        return "Architect";
    }
}
