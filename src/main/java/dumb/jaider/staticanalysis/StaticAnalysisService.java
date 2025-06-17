package dumb.jaider.staticanalysis;

import dumb.jaider.toolmanager.ToolDescriptor;
import dumb.jaider.toolmanager.ToolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class StaticAnalysisService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaticAnalysisService.class);
    private final ToolManager toolManager;

    public StaticAnalysisService(ToolManager toolManager) {
        this.toolManager = Objects.requireNonNull(toolManager, "ToolManager cannot be null.");
    }

    public List<ToolDescriptor> getAvailableAnalyzers() {
        return toolManager.getToolDescriptors().values().stream()
                .filter(td -> "static-analyzer".equalsIgnoreCase(td.getCategory()))
                .collect(Collectors.toList());
    }

    public List<StaticAnalysisIssue> runAnalysis(String toolName, Path targetPath, Map<String, String> runtimeOptions)
            throws Exception { // Broad exception for now

        var descriptor = toolManager.getToolDescriptor(toolName);
        if (descriptor == null) {
            throw new IllegalArgumentException("No descriptor found for tool: " + toolName);
        }
        if (!"static-analyzer".equalsIgnoreCase(descriptor.getCategory())) {
            throw new IllegalArgumentException("Tool " + toolName + " is not categorized as a static-analyzer.");
        }

        if (!toolManager.provisionTool(toolName)) { // Ensure tool is available
            throw new RuntimeException("Tool " + toolName + " is not available or could not be installed.");
        }

        var commandPattern = descriptor.getAnalysisCommandPattern();
        if (commandPattern == null || commandPattern.isBlank()) {
            throw new IllegalStateException("Analysis command pattern is not defined for tool: " + toolName);
        }

        // Replace placeholders
        var commandToExecute = commandPattern.replace("{targetPath}", targetPath.toString());

        // Handle other placeholders from defaultConfig or runtimeOptions
        Map<String, Object> defaultConfig = descriptor.getDefaultConfig() != null ? descriptor.getDefaultConfig() : Map.of();

        // Prioritize runtimeOptions over defaultConfig for substitutions
        // Example for {semgrepConfig}
        var semgrepConfigValue = runtimeOptions.getOrDefault("semgrepConfig",
                                    (String) defaultConfig.getOrDefault("semgrepConfig", "auto"));
        commandToExecute = commandToExecute.replace("{semgrepConfig}", semgrepConfigValue);

        // Add more placeholder replacements as needed based on tool descriptor conventions

        LOGGER.info("Executing static analysis command: {}", commandToExecute);
        // Simple space splitting. This might not be robust for arguments containing spaces.
        // A more sophisticated parser or quoting strategy would be needed for such cases.
        var pb = new ProcessBuilder(commandToExecute.split("\\s+"));


        // Determine working directory: Use targetPath's parent, or project root if available and appropriate
        // For now, let's assume project root (if available from a context) or targetPath.getParent()
        // This part might need refinement based on where JaiderModel/project root is accessed.
        // For a generic service, targetPath.getParent() is a safe bet for tools analyzing specific files/dirs.
        var workingDirectory = targetPath.getParent() != null ? targetPath.getParent().toFile() : new File(".");
        pb.directory(workingDirectory);

        var process = pb.start();
        var rawOutput = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rawOutput.append(line).append(System.lineSeparator());
            }
        }

        var errorOutput = new StringBuilder();
        try (var errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append(System.lineSeparator());
            }
        }

        var exitCode = process.waitFor();
        // Some tools (like Semgrep) might return non-0 exit code if issues are found.
        // This needs to be configurable per tool descriptor, or handled by the parser if it expects such behavior.
        // For now, we proceed to parsing even if exitCode is non-zero, as output might still contain results.
        LOGGER.info("{} execution finished with exit code: {}. Output length: {}", toolName, exitCode, rawOutput.length());
        if (!errorOutput.isEmpty()) {
            LOGGER.warn("Error stream output from {}:\n{}", toolName, errorOutput);
        }


        var parserClassName = descriptor.getResultsParserClass();
        if (parserClassName == null || parserClassName.isBlank()) {
            LOGGER.warn("No results parser class defined for tool: {}. Returning raw output if any.", toolName);
            // Depending on desired behavior, could throw exception or return a single issue with raw output.
            if (!rawOutput.isEmpty()) {
                return List.of(new StaticAnalysisIssue(targetPath.toString(), 0, 0, "Raw output from " + toolName + ":\n" + rawOutput + (!errorOutput.isEmpty() ? "\nErrors:\n" + errorOutput : ""), "RAW_OUTPUT", "INFO"));
            }
            return new ArrayList<>(); // No parser and no output
        }

        try {
            var parserClass = Class.forName(parserClassName);
            var parser = (StaticAnalysisResultsParser) parserClass.getDeclaredConstructor().newInstance();
            return parser.parse(rawOutput.toString(), descriptor.getDefaultConfig());
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("Failed to instantiate or use results parser {} for tool {}", parserClassName, toolName, e);
            throw new Exception("Error with results parser " + parserClassName + ": " + e.getMessage(), e);
        } catch (Exception e) { // Catch exceptions from the parser itself
             LOGGER.error("Parser {} failed for tool {}", parserClassName, toolName, e);
            throw e; // Re-throw parser's specific exception
        }
    }
}
