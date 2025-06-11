package dumb.jaider.tooling;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Provides context for a Tool's execution.
 * This includes information like target files, project directories, and tool-specific parameters.
 */
public class ToolContext {
    private final Path projectRoot;
    private final Map<String, Object> parameters;

    public ToolContext(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.parameters = new HashMap<>();
    }

    public Optional<Path> getProjectRoot() {
        return Optional.ofNullable(projectRoot);
    }

    public ToolContext addParameter(String key, Object value) {
        parameters.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getParameter(String key, Class<T> type) {
        Object value = parameters.get(key);
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    public Map<String, Object> getAllParameters() {
        return new HashMap<>(parameters); // Return a copy
    }
}
