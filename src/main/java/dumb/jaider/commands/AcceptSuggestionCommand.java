package dumb.jaider.commands;

import dumb.jaider.commands.AppContext; // Corrected import
import dumb.jaider.model.JaiderModel;
import dumb.jaider.suggestion.ActiveSuggestion;
import dumb.jaider.ui.UI;
import dev.langchain4j.data.message.AiMessage;

import java.util.List;

public class AcceptSuggestionCommand implements Command {

    // @Override // Commented out
    public String name() {
        return "/accept";
    }

    // @Override // Commented out
    public String description() {
        return "Accepts a proactive suggestion by its display number (e.g., /accept 1).";
    }

    // @Override // Commented out
    public void execute(String args, AppContext context) {
        JaiderModel model = context.model(); // Corrected accessor
        UI ui = context.ui(); // Corrected accessor

        if (args == null || args.isBlank()) {
            model.addLog(AiMessage.from("[Jaider] Please provide the number of the suggestion to accept. Usage: /accept <number>"));
            ui.redraw(model);
            return;
        }

        try {
            int suggestionNumber = Integer.parseInt(args.trim());
            List<ActiveSuggestion> activeSuggestions = model.getActiveSuggestions();

            if (activeSuggestions == null || activeSuggestions.isEmpty()) {
                model.addLog(AiMessage.from("[Jaider] No active suggestions to accept."));
                ui.redraw(model);
                return;
            }

            ActiveSuggestion foundSuggestion = null;
            for (ActiveSuggestion activeSuggestion : activeSuggestions) {
                if (activeSuggestion.getDisplayNumber() == suggestionNumber) {
                    foundSuggestion = activeSuggestion;
                    break;
                }
            }

            if (foundSuggestion != null) {
                String prefillCommand = foundSuggestion.getPrefillCommand();
                ui.setInputText(prefillCommand); // This method needs to be added to UI and TUI
                model.addLog(AiMessage.from(String.format("[Jaider] Prefilled input with suggestion %d: %s",
                        suggestionNumber, prefillCommand)));
                model.clearActiveSuggestions(); // Clear suggestions after accepting one
            } else {
                model.addLog(AiMessage.from("[Jaider] Suggestion number " + suggestionNumber + " not found."));
            }

        } catch (NumberFormatException e) {
            model.addLog(AiMessage.from("[Jaider] Invalid suggestion number. Please use a numeric value. Usage: /accept <number>"));
        }
        ui.redraw(model);
    }
}
