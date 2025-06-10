package dumb.jaider.toolmanager;

import dumb.jaider.tooling.Tool;
// import dev.langchain4j.service.AiServices; // For LM integration later
// import dumb.jaider.llm.LlmService; // Assuming an LlmService exists or will be created

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Files; // Added
import java.nio.file.DirectoryStream; // Added
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Using org.json for parsing descriptor files for now
import org.json.JSONObject;
import org.json.JSONArray;
import com.fasterxml.jackson.databind.ObjectMapper; // For more robust JSON later if needed


/**
 * Manages the lifecycle of tools: discovery, installation, configuration, and availability.
 */
public class ToolManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolManager.class);
    private final Map<String, ToolDescriptor> toolDescriptors = new HashMap<>();
    private final Map<String, Tool> availableTools = new HashMap<>(); // Tools ready to be used
    private final Path toolManifestsDir; // Directory where tool JSON descriptors are stored
    // private final LlmService llmService; // For LM-enhanced features

    public ToolManager(String toolManifestsDirStr /*, LlmService llmService */) {
        if (toolManifestsDirStr == null || toolManifestsDirStr.isBlank()) {
            LOGGER.warn("Tool manifests directory string is null or blank. ToolManager may not find descriptors.");
            this.toolManifestsDir = null; // Or a default safe path, or throw IllegalArgumentException
        } else {
            this.toolManifestsDir = Path.of(toolManifestsDirStr);
        }
        // this.llmService = llmService;
        loadToolDescriptors();
    }

    private void loadToolDescriptors() {
        if (toolManifestsDir == null || !Files.isDirectory(toolManifestsDir)) {
            LOGGER.warn("Tool manifests directory is not set or not a directory: {}", toolManifestsDir);
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(toolManifestsDir, "*.json")) {
            for (Path jsonFile : stream) {
                LOGGER.info("Found tool descriptor file: {}", jsonFile.toString());
                try (InputStream descriptorStream = Files.newInputStream(jsonFile)) {
                    ToolDescriptor descriptor = mapper.readValue(descriptorStream, ToolDescriptor.class);
                    if (descriptor != null && descriptor.getToolName() != null && !descriptor.getToolName().isEmpty()) {
                        toolDescriptors.put(descriptor.getToolName(), descriptor);
                        LOGGER.info("Successfully loaded and registered tool descriptor: {}", descriptor.getToolName());
                    } else {
                        LOGGER.warn("Parsed tool descriptor from {} is invalid (missing toolName or null).", jsonFile.getFileName());
                    }
                } catch (IOException e) {
                    LOGGER.error("Failed to load or parse tool descriptor: {}", jsonFile.getFileName(), e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error scanning tool manifests directory: {}", toolManifestsDir, e);
        }
    }

    // This method is now effectively replaced by the loop in loadToolDescriptors,
    // but kept if direct stream loading is needed elsewhere, though its direct usage here is removed.
    private void loadDescriptorFromStream(InputStream descriptorStream, String descriptorName, ObjectMapper mapper) {
        try {
            ToolDescriptor descriptor = mapper.readValue(descriptorStream, ToolDescriptor.class);
             if (descriptor != null && descriptor.getToolName() != null && !descriptor.getToolName().isEmpty()) {
                toolDescriptors.put(descriptor.getToolName(), descriptor);
                LOGGER.info("Loaded tool descriptor: {} from {}", descriptor.getToolName(), descriptorName);
            } else {
                LOGGER.warn("Parsed tool descriptor from {} is invalid (missing toolName or null).", descriptorName);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load or parse tool descriptor from stream: {}", descriptorName, e);
        }
    }

    public ToolDescriptor getToolDescriptor(String toolName) { // Changed return type
        return toolDescriptors.get(toolName);
    }

    /**
     * Returns a map of all loaded tool descriptors.
     * @return An unmodifiable map of tool names to their descriptors.
     */
    public Map<String, ToolDescriptor> getToolDescriptors() {
        return java.util.Collections.unmodifiableMap(new HashMap<>(toolDescriptors));
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
            LOGGER.warn("No descriptor found for tool: {}", toolName);
            return false;
        }

        if (isToolInstalled(descriptor)) {
            LOGGER.info("Tool {} is already installed.", toolName);
            // Further configuration steps could go here
            return true;
        }

        LOGGER.info("Tool {} not installed. Attempting to install...", toolName);
        return installTool(descriptor);
    }

    private boolean isToolInstalled(ToolDescriptor descriptor) {
        if (descriptor.getAvailabilityCheckCommand() == null || descriptor.getAvailabilityCheckCommand().isEmpty()) {
            LOGGER.warn("No availabilityCheckCommand for {}. Assuming not installed.", descriptor.getToolName());
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(descriptor.getAvailabilityCheckCommand().split("\\s+"));
            Process process = pb.start();
            return process.waitFor() == descriptor.getAvailabilityCheckExitCode();
        } catch (Exception e) {
            LOGGER.warn("Availability check failed for {}", descriptor.getToolName(), e);
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
                LOGGER.warn("No installation commands found for {} on platform {}", descriptor.getToolName(), platform);
                // TODO: Add LM interaction here using descriptor.getLmInstallationQueries()
                // For now, just fail.
                return false;
            }
        }

        LOGGER.info("Attempting to install {} using commands: {}", descriptor.getToolName(), commands);
        for (String command : commands) {
            try {
                String[] commandParts = command.split("\\s+");
                ProcessBuilder pb = new ProcessBuilder(commandParts);
                Process process = pb.start();

                // Capture stdout
                StringBuilder stdout = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append(System.lineSeparator());
                    }
                }

                // Capture stderr
                StringBuilder stderr = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append(System.lineSeparator());
                    }
                }

                int exitCode = process.waitFor();

                if (stdout.length() > 0) {
                    LOGGER.info("Stdout for command '{}':\n{}", command, stdout.toString());
                }

                if (exitCode != 0) {
                    LOGGER.error("Installation command failed: '{}' with exit code {}", command, exitCode);
                    if (stderr.length() > 0) {
                        LOGGER.error("Stderr for command '{}':\n{}", command, stderr.toString());
                    }
                    return false;
                } else {
                    if (stderr.length() > 0) {
                        LOGGER.warn("Stderr for command '{}' (exit code 0):\n{}", command, stderr.toString());
                    }
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.error("Error executing installation command: '{}'", command, e);
                Thread.currentThread().interrupt(); // Restore interrupted status
                return false;
            }
        }
        LOGGER.info("{} installation commands executed. Verifying installation...", descriptor.getToolName());
        return isToolInstalled(descriptor); // Verify after attempting install
    }

    // TODO: Methods for configuring tools, potentially using LM for suggestions.
    // TODO: Method to register an actual Tool instance once provisioned.
}
