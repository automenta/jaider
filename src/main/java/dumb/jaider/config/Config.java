package dumb.jaider.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import dumb.jaider.app.DependencyInjector;

public class Config {
    final Path configFile;
    private Map<String, JSONObject> componentDefinitions = new HashMap<>();
    private transient DependencyInjector injector;
    private static final String COMPONENTS_KEY = "components";

    final Map<String, String> apiKeys = new HashMap<>();
    public String llmProvider = "ollama", runCommand;
    public String ollamaBaseUrl = "http://localhost:11434";
    public String ollamaModelName = "llamablit";
    public String genericOpenaiBaseUrl = "http://localhost:8080/v1";
    public String genericOpenaiModelName = "local-model";
    public String genericOpenaiApiKey = "";
    public String geminiApiKey = "";
    public String geminiModelName = "gemini-1.5-flash-latest";
    public String tavilyApiKey = "";

    public Config(Path projectDir) {
        this.configFile = projectDir.resolve(".jaider.json");
        load();
    }

    void load() {
        try {
            if (!Files.exists(configFile)) createDefaultConfig();
            var j = new JSONObject(Files.readString(configFile));

            this.componentDefinitions.clear(); // Clear existing definitions

            llmProvider = j.optString("llmProvider", this.llmProvider);
            ollamaBaseUrl = j.optString("ollamaBaseUrl", this.ollamaBaseUrl);
            ollamaModelName = j.optString("ollamaModelName", this.ollamaModelName);
            genericOpenaiBaseUrl = j.optString("genericOpenaiBaseUrl", this.genericOpenaiBaseUrl);
            genericOpenaiModelName = j.optString("genericOpenaiModelName", this.genericOpenaiModelName);
            genericOpenaiApiKey = j.optString("genericOpenaiApiKey", this.genericOpenaiApiKey);
            geminiApiKey = j.optString("geminiApiKey", this.geminiApiKey);
            geminiModelName = j.optString("geminiModelName", this.geminiModelName);
            tavilyApiKey = j.optString("tavilyApiKey", this.tavilyApiKey);

            // Handle runCommand and testCommand migration
            if (j.has("runCommand")) {
                runCommand = j.getString("runCommand");
            } else if (j.has("testCommand")) {
                runCommand = j.getString("testCommand");
            } // If neither is present, runCommand retains its default class member value (null)

            var keys = j.optJSONObject("apiKeys");
            if (keys != null) keys.keySet().forEach(key -> apiKeys.put(key, keys.getString(key)));

            // Load component definitions
            if (j.has(COMPONENTS_KEY)) {
                JSONArray componentDefsArray = j.getJSONArray(COMPONENTS_KEY);
                for (int i = 0; i < componentDefsArray.length(); i++) {
                    JSONObject componentDef = componentDefsArray.getJSONObject(i);
                    if (componentDef.has("id")) {
                        this.componentDefinitions.put(componentDef.getString("id"), componentDef);
                    } else {
                        System.err.println("Component definition missing 'id' in config: " + componentDef.toString());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error loading config: " + e.getMessage());
            // Attempt to create a default config if loading fails, then re-attempt to load parts of it.
            try {
                createDefaultConfig(); // This will write a default file.
                // After creating default, we might want to ensure componentDefinitions is empty or reflects default.
                // The subsequent injector initialization will use whatever is in componentDefinitions.
            } catch (IOException ex) {
                System.err.println("Failed to create default config: " + ex.getMessage());
            }
        }

        // Initialize or re-initialize the injector
        if (!this.componentDefinitions.isEmpty()) {
            this.injector = new DependencyInjector(new HashMap<>(this.componentDefinitions)); // Pass a copy
            this.injector.registerSingleton("appConfig", this); // Register Config itself
        } else {
            this.injector = null; // No components defined
        }
    }

    private void createDefaultConfig() throws IOException {
        var defaultKeys = new JSONObject();
        defaultKeys.put("openai", "YOUR_OPENAI_API_KEY");
        defaultKeys.put("anthropic", "YOUR_ANTHROPIC_API_KEY");
        defaultKeys.put("google", "YOUR_GOOGLE_API_KEY");

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
        defaultConfig.put("runCommand", runCommand == null ? "" : runCommand);
        defaultConfig.put("apiKeys", defaultKeys);
            JSONArray componentDefs = new JSONArray();

            // ChatMemory Component
            JSONObject chatMemoryDef = new JSONObject();
            chatMemoryDef.put("id", "chatMemory");
            chatMemoryDef.put("class", "dev.langchain4j.memory.chat.MessageWindowChatMemory");
            JSONObject chatMemoryProps = new JSONObject();
            chatMemoryProps.put("maxMessages", 20);
            chatMemoryDef.put("properties", chatMemoryProps);
            componentDefs.put(chatMemoryDef);

            // LlmProviderFactory Component
            JSONObject llmFactoryDef = new JSONObject();
            llmFactoryDef.put("id", "llmProviderFactory");
            llmFactoryDef.put("class", "dumb.jaider.llm.LlmProviderFactory");
            JSONArray llmFactoryArgs = new JSONArray();
            llmFactoryArgs.put(new JSONObject().put("ref", "appConfig"));
            llmFactoryArgs.put(new JSONObject().put("ref", "jaiderModel"));
            llmFactoryDef.put("constructorArgs", llmFactoryArgs);
            componentDefs.put(llmFactoryDef);

            // StandardTools Component
            JSONObject standardToolsDef = new JSONObject();
            standardToolsDef.put("id", "standardTools");
            standardToolsDef.put("class", "dumb.jaider.tools.StandardTools");
            JSONArray standardToolsArgs = new JSONArray();
            standardToolsArgs.put(new JSONObject().put("ref", "jaiderModel"));
            standardToolsArgs.put(new JSONObject().put("ref", "appConfig"));
            standardToolsArgs.put(new JSONObject().put("ref", "appEmbeddingModel"));
            standardToolsDef.put("constructorArgs", standardToolsArgs);
            componentDefs.put(standardToolsDef);

            // CoderAgent Component
            JSONObject coderAgentDef = new JSONObject();
            coderAgentDef.put("id", "coderAgent");
            coderAgentDef.put("class", "dumb.jaider.agents.CoderAgent");
            JSONArray coderAgentArgs = new JSONArray();
            coderAgentArgs.put(new JSONObject().put("ref", "appChatLanguageModel"));
            coderAgentArgs.put(new JSONObject().put("ref", "chatMemory"));
            coderAgentArgs.put(new JSONObject().put("ref", "standardTools"));
            coderAgentDef.put("constructorArgs", coderAgentArgs);
            componentDefs.put(coderAgentDef);

            // ArchitectAgent Component
            JSONObject architectAgentDef = new JSONObject();
            architectAgentDef.put("id", "architectAgent");
            architectAgentDef.put("class", "dumb.jaider.agents.ArchitectAgent");
            JSONArray architectAgentArgs = new JSONArray();
            architectAgentArgs.put(new JSONObject().put("ref", "appChatLanguageModel"));
            architectAgentArgs.put(new JSONObject().put("ref", "chatMemory"));
            architectAgentArgs.put(new JSONObject().put("ref", "standardTools"));
            architectAgentDef.put("constructorArgs", architectAgentArgs);
            componentDefs.put(architectAgentDef);

            // AskAgent Component
            JSONObject askAgentDef = new JSONObject();
            askAgentDef.put("id", "askAgent");
            askAgentDef.put("class", "dumb.jaider.agents.AskAgent");
            JSONArray askAgentArgs = new JSONArray();
            askAgentArgs.put(new JSONObject().put("ref", "appChatLanguageModel"));
            askAgentArgs.put(new JSONObject().put("ref", "chatMemory"));
            askAgentDef.put("constructorArgs", askAgentArgs);
            componentDefs.put(askAgentDef);

            defaultConfig.put(COMPONENTS_KEY, componentDefs);
        Files.writeString(configFile, defaultConfig.toString(2));
    }

    public void save(String newConfig) throws IOException {
        Files.writeString(configFile, new JSONObject(newConfig).toString(2));
        if (this.injector != null) {
            this.injector.clearCache();
        }
        load();
    }

    public String getApiKey(String provider) {
        return apiKeys.getOrDefault(provider, System.getenv(provider.toUpperCase() + "_API_KEY"));
    }

    public String readForEditing() throws IOException {

        JSONObject configToEdit;
        if (Files.exists(configFile)) {
            configToEdit = new JSONObject(Files.readString(configFile));

            if (!configToEdit.has("llmProvider")) configToEdit.put("llmProvider", llmProvider);
            if (!configToEdit.has("ollamaBaseUrl")) configToEdit.put("ollamaBaseUrl", ollamaBaseUrl);
            if (!configToEdit.has("ollamaModelName")) configToEdit.put("ollamaModelName", ollamaModelName);
            if (!configToEdit.has("genericOpenaiBaseUrl")) configToEdit.put("genericOpenaiBaseUrl", genericOpenaiBaseUrl);
            if (!configToEdit.has("genericOpenaiModelName")) configToEdit.put("genericOpenaiModelName", genericOpenaiModelName);
            if (!configToEdit.has("genericOpenaiApiKey")) configToEdit.put("genericOpenaiApiKey", genericOpenaiApiKey);
            if (!configToEdit.has("geminiApiKey")) configToEdit.put("geminiApiKey", geminiApiKey);
            if (!configToEdit.has("geminiModelName")) configToEdit.put("geminiModelName", geminiModelName);
            if (!configToEdit.has("tavilyApiKey")) configToEdit.put("tavilyApiKey", tavilyApiKey);

            // Handle runCommand and testCommand migration for editing
            if (configToEdit.has("testCommand")) {
                if (!configToEdit.has("runCommand")) { // If runCommand isn't there, migrate testCommand's value
                    configToEdit.put("runCommand", configToEdit.getString("testCommand"));
                }
                configToEdit.remove("testCommand"); // Always remove testCommand if it was present
            }
            // Ensure runCommand exists, defaulting to current field value (which could be null or migrated)
            if (!configToEdit.has("runCommand")) configToEdit.put("runCommand", runCommand == null ? JSONObject.NULL : runCommand);

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
            if (!configToEdit.has(COMPONENTS_KEY)) configToEdit.put(COMPONENTS_KEY, new JSONArray(this.componentDefinitions.values()));
        }
        return configToEdit.toString(2);
    }

    public <T> T getComponent(String id, Class<T> type) {
        if (injector == null) {
            throw new IllegalStateException("DependencyInjector not initialized. No components defined or loaded from settings.json?");
        }
        Object componentInstance = injector.getComponent(id);
        if (componentInstance == null) {
            // This might happen if the component ID is wrong or definition is missing
            throw new RuntimeException("Component with id '" + id + "' not found or failed to create from injector.");
        }
        if (!type.isInstance(componentInstance)) {
            throw new ClassCastException("Component '" + id + "' is of type " + componentInstance.getClass().getName() +
                                         " but expected type " + type.getName());
        }
        return type.cast(componentInstance);
    }

    public DependencyInjector getInjector() {
        return injector;
    }
}
