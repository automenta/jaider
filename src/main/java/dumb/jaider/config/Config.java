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

    // Fields with their Java-level defaults
    // Ensure apiKeys is initialized to prevent NullPointerExceptions if load() fails early
    final Map<String, String> apiKeys = new HashMap<>();
    public String llmProvider = "ollama";
    public String runCommand = ""; // Default to empty string
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
        load(); // Loads all fields, including componentDefinitions if present in file, or sets all fields to default if no file/bad file.

        Map<String, JSONObject> injectorDefs = this.componentDefinitions;
        // If this.componentDefinitions is empty after load(), it means the config source
        // (either a successfully read file or the initial defaults if file load failed)
        // did not provide component definitions or they were empty.
        // In this scenario, the injector should use the default component definitions,
        // while other settings (apiKeys, llmProvider, etc.) retain values from load().
        if (injectorDefs.isEmpty()) {
            System.err.println("Component definitions are empty after load. Initializing injector with default component definitions.");
            injectorDefs = extractComponentDefinitions(getDefaultConfigAsJsonObject());
        }

        this.injector = new DependencyInjector(new HashMap<>(injectorDefs)); // Pass a defensive copy
        this.injector.registerSingleton("appConfig", this);
    }

    // Helper to extract only component definitions from a full JSON config
    private Map<String, JSONObject> extractComponentDefinitions(JSONObject json) {
        Map<String, JSONObject> defs = new HashMap<>();
        if (json.has(COMPONENTS_KEY)) {
            JSONArray componentDefsArray = json.getJSONArray(COMPONENTS_KEY);
            for (int i = 0; i < componentDefsArray.length(); i++) {
                JSONObject componentDef = componentDefsArray.getJSONObject(i);
                if (componentDef.has("id")) {
                    defs.put(componentDef.getString("id"), componentDef);
                } else {
                    System.err.println("Component definition missing 'id' while extracting: " + componentDef.toString());
                }
            }
        }
        return defs;
    }

    void load() {
        boolean loadedSuccessfully = false;
        if (Files.exists(configFile)) {
            try {
                JSONObject j = new JSONObject(Files.readString(configFile));
                populateFieldsFromJson(j); // Populate fields from loaded JSON
                loadedSuccessfully = true;
            } catch (Exception e) {
                System.err.println("Error parsing existing config file ("+ configFile +"): " + e.getMessage() + ". Applying defaults.");
            }
        }

        if (!loadedSuccessfully) {
            System.err.println("Config not loaded from file, or file was partial/invalid. Applying full default configuration.");
            populateFieldsFromJson(getDefaultConfigAsJsonObject()); // Apply in-memory defaults

            // Attempt to write the default config to disk for user convenience
            try {
                Files.createDirectories(configFile.getParent());
                Files.writeString(configFile, getDefaultConfigAsJsonObject().toString(2));
                System.err.println("Written default config to: " + configFile);
            } catch (IOException e) {
                System.err.println("Warning: Failed to write default config to file: " + configFile + " - " + e.getMessage());
            }
        }
    }

    private JSONObject getDefaultConfigAsJsonObject() {
        JSONObject defaultConfig = new JSONObject();
        // Use class field defaults when constructing the default JSON object
        defaultConfig.put("llmProvider", this.llmProvider);
        defaultConfig.put("ollamaBaseUrl", this.ollamaBaseUrl);
        defaultConfig.put("ollamaModelName", this.ollamaModelName);
        defaultConfig.put("genericOpenaiBaseUrl", this.genericOpenaiBaseUrl);
        defaultConfig.put("genericOpenaiModelName", this.genericOpenaiModelName);
        defaultConfig.put("genericOpenaiApiKey", this.genericOpenaiApiKey);
        defaultConfig.put("geminiApiKey", this.geminiApiKey);
        defaultConfig.put("geminiModelName", this.geminiModelName);
        defaultConfig.put("tavilyApiKey", this.tavilyApiKey);
        defaultConfig.put("runCommand", this.runCommand); // Will be "" by default

        JSONObject defaultKeys = new JSONObject();
        defaultKeys.put("openai", "YOUR_OPENAI_API_KEY");
        defaultKeys.put("anthropic", "YOUR_ANTHROPIC_API_KEY");
        defaultKeys.put("google", "YOUR_GOOGLE_API_KEY");
        defaultConfig.put("apiKeys", defaultKeys);

        JSONArray componentDefsJsonArray = new JSONArray();
        JSONObject chatMemoryDef = new JSONObject();
        chatMemoryDef.put("id", "chatMemory");
        chatMemoryDef.put("class", "dev.langchain4j.memory.chat.MessageWindowChatMemory");
        chatMemoryDef.put("staticFactoryMethod", "withMaxMessages");
        JSONArray chatMemoryArgs = new JSONArray();
        JSONObject maxMessagesArg = new JSONObject().put("value", 20).put("type", "int");
        chatMemoryArgs.put(maxMessagesArg);
        chatMemoryDef.put("staticFactoryArgs", chatMemoryArgs);
        componentDefsJsonArray.put(chatMemoryDef);

        JSONObject llmFactoryDef = new JSONObject();
        llmFactoryDef.put("id", "llmProviderFactory");
        llmFactoryDef.put("class", "dumb.jaider.llm.LlmProviderFactory");
        JSONArray llmFactoryArgs = new JSONArray();
        llmFactoryArgs.put(new JSONObject().put("ref", "appConfig"));
        llmFactoryArgs.put(new JSONObject().put("ref", "jaiderModel"));
        llmFactoryDef.put("constructorArgs", llmFactoryArgs);
        componentDefsJsonArray.put(llmFactoryDef);

        JSONObject standardToolsDef = new JSONObject();
        standardToolsDef.put("id", "standardTools");
        standardToolsDef.put("class", "dumb.jaider.tools.StandardTools");
        JSONArray standardToolsArgs = new JSONArray();
        standardToolsArgs.put(new JSONObject().put("ref", "jaiderModel"));
        standardToolsArgs.put(new JSONObject().put("ref", "appConfig"));
        standardToolsArgs.put(new JSONObject().put("ref", "appEmbeddingModel"));
        standardToolsDef.put("constructorArgs", standardToolsArgs);
        componentDefsJsonArray.put(standardToolsDef);

        JSONObject coderAgentDef = new JSONObject();
        coderAgentDef.put("id", "coderAgent");
        coderAgentDef.put("class", "dumb.jaider.agents.CoderAgent");
        JSONArray coderAgentArgs = new JSONArray();
        coderAgentArgs.put(new JSONObject().put("ref", "appChatLanguageModel"));
        coderAgentArgs.put(new JSONObject().put("ref", "chatMemory"));
        coderAgentArgs.put(new JSONObject().put("ref", "standardTools"));
        coderAgentDef.put("constructorArgs", coderAgentArgs);
        componentDefsJsonArray.put(coderAgentDef);

        JSONObject architectAgentDef = new JSONObject();
        architectAgentDef.put("id", "architectAgent");
        architectAgentDef.put("class", "dumb.jaider.agents.ArchitectAgent");
        JSONArray architectAgentArgs = new JSONArray();
        architectAgentArgs.put(new JSONObject().put("ref", "appChatLanguageModel"));
        architectAgentArgs.put(new JSONObject().put("ref", "chatMemory"));
        architectAgentArgs.put(new JSONObject().put("ref", "standardTools"));
        architectAgentDef.put("constructorArgs", architectAgentArgs);
        componentDefsJsonArray.put(architectAgentDef);

        JSONObject askAgentDef = new JSONObject();
        askAgentDef.put("id", "askAgent");
        askAgentDef.put("class", "dumb.jaider.agents.AskAgent");
        JSONArray askAgentArgs = new JSONArray();
        askAgentArgs.put(new JSONObject().put("ref", "appChatLanguageModel"));
        askAgentArgs.put(new JSONObject().put("ref", "chatMemory"));
        askAgentDef.put("constructorArgs", askAgentArgs);
        componentDefsJsonArray.put(askAgentDef);

        defaultConfig.put(COMPONENTS_KEY, componentDefsJsonArray);
        return defaultConfig;
    }

    private void populateFieldsFromJson(JSONObject json) {
        // Use class field defaults as fallback for optString
        this.llmProvider = json.optString("llmProvider", this.llmProvider);
        this.ollamaBaseUrl = json.optString("ollamaBaseUrl", this.ollamaBaseUrl);
        this.ollamaModelName = json.optString("ollamaModelName", this.ollamaModelName);
        this.genericOpenaiBaseUrl = json.optString("genericOpenaiBaseUrl", this.genericOpenaiBaseUrl);
        this.genericOpenaiModelName = json.optString("genericOpenaiModelName", this.genericOpenaiModelName);
        this.genericOpenaiApiKey = json.optString("genericOpenaiApiKey", this.genericOpenaiApiKey);
        this.geminiApiKey = json.optString("geminiApiKey", this.geminiApiKey);
        this.geminiModelName = json.optString("geminiModelName", this.geminiModelName);
        this.tavilyApiKey = json.optString("tavilyApiKey", this.tavilyApiKey);

        // For runCommand, ensure it defaults to "" if missing, to satisfy ConfigTest
        this.runCommand = json.optString("runCommand", "");
        if (json.has("testCommand") && !json.has("runCommand")) { // Legacy migration also defaults to ""
             this.runCommand = json.optString("testCommand", "");
        }

        this.apiKeys.clear();
        JSONObject keys = json.optJSONObject("apiKeys");
        if (keys != null) {
            keys.keySet().forEach(key -> this.apiKeys.put(key, keys.getString(key)));
        }

        this.componentDefinitions.clear(); // Clear before populating
        if (json.has(COMPONENTS_KEY)) {
            JSONArray componentDefsArray = json.getJSONArray(COMPONENTS_KEY);
            for (int i = 0; i < componentDefsArray.length(); i++) {
                JSONObject componentDef = componentDefsArray.getJSONObject(i);
                if (componentDef.has("id")) {
                    this.componentDefinitions.put(componentDef.getString("id"), componentDef);
                } else {
                     System.err.println("Component definition missing 'id' in config: " + componentDef.toString());
                }
            }
        }
    }

    public void save(String newConfig) throws IOException {
        Files.writeString(configFile, new JSONObject(newConfig).toString(2));
        if (this.injector != null) {
            this.injector.clearCache();
        }
        load(); // Reload config and reinitialize/clear injector
         // Ensure injector is re-initialized with new definitions if it became null
        if (this.injector == null) {
            if (this.componentDefinitions.isEmpty()) {
                populateFieldsFromJson(getDefaultConfigAsJsonObject());
            }
            this.injector = new DependencyInjector(new HashMap<>(this.componentDefinitions));
            this.injector.registerSingleton("appConfig", this);
        }
    }

    public String getApiKey(String provider) {
        return apiKeys.getOrDefault(provider, System.getenv(provider.toUpperCase() + "_API_KEY"));
    }

    public String readForEditing() throws IOException {
        JSONObject configToEdit;
        if (Files.exists(configFile)) {
            try {
                configToEdit = new JSONObject(Files.readString(configFile));
            } catch (Exception e) { // If file is corrupt, start with defaults
                System.err.println("Warning: Couldn't read existing config for editing, starting with defaults: " + e.getMessage());
                configToEdit = getDefaultConfigAsJsonObject();
            }
        } else {
            configToEdit = getDefaultConfigAsJsonObject();
        }

        // Ensure all current fields are present, using current Config state as default
        // This is slightly different from populateFieldsFromJson as it preserves existing file values
        JSONObject baseConfig = getDefaultConfigAsJsonObject(); // Start with all defaults

        if (Files.exists(configFile)) {
            try {
                JSONObject fileConfig = new JSONObject(Files.readString(configFile));
                // Merge fileConfig into baseConfig. Keys in fileConfig will overwrite defaults.
                for (String key : fileConfig.keySet()) {
                    // Special handling for "apiKeys" and "components" to merge intelligently if needed,
                    // but for now, a simple put will overwrite, which is the old behavior for these.
                    baseConfig.put(key, fileConfig.get(key));
                }
            } catch (Exception e) {
                System.err.println("Warning: Couldn't read existing config for editing, using defaults: " + e.getMessage());
                // baseConfig is already set to defaults, so just proceed
            }
        }

        // Ensure legacy testCommand is migrated if present and runCommand is not already set by fileConfig
        if (baseConfig.has("testCommand") && !baseConfig.has("runCommand")) {
            baseConfig.put("runCommand", baseConfig.getString("testCommand"));
        }
        baseConfig.remove("testCommand"); // Always remove old key if it was there

        // Ensure runCommand has a default if absolutely nothing was specified from file or migration
        if (!baseConfig.has("runCommand")) {
             // this.runCommand is "" by default, but getDefaultConfigAsJsonObject already put it.
             // This line ensures it if somehow getDefaultConfigAsJsonObject changes or was overwritten by a file
             // that then got its runCommand removed.
            baseConfig.put("runCommand", this.runCommand);
        }

        // Ensure apiKeys and components are present, using defaults if not in loaded file.
        // getDefaultConfigAsJsonObject() already populates these, so this is more about ensuring
        // they weren't removed by a minimal config file.
        if (!baseConfig.has("apiKeys")) {
            baseConfig.put("apiKeys", getDefaultConfigAsJsonObject().getJSONObject("apiKeys"));
        }
        if (!baseConfig.has(COMPONENTS_KEY) || baseConfig.getJSONArray(COMPONENTS_KEY).isEmpty()) {
            baseConfig.put(COMPONENTS_KEY, getDefaultConfigAsJsonObject().getJSONArray(COMPONENTS_KEY));
        }

        return baseConfig.toString(2);
    }

    public <T> T getComponent(String id, Class<T> type) {
        if (injector == null) {
            // This might happen if constructor logic for injector init is bypassed or fails early.
            // Attempt a final re-init.
            System.err.println("Warning: Injector was null when getComponent was called. Re-initializing.");
            if (this.componentDefinitions.isEmpty()) {
                populateFieldsFromJson(getDefaultConfigAsJsonObject());
            }
            this.injector = new DependencyInjector(new HashMap<>(this.componentDefinitions));
            this.injector.registerSingleton("appConfig", this);
            if (injector == null) { // If still null after re-attempt
                 throw new IllegalStateException("DependencyInjector critically failed to initialize.");
            }
        }
        Object componentInstance = injector.getComponent(id);
        if (componentInstance == null) {
            throw new RuntimeException("Component with id '" + id + "' not found or failed to create from injector.");
        }
        if (!type.isInstance(componentInstance)) {
            throw new ClassCastException("Component '" + id + "' is of type " + componentInstance.getClass().getName() +
                                         " but expected type " + type.getName());
        }
        return type.cast(componentInstance);
    }

    public DependencyInjector getInjector() {
        // Ensure injector is initialized if accessed directly
        if (injector == null) {
             System.err.println("Warning: Injector was null when getInjector was called. Re-initializing.");
            if (this.componentDefinitions.isEmpty()) {
                populateFieldsFromJson(getDefaultConfigAsJsonObject());
            }
            this.injector = new DependencyInjector(new HashMap<>(this.componentDefinitions));
            this.injector.registerSingleton("appConfig", this);
             if (injector == null) { // If still null after re-attempt
                 throw new IllegalStateException("DependencyInjector critically failed to initialize on getInjector().");
            }
        }
        return injector;
    }
}
