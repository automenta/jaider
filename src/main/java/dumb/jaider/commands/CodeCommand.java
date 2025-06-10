package dumb.jaider.commands;

import dumb.jaider.app.AppContext;
import dumb.jaider.agents.CoderAgent;
import dumb.jaider.ui.CommandSender;

public class CodeCommand implements Command {

    private final AppContext appContext;

    public CodeCommand(AppContext appContext) {
        this.appContext = appContext;
    }

    @Override
    public String getCommandName() {
        return "code";
    }

    @Override
    public String getHelp() {
        return "Engages the CoderAgent to perform code modifications based on your request. Usage: /code <your request>";
    }

    @Override
    public void execute(String[] args, CommandSender sender) {
        if (args.length == 0) {
            sender.sendMessage("Please provide a description of the code changes you want to make. Usage: /code <your request>");
            return;
        }

        String userRequest = String.join(" ", args);
        sender.sendMessage("Engaging CoderAgent with request: " + userRequest);

        try {
            CoderAgent coderAgent = appContext.getDependencyInjector().getComponent(CoderAgent.class);
            // Stream CoderAgent responses back to the sender
            // For now, just send a message indicating the agent would be invoked.
            // Actual streaming invocation will be like:
            // coderAgent.act(userRequest, responseChunk -> sender.sendMessage(responseChunk), () -> sender.sendMessage("CoderAgent finished."));
            // For simplicity in this step, we'll use a synchronous call and a placeholder message.

            String agentResponse = coderAgent.act(userRequest); // This is a synchronous call
            sender.sendMessage("CoderAgent response: " + agentResponse);

        } catch (Exception e) {
            sender.sendMessage("Error interacting with CoderAgent: " + e.getMessage());
            // Log the full stack trace for debugging
            // e.printStackTrace(); // Consider a proper logging framework
        }
    }
}
