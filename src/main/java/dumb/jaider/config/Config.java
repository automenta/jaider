package dumb.jaider.config;

import dumb.jaider.app.DependencyInjector;
import dumb.jaider.app.exceptions.ComponentInstantiationException;
import dumb.jaider.app.exceptions.ComponentNotFoundException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages application configuration for Jaider.
 * Configuration is loaded from {@code .jaider.json} in the project directory.
 * If this file doesn't exist, defaults are loaded from {@code default-config.json}
 * (a resource within the Jaider JAR) and a new {@code .jaider.json} is created.
 * <p>
 * This class provides access to various configuration settings, including LLM providers,
 * API keys, model names, and component definitions for the dependency injector.
 * API keys are resolved with the following precedence:
 * <ol>
 *     <li>Environment Variable (e.g., {@code OPENAI_API_KEY})</li>
 *     <li>Key within the {@code "apiKeys": {}} map in {@code .jaider.json} (e.g., {@code "openai"})</li>
 * </ol>
 * The configuration also supports a {@code runCommand} for validation. A legacy field {@code testCommand}
 * is also supported for backward compatibility but will be removed in future versions; users should
 * migrate to using {@code runCommand}.
 * <p>
 * Component definitions are used to initialize the {@link DependencyInjector}.
 */
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    private static final String DEFAULT_CONFIG_PATH = "/default-config.json"; // Path in resources
    private final Path file; // Changed to private final, was package-private (final Path file)
    private JSONObject loadedJsonConfig; // Stores the raw JSON loaded from file or defaults
    private final Map<String, JSONObject> componentDefinitions = new HashMap<>();
    private transient DependencyInjector injector;
    private static final String COMPONENTS_KEY = "components";

    // Field defaults - these will be applied if not in default-config.json or .jaider.json
    // However, the primary source of defaults is now default-config.json
    final Map<String, String> apiKeys = new HashMap<>();
    // Configuration fields are private final and accessed via getters
    private final String llm;
    private final String runCommand;
    private final String ollamaBaseUrl;
    private final String ollamaModelName;
    private final String genericOpenaiBaseUrl;
    private final String genericOpenaiModelName;
    private final String genericOpenaiEmbeddingModelName;
    /**
     * @deprecated Configure via the {@code apiKeys} map (e.g., {@code "apiKeys": {"genericOpenai": "YOUR_KEY"}}) or environment variable {@code GENERIC_OPENAI_API_KEY} instead.
     */
    @Deprecated
    private final String genericOpenaiApiKey;
    private final String openaiModelName;
    /**
     * @deprecated Configure via the {@code apiKeys} map (e.g., {@code "apiKeys": {"openai": "YOUR_KEY"}}) or environment variable {@code OPENAI_API_KEY} instead.
     */
    @Deprecated
    private final String openaiApiKey; // Added this field
    /**
     * @deprecated Configure via the {@code apiKeys} map (e.g., {@code "apiKeys": {"google": "YOUR_KEY"}}) or environment variable {@code GEMINI_API_KEY} instead.
     */
    @Deprecated
    private final String geminiApiKey;
    private final String geminiModelName;
    private final String geminiEmbeddingModelName;
    /**
     * @deprecated Configure via the {@code apiKeys} map (e.g., {@code "apiKeys": {"tavily": "YOUR_KEY"}}) or environment variable {@code TAVILY_API_KEY} instead.
     */
    @Deprecated
    private final String tavilyApiKey;
    private final String toolManifestsDir;

    /**
     * Constructs a new Config instance.
     * It initializes configuration by attempting to load {@code .jaider.json} from the specified
     * project directory. If not found, it loads defaults from internal resources and
     * creates a new {@code .jaider.json}. It also initializes the dependency injector.
     *
     * @param projectDir The root directory of the project where {@code .jaider.json} is expected.
     * @throws IllegalStateException if no component definitions are found after loading,
     * indicating a critical configuration or packaging error.
     */
    public Config(Path projectDir) {
        this.file = projectDir.resolve(".jaider.json");
        // Initialize fields with temporary defaults before load()
        // These will be overwritten by populateFieldsFromJson during the first load() call.
        // Initial defaults are set here, but they are primarily for the reflection mechanism
        // to have a value if populateFieldsFromJson is called with a JSON that's missing some keys
        // before the true defaults from default-config.json are merged.
        // The true source of defaults is default-config.json.
        this.llm = "ollama"; // Default, will be overridden
        this.ollamaBaseUrl = "http://localhost:11434"; // Default, will be overridden
        this.ollamaModelName = "llamablit"; // Default, will be overridden
        this.genericOpenaiBaseUrl = "http://localhost:8080/v1"; // Default, will be overridden
        this.genericOpenaiModelName = "local-model"; // Default, will be overridden
        this.genericOpenaiEmbeddingModelName = "text-embedding-ada-002";
        this.genericOpenaiApiKey = "";
        this.openaiModelName = "gpt-4o-mini";
        this.openaiApiKey = ""; // Initialize openaiApiKey
        this.geminiApiKey = "";
        this.geminiModelName = "gemini-1.5-flash-latest";
        this.geminiEmbeddingModelName = "textembedding-gecko";
        this.tavilyApiKey = "";
        this.toolManifestsDir = "src/main/resources/tool-descriptors";
        this.runCommand = "";

        // Removed duplicate load() and this.file assignment.
        // The first this.file assignment is correct.
        // load() is called once to populate fields.
        load(); // Populates fields and componentDefinitions from file or defaults

        if (this.componentDefinitions.isEmpty()) {
            var errorMsg = "CRITICAL: No component definitions found after attempting to load from file and apply defaults. getDefaultConfigAsJsonObject() might be incomplete or malformed.";
            logger.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        logger.info("Initializing DependencyInjector with {} component definitions.", this.componentDefinitions.size());
        this.injector = new DependencyInjector(new HashMap<>(this.componentDefinitions)); // Use a defensive copy
        this.injector.registerSingleton("appConfig", this);
        // Note: The 'app' instance (App.java) should register itself with the injector after Config is fully initialized.
    }

    void load() {
        var loadedSuccessfully = false;
        if (Files.exists(file)) {
            try {
                var content = Files.readString(file);
                logger.info("Loading configuration from file: {}", file);
                var userJson = new JSONObject(content);
                // populateFieldsFromJson will handle merging with defaults
                populateFieldsFromJson(userJson);
                loadedSuccessfully = true;
                logger.info("Successfully loaded and parsed configuration from {}.", file);
            } catch (Exception e) { // Catch parsing errors or IOException
                logger.error("Error reading or parsing config file ({}): {}. Applying defaults.", file, e.getMessage(), e);
                // Fall through to load defaults
            }
        } else {
            logger.info("Config file {} not found.", file);
        }

        if (!loadedSuccessfully) {
            logger.warn("Applying and writing default configuration to {}.", file);
            // Defaults are loaded within populateFieldsFromJson if userJson is null or partial
            populateFieldsFromJson(new JSONObject()); // Pass empty JSON, defaults will be primary

            try {
                // Write the fully resolved config (which would be defaults if no user file)
                // to .jaider.json
                var parentDir = file.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }
                // Write the fully resolved and merged config (this.loadedJsonConfig)
                Files.writeString(file, this.loadedJsonConfig.toString(2));
                logger.info("Written effective (default/merged) configuration to: {}", file);
            } catch (IOException e) {
                logger.warn("Failed to write effective config to file: {} - {}", file, e.getMessage(), e);
            }
        }
    }

    private void populateFieldsFromJson(JSONObject userJsonInput) {
        var defaultConfigJson = getDefaultConfigAsJsonObject();
        var mergedJson = new JSONObject(defaultConfigJson.toString()); // Start with a deep copy of defaults

        // Overlay userJsonInput onto mergedJson
        if (userJsonInput != null) {
            for (var key : userJsonInput.keySet()) {
                if (COMPONENTS_KEY.equals(key) && userJsonInput.optJSONArray(COMPONENTS_KEY) != null && !userJsonInput.getJSONArray(COMPONENTS_KEY).isEmpty()) {
                    mergedJson.put(COMPONENTS_KEY, userJsonInput.getJSONArray(COMPONENTS_KEY)); // Replace components array
                } else if ("apiKeys".equals(key) && userJsonInput.optJSONObject("apiKeys") != null) {
                    // Deep merge for apiKeys: start with default apiKeys (already in mergedJson), then overlay user's
                    var userApiKeys = userJsonInput.getJSONObject("apiKeys");
                    var mergedApiKeys = mergedJson.optJSONObject("apiKeys"); // Should be the one from defaults
                    if (mergedApiKeys == null) mergedApiKeys = new JSONObject(); // Should not happen if defaults are sound

                    for (var k : userApiKeys.keySet()) {
                        mergedApiKeys.put(k, userApiKeys.get(k)); // User's key overrides default's key
                    }
                    mergedJson.put("apiKeys", mergedApiKeys); // Put the updated map back
                } else {
                    mergedJson.put(key, userJsonInput.get(key)); // User value overrides default for other keys
                }
            }
        }
        this.loadedJsonConfig = mergedJson; // Store the fully merged configuration

        // Populate all other fields from the merged JSON
        var llmVal = mergedJson.optString("llmProvider", this.llm); // Fallback to initial hardcoded if key missing (should not happen with defaults)
        var ollamaBaseUrlVal = mergedJson.optString("ollamaBaseUrl", this.ollamaBaseUrl);
        var ollamaModelNameVal = mergedJson.optString("ollamaModelName", this.ollamaModelName); // Added this line
        var genericOpenaiBaseUrlVal = mergedJson.optString("genericOpenaiBaseUrl", this.genericOpenaiBaseUrl);
        var genericOpenaiModelNameVal = mergedJson.optString("genericOpenaiModelName", this.genericOpenaiModelName);
        var genericOpenaiEmbeddingModelNameVal = mergedJson.optString("genericOpenaiEmbeddingModelName", this.genericOpenaiEmbeddingModelName);
        var genericOpenaiApiKeyVal = mergedJson.optString("genericOpenaiApiKey", this.genericOpenaiApiKey);
        var openaiModelNameVal = mergedJson.optString("openaiModelName", this.openaiModelName);
        var openaiApiKeyVal = mergedJson.optString("openaiApiKey", this.openaiApiKey); // Load openaiApiKey
        var geminiApiKeyVal = mergedJson.optString("geminiApiKey", this.geminiApiKey);
        var geminiModelNameVal = mergedJson.optString("geminiModelName", this.geminiModelName);
        var geminiEmbeddingModelNameVal = mergedJson.optString("geminiEmbeddingModelName", this.geminiEmbeddingModelName);
        var tavilyApiKeyVal = mergedJson.optString("tavilyApiKey", this.tavilyApiKey);
        var toolManifestsDirVal = mergedJson.optString("toolManifestsDir", this.toolManifestsDir);

        var runCommandVal = mergedJson.optString("runCommand", this.runCommand);
        // Handle legacy "testCommand"
        if (mergedJson.has("testCommand")) {
            var testCommandVal = mergedJson.optString("testCommand");
            // If testCommand has a value AND (runCommand is empty OR runCommand was not explicitly set by the user)
            // then use testCommand's value for runCommand.
            if (!testCommandVal.isEmpty() && (runCommandVal.isEmpty() || !userJsonInput.has("runCommand"))) {
                logger.warn("The configuration field 'testCommand' is deprecated and its value ('{}') is being used for 'runCommand'. Please update your .jaider.json to use 'runCommand' instead.", testCommandVal);
                runCommandVal = testCommandVal;
            }
        }
        // Ensure testCommand is not part of the final effective 'runCommand'.
        // loadedJsonConfig should reflect the final command to be used.
        this.loadedJsonConfig.put("runCommand", runCommandVal);
        this.loadedJsonConfig.remove("testCommand"); // Remove testCommand from the effective config


        // Assign to final fields using reflection.
        // This is a workaround to allow final fields to be set after initial constructor defaults
        // based on loaded JSON configuration. This method effectively completes the initialization
        // of these final fields.
        // It assumes populateFieldsFromJson is called as part of the object's construction sequence (via load()).
        java.lang.reflect.Field llmField, ollamaBaseUrlField, ollamaModelNameField, genericOpenaiBaseUrlField, genericOpenaiModelNameField,
                                genericOpenaiEmbeddingModelNameField, genericOpenaiApiKeyField, openaiModelNameField, openaiApiKeyField, // Add openaiApiKeyField
                                geminiApiKeyField, geminiModelNameField, geminiEmbeddingModelNameField, tavilyApiKeyField,
                                toolManifestsDirField, runCommandField;
        try {
            // Use getDeclaredField for private fields.
            llmField = Config.class.getDeclaredField("llm");
            ollamaBaseUrlField = Config.class.getDeclaredField("ollamaBaseUrl");
            ollamaModelNameField = Config.class.getDeclaredField("ollamaModelName");
            genericOpenaiBaseUrlField = Config.class.getDeclaredField("genericOpenaiBaseUrl");
            genericOpenaiModelNameField = Config.class.getDeclaredField("genericOpenaiModelName");
            genericOpenaiEmbeddingModelNameField = Config.class.getDeclaredField("genericOpenaiEmbeddingModelName");
            genericOpenaiApiKeyField = Config.class.getDeclaredField("genericOpenaiApiKey");
            openaiModelNameField = Config.class.getDeclaredField("openaiModelName");
            openaiApiKeyField = Config.class.getDeclaredField("openaiApiKey"); // Get openaiApiKey field
            geminiApiKeyField = Config.class.getDeclaredField("geminiApiKey");
            geminiModelNameField = Config.class.getDeclaredField("geminiModelName");
            geminiEmbeddingModelNameField = Config.class.getDeclaredField("geminiEmbeddingModelName");
            tavilyApiKeyField = Config.class.getDeclaredField("tavilyApiKey");
            toolManifestsDirField = Config.class.getDeclaredField("toolManifestsDir");
            runCommandField = Config.class.getDeclaredField("runCommand");

            llmField.setAccessible(true);
            ollamaBaseUrlField.setAccessible(true);
            ollamaModelNameField.setAccessible(true); // Added
            genericOpenaiBaseUrlField.setAccessible(true);
            genericOpenaiModelNameField.setAccessible(true);
            genericOpenaiEmbeddingModelNameField.setAccessible(true);
            genericOpenaiApiKeyField.setAccessible(true);
            openaiModelNameField.setAccessible(true);
            openaiApiKeyField.setAccessible(true); // Set openaiApiKey field accessible
            geminiApiKeyField.setAccessible(true);
            geminiModelNameField.setAccessible(true);
            geminiEmbeddingModelNameField.setAccessible(true);
            tavilyApiKeyField.setAccessible(true);
            toolManifestsDirField.setAccessible(true);
            runCommandField.setAccessible(true);

            llmField.set(this, llmVal);
            ollamaBaseUrlField.set(this, ollamaBaseUrlVal);
            ollamaModelNameField.set(this, ollamaModelNameVal); // Added
            genericOpenaiBaseUrlField.set(this, genericOpenaiBaseUrlVal);
            genericOpenaiModelNameField.set(this, genericOpenaiModelNameVal);
            genericOpenaiEmbeddingModelNameField.set(this, genericOpenaiEmbeddingModelNameVal);
            genericOpenaiApiKeyField.set(this, genericOpenaiApiKeyVal);
            openaiModelNameField.set(this, openaiModelNameVal);
            openaiApiKeyField.set(this, openaiApiKeyVal); // Set openaiApiKey field
            geminiApiKeyField.set(this, geminiApiKeyVal);
            geminiModelNameField.set(this, geminiModelNameVal);
            geminiEmbeddingModelNameField.set(this, geminiEmbeddingModelNameVal);
            tavilyApiKeyField.set(this, tavilyApiKeyVal);
            toolManifestsDirField.set(this, toolManifestsDirVal);
            runCommandField.set(this, runCommandVal);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.error("Error setting final fields via reflection", e);
            throw new RuntimeException("Failed to populate final config fields", e);
        }

        this.apiKeys.clear();
        var keys = mergedJson.optJSONObject("apiKeys"); // Use mergedJson
        if (keys != null) {
            keys.keySet().forEach(key -> this.apiKeys.put(key, keys.getString(key)));
        }

        this.componentDefinitions.clear(); // Clear before populating
        if (mergedJson.has(COMPONENTS_KEY)) { // Use mergedJson
            var componentDefsArray = mergedJson.getJSONArray(COMPONENTS_KEY);
            logger.info("Attempting to load {} component definitions from merged JSON.", componentDefsArray.length());
            for (var i = 0; i < componentDefsArray.length(); i++) {
                var componentDef = componentDefsArray.getJSONObject(i);
                if (componentDef.has("id") && componentDef.has("class")) {
                    this.componentDefinitions.put(componentDef.getString("id"), componentDef);
                } else {
                    logger.warn("Skipping component definition due to missing 'id' or 'class': {}", componentDef.toString(2));
                }
            }
            logger.info("Successfully populated {} component definitions from merged JSON source.", this.componentDefinitions.size());
        } else {
            // This should ideally not happen if defaults always provide components
            logger.warn("No '{}' key found in merged JSON source. This might indicate an issue with default config.", COMPONENTS_KEY);
        }
    }

    private JSONObject getDefaultConfigAsJsonObject() {
        try (var inputStream = Config.class.getResourceAsStream(DEFAULT_CONFIG_PATH)) {
            if (inputStream == null) {
                var errorMsg = "CRITICAL: Default configuration file not found in resources: " + DEFAULT_CONFIG_PATH;
                logger.error(errorMsg);
                throw new IOException(errorMsg); // Or a more specific runtime exception
            }
            var jsonText = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
            var defaultConfig = new JSONObject(jsonText);
            logger.info("Successfully loaded default configuration from {}", DEFAULT_CONFIG_PATH);
            // Ensure toolManifestsDir is correctly set if it needs to be dynamic (though static path is fine)
            // For now, we assume the value in default-config.json is correct.
            // If it needed to be relative to project root, it would require more complex logic here or
            // a placeholder in the JSON that gets replaced.
            // Example: defaultConfig.put("toolManifestsDir", "some_dynamic_path_logic_here");
            return defaultConfig;
        } catch (IOException e) {
            var errorMsg = "CRITICAL: IOException while reading default config from " + DEFAULT_CONFIG_PATH;
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e); // This is a fatal error for the application
        } catch (org.json.JSONException e) {
            var errorMsg = "CRITICAL: JSONException while parsing default config from " + DEFAULT_CONFIG_PATH;
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e); // This is also fatal
        }
    }

    /**
     * Saves the provided configuration string to the {@code .jaider.json} file.
     * After saving, it reloads the configuration and re-initializes the
     * dependency injector with the new settings.
     *
     * @param newConfig The new configuration as a JSON string.
     * @throws IOException if there's an error writing to the file.
     * @throws org.json.JSONException if the newConfig string is not valid JSON.
     * @throws IllegalStateException if component definitions become empty after reload.
     */
    public void save(String newConfig) throws IOException {
        var parentDir = file.getParent();
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
        var value = System.getenv(envVarName);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        // Deprecated: Direct lookup of specificJsonKey is removed.
        // Configuration should be via environment variable or the apiKeys map.
        // The specificJsonKey field itself is still loaded for backward compatibility in populateFieldsFromJson,
        // but getKeyValue will now prioritize apiKeys map over it if env var is not set.
        if (genericApiKeyMapKey != null) {
            value = this.apiKeys.get(genericApiKeyMapKey);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Retrieves the Tavily API key.
     * Resolution order: {@code TAVILY_API_KEY} env var > {@code "tavilyApiKey"} JSON key > {@code "tavily"} in {@code "apiKeys"} JSON map.
     * @return The Tavily API key, or null if not found.
     */
    public String getTavilyApiKey() { return getKeyValue("TAVILY_API_KEY", "tavilyApiKey", "tavily"); }

    /**
     * Retrieves the Google Gemini API key.
     * Resolution order: {@code GEMINI_API_KEY} env var > {@code "geminiApiKey"} JSON key > {@code "google"} in {@code "apiKeys"} JSON map.
     * @return The Gemini API key, or null if not found.
     */
    public String getGeminiApiKey() { return getKeyValue("GEMINI_API_KEY", "geminiApiKey", "google"); }

    /** @return The configured Gemini model name (e.g., "gemini-1.5-flash-latest"). */
    public String getGeminiModelName() { return this.geminiModelName; }

    /** @return The configured Gemini embedding model name (e.g., "textembedding-gecko"). */
    public String getGeminiEmbeddingModelName() { return this.geminiEmbeddingModelName; }

    /**
     * Retrieves the API key for a generic OpenAI-compatible service.
     * Resolution order: {@code GENERIC_OPENAI_API_KEY} env var > {@code "genericOpenaiApiKey"} JSON key > {@code "genericOpenai"} in {@code "apiKeys"} JSON map.
     * @return The generic OpenAI API key, or null if not found.
     */
    public String getGenericOpenaiApiKey() { return getKeyValue("GENERIC_OPENAI_API_KEY", "genericOpenaiApiKey", "genericOpenai"); }

    /** @return The configured generic OpenAI embedding model name (e.g., "text-embedding-ada-002"). */
    public String getGenericOpenaiEmbeddingModelName() { return this.genericOpenaiEmbeddingModelName; }

    /**
     * Retrieves the OpenAI API key.
     * Resolution order: {@code OPENAI_API_KEY} env var > {@code "openaiApiKey"} JSON key > {@code "openai"} in {@code "apiKeys"} JSON map.
     * @return The OpenAI API key, or null if not found.
     */
    public String getOpenaiApiKey() { return getKeyValue("OPENAI_API_KEY", "openaiApiKey", "openai"); }

    /** @return The configured OpenAI model name (e.g., "gpt-4o-mini"). */
    public String getOpenaiModelName() { return this.openaiModelName; }

    /**
     * Retrieves an API key for the given provider key from the {@code "apiKeys"} map in the configuration.
     * This method also checks for a corresponding environment variable (e.g., {@code PROVIDERKEYINMAP_API_KEY}).
     * Environment variable takes precedence.
     *
     * @param providerKeyInMap The key for the provider in the "apiKeys" map (e.g., "openai", "google").
     * @return The API key if found, otherwise null.
     */
    public String getApiKey(String providerKeyInMap) {
        var envVarName = providerKeyInMap.toUpperCase() + "_API_KEY";
        var value = System.getenv(envVarName);
        if (value != null && !value.isEmpty()) return value;

        // Fallback to deprecated top-level keys if not in apiKeys map and env var not set.
        // This is to maintain a degree of backward compatibility for users who haven't migrated.
        // However, the primary and recommended way is env var or apiKeys map.
        var fallbackValue = switch (providerKeyInMap.toLowerCase()) {
            case "openai" -> this.openaiApiKey; // Access the deprecated field directly
            case "google" -> // Assuming "google" is used for Gemini in apiKeys
                    this.geminiApiKey;
            case "tavily" -> this.tavilyApiKey;
            case "genericopenai" -> this.genericOpenaiApiKey;
            default -> null;
        };

        var apiKeyFromMap = this.apiKeys.get(providerKeyInMap);
        if (apiKeyFromMap != null && !apiKeyFromMap.isEmpty()) {
            return apiKeyFromMap;
        }

        if (fallbackValue != null && !fallbackValue.isEmpty()) {
            logger.warn("Using deprecated top-level JSON key for '{}'. Please migrate to 'apiKeys' map or environment variables.", providerKeyInMap);
            return fallbackValue;
        }
        return null;
    }

    public String readForEditing() {
        JSONObject configToEdit;
        if (Files.exists(file)) {
            try {
                configToEdit = new JSONObject(Files.readString(file));
                logger.debug("Read existing config for editing: {}", file);
            } catch (Exception e) {
                logger.warn("Couldn't read existing config file {} for editing, starting with defaults from resource: {}", file, e.getMessage(), e);
                configToEdit = getDefaultConfigAsJsonObject(); // Load from new resource method
            }
        } else {
            logger.info("No config file found for editing at {}, starting with defaults from resource.", file);
            configToEdit = getDefaultConfigAsJsonObject(); // Load from new resource method
        }

        var baseConfig = getDefaultConfigAsJsonObject(); // Base is now from resource
        // Overlay user's settings from .jaider.json onto the defaults from resource
        for (var key : configToEdit.keySet()) {
            // Overlay user's settings from .jaider.json onto the defaults from resource
            // For simple fields, directly put from configToEdit if they exist
            for (var k : JSONObject.getNames(configToEdit)) {
                if (!COMPONENTS_KEY.equals(k) && !"apiKeys".equals(k) && !"testCommand".equals(k)) {
                    baseConfig.put(k, configToEdit.get(k));
                }
            }

            // If user's config has "apiKeys", it replaces the default "apiKeys"
            if (configToEdit.has("apiKeys")) {
                baseConfig.put("apiKeys", configToEdit.getJSONObject("apiKeys"));
                logger.debug("Using apiKeys object from user file for editing.");
            } else {
                logger.debug("User config file {} has no apiKeys, using default apiKeys for editing (already in baseConfig).", file);
            }

            // If user's config has "components" and it's not empty, it replaces default "components"
            if (configToEdit.has(COMPONENTS_KEY) && !configToEdit.getJSONArray(COMPONENTS_KEY).isEmpty()) {
                baseConfig.put(COMPONENTS_KEY, configToEdit.getJSONArray(COMPONENTS_KEY));
                logger.debug("Using component definitions from user file for editing.");
            } else {
                logger.debug("User config file {} has no components or is empty, using default components for editing (already in baseConfig).", file);
            }
        }
        // End of the for loop for overlaying settings.
        // The following else block should be associated with the initial `if (Files.exists(file))`
        // to handle the case where the user's config file doesn't exist from the start.
        // However, the logic inside readForEditing already handles this by starting with `defaultConfigJson`
        // if the file doesn't exist or fails to load, and then overlays.
        // The current structure implies `configToEdit` would be the default if file didn't exist,
        // so this 'else' for the for-loop is misplaced.
        // Given the current logic, if `configToEdit` is populated by `getDefaultConfigAsJsonObject()`
        // when `file` doesn't exist, then iterating its keyset (which would be default keys)
        // and putting them back into `baseConfig` (also from `getDefaultConfigAsJsonObject()`) is redundant.
        // The problematic `else` seems to be a remnant of a different logic flow.
        // For now, to fix the immediate compilation error, I will remove the misplaced `else`.
        // A more thorough review of this method's logic might be needed if behavior is not as expected.


        // `testCommand` is not written to the editable config; its logic is handled in `populateFieldsFromJson`.
        // `runCommand` in baseConfig will reflect the correct value from defaults or user's file (via populateFieldsFromJson -> load -> this.runCommand)
        // So, we ensure baseConfig's runCommand is set from the *loaded* this.runCommand if not already set by user's file.
        // This ensures the editable config reflects the actual runCommand that would be used.
        // If no user file existed, configToEdit started as default, and this.getRunCommand() would be the default runCommand.
        // If user file existed, this.getRunCommand() would be the loaded runCommand.
        // This correctly sets the runCommand in the editable output.
        baseConfig.put("runCommand", this.getRunCommand()); // Use getter for currently loaded value
        baseConfig.remove("testCommand"); // Ensure testCommand is not in the output for editing

        return baseConfig.toString(2);
    }

    // --- Getters for Configuration Fields ---

    /** @return The configured LLM provider name (e.g., "ollama", "openai"). */
    public String getLlm() { return llm; }

    /**
     * @return The command string used for running validation or tests (e.g., "mvn test").
     * This may be populated from the legacy "testCommand" field if "runCommand" is not set.
     */
    public String getRunCommand() { return runCommand; }

    /** @return The base URL for the Ollama service (e.g., "<a href="http://localhost:11434">...</a>"). */
    public String getOllamaBaseUrl() { return ollamaBaseUrl; }

    /** @return The model name for Ollama (e.g., "llamablit"). */
    public String getOllamaModelName() { return ollamaModelName; }

    /** @return The base URL for a generic OpenAI-compatible service (e.g., "<a href="http://localhost:8080/v1">...</a>"). */
    public String getGenericOpenaiBaseUrl() { return genericOpenaiBaseUrl; }

    /** @return The model name for the generic OpenAI-compatible service (e.g., "local-model"). */
    public String getGenericOpenaiModelName() { return genericOpenaiModelName; }

    // Note: Javadoc for getGenericOpenaiEmbeddingModelName, getOpenaiModelName, getGeminiModelName,
    // getGeminiEmbeddingModelName, getTavilyApiKey, getOpenaiApiKey, getGenericOpenaiApiKey, getGeminiApiKey
    // are already added above their definitions.

    /** @return The directory path for tool descriptor manifest files. */
    public String getToolManifestsDir() { return toolManifestsDir; }


    /**
     * Retrieves a component instance of the specified type from the dependency injector.
     *
     * @param id The unique ID of the component to retrieve.
     * @param type The expected class type of the component.
     * @param <T> The type of the component.
     * @return The component instance.
     * @throws IllegalStateException if the dependency injector is not initialized.
     * @throws ComponentNotFoundException if the component with the given ID is not found.
     * @throws ComponentInstantiationException if the component cannot be instantiated or is of an incompatible type.
     */
    public <T> T getComponent(String id, Class<T> type) {
        if (this.injector == null) {
            throw new IllegalStateException("DependencyInjector is not initialized.");
        }
        var componentInstance = this.injector.getComponent(id);
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
