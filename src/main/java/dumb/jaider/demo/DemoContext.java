package dumb.jaider.demo;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DemoContext {
    // General purpose data map
    private final Map<String, Object> data = new HashMap<>();

    // Specific, commonly used fields (optional, but can be convenient)
    private String currentCode;
    private Path projectPath;
    private Path currentFilePath;
    private String lastLLMResponse;

    public void set(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public String getCurrentCode() {
        return currentCode;
    }

    public void setCurrentCode(String currentCode) {
        this.currentCode = currentCode;
    }

    public Path getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(Path projectPath) {
        this.projectPath = projectPath;
    }

    public Path getCurrentFilePath() {
        return currentFilePath;
    }

    public void setCurrentFilePath(Path currentFilePath) {
        this.currentFilePath = currentFilePath;
    }

    public String getLastLLMResponse() {
        return lastLLMResponse;
    }

    public void setLastLLMResponse(String lastLLMResponse) {
        this.lastLLMResponse = lastLLMResponse;
    }
}
