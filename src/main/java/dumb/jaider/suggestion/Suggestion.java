package dumb.jaider.suggestion;

public class Suggestion {
    private final String suggestedToolName;
    private final String suggestionText;
    private final String toolDescription;
    private final double confidenceScore;

    public Suggestion(String suggestedToolName, String suggestionText, String toolDescription, double confidenceScore) {
        this.suggestedToolName = suggestedToolName;
        this.suggestionText = suggestionText;
        this.toolDescription = toolDescription;
        this.confidenceScore = confidenceScore;
    }

    public String getSuggestedToolName() {
        return suggestedToolName;
    }

    public String getSuggestionText() {
        return suggestionText;
    }

    public String getToolDescription() {
        return toolDescription;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

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
