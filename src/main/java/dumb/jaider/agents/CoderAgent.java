package dumb.jaider.agents;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel; // Changed from ChatLanguageModel
import dumb.jaider.tools.JaiderTools;
import dumb.jaider.tools.StandardTools;

import java.util.HashSet;
import java.util.Set;

public class CoderAgent extends AbstractAgent {

    // This constructor is likely for testing or fallback.
    // It should ideally also have a proper system prompt if used.
    public CoderAgent(ChatModel model, ChatMemory memory, Set<Object> tools, JaiderAiService aiService) { // Changed from ChatLanguageModel
        super(model, memory, tools, aiService, "You are an expert software developer. Your primary goal is to write and modify code based on user requests. Use the available tools to interact with the file system, apply diffs, run validation commands, and analyze code. Propose self-updates if you identify improvements to your own Jaider codebase. Always ask for plan approval before making changes.");
    }

    // Constructor intended for Dependency Injection
    public CoderAgent(ChatModel model, ChatMemory memory, // Changed from ChatLanguageModel
                      StandardTools standardTools, JaiderTools jaiderTools,
                      Object smartRenameTool, Object analysisTools, Object listContextFilesTool) {
        super(model, memory,
              createToolSet(standardTools, jaiderTools, smartRenameTool, analysisTools, listContextFilesTool),
              "You are an expert software developer. Your primary goal is to write and modify code based on user requests. Use the available tools to interact with the file system, apply diffs, run validation commands, and analyze code. Propose self-updates if you identify improvements to your own Jaider codebase. Always ask for plan approval before making changes."
        );
    }

    private static Set<Object> createToolSet(Object... tools) {
        Set<Object> toolSet = new HashSet<>();
        for (Object tool : tools) {
            if (tool != null) {
                toolSet.add(tool);
            }
        }
        return toolSet;
    }

    @Override
    public String name() {
        return "Coder";
    }

    @Override
    public String act(String userQuery) {
        if (this.ai == null) {
            // This might happen if the super constructor that initializes AiService was not called correctly,
            // or if the AiService instance itself is null.
            throw new IllegalStateException("AI service is not initialized for CoderAgent.");
        }
        return this.ai.chat(userQuery);
    }
}
