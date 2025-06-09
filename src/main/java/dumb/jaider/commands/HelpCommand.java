package dumb.jaider.commands;

import dev.langchain4j.data.message.AiMessage;

import java.util.stream.Collectors;

public class HelpCommand implements Command {
    @Override
    public void execute(String args, AppContext context) {
        String availableModes = context.getApp().getAvailableAgentNames().stream()
                                     .sorted()
                                     .collect(Collectors.joining(", "));
        if (availableModes.isEmpty()) {
            availableModes = "No modes available (this is unexpected).";
        }

        var helpTxt = String.format("""
            [Jaider] --- Jaider Help ---
            Jaider is an AI assistant with multiple modes of operation.

            MODES:
            Switch modes with `/mode <ModeName>`. Available modes: %s.
            - Coder: The default mode for writing and fixing code.
            - Architect: A read-only mode for high-level questions about the codebase.
            - Ask: A simple Q&A mode with no access to your files.

            COMMANDS:
            /add <files...>: Add files to the context.
            /undo: Attempts to revert the last applied change.
            /index: Create a searchable index of your project (for RAG).
            /edit-config: Open the .jaider.json configuration file.
            /help: Show this help message.
            /exit: Exit Jaider.
        """, availableModes);
        context.getModel().addLog(AiMessage.from(helpTxt));
    }
}
