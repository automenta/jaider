package dumb.jaider.config;

import dumb.jaider.app.DependencyInjector;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger; // Added import
import org.slf4j.LoggerFactory; // Added import

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class); // Added logger
    final Path file;
    private JSONObject loadedJsonConfig; // Store the raw JSON
    private final Map<String, JSONObject> def = new HashMap<>();
    private transient DependencyInjector injector;
    private static final String COMPONENTS_KEY = "components";

    // Fields with their Java-level defaults
    // Ensure apiKeys is initialized to prevent NullPointerExceptions if load() fails early
    final Map<String, String> apiKeys = new HashMap<>();
    public String llm = "ollama";
    public String runCommand = ""; // Default to empty string
    public String ollamaBaseUrl = "http://localhost:11434";
    public String ollamaModelName = "llamablit";
    public String genericOpenaiBaseUrl = "http://localhost:8080/v1";
    public String genericOpenaiModelName = "local-model";
    public String genericOpenaiEmbeddingModelName = "text-embedding-ada-002"; // Added for Generic OpenAI Embedding
    public String genericOpenaiApiKey = "";
    public String openaiModelName = "gpt-4o-mini"; // Added for OpenAI
    public String geminiApiKey = "";
    public String geminiModelName = "gemini-1.5-flash-latest";
    public String geminiEmbeddingModelName = "textembedding-gecko"; // Added for Gemini Embedding
    public String tavilyApiKey = "";
    public String toolManifestsDir = "src/main/resources/tool-descriptors"; // Added for ToolManager

    public Config(Path projectDir) {
        this.file = projectDir.resolve(".jaider.json");
        load(); // Loads all fields, including componentDefinitions if present in file, or sets all fields to default if no file/bad file.

        Map<String, JSONObject> injectorDefs = this.def;
        // If this.componentDefinitions is empty after load(), it means the config source
        // (either a successfully read file or the initial defaults if file load failed)
        // did not provide component definitions or they were empty.
        // In this scenario, the injector should use the default component definitions,
        // while other settings (apiKeys, llmProvider, etc.) retain values from load().
        // this.def is populated by populateFieldsFromJson, called by load().
        if (this.def.isEmpty()) {
            // This case implies that even getDefaultConfigAsJsonObject() didn't provide component definitions,
            // or they were explicitly cleared or filtered out before this point.
            // This should ideally not happen if getDefaultConfigAsJsonObject() is correctly structured.
            logger.warn("Component definitions (this.def) are empty after load and potential default population. Attempting to extract from default JSON one last time."); // MODIFIED
            injectorDefs = extractComponentDefinitions(getDefaultConfigAsJsonObject());
            if (injectorDefs.isEmpty()) { // If still empty, it's a critical configuration error.
                 logger.error("Critical: No component definitions could be derived from defaults. Application cannot safely start."); // MODIFIED
                 throw new IllegalStateException("No component definitions found after loading and attempting to apply defaults. Application cannot safely start.");
            }
        } else {
            injectorDefs = this.def; // Use the already populated definitions
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
                if (componentDef.has("id") && componentDef.has("class")) { // MODIFIED check
                    defs.put(componentDef.getString("id"), componentDef);
                } else {
                    logger.warn("Component definition missing 'id' or 'class' while extracting from default JSON: {}", componentDef.toString(2)); // MODIFIED
                }
            }
        }
        return defs;
    }

    void load() {
        boolean loadedSuccessfully = false;
        if (Files.exists(file)) {
            try {
                String content = Files.readString(file);
                JSONObject j = new JSONObject(content);
                this.loadedJsonConfig = j; // Store it
                populateFieldsFromJson(j);
                loadedSuccessfully = true;
            } catch (Exception e) { // Broader catch, ensure exception is logged
                logger.error("Error parsing existing config file ({}): {}. Applying defaults.", file, e.getMessage(), e); // MODIFIED
            }
        }

        if (!loadedSuccessfully) {
            logger.warn("Config file {} not found or was invalid. Applying and writing default configuration.", file); // MODIFIED
            JSONObject defaultConfigJson = getDefaultConfigAsJsonObject();
            this.loadedJsonConfig = defaultConfigJson; // Store default JSON
            populateFieldsFromJson(defaultConfigJson); // This populates this.def as well

            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, this.loadedJsonConfig.toString(2));
                logger.info("Written default config to: {}", file); // MODIFIED
            } catch (IOException e) {
                logger.warn("Failed to write default config to file: {} - {}", file, e.getMessage(), e); // MODIFIED
            }
        }
    }

    private JSONObject getDefaultConfigAsJsonObject() {
        JSONObject defaultConfig = new JSONObject();
        // Use class field defaults when constructing the default JSON object
        defaultConfig.put("llmProvider", this.llm);
        defaultConfig.put("ollamaBaseUrl", this.ollamaBaseUrl);
        defaultConfig.put("ollamaModelName", this.ollamaModelName);
        defaultConfig.put("genericOpenaiBaseUrl", this.genericOpenaiBaseUrl);
        defaultConfig.put("genericOpenaiModelName", this.genericOpenaiModelName);
        defaultConfig.put("genericOpenaiEmbeddingModelName", this.genericOpenaiEmbeddingModelName); // Added for Generic OpenAI Embedding
        defaultConfig.put("genericOpenaiApiKey", this.genericOpenaiApiKey);
        defaultConfig.put("openaiModelName", this.openaiModelName); // Added for OpenAI
        defaultConfig.put("geminiApiKey", this.geminiApiKey);
        defaultConfig.put("geminiModelName", this.geminiModelName);
        defaultConfig.put("geminiEmbeddingModelName", this.geminiEmbeddingModelName); // Added for Gemini Embedding
        defaultConfig.put("tavilyApiKey", this.tavilyApiKey);
        defaultConfig.put("openaiApiKey", ""); // Add this for the new getOpenaiApiKey specific key
        defaultConfig.put("runCommand", this.runCommand); // Will be "" by default

        JSONObject defaultKeys = new JSONObject();
        defaultKeys.put("openai", "YOUR_OPENAI_API_KEY"); // Stays for general "openai" map access
        defaultKeys.put("anthropic", "YOUR_ANTHROPIC_API_KEY");
        defaultKeys.put("google", "YOUR_GOOGLE_API_KEY"); // Stays for general "google" map access (Gemini)
        defaultKeys.put("tavily", ""); // Add to map for consistency if getKeyValue uses it
        defaultKeys.put("genericOpenai", ""); // Add to map for consistency
        defaultConfig.put("apiKeys", defaultKeys);
        defaultConfig.put("toolManifestsDir", this.toolManifestsDir); // Added toolManifestsDir to JSON

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

        // New component definitions for self-update services
        componentDefsJsonArray.put(new JSONObject()
            .put("id", "userInterfaceService")
            .put("class", "org.jaider.ui.CommandLineUserInterfaceService")
        );
        componentDefsJsonArray.put(new JSONObject()
            .put("id", "gitService")
            .put("class", "org.jaider.service.LocalGitService")
        );
        componentDefsJsonArray.put(new JSONObject()
            .put("id", "buildManagerService")
            .put("class", "org.jaider.service.BuildManagerService")
        );
        componentDefsJsonArray.put(new JSONObject()
            .put("id", "restartService")
            .put("class", "org.jaider.service.BasicRestartService")
        );
        componentDefsJsonArray.put(new JSONObject()
            .put("id", "selfUpdateOrchestratorService")
            .put("class", "org.jaider.service.SelfUpdateOrchestratorService")
            .put("constructorArgs", new JSONArray()
                .put(new JSONObject().put("ref", "jaiderModel"))
                .put(new JSONObject().put("ref", "userInterfaceService"))
                .put(new JSONObject().put("ref", "buildManagerService"))
                .put(new JSONObject().put("ref", "gitService"))
                .put(new JSONObject().put("ref", "restartService"))
            )
        );
        componentDefsJsonArray.put(new JSONObject()
            .put("id", "jaiderTools")
            .put("class", "dumb.jaider.tools.JaiderTools")
            .put("constructorArgs", new JSONArray()
                .put(new JSONObject().put("ref", "jaiderModel"))
                .put(new JSONObject().put("ref", "selfUpdateOrchestratorService"))
            )
        );

        JSONObject coderAgentDef = new JSONObject();
        coderAgentDef.put("id", "coderAgent");
        coderAgentDef.put("class", "dumb.jaider.agents.CoderAgent");
        JSONArray coderAgentArgs = new JSONArray();
        coderAgentArgs.put(new JSONObject().put("ref", "appChatLanguageModel"));
        coderAgentArgs.put(new JSONObject().put("ref", "chatMemory"));
        coderAgentArgs.put(new JSONObject().put("ref", "standardTools"));
        coderAgentArgs.put(new JSONObject().put("ref", "jaiderTools"));
        coderAgentArgs.put(new JSONObject().put("ref", "smartRenameTool")); // Added smartRenameTool
        coderAgentDef.put("constructorArgs", coderAgentArgs);
        componentDefsJsonArray.put(coderAgentDef);

        JSONObject architectAgentDef = new JSONObject();
        architectAgentDef.put("id", "architectAgent");
        architectAgentDef.put("class", "dumb.jaider.agents.ArchitectAgent");
        JSONArray architectAgentArgs = new JSONArray();
        architectAgentArgs.put(new JSONObject().put("ref", "appChatLanguageModel"));
        architectAgentArgs.put(new JSONObject().put("ref", "chatMemory"));
        architectAgentArgs.put(new JSONObject().put("ref", "standardTools"));
        // Note: architectAgent does not get jaiderTools by default, consistent with original structure
        architectAgentDef.put("constructorArgs", architectAgentArgs);
        componentDefsJsonArray.put(architectAgentDef);

        JSONObject askAgentDef = new JSONObject();
        askAgentDef.put("id", "askAgent");
        askAgentDef.put("class", "dumb.jaider.agents.AskAgent");
        JSONArray askAgentArgs = new JSONArray();
        askAgentArgs.put(new JSONObject().put("ref", "appChatLanguageModel"));
        askAgentArgs.put(new JSONObject().put("ref", "chatMemory"));
        // Note: askAgent does not get jaiderTools by default
        askAgentDef.put("constructorArgs", askAgentArgs);
        componentDefsJsonArray.put(askAgentDef);

        // Definition for ToolManager
        JSONObject toolManagerDef = new JSONObject();
        toolManagerDef.put("id", "toolManager");
        toolManagerDef.put("class", "dumb.jaider.toolmanager.ToolManager");
        JSONArray toolManagerArgs = new JSONArray();
        // ToolManager expects a Path. We need to convert the string from config to Path.
        // The DI system currently supports "ref", "value" (simple types), and "list".
        // It doesn't directly support creating Path objects from string config values.
        // For now, let's assume ToolManager's constructor will handle String to Path,
        // or we modify ToolManager to accept String and convert internally,
        // or enhance DI. Simpler for now: assume ToolManager can take a String path.
        // If ToolManager strictly needs Path, this needs more DI enhancement or a factory.
        // Let's assume ToolManager is modified to take String, or DI is enhanced.
        // For the purpose of this step, we'll define it as if DI can provide a Path directly
        // from a configured string. This might require a custom factory or type converter in DI.
        // As a simpler immediate step, let's assume ToolManager's constructor takes a String path.
        // Path toolManifestsDir = Paths.get(this.toolManifestsDir); // This logic should be in ToolManager or DI
        // ToolManager constructor now takes String, so DI can pass the string value directly.
        toolManagerArgs.put(new JSONObject().put("value", this.toolManifestsDir).put("type", "java.lang.String"));
        toolManagerDef.put("constructorArgs", toolManagerArgs);
        componentDefsJsonArray.put(toolManagerDef);

        // ParserRegistry definition
        JSONObject parserRegistryDef = new JSONObject();
        parserRegistryDef.put("id", "parserRegistry");
        parserRegistryDef.put("class", "dumb.jaider.refactoring.ParserRegistry");
        // No constructor args for now, parsers will be registered programmatically or via a later config mechanism
        componentDefsJsonArray.put(parserRegistryDef);

        // RefactoringService definition
        JSONObject refactoringServiceDef = new JSONObject();
        refactoringServiceDef.put("id", "refactoringService");
        refactoringServiceDef.put("class", "dumb.jaider.refactoring.RefactoringService");
        JSONArray refactoringServiceArgs = new JSONArray();
        refactoringServiceArgs.put(new JSONObject().put("ref", "parserRegistry"));
        refactoringServiceDef.put("constructorArgs", refactoringServiceArgs);
        componentDefsJsonArray.put(refactoringServiceDef);

        // SmartRenameTool definition (assuming it's added to JaiderTools or as a separate component)
        // For now, let's define it as a separate component for clarity.
        // If it becomes part of JaiderTools, this definition would change.
        JSONObject smartRenameToolDef = new JSONObject();
        smartRenameToolDef.put("id", "smartRenameTool");
        smartRenameToolDef.put("class", "dumb.jaider.tools.SmartRenameTool");
        JSONArray smartRenameToolArgs = new JSONArray();
        smartRenameToolArgs.put(new JSONObject().put("ref", "refactoringService"));
        smartRenameToolDef.put("constructorArgs", smartRenameToolArgs);
        componentDefsJsonArray.put(smartRenameToolDef);

        // ListContextFilesTool definition
        JSONObject listContextFilesToolDef = new JSONObject();
        listContextFilesToolDef.put("id", "listContextFilesTool");
        listContextFilesToolDef.put("class", "dumb.jaider.tools.ListContextFilesTool");
        JSONArray listContextFilesToolArgs = new JSONArray();
        listContextFilesToolArgs.put(new JSONObject().put("ref", "jaiderModel"));
        listContextFilesToolDef.put("constructorArgs", listContextFilesToolArgs);
        componentDefsJsonArray.put(listContextFilesToolDef);

        // StaticAnalysisService definition
        JSONObject staticAnalysisServiceDef = new JSONObject();
        staticAnalysisServiceDef.put("id", "staticAnalysisService");
        staticAnalysisServiceDef.put("class", "dumb.jaider.staticanalysis.StaticAnalysisService");
        JSONArray staticAnalysisServiceArgs = new JSONArray();
        staticAnalysisServiceArgs.put(new JSONObject().put("ref", "toolManager"));
        staticAnalysisServiceDef.put("constructorArgs", staticAnalysisServiceArgs);
        componentDefsJsonArray.put(staticAnalysisServiceDef);

        // AnalysisTools definition
        JSONObject analysisToolsDef = new JSONObject();
        analysisToolsDef.put("id", "analysisTools");
        analysisToolsDef.put("class", "dumb.jaider.tools.AnalysisTools");
        JSONArray analysisToolsArgs = new JSONArray();
        analysisToolsArgs.put(new JSONObject().put("ref", "staticAnalysisService"));
        analysisToolsArgs.put(new JSONObject().put("ref", "jaiderModel")); // AnalysisTools needs JaiderModel
        analysisToolsDef.put("constructorArgs", analysisToolsArgs);
        componentDefsJsonArray.put(analysisToolsDef);

        defaultConfig.put(COMPONENTS_KEY, componentDefsJsonArray);
        return defaultConfig;
    }

    private void populateFieldsFromJson(JSONObject json) {
        // Use class field defaults as fallback for optString
        this.llm = json.optString("llmProvider", this.llm);
        this.ollamaBaseUrl = json.optString("ollamaBaseUrl", this.ollamaBaseUrl);
        this.ollamaModelName = json.optString("ollamaModelName", this.ollamaModelName);
        this.genericOpenaiBaseUrl = json.optString("genericOpenaiBaseUrl", this.genericOpenaiBaseUrl);
        this.genericOpenaiModelName = json.optString("genericOpenaiModelName", this.genericOpenaiModelName);
        this.genericOpenaiEmbeddingModelName = json.optString("genericOpenaiEmbeddingModelName", this.genericOpenaiEmbeddingModelName); // Added for Generic OpenAI Embedding
        this.genericOpenaiApiKey = json.optString("genericOpenaiApiKey", this.genericOpenaiApiKey);
        this.openaiModelName = json.optString("openaiModelName", this.openaiModelName); // Added for OpenAI
        this.geminiApiKey = json.optString("geminiApiKey", this.geminiApiKey);
        this.geminiModelName = json.optString("geminiModelName", this.geminiModelName);
        this.geminiEmbeddingModelName = json.optString("geminiEmbeddingModelName", this.geminiEmbeddingModelName); // Added for Gemini Embedding
        this.tavilyApiKey = json.optString("tavilyApiKey", this.tavilyApiKey);
        this.toolManifestsDir = json.optString("toolManifestsDir", this.toolManifestsDir); // Populate toolManifestsDir

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

        this.def.clear(); // Clear before populating
        if (json.has(COMPONENTS_KEY)) {
            JSONArray componentDefsArray = json.getJSONArray(COMPONENTS_KEY);
            for (int i = 0; i < componentDefsArray.length(); i++) {
                JSONObject componentDef = componentDefsArray.getJSONObject(i);
                if (componentDef.has("id") && componentDef.has("class")) { // MODIFIED check
                    this.def.put(componentDef.getString("id"), componentDef);
                } else {
                    logger.warn("Component definition in config file missing 'id' or 'class', skipping: {}", componentDef.toString(2)); // MODIFIED
                }
            }
        }
    }

    public void save(String newConfig) throws IOException {
        Files.writeString(file, new JSONObject(newConfig).toString(2));
        if (this.injector != null) {
            this.injector.clearCache();
        }
        load(); // Reload config and reinitialize/clear injector
        // Ensure injector is re-initialized with new definitions if it became null
        if (this.injector == null) {
            if (this.def.isEmpty()) {
                populateFieldsFromJson(getDefaultConfigAsJsonObject());
            }
            this.injector = new DependencyInjector(new HashMap<>(this.def));
            this.injector.registerSingleton("appConfig", this);
        }
    }

    private String getKeyValue(String envVarName, String specificJsonKey, String genericApiKeyMapKey) {
        String value = System.getenv(envVarName);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        // Check specific top-level key in the loaded JSON
        if (this.loadedJsonConfig != null && this.loadedJsonConfig.has(specificJsonKey)) {
            value = this.loadedJsonConfig.optString(specificJsonKey, null);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        // Fallback to apiKeys map (which is already populated from loadedJsonConfig or defaults)
        if (genericApiKeyMapKey != null) {
            value = this.apiKeys.get(genericApiKeyMapKey);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null; // Or return "" if empty string is preferred over null
    }

    public String getTavilyApiKey() {
        return getKeyValue("TAVILY_API_KEY", "tavilyApiKey", "tavily");
    }

    public String getGeminiApiKey() {
        return getKeyValue("GEMINI_API_KEY", "geminiApiKey", "google");
    }

    public String getGeminiModelName() { // Added getter
        return this.geminiModelName;
    }

    public String getGeminiEmbeddingModelName() { // Added getter for Gemini Embedding
        return this.geminiEmbeddingModelName;
    }

    public String getGenericOpenaiApiKey() {
        return getKeyValue("GENERIC_OPENAI_API_KEY", "genericOpenaiApiKey", "genericOpenai");
    }

    public String getGenericOpenaiEmbeddingModelName() { // Added getter for Generic OpenAI Embedding
        return this.genericOpenaiEmbeddingModelName;
    }

    public String getOpenaiApiKey() {
        return getKeyValue("OPENAI_API_KEY", "openaiApiKey", "openai");
    }

    public String getOpenaiModelName() { // Added getter for OpenAI model name
        return this.openaiModelName;
    }

    // Simpler version, prioritizing env, then the apiKeys map.
    public String getApiKey(String providerKeyInMap) {
        String envVarName = providerKeyInMap.toUpperCase() + "_API_KEY";
        String value = System.getenv(envVarName);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return this.apiKeys.getOrDefault(providerKeyInMap, null); // return null if not found
    }

    public String readForEditing() {
        JSONObject configToEdit;
        if (Files.exists(file)) {
            try {
                configToEdit = new JSONObject(Files.readString(file));
            } catch (Exception e) { // If file is corrupt, start with defaults
                logger.warn("Couldn't read existing config file {} for editing, starting with defaults: {}", file, e.getMessage(), e); // MODIFIED
                configToEdit = getDefaultConfigAsJsonObject();
            }
        } else {
            configToEdit = getDefaultConfigAsJsonObject();
        }

        // Ensure all current fields are present, using current Config state as default
        // This is slightly different from populateFieldsFromJson as it preserves existing file values
        JSONObject baseConfig = getDefaultConfigAsJsonObject(); // Start with all defaults

        if (Files.exists(file)) {
            try {
                JSONObject fileConfig = new JSONObject(Files.readString(file));
                // Merge fileConfig into baseConfig. Keys in fileConfig will overwrite defaults.
                for (String key : fileConfig.keySet()) {
                    // Special handling for "apiKeys" and "components" to merge intelligently if needed,
                    // but for now, a simple put will overwrite, which is the old behavior for these.
                    baseConfig.put(key, fileConfig.get(key));
                }
            } catch (Exception e) { // Broader catch, ensure exception is logged
                logger.warn("Couldn't process existing config file {} during merge for editing, using defaults on top of base: {}", file, e.getMessage(), e); // MODIFIED
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
        if (this.injector == null) {
            // This state should not be reached if the constructor logic is sound and enforced.
            throw new IllegalStateException("DependencyInjector is not initialized. Config constructor might have failed or been bypassed.");
        }
        Object componentInstance = this.injector.getComponent(id);
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
        if (this.injector == null) {
            // This state should not be reached if the constructor logic is sound and enforced.
            throw new IllegalStateException("DependencyInjector is not initialized. Config constructor might have failed or been bypassed.");
        }
        return this.injector;
    }
}
