package dumb.jaider.toolmanager;

import dumb.jaider.tooling.Tool;
// import dev.langchain4j.service.AiServices; // For LM integration later
// import dumb.jaider.llm.LlmService; // Assuming an LlmService exists or will be created

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

// Using org.json for parsing descriptor files for now
import org.json.JSONObject;
import org.json.JSONArray;
import com.fasterxml.jackson.databind.ObjectMapper; // For more robust JSON later if needed


/**
 * Manages the lifecycle of tools: discovery, installation, configuration, and availability.
 */
public class ToolManager {
    private static final Logger LOGGER = Logger.getLogger(ToolManager.class.getName());
    private final Map<String, ToolDescriptor> toolDescriptors = new HashMap<>();
    private final Map<String, Tool> availableTools = new HashMap<>(); // Tools ready to be used
    private final Path toolManifestsDir; // Directory where tool JSON descriptors are stored
    // private final LlmService llmService; // For LM-enhanced features

    public ToolManager(Path toolManifestsDir /*, LlmService llmService */) {
        this.toolManifestsDir = toolManifestsDir;
        // this.llmService = llmService;
        loadToolDescriptors();
    }

    private void loadToolDescriptors() {
        // This is a placeholder. In a real scenario, you'd scan toolManifestsDir for JSON files.
        // For now, we can imagine loading one example descriptor.
        // Example: loadDescriptorFromResource("semgrep-descriptor.json");
        LOGGER.info("ToolManager: Placeholder for loading tool descriptors from " + toolManifestsDir);
    }

    // Example of how a descriptor might be loaded (using org.json for simplicity for now)
    private void loadDescriptor(InputStream descriptorStream, String descriptorName) {
        try {
            // In a real app, using Jackson or Gson would be more robust than org.json for POJO mapping.
            ObjectMapper mapper = new ObjectMapper();
            ToolDescriptor descriptor = mapper.readValue(descriptorStream, ToolDescriptor.class);
            toolDescriptors.put(descriptor.getToolName(), descriptor);
            LOGGER.info("Loaded tool descriptor: " + descriptor.getToolName());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load or parse tool descriptor: " + descriptorName, e);
        }
    }


    public Optional<ToolDescriptor> getDescriptor(String toolName) {
        return Optional.ofNullable(toolDescriptors.get(toolName));
    }

    /**
     * Checks if a tool is installed and configured based on its descriptor.
     * If not, attempts to install and configure it.
     *
     * @param toolName Name of the tool.
     * @return True if the tool is ready, false otherwise.
     */
    public boolean provisionTool(String toolName) {
        ToolDescriptor descriptor = toolDescriptors.get(toolName);
        if (descriptor == null) {
            LOGGER.warning("No descriptor found for tool: " + toolName);
            return false;
        }

        if (isToolInstalled(descriptor)) {
            LOGGER.info("Tool " + toolName + " is already installed.");
            // Further configuration steps could go here
            return true;
        }

        LOGGER.info("Tool " + toolName + " not installed. Attempting to install...");
        return installTool(descriptor);
    }

    private boolean isToolInstalled(ToolDescriptor descriptor) {
        if (descriptor.getAvailabilityCheckCommand() == null || descriptor.getAvailabilityCheckCommand().isEmpty()) {
            LOGGER.warning("No availabilityCheckCommand for " + descriptor.getToolName() + ". Assuming not installed.");
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(descriptor.getAvailabilityCheckCommand().split("\\s+"));
            Process process = pb.start();
            return process.waitFor() == descriptor.getAvailabilityCheckExitCode();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Availability check failed for " + descriptor.getToolName(), e);
            return false;
        }
    }

    private String getCurrentPlatform() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) return "windows";
        if (osName.contains("mac")) return "macos";
        if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) return "linux";
        return "any"; // Fallback
    }


    private boolean installTool(ToolDescriptor descriptor) {
        // This is where LM interaction would happen.
        // 1. Get platform-specific installation commands from descriptor.
        // 2. If not present, use llmInstallationQueries with LlmService to get commands.
        // 3. Execute commands.

        String platform = getCurrentPlatform();
        List<String> commands = Optional.ofNullable(descriptor.getInstallationCommands())
                                        .map(p -> p.get(platform))
                                        .orElse(new ArrayList<>());

        if (commands.isEmpty()) {
             List<String> anyPlatformCommands = Optional.ofNullable(descriptor.getInstallationCommands())
                                        .map(p -> p.get("any"))
                                        .orElse(new ArrayList<>());
            if(!anyPlatformCommands.isEmpty()){
                commands = anyPlatformCommands;
            } else {
                LOGGER.warning("No installation commands found for " + descriptor.getToolName() + " on platform " + platform);
                // TODO: Add LM interaction here using descriptor.getLmInstallationQueries()
                // For now, just fail.
                return false;
            }
        }

        LOGGER.info("Attempting to install " + descriptor.getToolName() + " using commands: " + commands);
        for (String command : commands) {
            try {
                // Simple command execution. Needs to be more robust (working directory, env vars, etc.)
                Process process = Runtime.getRuntime().exec(command); // UNSAFE: Command injection vulnerable
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    LOGGER.severe("Installation command failed: '" + command + "' with exit code " + exitCode);
                    // TODO: Capture and log stderr/stdout from the process
                    return false;
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error executing installation command: '" + command + "'", e);
                return false;
            }
        }
        LOGGER.info(descriptor.getToolName() + " installation commands executed. Verifying installation...");
        return isToolInstalled(descriptor); // Verify after attempting install
    }

    // TODO: Methods for configuring tools, potentially using LM for suggestions.
    // TODO: Method to register an actual Tool instance once provisioned.
}
