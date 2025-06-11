package dumb.jaider.tools;

import dev.langchain4j.agent.tool.Tool;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.staticanalysis.StaticAnalysisIssue;
import dumb.jaider.staticanalysis.StaticAnalysisService;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class AnalysisTools {

    private final StaticAnalysisService staticAnalysisService;
    private final JaiderModel jaiderModel; // To get project root

    public AnalysisTools(StaticAnalysisService staticAnalysisService, JaiderModel jaiderModel) {
        this.staticAnalysisService = staticAnalysisService;
        this.jaiderModel = jaiderModel;
    }

    @Tool("Runs a named static analysis tool. Provide toolName (e.g., 'Semgrep') and targetPath (relative to project root).")
    public List<StaticAnalysisIssue> performStaticAnalysis(String toolName, String targetPathStr) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must be provided.");
        }
        if (targetPathStr == null || targetPathStr.isBlank()) {
            throw new IllegalArgumentException("targetPathStr must be provided.");
        }

        // Resolve targetPathStr relative to the project root from JaiderModel
        Path projectDir = jaiderModel.dir; // Corrected
        if (projectDir == null) {
            // This case should ideally not happen if JaiderModel is always initialized with a dir.
            // Fallback to current working directory, or throw error.
            System.err.println("Warning: JaiderModel projectDir is null. Resolving targetPath from current working directory.");
            projectDir = Paths.get("").toAbsolutePath();
        }
        Path targetPath = projectDir.resolve(targetPathStr).normalize();

        try {
            return staticAnalysisService.runAnalysis(toolName, targetPath, Collections.emptyMap());
        } catch (Exception e) {
            // Convert general exception to a runtime one or a specific error format for LLM
            System.err.println("Error during static analysis execution for " + toolName + " on " + targetPathStr + ": " + e.getMessage());
            e.printStackTrace();
            // Return a list with a single issue indicating the error, so LLM gets feedback
            return List.of(new StaticAnalysisIssue(
                targetPathStr, 0, 0,
                "Error running tool " + toolName + ": " + e.getMessage(),
                "ANALYSIS_EXECUTION_ERROR",
                "ERROR"
            ));
        }
    }
}
