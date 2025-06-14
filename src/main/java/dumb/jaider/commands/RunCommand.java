package dumb.jaider.commands;

import dumb.jaider.agents.Agent;
import dumb.jaider.tools.StandardTools;
import dumb.jaider.model.JaiderModel; // Required for addLog via AppContext
import dev.langchain4j.data.message.AiMessage; // Required for AiMessage

public class RunCommand implements Command {

    @Override
    public void execute(String args, AppContext context) {
        JaiderModel model = context.getModel();
        Agent currentAgent = context.getApp().getAgent(); // Get current agent from App

        if (currentAgent == null) {
            model.addLog(AiMessage.from("[RunCommand] Error: No active agent found."));
            return;
        }

        StandardTools standardTools = null;
        for (Object tool : currentAgent.tools()) {
            if (tool instanceof StandardTools) {
                standardTools = (StandardTools) tool;
                break;
            }
        }

        if (standardTools == null) {
            model.addLog(AiMessage.from("[RunCommand] Error: StandardTools not available for the current agent."));
            return;
        }

        model.addLog(AiMessage.from("[RunCommand] Executing run command with args: '" + (args == null ? "" : args) + "'"));
        try {
            String result = standardTools.runValidationCommand(args);
            // The result from runValidationCommand is already a JSON string,
            // potentially with newlines for readability.
            // Logging it directly is fine. If it needs to be part of another AI message,
            // it should be formatted appropriately.
            model.addLog(AiMessage.from("[RunCommand Result]
" + result));
        } catch (Exception e) {
            model.addLog(AiMessage.from("[RunCommand] Error executing validation command: " + e.getMessage()));
            // Optionally log stack trace to system error or a dedicated log file for debugging
            // e.printStackTrace();
        }
    }
}
