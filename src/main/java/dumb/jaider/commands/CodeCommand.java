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

            // Indicate that streaming is starting
            // sender.sendMessage("CoderAgent processing stream..."); // Optional: if you want an initial message

            coderAgent.streamAct(userRequest, responseChunk -> {
                sender.sendMessage(responseChunk);
            });

            // Optionally, send a message indicating streaming is complete if the streamAct method is blocking
            // or if there's a separate callback for completion.
            // For now, assume streamAct handles the full interaction or is non-blocking
            // and separate completion message might be handled by the agent if needed.
            // sender.sendMessage("CoderAgent stream finished.");


        } catch (Exception e) {
            sender.sendMessage("Error interacting with CoderAgent: " + e.getMessage());
            // Log the full stack trace for debugging
            // Consider using a proper logging framework:
            // import org.slf4j.Logger;
            // import org.slf4j.LoggerFactory;
            // private static final Logger logger = LoggerFactory.getLogger(CodeCommand.class);
            // logger.error("Error in CodeCommand", e);
        }
    }
}
