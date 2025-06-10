package dumb.jaider.commands;

import dev.langchain4j.data.message.AiMessage;
import java.io.IOException;
import dumb.jaider.app.App; // For App.State


public class EditConfigCommand implements Command {
    @Override
    public void execute(String args, AppContext context) {
        context.getAppInstance().setStatePublic(App.State.WAITING_USER_CONFIRMATION); // Needs public access
        try {
            context.getUi().requestConfigEdit(context.getConfig().readForEditing()).thenAccept(newConfigStr -> {
                if (newConfigStr != null) {
                    try {
                        context.getAppInstance().updateAppConfigPublic(newConfigStr); // New public method
                        // Log is now handled by updateAppConfigPublic
                    } catch (IOException e) {
                        context.getModel().addLog(AiMessage.from("[Error] Failed to save config: " + e.getMessage()));
                    }
                }
                context.getAppInstance().finishTurnPublic(null); // Needs public access
            });
        } catch (IOException e) {
            context.getAppInstance().finishTurnPublic(AiMessage.from("[Error] Could not read config file: " + e.getMessage())); // Needs public access
        }
    }
}
