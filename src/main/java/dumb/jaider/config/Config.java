package dumb.jaider.config;

import dumb.jaider.app.DependencyInjector;
import dumb.jaider.app.exceptions.ComponentNotFoundException;
import dumb.jaider.app.exceptions.ComponentInstantiationException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    final Path file;
    private JSONObject loadedJsonConfig; // Stores the raw JSON loaded from file or defaults
    private final Map<String, JSONObject> componentDefinitions = new HashMap<>();
    private transient DependencyInjector injector;
    private static final String COMPONENTS_KEY = "components";

    // Field defaults
    final Map<String, String> apiKeys = new HashMap<>();
    public String llm = "ollama";
    public String runCommand = "";
    public String ollamaBaseUrl = "http://localhost:11434";
    public String ollamaModelName = "llamablit";
    public String genericOpenaiBaseUrl = "http://localhost:8080/v1";
    public String genericOpenaiModelName = "local-model";
    public String genericOpenaiEmbeddingModelName = "text-embedding-ada-002";
    public String genericOpenaiApiKey = "";
    public String openaiModelName = "gpt-4o-mini";
    public String geminiApiKey = "";
    public String geminiModelName = "gemini-1.5-flash-latest";
    public String geminiEmbeddingModelName = "textembedding-gecko";
    public String tavilyApiKey = "";
    public String toolManifestsDir = "src/main/resources/tool-descriptors";

    public Config(Path projectDir) {
        this.file = projectDir.resolve(".jaider.json");
        load(); // Populates fields and componentDefinitions from file or defaults

        if (this.componentDefinitions.isEmpty()) {
            String errorMsg = "CRITICAL: No component definitions found after attempting to load from file and apply defaults. getDefaultConfigAsJsonObject() might be incomplete or malformed.";
            logger.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        logger.info("Initializing DependencyInjector with {} component definitions.", this.componentDefinitions.size());
        this.injector = new DependencyInjector(new HashMap<>(this.componentDefinitions)); // Use a defensive copy
        this.injector.registerSingleton("appConfig", this);
        // Note: The 'app' instance (App.java) should register itself with the injector after Config is fully initialized.
    }

    void load() {
        boolean loadedSuccessfully = false;
        if (Files.exists(file)) {
            try {
                String content = Files.readString(file);
                logger.info("Loading configuration from file: {}", file);
                JSONObject j = new JSONObject(content);
                this.loadedJsonConfig = j;
                populateFieldsFromJson(j); // This will populate fields and componentDefinitions
                loadedSuccessfully = true;
                logger.info("Successfully loaded and parsed configuration from {}.", file);
            } catch (Exception e) { // Catch parsing errors or IOException
                logger.error("Error reading or parsing config file ({}): {}. Applying defaults.", file, e.getMessage(), e);
            }
        } else {
            logger.info("Config file {} not found.", file);
        }

        if (!loadedSuccessfully) {
            logger.warn("Applying and writing default configuration to {}.", file);
            JSONObject defaultConfigJson = getDefaultConfigAsJsonObject();
            this.loadedJsonConfig = defaultConfigJson; // Keep a copy of the raw default JSON
            populateFieldsFromJson(defaultConfigJson); // Populate fields and componentDefinitions from defaults

            try {
                Path parentDir = file.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }
                Files.writeString(file, this.loadedJsonConfig.toString(2)); // Write the generated default JSON
                logger.info("Written default configuration to: {}", file);
            } catch (IOException e) {
                logger.warn("Failed to write default config to file: {} - {}", file, e.getMessage(), e);
            }
        }
    }

    private void populateFieldsFromJson(JSONObject json) {
        // Populate all other fields from JSON, using current field values as defaults
        this.llm = json.optString("llmProvider", this.llm);
        this.ollamaBaseUrl = json.optString("ollamaBaseUrl", this.ollamaBaseUrl);
        // ... (all other simple fields as before) ...
        this.genericOpenaiBaseUrl = json.optString("genericOpenaiBaseUrl", this.genericOpenaiBaseUrl);
        this.genericOpenaiModelName = json.optString("genericOpenaiModelName", this.genericOpenaiModelName);
        this.genericOpenaiEmbeddingModelName = json.optString("genericOpenaiEmbeddingModelName", this.genericOpenaiEmbeddingModelName);
        this.genericOpenaiApiKey = json.optString("genericOpenaiApiKey", this.genericOpenaiApiKey);
        this.openaiModelName = json.optString("openaiModelName", this.openaiModelName);
        this.geminiApiKey = json.optString("geminiApiKey", this.geminiApiKey);
        this.geminiModelName = json.optString("geminiModelName", this.geminiModelName);
        this.geminiEmbeddingModelName = json.optString("geminiEmbeddingModelName", this.geminiEmbeddingModelName);
        this.tavilyApiKey = json.optString("tavilyApiKey", this.tavilyApiKey);
        this.toolManifestsDir = json.optString("toolManifestsDir", this.toolManifestsDir);
        this.runCommand = json.optString("runCommand", "");
        if (json.has("testCommand") && !json.has("runCommand")) { // Legacy
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
            logger.info("Attempting to load {} component definitions from provided JSON.", componentDefsArray.length());
            for (int i = 0; i < componentDefsArray.length(); i++) {
                JSONObject componentDef = componentDefsArray.getJSONObject(i);
                if (componentDef.has("id") && componentDef.has("class")) {
                    this.componentDefinitions.put(componentDef.getString("id"), componentDef);
                } else {
                    logger.warn("Skipping component definition due to missing 'id' or 'class': {}", componentDef.toString(2));
                }
            }
            logger.info("Successfully populated {} component definitions from JSON source.", this.componentDefinitions.size());
        } else {
            logger.warn("No '{}' key found in JSON source. No component definitions loaded from this source.", COMPONENTS_KEY);
        }
    }

    private JSONObject getDefaultConfigAsJsonObject() {
        JSONObject defaultConfig = new JSONObject();
        // Populate simple fields
        defaultConfig.put("llmProvider", "ollama"); // Default value
        defaultConfig.put("ollamaBaseUrl", "http://localhost:11434");
        defaultConfig.put("ollamaModelName", "llamablit");
        defaultConfig.put("genericOpenaiBaseUrl", "http://localhost:8080/v1");
        defaultConfig.put("genericOpenaiModelName", "local-model");
        defaultConfig.put("genericOpenaiEmbeddingModelName", "text-embedding-ada-002");
        defaultConfig.put("genericOpenaiApiKey", "");
        defaultConfig.put("openaiModelName", "gpt-4o-mini");
        defaultConfig.put("openaiApiKey", ""); // Specific key for OpenAI calls
        defaultConfig.put("geminiApiKey", "");
        defaultConfig.put("geminiModelName", "gemini-1.5-flash-latest");
        defaultConfig.put("geminiEmbeddingModelName", "textembedding-gecko");
        defaultConfig.put("tavilyApiKey", "");
        defaultConfig.put("runCommand", "");
        defaultConfig.put("toolManifestsDir", "src/main/resources/tool-descriptors");

        JSONObject defaultKeys = new JSONObject();
        defaultKeys.put("openai", "YOUR_OPENAI_API_KEY");
        defaultKeys.put("anthropic", "YOUR_ANTHROPIC_API_KEY");
        defaultKeys.put("google", "YOUR_GOOGLE_API_KEY"); // For Gemini
        defaultKeys.put("tavily", "");
        defaultKeys.put("genericOpenai", "");
        defaultConfig.put("apiKeys", defaultKeys);

        JSONArray components = new JSONArray();
        // Foundational
        components.put(new JSONObject().put("id", "chatMemory").put("class", "dev.langchain4j.memory.chat.MessageWindowChatMemory").put("staticFactoryMethod", "withMaxMessages").put("staticFactoryArgs", new JSONArray().put(new JSONObject().put("value", 20).put("type", "int"))));
        components.put(new JSONObject().put("id", "llmProviderFactory").put("class", "dumb.jaider.llm.LlmProviderFactory").put("constructorArgs", new JSONArray().put(new JSONObject().put("ref", "appConfig")).put(new JSONObject().put("ref", "jaiderModel"))));
        components.put(new JSONObject().put("id", "toolManager").put("class", "dumb.jaider.toolmanager.ToolManager").put("constructorArgs", new JSONArray().put(new JSONObject().put("value", this.toolManifestsDir).put("type", "java.lang.String"))));

        // Tools & Refactoring
        components.put(new JSONObject().put("id", "standardTools").put("class", "dumb.jaider.tools.StandardTools").put("constructorArgs", new JSONArray().put(new JSONObject().put("ref", "jaiderModel")).put(new JSONObject().put("ref", "appConfig")).put(new JSONObject().put("ref", "appEmbeddingModel"))));
        components.put(new JSONObject().put("id", "parserRegistry").put("class", "dumb.jaider.refactoring.ParserRegistry"));
        components.put(new JSONObject().put("id", "refactoringService").put("class", "dumb.jaider.refactoring.RefactoringService").put("constructorArgs", new JSONArray().put(new JSONObject().put("ref", "parserRegistry"))));
        components.put(new JSONObject().put("id", "smartRenameTool").put("class", "dumb.jaider.tools.SmartRenameTool").put("constructorArgs", new JSONArray().put(new JSONObject().put("ref", "refactoringService"))));
        components.put(new JSONObject().put("id", "listContextFilesTool").put("class", "dumb.jaider.tools.ListContextFilesTool").put("constructorArgs", new JSONArray().put(new JSONObject().put("ref", "jaiderModel"))));
        components.put(new JSONObject().put("id", "staticAnalysisService").put("class", "dumb.jaider.staticanalysis.StaticAnalysisService").put("constructorArgs", new JSONArray().put(new JSONObject().put("ref", "toolManager"))));
        components.put(new JSONObject().put("id", "analysisTools").put("class", "dumb.jaider.tools.AnalysisTools").put("constructorArgs", new JSONArray().put(new JSONObject().put("ref", "staticAnalysisService")).put(new JSONObject().put("ref", "jaiderModel"))));

        // Self-Update Services
        components.put(new JSONObject().put("id", "userInterfaceService").put("class", "dumb.jaider.ui.CommandLineUserInterfaceService")); // Assuming dumb.jaider.ui
        components.put(new JSONObject().put("id", "gitService").put("class", "dumb.jaider.service.LocalGitService"));
        components.put(new JSONObject().put("id", "buildManagerService").put("class", "dumb.jaider.service.BuildManagerService"));
        components.put(new JSONObject().put("id", "restartService").put("class", "dumb.jaider.service.BasicRestartService"));
        components.put(new JSONObject().put("id", "selfUpdateOrchestratorService").put("class", "dumb.jaider.service.SelfUpdateOrchestratorService")
            .put("constructorArgs", new JSONArray()
                .put(new JSONObject().put("ref", "jaiderModel"))
                .put(new JSONObject().put("ref", "userInterfaceService"))
                .put(new JSONObject().put("ref", "buildManagerService"))
                .put(new JSONObject().put("ref", "gitService"))
                .put(new JSONObject().put("ref", "restartService"))
                .put(new JSONObject().put("ref", "appConfig"))
                // .put(new JSONObject().put("ref", "app")) // If app is needed directly
            ));
        components.put(new JSONObject().put("id", "jaiderTools").put("class", "dumb.jaider.tools.JaiderTools")
            .put("constructorArgs", new JSONArray()
                .put(new JSONObject().put("ref", "jaiderModel"))
                .put(new JSONObject().put("ref", "selfUpdateOrchestratorService"))
            ));

        // Agents (assuming constructors take individual tool refs or a toolset ref if defined)
        JSONArray coderAgentTools = new JSONArray()
            .put(new JSONObject().put("ref", "standardTools"))
            .put(new JSONObject().put("ref", "jaiderTools"))
            .put(new JSONObject().put("ref", "smartRenameTool"))
            .put(new JSONObject().put("ref", "analysisTools"))
            .put(new JSONObject().put("ref", "listContextFilesTool"));
        components.put(new JSONObject().put("id", "coderAgent").put("class", "dumb.jaider.agents.CoderAgent")
            .put("constructorArgs", new JSONArray()
                .put(new JSONObject().put("ref", "appChatLanguageModel"))
                .put(new JSONObject().put("ref", "chatMemory"))
                .put(new JSONObject().put("list", coderAgentTools)) // Assuming CoderAgent can take a List<Object> of tools
                .put(new JSONObject().put("value", "You are a super-intelligent AI developer named Jaider. Your goal is to help users build and modify software projects by applying changes directly to their codebase. When a user asks for a change, understand the request, identify the files to modify, and then use the 'applyDiff' tool to provide the necessary changes in the diff format. Always ensure your diffs are correct and directly applicable. If you need to read a file, use 'readFile'. If you need to list files, use 'listFiles'. If a user asks you to add a file to context, use 'addFilesToContext'. If asked to remove, use 'removeFilesFromContext'. If you need to create a new file, use 'createFile'. Do not ask the user to make changes manually. You are empowered to make them directly. Be proactive and precise. For complex tasks, break them down into smaller, manageable steps, applying diffs for each. Always provide a commit message with your diffs. State: END_OF_PLAN at the end of your plan. Do not use it anywhere else. If a user asks you to do something that is not a coding task, or if it is a very complex task that you are not sure how to do, or if it is a task that is too risky, you should decline the request and explain why. You should be able to update any file, including configuration files like pom.xml or build.gradle, or JSON files. Use the 'DependencyUpdater' tool to check for and apply dependency updates to pom.xml when the user asks for it. If you need to rename files or directories, use the 'renameFileOrDirectory' tool. If you need to perform a smart rename of a symbol (class, method, variable) across multiple files, use the 'smartRename' tool. If you need to find relevant code snippets, use the 'findRelevantCode' tool. If you need to analyze code for issues, use the 'analyzeCode' tool. Always respond in Markdown format, ensuring any code blocks or diffs are correctly formatted. If you are providing a diff, ensure the diff is enclosed in a ```diff ... ``` block.").put("type", "java.lang.String")) // System message
            ));
        components.put(new JSONObject().put("id", "architectAgent").put("class", "dumb.jaider.agents.ArchitectAgent")
            .put("constructorArgs", new JSONArray()
                .put(new JSONObject().put("ref", "appChatLanguageModel"))
                .put(new JSONObject().put("ref", "chatMemory"))
                .put(new JSONObject().put("ref", "standardTools"))
            ));
        components.put(new JSONObject().put("id", "askAgent").put("class", "dumb.jaider.agents.AskAgent")
            .put("constructorArgs", new JSONArray()
                .put(new JSONObject().put("ref", "appChatLanguageModel"))
                .put(new JSONObject().put("ref", "chatMemory"))
            ));

        // Application Services (to be fetched by App.java)
        components.put(new JSONObject().put("id", "proactiveSuggestionService").put("class", "dumb.jaider.suggestion.ProactiveSuggestionService").put("constructorArgs", new JSONArray().put(new JSONObject().put("ref", "toolManager"))));
        components.put(new JSONObject().put("id", "agentService").put("class", "dumb.jaider.app.AgentService").put("constructorArgs", new JSONArray()
            .put(new JSONObject().put("ref", "appConfig"))
            .put(new JSONObject().put("ref", "jaiderModel"))
            .put(new JSONObject().put("ref", "chatMemory"))
            .put(new JSONObject().put("ref", "llmProviderFactory"))));
        components.put(new JSONObject().put("id", "sessionManager").put("class", "dumb.jaider.app.SessionManager").put("constructorArgs", new JSONArray()
            .put(new JSONObject().put("ref", "jaiderModel"))
            .put(new JSONObject().put("ref", "chatMemory"))
            .put(new JSONObject().put("ref", "ui")))); // ui is registered by App
        components.put(new JSONObject().put("id", "toolLifecycleManager").put("class", "dumb.jaider.app.ToolLifecycleManager").put("constructorArgs", new JSONArray()
            .put(new JSONObject().put("ref", "app")) // App instance
            .put(new JSONObject().put("ref", "agentService"))));
        components.put(new JSONObject().put("id", "selfUpdateService").put("class", "dumb.jaider.app.SelfUpdateService").put("constructorArgs", new JSONArray()
            .put(new JSONObject().put("ref", "appConfig"))
            .put(new JSONObject().put("ref", "jaiderModel"))
            .put(new JSONObject().put("ref", "app"))));
        components.put(new JSONObject().put("id", "agentInteractionService").put("class", "dumb.jaider.app.AgentInteractionService").put("constructorArgs", new JSONArray()
            .put(new JSONObject().put("ref", "app"))
            .put(new JSONObject().put("ref", "jaiderModel"))
            .put(new JSONObject().put("ref", "chatMemory"))
            .put(new JSONObject().put("ref", "ui"))
            .put(new JSONObject().put("ref", "agentService"))
            .put(new JSONObject().put("ref", "toolLifecycleManager"))
            .put(new JSONObject().put("ref", "sessionManager"))
            .put(new JSONObject().put("ref", "selfUpdateService"))));
        components.put(new JSONObject().put("id", "userInputHandler").put("class", "dumb.jaider.app.UserInputHandler").put("constructorArgs", new JSONArray()
            .put(new JSONObject().put("ref", "app"))
            .put(new JSONObject().put("ref", "jaiderModel"))
            .put(new JSONObject().put("ref", "appConfig"))
            .put(new JSONObject().put("ref", "ui"))
            .put(new JSONObject().put("ref", "agentService")) // UserInputHandler will get current agent from agentService via app
            .put(new JSONObject().put("ref", "proactiveSuggestionService"))
            .put(new JSONObject().put("ref", "commandsMap")) // Assuming commandsMap is registered by App
            ));

        defaultConfig.put(COMPONENTS_KEY, components);
        logger.info("Generated default component definitions. Total components defined: {}", components.length());
        return defaultConfig;
    }

    public void save(String newConfig) throws IOException {
        Path parentDir = file.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        Files.writeString(file, new JSONObject(newConfig).toString(2));
        logger.info("Configuration saved to {}", file);

        if (this.injector != null) {
            this.injector.clearCache();
        }
        load(); // Reload config fields and component definitions

        logger.info("Re-initializing DependencyInjector with new configuration after save.");
        if (this.componentDefinitions.isEmpty()) {
             logger.error("CRITICAL: No component definitions after saving and reloading. Application may be unstable.");
             throw new IllegalStateException("Component definitions became empty after save and reload.");
        }
        this.injector = new DependencyInjector(new HashMap<>(this.componentDefinitions));
        this.injector.registerSingleton("appConfig", this);
        // App instance needs to be re-registered by App.update() if the injector instance is new.
        logger.info("DependencyInjector re-initialized.");
    }

    private String getKeyValue(String envVarName, String specificJsonKey, String genericApiKeyMapKey) {
        String value = System.getenv(envVarName);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        if (this.loadedJsonConfig != null && this.loadedJsonConfig.has(specificJsonKey)) {
            value = this.loadedJsonConfig.optString(specificJsonKey, null);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        if (genericApiKeyMapKey != null) {
            value = this.apiKeys.get(genericApiKeyMapKey);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    public String getTavilyApiKey() { return getKeyValue("TAVILY_API_KEY", "tavilyApiKey", "tavily"); }
    public String getGeminiApiKey() { return getKeyValue("GEMINI_API_KEY", "geminiApiKey", "google"); }
    public String getGeminiModelName() { return this.geminiModelName; }
    public String getGeminiEmbeddingModelName() { return this.geminiEmbeddingModelName; }
    public String getGenericOpenaiApiKey() { return getKeyValue("GENERIC_OPENAI_API_KEY", "genericOpenaiApiKey", "genericOpenai"); }
    public String getGenericOpenaiEmbeddingModelName() { return this.genericOpenaiEmbeddingModelName; }
    public String getOpenaiApiKey() { return getKeyValue("OPENAI_API_KEY", "openaiApiKey", "openai"); }
    public String getOpenaiModelName() { return this.openaiModelName; }
    public String getApiKey(String providerKeyInMap) {
        String envVarName = providerKeyInMap.toUpperCase() + "_API_KEY";
        String value = System.getenv(envVarName);
        if (value != null && !value.isEmpty()) return value;
        return this.apiKeys.getOrDefault(providerKeyInMap, null);
    }

    public String readForEditing() {
        JSONObject configToEdit;
        if (Files.exists(file)) {
            try {
                configToEdit = new JSONObject(Files.readString(file));
                 logger.debug("Read existing config for editing: {}", file);
            } catch (Exception e) {
                logger.warn("Couldn't read existing config file {} for editing, starting with defaults: {}", file, e.getMessage(), e);
                configToEdit = getDefaultConfigAsJsonObject();
            }
        } else {
            logger.info("No config file found for editing at {}, starting with defaults.", file);
            configToEdit = getDefaultConfigAsJsonObject();
        }

        JSONObject baseConfig = getDefaultConfigAsJsonObject();
        for (String key : configToEdit.keySet()) {
            if (!COMPONENTS_KEY.equals(key) && !"apiKeys".equals(key)) {
                 baseConfig.put(key, configToEdit.get(key));
            }
        }
        if (configToEdit.has("apiKeys")) {
            JSONObject fileApiKeys = configToEdit.getJSONObject("apiKeys");
            JSONObject defaultApiKeys = baseConfig.getJSONObject("apiKeys");
            for (String key : fileApiKeys.keySet()) {
                defaultApiKeys.put(key, fileApiKeys.get(key));
            }
        }
        if (configToEdit.has(COMPONENTS_KEY) && configToEdit.getJSONArray(COMPONENTS_KEY).length() > 0) {
            baseConfig.put(COMPONENTS_KEY, configToEdit.getJSONArray(COMPONENTS_KEY));
            logger.debug("Using component definitions from file for editing.");
        } else {
            logger.debug("Using default component definitions for editing as file had none or was empty.");
        }

        if (baseConfig.has("testCommand") && !baseConfig.has("runCommand")) {
            baseConfig.put("runCommand", baseConfig.getString("testCommand"));
        }
        baseConfig.remove("testCommand");

        if (!baseConfig.has("runCommand")) {
            baseConfig.put("runCommand", this.runCommand);
        }
        return baseConfig.toString(2);
    }

    public <T> T getComponent(String id, Class<T> type) {
        if (this.injector == null) {
            throw new IllegalStateException("DependencyInjector is not initialized.");
        }
        Object componentInstance = this.injector.getComponent(id);
        if (componentInstance == null) {
            // ComponentNotFoundException would be thrown by injector.getComponent(id) itself.
            // This is a fallback or can be removed if injector's exception is sufficient.
            throw new ComponentNotFoundException(id, "Component instance was null after retrieval attempt.");
        }
        if (!type.isInstance(componentInstance)) {
            throw new ComponentInstantiationException(id, "Component is of type " + componentInstance.getClass().getName() +
                                         " but expected type " + type.getName() + ". Check DI configuration for component '" + id + "'.");
        }
        return type.cast(componentInstance);
    }

    public DependencyInjector getInjector() {
        if (this.injector == null) {
             throw new IllegalStateException("DependencyInjector is not initialized.");
        }
        return this.injector;
    }
}
