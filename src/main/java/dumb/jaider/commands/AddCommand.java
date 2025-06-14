package dumb.jaider.commands;

import java.util.Arrays;
import dev.langchain4j.data.message.AiMessage; // Assuming this is the correct AiMessage class

/**
 * Handles the "/add" command, allowing users to add one or more files
 * to the application's context. These files can then be used by the AI agent.
 */
public class AddCommand implements Command {

    /**
     * Executes the add command.
     * <p>
     * Takes a string of space-separated file paths, resolves them against the
     * project directory, and adds them to the {@link dumb.jaider.model.JaiderModel}'s
     * set of contextual files. After adding files, it triggers an update of the
     * token count via the {@link dumb.jaider.app.App} instance.
     * <p>
     * If no arguments are provided, it logs a usage message.
     *
     * @param args A string containing space-separated file paths or glob patterns
     *             (glob pattern support would depend on underlying implementation not shown here).
     *             If null or blank, usage information is displayed.
     * @param context The {@link AppContext} providing access to the model, app, and config.
     */
    @Override
    public void execute(String args, AppContext context) {
        if (args == null || args.isBlank()) {
            context.getJaiderModel().addLog(AiMessage.from("Usage: /add <file1> [file2] ..."));
            return;
        }
        // Trim leading/trailing whitespace from the overall args string before splitting
        String trimmedArgs = args.trim();
        if (trimmedArgs.isEmpty()) { // Check again after trim, in case args was just whitespace
            context.getJaiderModel().addLog(AiMessage.from("Usage: /add <file1> [file2] ..."));
            return;
        }

        String[] filesToAdd = trimmedArgs.split("\\s+");

        // It's good practice to log which files are actually being added.
        // The current implementation resolves paths but doesn't check for existence here.
        // That might be handled by the agent or when files are actually read.
        Arrays.stream(filesToAdd)
                .filter(fileName -> !fileName.isBlank()) // Ensure no empty strings from multiple spaces
                .map(fileName -> context.getJaiderModel().getDir().resolve(fileName.trim())) // Trim individual file names
                .forEach(path -> context.getJaiderModel().getFiles().add(path));

        context.getApp().updateTokenCountPublic(); // Assumes this method exists and is public

        // Provide feedback on what was added. String.join is good.
        // Consider if filesToAdd itself should be filtered for blank strings before joining for the log.
        String addedFilesMessage = String.join(", ", Arrays.stream(filesToAdd)
                                                            .map(String::trim)
                                                            .filter(s -> !s.isEmpty())
                                                            .toArray(String[]::new));
        if (!addedFilesMessage.isEmpty()) {
            context.getJaiderModel().addLog(AiMessage.from("Added to context: " + addedFilesMessage));
        } else {
            // This case might happen if input was like "/add   " and then split by space.
            // The initial isBlank() check should catch most, but defensive coding here is fine.
            context.getJaiderModel().addLog(AiMessage.from("Usage: /add <file1> [file2] ... (no valid file names provided)"));
        }
    }
}
