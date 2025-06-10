package dumb.jaider.tooling.staticanalysis;

// Represents a single issue found by a static analysis tool.
public class StaticAnalysisIssue {
    private final String filePath;
    private final int startLine;
    private final int endLine;
    private final String message;
    private final String ruleId;
    private final String severity; // e.g., ERROR, WARNING, INFO

    public StaticAnalysisIssue(String filePath, int startLine, int endLine, String message, String ruleId, String severity) {
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.message = message;
        this.ruleId = ruleId;
        this.severity = severity;
    }

    // Getters
    public String getFilePath() { return filePath; }
    public int getStartLine() { return startLine; }
    public int getEndLine() { return endLine; }
    public String getMessage() { return message; }
    public String getRuleId() { return ruleId; }
    public String getSeverity() { return severity; }

    @Override
    public String toString() {
        return String.format("[%s] %s:%d-%d: %s (%s)", severity, filePath, startLine, endLine, message, ruleId);
    }
}
