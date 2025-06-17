package dumb.jaider.suggestion;

import java.util.List;

public class SuggestionRule {
    private final List<String> keywords;
    private final String regexPattern;
    private final String targetToolName;
    private final String suggestionFormat;

    // Constructor for keyword-based rules
    public SuggestionRule(List<String> keywords, String targetToolName, String suggestionFormat) {
        this.keywords = keywords;
        this.regexPattern = null;
        this.targetToolName = targetToolName;
        this.suggestionFormat = suggestionFormat;
    }

    // Constructor for regex-based rules
    public SuggestionRule(String regexPattern, String targetToolName, String suggestionFormat) {
        this.keywords = null;
        this.regexPattern = regexPattern;
        this.targetToolName = targetToolName;
        this.suggestionFormat = suggestionFormat;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public String getRegexPattern() {
        return regexPattern;
    }

    public String getTargetToolName() {
        return targetToolName;
    }

    public String getSuggestionFormat() {
        return suggestionFormat;
    }

    public boolean matches(String userInput) {
        var lowerInput = userInput.toLowerCase();
        if (keywords != null) {
            for (var keyword : keywords) {
                if (lowerInput.contains(keyword.toLowerCase())) {
                    return true;
                }
            }
        } else if (regexPattern != null) {
            return lowerInput.matches(regexPattern); // Simple match, consider Pattern.compile for efficiency
        }
        return false;
    }
}
