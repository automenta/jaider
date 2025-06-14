package dumb.jaider.commands;

import dumb.jaider.agents.Agent;
import dumb.jaider.tools.StandardTools;
import dumb.jaider.model.JaiderModel; // Required for addLog via AppContext
import dev.langchain4j.data.message.AiMessage;

/**
 * Handles the "/run" command, which typically executes a validation or test command
 * defined in the application's configuration. This command requires an active agent
 * with {@link StandardTools} available.
 */
public class RunCommand implements Command {

    /**
     * Executes the run command.
     * <p>
     * It first checks for an active AI agent and the availability of {@link StandardTools}
     * for that agent. If both are present, it invokes the {@code runValidationCommand}
     * method from {@link StandardTools}, passing any arguments provided to the /run command.
     * <p>
     * The outcome (success or error) of the validation command is logged to the
     * {@link JaiderModel}.
     *
     * @param args Arguments to be passed to the validation command. Can be null or empty.
     * @param context The {@link AppContext} providing access to the current application state,
     *                including the active agent and model.
     */
    @Override
    public void execute(String args, AppContext context) {
        JaiderModel model = context.model(); // Using getter from AppContext
        Agent currentAgent = context.app().getCurrentAgent();

        if (currentAgent == null) {
            model.addLog(AiMessage.from("[RunCommand] Error: No active agent found."));
            return;
        }

        StandardTools standardTools = null;
        if (currentAgent.tools() != null) { // Check if tools set is null before iterating
            for (Object tool : currentAgent.tools()) {
                if (tool instanceof StandardTools) {
                    standardTools = (StandardTools) tool;
                    break;
                }
            }
        }

        if (standardTools == null) {
            model.addLog(AiMessage.from("[RunCommand] Error: StandardTools not available for the current agent."));
            return;
        }

        String commandToExecute = (args == null) ? "" : args.trim();

        model.addLog(AiMessage.from("[RunCommand] Executing run command with args: '" + commandToExecute + "'"));
        try {
            String result = standardTools.runValidationCommand(commandToExecute);
            // The result from runValidationCommand might be multi-line or JSON.
            // Logging it directly.
            model.addLog(AiMessage.from("[RunCommand Result]\n" + result)); // Added newline for better formatting if result is multi-line
        } catch (Exception e) {
            // Log the exception message to the JaiderModel for user visibility
            model.addLog(AiMessage.from("[RunCommand] Error executing validation command: " + e.getMessage()));
            // It's also good practice to log the stack trace to the application logs for debugging.
            // Assuming a logger is available (e.g., SLF4J) - if not, this part would be omitted or use System.err.
            // org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RunCommand.class);
            // logger.error("Exception during /run command execution:", e);
        }
    }
}
