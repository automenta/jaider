package dumb.jaider.tooling.staticanalysis;

import dumb.jaider.tooling.Tool;
import dumb.jaider.tooling.ToolContext;
import org.json.JSONObject; // Will need to ensure this dependency is handled later if not present
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;

public class SemgrepTool implements Tool {

    @Override
    public String getName() {
        return "Semgrep";
    }

    @Override
    public String getDescription() {
        return "Performs static analysis using Semgrep to find code patterns and potential issues.";
    }

    @Override
    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("semgrep", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String execute(ToolContext context) throws Exception {
        Path targetPath = context.getParameter("targetPath", Path.class)
                                 .orElseThrow(() -> new IllegalArgumentException("Missing 'targetPath' in ToolContext for Semgrep."));

        String semgrepConfig = context.getParameter("semgrepConfig", String.class)
                                      .orElse("auto"); // Default to 'auto' configuration

        // Ensure targetPath is within projectRoot if projectRoot is present
        context.getProjectRoot().ifPresent(root -> {
            if (!targetPath.toAbsolutePath().startsWith(root.toAbsolutePath())) {
                throw new IllegalArgumentException("targetPath is outside the projectRoot");
            }
        });

        ProcessBuilder pb = new ProcessBuilder(
            "semgrep",
            "scan",
            "--config", semgrepConfig,
            "--json", // Output in JSON format for easier parsing
            targetPath.toString()
        );

        context.getProjectRoot().ifPresent(root -> pb.directory(root.toFile())); // Run from project root if specified

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }
        process.waitFor();
        if (process.exitValue() != 0 && process.exitValue() != 1) { // Semgrep exits 1 if findings, 0 if no findings
             try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                StringBuilder errorOutput = new StringBuilder("Semgrep execution failed with exit code " + process.exitValue() + ":\n");
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append(System.lineSeparator());
                }
                throw new RuntimeException(errorOutput.toString());
            }
        }
        return output.toString();
    }

    @Override
    public Object parseOutput(String rawOutput) {
        List<StaticAnalysisIssue> issues = new ArrayList<>();
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            return issues; // No output to parse
        }

        try {
            JSONObject jsonOutput = new JSONObject(rawOutput);
            JSONArray results = jsonOutput.getJSONArray("results");

            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                String filePath = result.getString("path");
                int startLine = result.getJSONObject("start").getInt("line");
                int endLine = result.getJSONObject("end").getInt("line");
                JSONObject extra = result.getJSONObject("extra");
                String message = extra.getString("message");
                String ruleId = extra.getString("check_id"); // Or "rule_id" depending on Semgrep version/config
                String severity = extra.getString("severity"); // e.g., "ERROR", "WARNING", "INFO"

                issues.add(new StaticAnalysisIssue(filePath, startLine, endLine, message, ruleId, severity));
            }
        } catch (Exception e) {
            // Consider logging the parsing error
            System.err.println("Failed to parse Semgrep JSON output: " + e.getMessage());
            // Optionally, could return the raw string or a custom error object
            return null;
        }
        return issues;
    }
}
