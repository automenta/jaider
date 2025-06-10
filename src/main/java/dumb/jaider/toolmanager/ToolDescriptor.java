package dumb.jaider.toolmanager;

import java.util.List;
import java.util.Map;

/**
 * Describes a command-line tool that can be managed by Jaider.
 * This would typically be deserialized from a JSON manifest file.
 */
public class ToolDescriptor {
    private String toolName; // Matches the Tool.getName()
    private String displayName; // Human-friendly name
    private String description;
    private String version;
    private String homepageUrl;

    // How to check if the tool is installed (e.g., command like "tool --version")
    private String availabilityCheckCommand;
    // Expected exit code for successful availability check, typically 0
    private int availabilityCheckExitCode = 0;

    // Installation details (can be platform-specific)
    // Key: platform (e.g., "linux", "macos", "windows", "any")
    // Value: List of installation command steps
    private Map<String, List<String>> installationCommands;

    // Suggestions for how the LM can find installation instructions if not provided
    private List<String> lmInstallationQueries;

    // Default configuration parameters for the tool
    private Map<String, Object> defaultConfig;

    // New fields for static analysis integration
    private String analysisCommandPattern;
    private String resultsParserClass;
    private String category = "generic"; // Default category

    // Getters and Setters (or make fields public if simple POJO)
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getHomepageUrl() { return homepageUrl; }
    public void setHomepageUrl(String homepageUrl) { this.homepageUrl = homepageUrl; }
    public String getAvailabilityCheckCommand() { return availabilityCheckCommand; }
    public void setAvailabilityCheckCommand(String availabilityCheckCommand) { this.availabilityCheckCommand = availabilityCheckCommand; }
    public int getAvailabilityCheckExitCode() { return availabilityCheckExitCode; }
    public void setAvailabilityCheckExitCode(int availabilityCheckExitCode) { this.availabilityCheckExitCode = availabilityCheckExitCode; }
    public Map<String, List<String>> getInstallationCommands() { return installationCommands; }
    public void setInstallationCommands(Map<String, List<String>> installationCommands) { this.installationCommands = installationCommands; }
    public List<String> getLmInstallationQueries() { return lmInstallationQueries; }
    public void setLmInstallationQueries(List<String> lmInstallationQueries) { this.lmInstallationQueries = lmInstallationQueries; }
    public Map<String, Object> getDefaultConfig() { return defaultConfig; }
    public void setDefaultConfig(Map<String, Object> defaultConfig) { this.defaultConfig = defaultConfig; }

    public String getAnalysisCommandPattern() { return analysisCommandPattern; }
    public void setAnalysisCommandPattern(String analysisCommandPattern) { this.analysisCommandPattern = analysisCommandPattern; }
    public String getResultsParserClass() { return resultsParserClass; }
    public void setResultsParserClass(String resultsParserClass) { this.resultsParserClass = resultsParserClass; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
