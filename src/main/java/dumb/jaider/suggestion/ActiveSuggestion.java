package dumb.jaider.suggestion;

public record ActiveSuggestion(int displayNumber, Suggestion originalSuggestion, String prefillCommand) {

    @Override
    public String toString() {
        return "ActiveSuggestion{" +
                "displayNumber=" + displayNumber +
                ", originalSuggestion=" + originalSuggestion +
                ", prefillCommand='" + prefillCommand + '\'' +
                '}';
    }
}
