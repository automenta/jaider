package dumb.jaider.staticanalysis;

import java.util.List;
import java.util.Map;

public interface StaticAnalysisResultsParser {
    /**
     * Parses the raw output from a static analysis tool into a list of issues.
     *
     * @param rawOutput The raw string output from the tool.
     * @param defaultConfigFromDescriptor A map of default configurations from the tool's descriptor,
     *                                   which might be useful for parsing (e.g., severity mappings).
     * @return A list of {@link StaticAnalysisIssue} objects.
     * @throws Exception If parsing fails.
     */
    List<StaticAnalysisIssue> parse(String rawOutput, Map<String, Object> defaultConfigFromDescriptor) throws Exception;
}
