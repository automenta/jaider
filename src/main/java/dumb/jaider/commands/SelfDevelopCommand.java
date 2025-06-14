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

        Object agent = context.app().getAgent("Coder"); // Use app context to get agent
        if (agent == null) {
            return "Error: CoderAgent not found.";
        }

        if (!(agent instanceof CoderAgent)) {
            return "Error: CoderAgent not available or of wrong type.";
        }

        CoderAgent coderAgent = (CoderAgent) agent;

        // Construct the detailed prompt for the CoderAgent
        // This prompt structure is based on the main thought process design.
        String constructedPrompt = String.format(
            "Task: Enhance Jaider's Capabilities\n" +
            "------------------------------------\n" +
            "Description: %s\n" +
            "------------------------------------\n" +
            "Jaider's self-development task is to implement the following feature or improvement:\n\n" +
            "'%s'\n\n" +
            "Instructions for CoderAgent:\n" +
            "1. Analyze the request and understand the required changes to Jaider's codebase.\n" +
            "2. Identify the relevant files and modules that need modification.\n" +
            "3. If new files are needed (e.g., new commands, services), plan their structure and interactions.\n" +
            "4. Generate the necessary code changes using the `proposeSelfUpdate` tool.\n" +
            "   - Ensure generated diffs are in the correct git merge diff format.\n" +
            "   - Provide clear commit messages for each proposed update.\n" +
            "5. If multiple steps or files are involved, propose them sequentially.\n" +
            "6. If you need to read existing files to inform your changes, use the appropriate tools.\n" +
            "7. After proposing changes, Jaider (potentially guided by a human user) will review and apply them.\n" +
            "8. Aim for modular and well-documented code.\n\n" +
            "Please proceed with implementing this task.",
            args, args // args is used twice in the prompt as per typical detailed prompts
        );

        try {
            return coderAgent.act(constructedPrompt);
        } catch (Exception e) {
            // Log the exception for debugging purposes if a logging framework is available
            // For now, returning a generic error message
            System.err.println("Error executing CoderAgent.act: " + e.getMessage());
            e.printStackTrace();
            return "Error: An unexpected error occurred while executing the self-development task with CoderAgent.";
        }
    }
}
