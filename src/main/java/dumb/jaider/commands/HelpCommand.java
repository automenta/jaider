package dumb.jaider.commands;

import dev.langchain4j.data.message.AiMessage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles the "/help" command, displaying a list of available commands
 * and other useful information to the user.
 */
public class HelpCommand implements Command {

    // Using ANSI escape codes for bold text in terminals that support it.
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BOLD = "\u001B[1m";

    // Static map for command descriptions. LinkedHashMap to maintain insertion order for display.
    // This was referenced in HelpCommandTest.java, so defining it here.
    static final Map<String, String> COMMANDS_HELP = new LinkedHashMap<>();
    static {
        COMMANDS_HELP.put("/add <file1> [file2] ...", "Add file(s) to the context. Supports glob patterns.");
        COMMANDS_HELP.put("/run [args]", "Run the validation command defined in config, passing optional arguments.");
        COMMANDS_HELP.put("/editconfig", "Edit the .jaider.json configuration file.");
        COMMANDS_HELP.put("/summarize [files...]", "Summarize specified files or the current context if no files are given.");
        COMMANDS_HELP.put("/mode <mode_name>", "Switch the active agent mode (e.g., Coder, Architect, Ask).");
        COMMANDS_HELP.put("/undo", "Revert the last code modification applied by Jaider.");
        COMMANDS_HELP.put("/index", "Manage the project's semantic index (used by some agents for context).");
        COMMANDS_HELP.put("/selfdevelop <task_description>", "EXPERIMENTAL: Allow Jaider to attempt a task by developing a plan and executing it.");
        COMMANDS_HELP.put("/exit", "Exit Jaider.");
        COMMANDS_HELP.put("/help", "Show this help message.");
        COMMANDS_HELP.put("/accept", "Accept the current proactive suggestion (aliases: /a, y, yes).");
        // Add other commands as they are implemented
    }

    /**
     * Executes the help command.
     * <p>
     * It retrieves available agent modes and formats a help message that includes
     * these modes and a list of all defined commands with their descriptions.
     * The arguments to this command are ignored.
     *
     * @param args Ignored.
     * @param context The {@link AppContext} providing access to application state,
     *                including available agent names.
     */
    @Override
    public void execute(String args, AppContext context) {
        var availableModes = context.app().getAvailableAgentNames().stream()
                                     .sorted()
                                     .collect(Collectors.joining(", "));
        if (availableModes.isEmpty()) {
            availableModes = "No modes available (this is unexpected).";
        }

        var helpTextBuilder = new StringBuilder();
        helpTextBuilder.append(ANSI_BOLD).append("Jaider Commands:").append(ANSI_RESET).append("\n");

        for (var entry : COMMANDS_HELP.entrySet()) {
            helpTextBuilder.append(ANSI_BOLD).append(entry.getKey()).append(ANSI_RESET)
                           .append(" - ").append(entry.getValue()).append("\n");
        }

        helpTextBuilder.append("\n").append(ANSI_BOLD).append("MODES:").append(ANSI_RESET).append("\n");
        helpTextBuilder.append("Switch modes with ").append(ANSI_BOLD).append("/mode <ModeName>").append(ANSI_RESET)
                       .append(". Available modes: ").append(availableModes).append(".\n");
        helpTextBuilder.append("- Coder: The default mode for writing and fixing code.\n");
        helpTextBuilder.append("- Architect: A read-only mode for high-level questions about the codebase.\n");
        helpTextBuilder.append("- Ask: A simple Q&A mode with no access to your files.\n");


        // The original formatting used String.format with a text block.
        // Replicating that structure with the dynamic COMMANDS_HELP map:

        // For logging, typically one addLog call per message or structured block is better.
        // The test HelpCommandTest expects multiple calls if we log line by line.
        // Let's log title, then each command, then modes block.

        context.model().addLog(AiMessage.from(ANSI_BOLD + "Jaider Commands:" + ANSI_RESET));
        COMMANDS_HELP.forEach((cmd, desc) -> context.model().addLog(AiMessage.from(ANSI_BOLD + cmd + ANSI_RESET + " - " + desc)));

        var modesHelp = String.format("""

            %sMODES:%s
            Switch modes with %s/mode <ModeName>%s. Available modes: %s.
            - Coder: The default mode for writing and fixing code.
            - Architect: A read-only mode for high-level questions about the codebase.
            - Ask: A simple Q&A mode with no access to your files.
            """, ANSI_BOLD, ANSI_RESET, ANSI_BOLD, ANSI_RESET, availableModes);
        context.model().addLog(AiMessage.from(modesHelp));
    }
}
