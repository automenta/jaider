package dumb.jaider.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
// JSONArray is not used in the current Config class, so it's commented out.
// import org.json.JSONArray;

public class Config {
    final Path configFile;
    final Map<String, String> apiKeys = new HashMap<>();
    public String llmProvider = "ollama", runCommand; // Renamed testCommand to runCommand
    public String ollamaBaseUrl = "http://localhost:11434";
    public String ollamaModelName = "llamablit";
    public String genericOpenaiBaseUrl = "http://localhost:8080/v1";
    public String genericOpenaiModelName = "local-model";
    public String genericOpenaiApiKey = ""; // Typically "EMPTY" or "NA" for local models if header is mandatory
    public String geminiApiKey = "";
    public String geminiModelName = "gemini-1.5-flash-latest"; // Default Gemini model
    public String tavilyApiKey = "";

    public Config(Path projectDir) {
        this.configFile = projectDir.resolve(".jaider.json");
        load();
    }

    void load() {
        try {
            if (!Files.exists(configFile)) createDefaultConfig();
            var j = new JSONObject(Files.readString(configFile));
            llmProvider = j.optString("llmProvider", this.llmProvider);
            ollamaBaseUrl = j.optString("ollamaBaseUrl", this.ollamaBaseUrl);
            ollamaModelName = j.optString("ollamaModelName", this.ollamaModelName);
            genericOpenaiBaseUrl = j.optString("genericOpenaiBaseUrl", this.genericOpenaiBaseUrl);
            genericOpenaiModelName = j.optString("genericOpenaiModelName", this.genericOpenaiModelName);
            genericOpenaiApiKey = j.optString("genericOpenaiApiKey", this.genericOpenaiApiKey);
            geminiApiKey = j.optString("geminiApiKey", this.geminiApiKey);
            geminiModelName = j.optString("geminiModelName", this.geminiModelName);
            tavilyApiKey = j.optString("tavilyApiKey", this.tavilyApiKey);
            // Backward compatibility: read "testCommand" if "runCommand" is not present
            if (j.has("runCommand")) {
                runCommand = j.optString("runCommand", "");
            } else {
                runCommand = j.optString("testCommand", ""); // Fallback to testCommand
            }
            var keys = j.optJSONObject("apiKeys");
            if (keys != null) keys.keySet().forEach(key -> apiKeys.put(key, keys.getString(key)));
        } catch (Exception e) { /* Use defaults on failure, fields already have defaults */ }
    }

    private void createDefaultConfig() throws IOException {
        var defaultKeys = new JSONObject();
        defaultKeys.put("openai", "YOUR_OPENAI_API_KEY");
        defaultKeys.put("anthropic", "YOUR_ANTHROPIC_API_KEY");
        defaultKeys.put("google", "YOUR_GOOGLE_API_KEY");
        // Note: genericOpenaiApiKey is stored separately, not in the 'apiKeys' map by default
        var defaultConfig = new JSONObject();
        defaultConfig.put("llmProvider", llmProvider);
        defaultConfig.put("ollamaBaseUrl", ollamaBaseUrl);
        defaultConfig.put("ollamaModelName", ollamaModelName);
        defaultConfig.put("genericOpenaiBaseUrl", genericOpenaiBaseUrl);
        defaultConfig.put("genericOpenaiModelName", genericOpenaiModelName);
        defaultConfig.put("genericOpenaiApiKey", genericOpenaiApiKey);
        defaultConfig.put("geminiApiKey", geminiApiKey);
        defaultConfig.put("geminiModelName", geminiModelName);
        defaultConfig.put("tavilyApiKey", tavilyApiKey);
        defaultConfig.put("runCommand", runCommand == null ? "" : runCommand); // Use runCommand
        defaultConfig.put("apiKeys", defaultKeys); // For OpenAI, Anthropic, Google keys (geminiApiKey is separate)
        Files.writeString(configFile, defaultConfig.toString(2));
    }

    public void save(String newConfig) throws IOException {
        // When saving, parse the newConfig string and write it.
        // Then, reload to ensure the in-memory config reflects the saved file.
        Files.writeString(configFile, new JSONObject(newConfig).toString(2));
        load();
    }

    public String getApiKey(String provider) {
        return apiKeys.getOrDefault(provider, System.getenv(provider.toUpperCase() + "_API_KEY"));
    }

    public String readForEditing() throws IOException {
        // If file exists, load it to show the most accurate current state for editing.
        // Otherwise, construct a JSON string from current in-memory config settings.
        JSONObject configToEdit;
        if (Files.exists(configFile)) {
            configToEdit = new JSONObject(Files.readString(configFile));
            // Ensure all potentially new fields are present, falling back to current in-memory defaults if missing in file
            if (!configToEdit.has("llmProvider")) configToEdit.put("llmProvider", llmProvider);
            if (!configToEdit.has("ollamaBaseUrl")) configToEdit.put("ollamaBaseUrl", ollamaBaseUrl);
            if (!configToEdit.has("ollamaModelName")) configToEdit.put("ollamaModelName", ollamaModelName);
            if (!configToEdit.has("genericOpenaiBaseUrl")) configToEdit.put("genericOpenaiBaseUrl", genericOpenaiBaseUrl);
            if (!configToEdit.has("genericOpenaiModelName")) configToEdit.put("genericOpenaiModelName", genericOpenaiModelName);
            if (!configToEdit.has("genericOpenaiApiKey")) configToEdit.put("genericOpenaiApiKey", genericOpenaiApiKey);
            if (!configToEdit.has("geminiApiKey")) configToEdit.put("geminiApiKey", geminiApiKey);
            if (!configToEdit.has("geminiModelName")) configToEdit.put("geminiModelName", geminiModelName);
            if (!configToEdit.has("tavilyApiKey")) configToEdit.put("tavilyApiKey", tavilyApiKey);
            if (configToEdit.has("testCommand") && !configToEdit.has("runCommand")) { // Backward compatibility display
                configToEdit.put("runCommand", configToEdit.getString("testCommand"));
                configToEdit.remove("testCommand");
            }
            if (!configToEdit.has("runCommand")) configToEdit.put("runCommand", runCommand == null ? "" : runCommand);
            if (!configToEdit.has("apiKeys")) configToEdit.put("apiKeys", new JSONObject(apiKeys));
        } else {
            configToEdit = new JSONObject();
            configToEdit.put("llmProvider", llmProvider);
            configToEdit.put("ollamaBaseUrl", ollamaBaseUrl);
            configToEdit.put("ollamaModelName", ollamaModelName);
            configToEdit.put("genericOpenaiBaseUrl", genericOpenaiBaseUrl);
            configToEdit.put("genericOpenaiModelName", genericOpenaiModelName);
            configToEdit.put("genericOpenaiApiKey", genericOpenaiApiKey);
            configToEdit.put("geminiApiKey", geminiApiKey);
            configToEdit.put("geminiModelName", geminiModelName);
            configToEdit.put("tavilyApiKey", tavilyApiKey);
            configToEdit.put("runCommand", runCommand == null ? "" : runCommand);
            configToEdit.put("apiKeys", new JSONObject(apiKeys));
        }
        return configToEdit.toString(2);
    }
}
