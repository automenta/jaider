package dumb.jaider.staticanalysis;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SemgrepResultsParser implements StaticAnalysisResultsParser {

    @Override
    public List<StaticAnalysisIssue> parse(String rawOutput, Map<String, Object> defaultConfigFromDescriptor) throws Exception {
        List<StaticAnalysisIssue> issues = new ArrayList<>();
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            return issues; // No output to parse
        }

        // The defaultConfigFromDescriptor is not used in this specific parser,
        // but could be used by other parsers for more complex configurations.

        try {
            var jsonOutput = new JSONObject(rawOutput);
            var results = jsonOutput.getJSONArray("results");

            for (var i = 0; i < results.length(); i++) {
                var result = results.getJSONObject(i);
                var filePath = result.getString("path");
                var startLine = result.getJSONObject("start").getInt("line");
                var endLine = result.getJSONObject("end").getInt("line");
                // int startCol = result.getJSONObject("start").getInt("col"); // Available if needed
                // int endCol = result.getJSONObject("end").getInt("col"); // Available if needed

                var extra = result.getJSONObject("extra");
                var message = extra.getString("message");

                // Semgrep's "check_id" is often more specific like "rules.java.lang.security.audit.formatted-sql-string.formatted-sql-string"
                // while "metadata.rule_id" (if present from custom rulesets) might be simpler.
                // For now, using "check_id" as it's generally available.
                var ruleId = extra.getString("check_id");

                var severity = extra.getString("severity"); // e.g., "ERROR", "WARNING", "INFO"

                issues.add(new StaticAnalysisIssue(filePath, startLine, endLine, message, ruleId, severity));
            }
        } catch (Exception e) {
            // Wrap org.json.JSONException or other parsing exceptions into a generic Exception
            // as per the interface, or define a custom parsing exception.
            throw new Exception("Failed to parse Semgrep JSON output: " + e.getMessage(), e);
        }
        return issues;
    }
}
