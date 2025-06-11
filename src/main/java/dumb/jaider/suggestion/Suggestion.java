package dumb.jaider.suggestion;

public record Suggestion(String suggestedToolName, String suggestionText, String toolDescription,
                         double confidenceScore) {

    @Override
    public String toString() {
        return "Suggestion{" +
                "suggestedToolName='" + suggestedToolName + '\'' +
                ", suggestionText='" + suggestionText + '\'' +
                ", toolDescription='" + toolDescription + '\'' +
                ", confidenceScore=" + confidenceScore +
                '}';
    }
}
