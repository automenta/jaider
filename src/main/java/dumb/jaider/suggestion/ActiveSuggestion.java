package dumb.jaider.suggestion;

public class ActiveSuggestion {
    private final int displayNumber;
    private final Suggestion originalSuggestion;
    private final String prefillCommand;

    public ActiveSuggestion(int displayNumber, Suggestion originalSuggestion, String prefillCommand) {
        this.displayNumber = displayNumber;
        this.originalSuggestion = originalSuggestion;
        this.prefillCommand = prefillCommand;
    }

    public int getDisplayNumber() {
        return displayNumber;
    }

    public Suggestion getOriginalSuggestion() {
        return originalSuggestion;
    }

    public String getPrefillCommand() {
        return prefillCommand;
    }

    @Override
    public String toString() {
        return "ActiveSuggestion{" +
                "displayNumber=" + displayNumber +
                ", originalSuggestion=" + originalSuggestion +
                ", prefillCommand='" + prefillCommand + '\'' +
                '}';
    }
}
