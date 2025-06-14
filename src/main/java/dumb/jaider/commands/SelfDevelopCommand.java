package dumb.jaider.commands;

import dumb.jaider.commands.AppContext; // Corrected import
import dumb.jaider.agents.CoderAgent;   // Corrected import
import dev.langchain4j.data.message.AiMessage; // For logging

public class SelfDevelopCommand implements Command { // Implement non-generic Command interface

    @Override
    public void execute(String args, AppContext context) { // Return void
        if (args == null || args.trim().isEmpty()) {
            context.model().addLog(AiMessage.from("Usage: /self-develop <task_description>"));
            return;
        }

        Object agentObject = context.app().getAgent("Coder");
        if (agentObject == null) {
            context.model().addLog(AiMessage.from("Error: CoderAgent not found."));
            return;
        }

        if (!(agentObject instanceof CoderAgent)) {
            context.model().addLog(AiMessage.from("Error: CoderAgent not available or of wrong type. Expected dumb.jaider.agents.CoderAgent but found " + agentObject.getClass().getName()));
            return;
        }

        CoderAgent coderAgent = (CoderAgent) agentObject;

        String constructedPrompt;
        String lowerCaseArgs = args.toLowerCase().trim();

        if (lowerCaseArgs.equals("update project dependencies") || lowerCaseArgs.equals("update dependencies")) {
            constructedPrompt = String.format(
                "Task: Enhance Jaider's Capabilities - Update Dependencies\n" +
                "------------------------------------\n" +
                "Description: %s\n" +
                "------------------------------------\n" +
                "Jaider's self-development task is to implement the following feature or improvement:\n\n" +
                "'%s'\n\n" +
                "Instructions for CoderAgent:\n" +
                "1. This task requires updating the project's Maven dependencies.\n" +
                "2. Use the \"DependencyUpdater\" tool to identify outdated dependencies and get the necessary diffs for `pom.xml`.\n" +
                "   The `DependencyUpdater` tool should be called without arguments.\n" +
                "3. The \"DependencyUpdater\" tool will return a list of proposed updates, each containing a 'diff' field for `pom.xml`,\n" +
                "   as well as 'groupId', 'artifactId', and 'newVersion' for constructing a commit message.\n" +
                "4. For each proposed update from the \"DependencyUpdater\" tool:\n" +
                "    a. Construct a clear commit message, for example: \"Update dependency org.example:artifact-name to 1.2.3\".\n" +
                "    b. Use the \"proposeSelfUpdate\" tool. The `filePath` parameter should be \"pom.xml\".\n" +
                "       The `diffContent` parameter should be the 'diff' string obtained from the \"DependencyUpdater\" tool for the specific dependency.\n" +
                "       The `commitMessage` parameter should be the one you constructed.\n" +
                "5. Propose each dependency update one by one. Do not batch them into a single `proposeSelfUpdate` call.\n" +
                "6. If the \"DependencyUpdater\" tool returns an empty list (indicating no updates found), or if it returns an error structure,\n" +
                "   report this outcome back to the user clearly. For example: \"No dependencies found to update.\" or \"Error while checking for dependency updates: [error details]\".\n" +
                "   Do not attempt to call `proposeSelfUpdate` if there are no valid updates or if an error occurred.\n\n" +
                "Please proceed with implementing this task.",
                args, args
            );
        } else {
            // Default generic self-develop prompt
            constructedPrompt = String.format(
                "Task: Enhance Jaider's Capabilities\n" +
                "------------------------------------\n" +
                "Description: %s\n" +
                "------------------------------------\n" +
                "Jaider's self-development task is to implement the following feature or improvement:\n\n" +
                "'%s'\n\n" +
                "Instructions for CoderAgent:\n" +
                "1. Analyze the request and understand the required changes to Jaider's codebase.\n" +
                "2. Identify the relevant files and modules that need modification or creation.\n" +
                "3. If new files are needed (e.g., new commands, services, tools), plan their structure, content, and interactions carefully.\n" +
                "4. Generate the necessary code changes using the `proposeSelfUpdate` tool.\n" +
                "   - For each file to be changed or created, call `proposeSelfUpdate` once.\n" +
                "   - Ensure generated diffs are in the standard unified diff format (e.g., output of `diff -u`).\n" +
                "   - Provide clear, descriptive commit messages for each proposed update.\n" +
                "5. If multiple steps or files are involved, propose them sequentially. Explain your overall plan if it's complex.\n" +
                "6. If you need to read existing files to inform your changes, use tools like `readFile` or `findRelevantCode`.\n" +
                "7. The `proposeSelfUpdate` tool handles user review and the actual application, build, test, and commit process.\n" +
                "8. Aim for modular, well-documented, and correct code. Ensure new Java code compiles and follows project conventions.\n" +
                "9. If the task is vague, ask for clarification. If the task seems too complex for a single step, break it down.\n\n" +
                "Please proceed with implementing this task.",
                args, args
            );
        }

        try {
            String agentResponse = coderAgent.act(constructedPrompt);
            context.model().addLog(AiMessage.from(agentResponse)); // Log agent's response
        } catch (Exception e) {
            System.err.println("Error executing CoderAgent.act for task '" + args + "': " + e.getMessage());
            e.printStackTrace();
            context.model().addLog(AiMessage.from("Error: An unexpected error occurred while executing the self-development task with CoderAgent. Details: " + e.getMessage()));
        }
    }
}
