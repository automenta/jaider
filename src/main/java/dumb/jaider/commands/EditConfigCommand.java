package dumb.jaider.commands;

import dev.langchain4j.data.message.AiMessage;
import dumb.jaider.app.App;

import java.io.IOException;


public class EditConfigCommand implements Command {
    @Override
    public void execute(String args, AppContext context) {
        context.app().setStatePublic(App.State.WAITING_USER_CONFIRMATION); // Needs public access
        context.ui().configEdit(context.config().readForEditing()).thenAccept(newConfigStr -> {
            if (newConfigStr != null) {
                try {
                    context.app().updateAppConfigPublic(newConfigStr); // New public method
                    // Log is now handled by updateAppConfigPublic
                } catch (IOException e) {
                    context.model().addLog(AiMessage.from("[Error] Failed to save config: " + e.getMessage()));
                }
            }
            context.app().finishTurnPublic(null); // Needs public access
        });

    }
}
