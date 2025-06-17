package dumb.jaider.toolmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import dumb.jaider.tooling.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


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

        var mapper = new ObjectMapper();
        try (var stream = Files.newDirectoryStream(toolManifestsDir, "*.json")) {
            for (var jsonFile : stream) {
                LOGGER.info("Found tool descriptor file: {}", jsonFile.toString());
                try (var descriptorStream = Files.newInputStream(jsonFile)) {
                    var descriptor = mapper.readValue(descriptorStream, ToolDescriptor.class);
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
            var descriptor = mapper.readValue(descriptorStream, ToolDescriptor.class);
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
        return Map.copyOf(toolDescriptors);
    }

    /**
     * Checks if a tool is installed and configured based on its descriptor.
     * If not, attempts to install and configure it.
     *
     * @param toolName Name of the tool.
     * @return True if the tool is ready, false otherwise.
     */
    public boolean provisionTool(String toolName) {
        var descriptor = toolDescriptors.get(toolName);
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
            var pb = new ProcessBuilder(descriptor.getAvailabilityCheckCommand().split("\\s+"));
            var process = pb.start();
            return process.waitFor() == descriptor.getAvailabilityCheckExitCode();
        } catch (Exception e) {
            LOGGER.warn("Availability check failed for {}", descriptor.getToolName(), e);
            return false;
        }
    }

    private String getCurrentPlatform() {
        var osName = System.getProperty("os.name").toLowerCase();
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

        var platform = getCurrentPlatform();
        var commands = Optional.ofNullable(descriptor.getInstallationCommands())
                                        .map(p -> p.get(platform))
                                        .orElse(new ArrayList<>());

        if (commands.isEmpty()) {
            var anyPlatformCommands = Optional.ofNullable(descriptor.getInstallationCommands())
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
        for (var command : commands) {
            try {
                var commandParts = command.split("\\s+");
                var pb = new ProcessBuilder(commandParts);
                var process = pb.start();

                // Capture stdout
                var stdout = new StringBuilder();
                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append(System.lineSeparator());
                    }
                }

                // Capture stderr
                var stderr = new StringBuilder();
                try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append(System.lineSeparator());
                    }
                }

                var exitCode = process.waitFor();

                if (!stdout.isEmpty()) {
                    LOGGER.info("Stdout for command '{}':\n{}", command, stdout);
                }

                if (exitCode != 0) {
                    LOGGER.error("Installation command failed: '{}' with exit code {}", command, exitCode);
                    if (!stderr.isEmpty()) {
                        LOGGER.error("Stderr for command '{}':\n{}", command, stderr);
                    }
                    return false;
                } else {
                    if (!stderr.isEmpty()) {
                        LOGGER.warn("Stderr for command '{}' (exit code 0):\n{}", command, stderr);
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
