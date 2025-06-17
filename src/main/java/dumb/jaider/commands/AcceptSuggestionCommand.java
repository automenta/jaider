package dumb.jaider.commands;

import dev.langchain4j.data.message.AiMessage;
import dumb.jaider.suggestion.ActiveSuggestion;

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
        var model = context.model(); // Corrected accessor
        var ui = context.ui(); // Corrected accessor

        if (args == null || args.isBlank()) {
            model.addLog(AiMessage.from("[Jaider] Please provide the number of the suggestion to accept. Usage: /accept <number>"));
            ui.redraw(model);
            return;
        }

        try {
            var suggestionNumber = Integer.parseInt(args.trim());
            var activeSuggestions = model.getActiveSuggestions();

            if (activeSuggestions == null || activeSuggestions.isEmpty()) {
                model.addLog(AiMessage.from("[Jaider] No active suggestions to accept."));
                ui.redraw(model);
                return;
            }

            ActiveSuggestion foundSuggestion = null;
            for (var activeSuggestion : activeSuggestions) {
                if (activeSuggestion.displayNumber() == suggestionNumber) {
                    foundSuggestion = activeSuggestion;
                    break;
                }
            }

            if (foundSuggestion != null) {
                var prefillCommand = foundSuggestion.prefillCommand();
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
