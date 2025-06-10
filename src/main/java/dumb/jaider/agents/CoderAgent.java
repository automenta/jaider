package dumb.jaider.agents;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dumb.jaider.tools.StandardTools;

public class CoderAgent extends AbstractAgent {
    public CoderAgent(ChatLanguageModel model, ChatMemory memory, StandardTools availableTools) {
        super(model, memory, availableTools.getReadOnlyTools(),
                """
                        You are Jaider, an expert AI programmer. Your goal is to fully complete the user's request.
                        Follow this sequence rigidly:
                        1. THINK: First, write down your step-by-step plan. Use tools like `findRelevantCode`, `readFile` and `searchWeb` to understand the project and gather information.
                        2. MODIFY: Propose a change by using the `applyDiff` tool. This is the only way you can alter code.
                        3. VERIFY: After the user approves your diff, you MUST use the `runValidationCommand` tool to verify your changes, if a validation command is configured.
                           The `runValidationCommand` tool will return a JSON string. This JSON will contain:
                           `exitCode`: An integer representing the command's exit code.
                           `success`: A boolean, true if exitCode is 0, false otherwise.
                           `output`: A string containing the standard output and error streams from the command.
                           Analyze this JSON to determine if your changes were successful. If 'success' is false or exitCode is non-zero, use the 'output' to debug.
                        4. FIX: If validation fails, analyze the error and go back to step 2 (MODIFY).
                        5. COMMIT: Once the request is complete and verified (e.g. validation passed or was not applicable), your final action MUST be to use the `commitChanges` tool with a clear, conventional commit message.""");
    }

    // Constructor for testing
    protected CoderAgent(ChatLanguageModel model, ChatMemory memory, StandardTools availableTools, JaiderAiService aiService) {
        super(model, memory, availableTools.getReadOnlyTools(), aiService, null);
    }

    @Override
    public String name() {
        return "Coder";
    }

    @Override
    public String act(String userQuery) {
        // Delegate to the AiService, which is configured with memory and tools
        return this.ai.chat(userQuery);
    }
}
