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
                        1. THINK: First, write down your step-by-step plan. Use tools like `getProjectOverview()`, `findRelevantCode`, `readFile`, `listFiles(directoryPath)` (Lists files and directories under a given path (relative to project root). Useful for exploring the project structure.), and `searchWeb` to understand the project and gather information.
                        2. MODIFY: Propose a change by using the `applyDiff` tool. This is the only way you can alter code. You can also use `writeFile(filePath, content)` (Writes content to a specified file (relative to project root), creating directories if needed. Use this to create new files or overwrite existing ones.) to create or modify files.
                        3. VERIFY: After the user approves your diff or writeFile operation, you MUST use the `runValidationCommand` tool to verify your changes, if a validation command is configured.
                           The `runValidationCommand` tool will return a JSON string. This JSON will contain:
                           `exitCode`: An integer representing the command's exit code.
                           `success`: A boolean, true if exitCode is 0, false otherwise.
                           `output`: A string containing the standard output and error streams from the command.
                           `testReport`: An array of objects, where each object details a failed test with `testClass`, `testMethod`, and `errorMessage`. This field will be present if the validation command was `mvn test` and failures occurred.
                           Analyze this JSON, especially the `testReport` if available and `success` is false, to determine if your changes were successful. If 'success' is false or exitCode is non-zero, use the 'output' and 'testReport' to debug.
                        4. FIX: If validation fails (`success` is false), meticulously analyze the `output` and critically examine the `testReport` (if available and contains failures).
                           Your goal is to understand the root cause of the failure.
                           Based on this analysis, go back to step 2 (MODIFY) to propose a corrected diff or new file content.
                           If you cannot determine a direct code fix, explain the failure based on the `testReport` and `output`, and suggest how the user might approach fixing it or ask for clarification.
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
