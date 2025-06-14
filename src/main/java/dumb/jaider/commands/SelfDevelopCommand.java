package dumb.jaider.commands;

import dumb.jaider.app.AppContext;
import dumb.jaider.master.coder.CoderAgent; // Assuming this is the correct package for CoderAgent

public class SelfDevelopCommand implements Command<String> {

    @Override
    public String keyword() {
        return "self-develop";
    }

    @Override
    public String description() {
        return "Initiates a self-development task for Jaider. Arg: <task_description>";
    }

    @Override
    public String execute(String args, AppContext context) {
        if (args == null || args.trim().isEmpty()) {
            return "Usage: /self-develop <task_description>";
        }

        Object agentObject = context.app().getAgent("Coder"); // Renamed for clarity
        if (agentObject == null) {
            return "Error: CoderAgent not found.";
        }

        if (!(agentObject instanceof CoderAgent)) {
            // Corrected the import path in my mental model, it should be dumb.jaider.agents.CoderAgent
            // If the actual class is dumb.jaider.master.coder.CoderAgent, this check is fine.
            return "Error: CoderAgent not available or of wrong type. Expected dumb.jaider.master.coder.CoderAgent but found " + agentObject.getClass().getName();
        }

        CoderAgent coderAgent = (CoderAgent) agentObject; // Use the correctly typed variable

        String constructedPrompt;
        String lowerCaseArgs = args.toLowerCase().trim();

        // Check if the CoderAgent class is the one from dumb.jaider.master.coder
        // This is just a placeholder for the actual check needed based on file structure.
        // The original code had: import dumb.jaider.master.coder.CoderAgent;
        // If that's the case, the instanceof check should be against that.
        // For the sake of this subtask, assume the type check above is correct.

        if (lowerCaseArgs.equals("update project dependencies") || lowerCaseArgs.equals("update dependencies")) {
            constructedPrompt = String.format(
                "Task: Enhance Jaider's Capabilities - Update Dependencies\n" + // Fixed missing \n
                "------------------------------------\n" +
                "Description: %s\n" + // This will be the user's input e.g., "update project dependencies"
                "------------------------------------\n" +
                "Jaider's self-development task is to implement the following feature or improvement:\n\n" + // Fixed missing \n
                "'%s'\n\n" + // User's input again
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
                "   Do not attempt to call `proposeSelfUpdate` if there are no valid updates or if an error occurred.\n\n" + // Fixed missing \n
                "Please proceed with implementing this task.",
                args, args
            );
        } else {
            // Default generic self-develop prompt
            constructedPrompt = String.format(
                "Task: Enhance Jaider's Capabilities\n" +
                "------------------------------------\n" +
                "Description: %s\n" + // User's input
                "------------------------------------\n" +
                "Jaider's self-development task is to implement the following feature or improvement:\n\n" + // Fixed missing \n
                "'%s'\n\n" + // User's input again
                "Instructions for CoderAgent:\n" +
                "1. Analyze the request and understand the required changes to Jaider's codebase.\n" +
                "2. Identify the relevant files and modules that need modification or creation.\n" +
                "3. If new files are needed (e.g., new commands, services, tools), plan their structure, content, and interactions carefully.\n" +
                "4. Generate the necessary code changes using the `proposeSelfUpdate` tool.\n" +
                "   - For each file to be changed or created, call `proposeSelfUpdate` once.\n" +
                "   - Ensure generated diffs are in the standard unified diff format (e.g., output of `diff -u`).\n" + // Clarified diff format
                "   - Provide clear, descriptive commit messages for each proposed update.\n" +
                "5. If multiple steps or files are involved, propose them sequentially. Explain your overall plan if it's complex.\n" +
                "6. If you need to read existing files to inform your changes, use tools like `readFile` or `findRelevantCode`.\n" + // Corrected tool name based on typical availability
                "7. The `proposeSelfUpdate` tool handles user review and the actual application, build, test, and commit process.\n" +
                "8. Aim for modular, well-documented, and correct code. Ensure new Java code compiles and follows project conventions.\n" +
                "9. If the task is vague, ask for clarification. If the task seems too complex for a single step, break it down.\n\n" + // Fixed missing \n
                "Please proceed with implementing this task.",
                args, args
            );
        }

        try {
            // Ensure coderAgent is the variable name used if agentObject was cast to it.
            return coderAgent.act(constructedPrompt);
        } catch (Exception e) {
            // It's good practice to log to Jaider's logger if available, instead of System.err
            // For now, System.err is kept as per original code.
            System.err.println("Error executing CoderAgent.act for task '" + args + "': " + e.getMessage());
            e.printStackTrace(); // Stack trace is useful for debugging.
            return "Error: An unexpected error occurred while executing the self-development task with CoderAgent. Details: " + e.getMessage();
        }
    }
}
