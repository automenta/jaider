package dumb.jaider.staticanalysis;

/**
 * @param ruleId  Or check_id from Semgrep
 * @param severity  e.g., "ERROR", "WARNING", "INFO" */
public record StaticAnalysisIssue(String filePath, int startLine, int endLine, String message, String ruleId,
                                  String severity) {

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

}
