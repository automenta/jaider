package dumb.jaider.staticanalysis;

import java.util.Objects;

public class StaticAnalysisIssue {
    private final String filePath;
    private final int startLine;
    private final int endLine;
    private final String message;
    private final String ruleId; // Or check_id from Semgrep
    private final String severity; // e.g., "ERROR", "WARNING", "INFO"

    public StaticAnalysisIssue(String filePath, int startLine, int endLine, String message, String ruleId, String severity) {
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.message = message;
        this.ruleId = ruleId;
        this.severity = severity;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public String getMessage() {
        return message;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getSeverity() {
        return severity;
    }

    @Override
    public String toString() {
        return "StaticAnalysisIssue{" +
                "filePath='" + filePath + '\'' +
                ", startLine=" + startLine +
                ", endLine=" + endLine +
                ", message='" + message + '\'' +
                ", ruleId='" + ruleId + '\'' +
                ", severity='" + severity + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StaticAnalysisIssue that = (StaticAnalysisIssue) o;
        return startLine == that.startLine &&
               endLine == that.endLine &&
               Objects.equals(filePath, that.filePath) &&
               Objects.equals(message, that.message) &&
               Objects.equals(ruleId, that.ruleId) &&
               Objects.equals(severity, that.severity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, startLine, endLine, message, ruleId, severity);
    }
}
