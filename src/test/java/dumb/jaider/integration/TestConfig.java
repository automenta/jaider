package dumb.jaider.integration;

public class TestConfig {
    private String ollamaBaseUrl = "http://localhost:11434"; // Default, can be overridden
    private String ollamaModelName = "orca-mini"; // Default, can be overridden

    // Allow overriding defaults via constructor or setters if needed
    public TestConfig() {}

    public TestConfig(String ollamaBaseUrl, String ollamaModelName) {
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.ollamaModelName = ollamaModelName;
    }

    public String getOllamaBaseUrl() {
        return ollamaBaseUrl;
    }

    public void setOllamaBaseUrl(String ollamaBaseUrl) {
        this.ollamaBaseUrl = ollamaBaseUrl;
    }

    public String getOllamaModelName() {
        return ollamaModelName;
    }

    public void setOllamaModelName(String ollamaModelName) {
        this.ollamaModelName = ollamaModelName;
    }
}
