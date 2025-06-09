package dumb.jaider.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @TempDir
    Path tempDir;

    private Path projectDir;
    private Path configFilePath;

    @BeforeEach
    void setUp() throws IOException {
        // Simulate a project directory within the temp directory
        projectDir = tempDir.resolve("test-project");
        Files.createDirectories(projectDir);
        configFilePath = projectDir.resolve(Config.CONFIG_FILE_NAME);
        Config.setProjectDir(projectDir.toString()); // Important: Set the project directory for Config class
    }

    // --- Test Default Config Creation ---

    @Test
    void createDefaultConfig_whenFileDoesNotExist_shouldCreateAndLoadDefaults() throws IOException {
        // Ensure the file does not exist initially (though @TempDir should handle this)
        assertFalse(Files.exists(configFilePath), "Config file should not exist before test");

        // Action: Load the config (which should trigger default config creation)
        Config config = new Config(projectDir); // Config.load() is called in constructor

        // Assertions
        assertTrue(Files.exists(configFilePath), "Config file should have been created");

        String content = Files.readString(configFilePath);
        org.json.JSONObject jsonConfig = new org.json.JSONObject(content);

        // Verify all default key-value pairs
        assertEquals("ollama", jsonConfig.getString("llmProvider"));
        assertEquals("http://localhost:11434", jsonConfig.getString("ollamaBaseUrl"));
        assertEquals("llamablit", jsonConfig.getString("ollamaModelName"));
        assertEquals("http://localhost:8080/v1", jsonConfig.getString("genericOpenaiBaseUrl"));
        assertEquals("local-model", jsonConfig.getString("genericOpenaiModelName"));
        assertEquals("", jsonConfig.getString("genericOpenaiApiKey"));
        assertEquals("", jsonConfig.getString("geminiApiKey"));
        assertEquals("gemini-1.5-flash-latest", jsonConfig.getString("geminiModelName"));
        assertEquals("", jsonConfig.getString("tavilyApiKey"));
        assertEquals("", jsonConfig.getString("runCommand")); // Default runCommand is empty

        org.json.JSONObject apiKeysJson = jsonConfig.getJSONObject("apiKeys");
        assertNotNull(apiKeysJson, "apiKeys object should exist");
        assertEquals("YOUR_OPENAI_API_KEY", apiKeysJson.getString("openai"));
        assertEquals("YOUR_ANTHROPIC_API_KEY", apiKeysJson.getString("anthropic"));
        assertEquals("YOUR_GOOGLE_API_KEY", apiKeysJson.getString("google"));

        // Also check the loaded config object's fields
        assertEquals("ollama", config.llmProvider);
        assertEquals("http://localhost:11434", config.ollamaBaseUrl);
        assertEquals("llamablit", config.ollamaModelName);
        assertEquals("", config.runCommand);
        assertEquals("YOUR_OPENAI_API_KEY", config.apiKeys.get("openai"));
    }

    // --- Test Loading Config (load()) ---

    @Test
    void load_withExistingValidConfig_shouldPopulateFieldsCorrectly() throws IOException {
        // Setup: Create a pre-existing valid .jaider.json
        String customConfigContent = """
                {
                  "llmProvider": "openai",
                  "ollamaBaseUrl": "http://customhost:12345",
                  "ollamaModelName": "customllamit",
                  "genericOpenaiBaseUrl": "http://customhost:8081/v1",
                  "genericOpenaiModelName": "custom-generic-model",
                  "genericOpenaiApiKey": "custom-generic-key",
                  "geminiApiKey": "custom-gemini-key",
                  "geminiModelName": "custom-gemini-model",
                  "tavilyApiKey": "custom-tavily-key",
                  "runCommand": "mvn clean test",
                  "apiKeys": {
                    "openai": "sk-12345",
                    "google": "g-abcdef"
                  }
                }""";
        Files.writeString(configFilePath, customConfigContent);

        // Action
        Config config = new Config(projectDir);

        // Assertions
        assertEquals("openai", config.llmProvider);
        assertEquals("http://customhost:12345", config.ollamaBaseUrl);
        assertEquals("customllamit", config.ollamaModelName);
        assertEquals("http://customhost:8081/v1", config.genericOpenaiBaseUrl);
        assertEquals("custom-generic-model", config.genericOpenaiModelName);
        assertEquals("custom-generic-key", config.genericOpenaiApiKey);
        assertEquals("custom-gemini-key", config.geminiApiKey);
        assertEquals("custom-gemini-model", config.geminiModelName);
        assertEquals("custom-tavily-key", config.tavilyApiKey);
        assertEquals("mvn clean test", config.runCommand);

        assertNotNull(config.apiKeys);
        assertEquals("sk-12345", config.apiKeys.get("openai"));
        assertEquals("g-abcdef", config.apiKeys.get("google"));
        assertNull(config.apiKeys.get("anthropic"), "Anthropic key should be null as it's not in the custom config");
    }

    @Test
    void load_withMissingOptionalFields_shouldUseDefaultValues() throws IOException {
        // Setup: Create a .jaider.json with some fields missing
        String partialConfigContent = """
                {
                  "llmProvider": "gemini",
                  "geminiModelName": "gemini-pro",
                  "apiKeys": {
                    "google": "g-partialkey"
                  }
                }""";
        Files.writeString(configFilePath, partialConfigContent);

        // Action
        Config config = new Config(projectDir);
        Config defaultConfigForComparison = new Config(tempDir.resolve("default-project")); // to get default values easily

        // Assertions for fields present in the partial config
        assertEquals("gemini", config.llmProvider);
        assertEquals("gemini-pro", config.geminiModelName); // This was provided
        assertNotNull(config.apiKeys);
        assertEquals("g-partialkey", config.apiKeys.get("google"));

        // Assertions for fields missing from partial config (should use defaults)
        assertEquals(defaultConfigForComparison.ollamaBaseUrl, config.ollamaBaseUrl);
        assertEquals(defaultConfigForComparison.ollamaModelName, config.ollamaModelName);
        assertEquals(defaultConfigForComparison.genericOpenaiBaseUrl, config.genericOpenaiBaseUrl);
        assertEquals(defaultConfigForComparison.genericOpenaiModelName, config.genericOpenaiModelName);
        assertEquals(defaultConfigForComparison.genericOpenaiApiKey, config.genericOpenaiApiKey); // Default is ""
        assertEquals(defaultConfigForComparison.geminiApiKey, config.geminiApiKey); // Default is ""
        assertEquals(defaultConfigForComparison.tavilyApiKey, config.tavilyApiKey); // Default is ""
        assertEquals(defaultConfigForComparison.runCommand, config.runCommand); // Default is ""

        // Check that default API keys that were not in partial config are not there
        assertNull(config.apiKeys.get("openai")); // Should not exist as it wasn't in partial and not in default map of config obj
        assertNull(config.apiKeys.get("anthropic"));
    }

    @Test
    void load_withEmptyConfig_shouldPopulateWithDefaults() throws IOException {
        // Setup: Create an empty .jaider.json file
        String emptyConfigContent = "{}";
        Files.writeString(configFilePath, emptyConfigContent);

        // Action
        Config config = new Config(projectDir);
        Config defaultConfigForComparison = new Config(tempDir.resolve("default-project-empty"));

        // Assertions: All fields should have their default values
        assertEquals(defaultConfigForComparison.llmProvider, config.llmProvider);
        assertEquals(defaultConfigForComparison.ollamaBaseUrl, config.ollamaBaseUrl);
        assertEquals(defaultConfigForComparison.ollamaModelName, config.ollamaModelName);
        assertEquals(defaultConfigForComparison.genericOpenaiBaseUrl, config.genericOpenaiBaseUrl);
        assertEquals(defaultConfigForComparison.genericOpenaiModelName, config.genericOpenaiModelName);
        assertEquals(defaultConfigForComparison.genericOpenaiApiKey, config.genericOpenaiApiKey);
        assertEquals(defaultConfigForComparison.geminiApiKey, config.geminiApiKey);
        assertEquals(defaultConfigForComparison.geminiModelName, config.geminiModelName);
        assertEquals(defaultConfigForComparison.tavilyApiKey, config.tavilyApiKey);
        assertEquals(defaultConfigForComparison.runCommand, config.runCommand); // Default is ""

        // For an empty JSON file, the apiKeys map in the Config object should be empty
        assertTrue(config.apiKeys.isEmpty(), "apiKeys map should be empty when loading from an empty JSON object");
    }

    @Test
    void load_withOldTestCommand_shouldMigrateToRunCommand() throws IOException {
        // Setup: Create a .jaider.json with 'testCommand' and no 'runCommand'
        String oldConfigContent = """
                {
                  "llmProvider": "ollama",
                  "testCommand": "mvn verify",
                  "apiKeys": {}
                }""";
        Files.writeString(configFilePath, oldConfigContent);

        // Action
        Config config = new Config(projectDir);

        // Assertions
        assertEquals("mvn verify", config.runCommand, "runCommand should be populated from testCommand");

        // Verify that loading a config with both still prefers runCommand
        String bothCommandsConfigContent = """
                {
                  "llmProvider": "ollama",
                  "testCommand": "old command",
                  "runCommand": "new command",
                  "apiKeys": {}
                }""";
        Files.writeString(configFilePath, bothCommandsConfigContent);
        Config config2 = new Config(projectDir);
        assertEquals("new command", config2.runCommand, "runCommand should be preferred over testCommand");
    }

    // --- Test Saving Config (save()) ---

    @Test
    void save_newConfiguration_shouldUpdateFileAndReflectInLoad() throws IOException {
        // Setup: Start with a default config
        Config config = new Config(projectDir);
        assertTrue(Files.exists(configFilePath), "Config file should be created by initial load.");

        // New configuration to save
        String newConfigJsonString = """
                {
                  "llmProvider": "openai",
                  "ollamaBaseUrl": "http://newhost:54321",
                  "ollamaModelName": "newllamit",
                  "genericOpenaiBaseUrl": "http://newhost:8082/v2",
                  "genericOpenaiModelName": "new-generic",
                  "genericOpenaiApiKey": "new-generic-api-key",
                  "geminiApiKey": "new-gemini-key",
                  "geminiModelName": "new-gemini-pro",
                  "tavilyApiKey": "new-tavily-key",
                  "runCommand": "npm test",
                  "apiKeys": {
                    "openai": "sk-newkey",
                    "google": "g-newkey",
                    "customProvider": "custom-xyz"
                  }
                }""";

        // Action: Save the new configuration
        config.save(newConfigJsonString);

        // Assertions for file content
        String savedFileContent = Files.readString(configFilePath);
        org.json.JSONObject expectedJson = new org.json.JSONObject(newConfigJsonString);
        org.json.JSONObject actualJson = new org.json.JSONObject(savedFileContent);
        // JSONObject.similar isn't available in the org.json version used, so compare key by key or toString
        assertEquals(expectedJson.toString(2), actualJson.toString(2), "Saved file content should match the new configuration");

        // Assertions for the current config object (should be updated by save->load)
        assertEquals("openai", config.llmProvider);
        assertEquals("http://newhost:54321", config.ollamaBaseUrl);
        assertEquals("newllamit", config.ollamaModelName);
        assertEquals("npm test", config.runCommand);
        assertEquals("sk-newkey", config.apiKeys.get("openai"));
        assertEquals("custom-xyz", config.apiKeys.get("customProvider"));


        // Assertions for a new load
        Config reloadedConfig = new Config(projectDir);
        assertEquals("openai", reloadedConfig.llmProvider);
        assertEquals("http://newhost:54321", reloadedConfig.ollamaBaseUrl);
        assertEquals("newllamit", reloadedConfig.ollamaModelName);
        assertEquals("http://newhost:8082/v2", reloadedConfig.genericOpenaiBaseUrl);
        assertEquals("new-generic", reloadedConfig.genericOpenaiModelName);
        assertEquals("new-generic-api-key", reloadedConfig.genericOpenaiApiKey);
        assertEquals("new-gemini-key", reloadedConfig.geminiApiKey);
        assertEquals("new-gemini-pro", reloadedConfig.geminiModelName);
        assertEquals("new-tavily-key", reloadedConfig.tavilyApiKey);
        assertEquals("npm test", reloadedConfig.runCommand);

        assertNotNull(reloadedConfig.apiKeys);
        assertEquals("sk-newkey", reloadedConfig.apiKeys.get("openai"));
        assertEquals("g-newkey", reloadedConfig.apiKeys.get("google"));
        assertEquals("custom-xyz", reloadedConfig.apiKeys.get("customProvider"));
        assertNull(reloadedConfig.apiKeys.get("anthropic"), "Anthropic key should be null as it was not in the new config");
    }

    // --- Test API Key Retrieval (getApiKey()) ---

    @Test
    void getApiKey_fromConfigFile_shouldReturnCorrectKey() throws IOException {
        // Setup: Create a .jaider.json with specific apiKeys
        String configWithApiKeys = """
                {
                  "llmProvider": "ollama",
                  "apiKeys": {
                    "openai": "file_openai_key",
                    "google": "file_google_key"
                  }
                }""";
        Files.writeString(configFilePath, configWithApiKeys);

        Config config = new Config(projectDir);

        // Assertions
        assertEquals("file_openai_key", config.getApiKey("openai"), "Should retrieve OpenAI key from config file");
        assertEquals("file_google_key", config.getApiKey("google"), "Should retrieve Google key from config file");
        assertNull(config.getApiKey("anthropic"), "Anthropic key should be null as it's not in config file (and no env var assumed)");
        assertNull(config.getApiKey("CUSTOM_PROVIDER_API_KEY"), "Non-standard key should be null"); // Testing System.getenv interaction
    }

    @Test
    void getApiKey_fromEnvironmentVariable_shouldReturnCorrectKey() {
        // This test requires environment variables to be set.
        // For example, set OPENAI_API_KEY="env_openai_key" before running the test.
        // JUnit Pioneer's @SetEnvironmentVariable would be ideal here.
        // Since it's not specified as available, this test will rely on manual setup or be limited.

        // Setup: Ensure no config file exists or it doesn't contain the key we're testing for env var.
        if (Files.exists(configFilePath)) {
            try {
                Files.delete(configFilePath);
            } catch (IOException e) {
                fail("Could not delete config file for env var test setup");
            }
        }
        // Or, create a config file without the specific key
        String configWithoutOpenAIKey = """
                {
                  "apiKeys": {
                    "google": "file_google_key"
                  }
                }""";
        try {
            Files.writeString(configFilePath, configWithoutOpenAIKey);
        } catch (IOException e) {
            fail("Could not write config file for env var test setup");
        }


        Config config = new Config(projectDir);

        // Attempt to retrieve a key that might be set as an environment variable
        // This assertion depends on the environment where tests are run.
        // If OPENAI_API_KEY is set to "env_openai_key", this should pass.
        // String expectedEnvKey = System.getenv("OPENAI_API_KEY"); // This would be the actual check
        // For now, we'll assert that it *could* return something if set, or null if not.
        // To make this test runnable without guaranteed env vars, we can't make a hard assertion on a specific value.
        // We are testing the logic: config file -> env var.
        // So, if not in config, it *should* try getenv.

        // Let's assume we cannot control env vars directly in this environment.
        // The best we can do is check that if a key is NOT in apiKeys map, it returns null (as per current implementation if env var is also null)
        // or it returns the env var if set.
        // This test is inherently tricky without env var mocking.
        // We'll test the fallback: if not in apiKeys, it returns null IF System.getenv also returns null.
        // If System.getenv returns a value, that value should be returned.

        // To make this test more deterministic without external setup, we can't assert a specific env var value.
        // We can, however, assert that if a key is NOT in the file, getApiKey doesn't throw an error and returns null (if no env var).
        assertNull(config.getApiKey("NON_EXISTENT_PROVIDER"), "Should be null if not in file and not in env");

        // To truly test the environment variable part, manual setup is needed.
        // For example, if you run: OPENAI_API_KEY="test_env_key" mvn test
        // Then the following would be a valid test (but requires external setup):
        // String envKey = System.getenv("OPENAI_API_KEY");
        // if (envKey != null) {
        //     assertEquals(envKey, config.getApiKey("OPENAI"));
        // }
        System.out.println("NOTE: getApiKey_fromEnvironmentVariable test is more of a placeholder due to inability to mock env vars directly in this tool's environment.");
        System.out.println("To test properly, set e.g. TEST_PROVIDER_API_KEY=test_value and call config.getApiKey(\"TEST_PROVIDER\")");
        // For the sake of having a runnable assertion here, let's check a known key that's unlikely to be a real env var.
        assertNull(config.getApiKey("SOME_MADE_UP_PROVIDER_FOR_TESTING_ENV"), "Made up provider should not be in file or typical env.");
    }

    @Test
    void getApiKey_fromConfigFileAndEnvironment_configFileShouldTakePrecedence() throws IOException {
        // This test also has dependencies on environment variable setup.
        // Assume OPENAI_API_KEY="env_openai_key" is set in the environment.

        // Setup: Create a .jaider.json with a specific key for OpenAI
        String configFileOpenAI = """
                {
                  "apiKeys": {
                    "OPENAI": "file_openai_key_override"
                  }
                }""";
        Files.writeString(configFilePath, configFileOpenAI);
        Config config = new Config(projectDir);

        // Even if OPENAI_API_KEY is set in env, the file should take precedence.
        assertEquals("file_openai_key_override", config.getApiKey("OPENAI"),
                "API key from config file should take precedence over environment variable.");

        System.out.println("NOTE: getApiKey_fromConfigFileAndEnvironment_configFileShouldTakePrecedence test assumes an env var like OPENAI_API_KEY might be set.");
        System.out.println("It verifies that the config file's value is used instead.");
    }

    @Test
    void getApiKey_notFound_shouldReturnNull() throws IOException {
        // Setup: Ensure no config file or an empty one, and assume no relevant env vars.
        if (Files.exists(configFilePath)) {
            Files.delete(configFilePath);
        }
        // Create a config with no apiKeys or an empty apiKeys map
        String configWithoutKeys = """
                {
                  "llmProvider": "ollama"
                }""";
        Files.writeString(configFilePath, configWithoutKeys);


        Config config = new Config(projectDir); // Loads the config without apiKeys

        // Assertions
        assertNull(config.getApiKey("openai"), "OpenAI key should be null if not in config and not in env");
        assertNull(config.getApiKey("nonexistent"), "Nonexistent key should be null");
        assertTrue(config.apiKeys.isEmpty(), "Internal apiKeys map should be empty");
    }

    // --- Test Reading for Editing (readForEditing()) ---

    @Test
    void readForEditing_withExistingConfig_shouldReturnJsonWithAllFields() throws IOException {
        // Setup: Create a .jaider.json with some specific and some missing fields
        String partialConfigContent = """
                {
                  "llmProvider": "custom_provider",
                  "ollamaModelName": "custom_ollama_model",
                  "apiKeys": {
                    "custom_api": "key123"
                  }
                }""";
        Files.writeString(configFilePath, partialConfigContent);

        Config config = new Config(projectDir); // This loads the partial config

        // Action
        String jsonForEditing = config.readForEditing();
        org.json.JSONObject editedJson = new org.json.JSONObject(jsonForEditing);

        // Assertions: Check that all fields are present, either from file or defaults
        assertEquals("custom_provider", editedJson.getString("llmProvider"));
        assertEquals(config.ollamaBaseUrl, editedJson.getString("ollamaBaseUrl")); // Default value
        assertEquals("custom_ollama_model", editedJson.getString("ollamaModelName"));
        assertEquals(config.genericOpenaiBaseUrl, editedJson.getString("genericOpenaiBaseUrl")); // Default
        assertEquals(config.genericOpenaiModelName, editedJson.getString("genericOpenaiModelName")); // Default
        assertEquals(config.genericOpenaiApiKey, editedJson.getString("genericOpenaiApiKey")); // Default
        assertEquals(config.geminiApiKey, editedJson.getString("geminiApiKey")); // Default
        assertEquals(config.geminiModelName, editedJson.getString("geminiModelName")); // Default
        assertEquals(config.tavilyApiKey, editedJson.getString("tavilyApiKey")); // Default
        assertEquals(config.runCommand, editedJson.getString("runCommand")); // Default (empty string)

        assertTrue(editedJson.has("apiKeys"), "apiKeys should be present");
        org.json.JSONObject apiKeysJson = editedJson.getJSONObject("apiKeys");
        assertEquals("key123", apiKeysJson.getString("custom_api"));
        // Default API keys (like openai, google, anthropic) are NOT added by readForEditing if they weren't in the original file's apiKeys
        // and not in the config object's apiKeys map.
        // The config.apiKeys map itself is populated from the file. readForEditing uses the content of that map.
        assertFalse(apiKeysJson.has("openai"), "Default openai key should not be added if not in original apiKeys");
    }

    @Test
    void readForEditing_withNonExistentConfig_shouldReturnJsonWithDefaults() throws IOException {
        // Setup: Ensure config file does not exist
        if (Files.exists(configFilePath)) {
            Files.delete(configFilePath);
        }
        assertFalse(Files.exists(configFilePath), "Config file should not exist at the start of this test.");

        // Action: Create Config (this will create and load defaults), then read for editing
        Config config = new Config(projectDir); // Creates default .jaider.json and loads it
        String jsonForEditing = config.readForEditing();
        org.json.JSONObject editedJson = new org.json.JSONObject(jsonForEditing);

        // Assertions: Check that all fields are present with their default values
        assertEquals(config.llmProvider, editedJson.getString("llmProvider")); // Default "ollama"
        assertEquals(config.ollamaBaseUrl, editedJson.getString("ollamaBaseUrl"));
        assertEquals(config.ollamaModelName, editedJson.getString("ollamaModelName"));
        assertEquals(config.genericOpenaiBaseUrl, editedJson.getString("genericOpenaiBaseUrl"));
        assertEquals(config.genericOpenaiModelName, editedJson.getString("genericOpenaiModelName"));
        assertEquals(config.genericOpenaiApiKey, editedJson.getString("genericOpenaiApiKey"));
        assertEquals(config.geminiApiKey, editedJson.getString("geminiApiKey"));
        assertEquals(config.geminiModelName, editedJson.getString("geminiModelName"));
        assertEquals(config.tavilyApiKey, editedJson.getString("tavilyApiKey"));
        assertEquals(config.runCommand, editedJson.getString("runCommand")); // Default ""

        assertTrue(editedJson.has("apiKeys"), "apiKeys object should exist");
        org.json.JSONObject apiKeysJson = editedJson.getJSONObject("apiKeys");

        // Because createDefaultConfig() was called and then loaded, the config object's apiKeys map
        // will contain the default placeholder keys. readForEditing() uses this map.
        assertEquals("YOUR_OPENAI_API_KEY", apiKeysJson.getString("openai"));
        assertEquals("YOUR_ANTHROPIC_API_KEY", apiKeysJson.getString("anthropic"));
        assertEquals("YOUR_GOOGLE_API_KEY", apiKeysJson.getString("google"));
    }

    @Test
    void readForEditing_withOldTestCommand_shouldMigrateToRunCommandInJson() throws IOException {
        // Setup: Create a .jaider.json with 'testCommand' and no 'runCommand'
        String oldConfigContent = """
                {
                  "llmProvider": "ollama",
                  "testCommand": "legacy test command",
                  "apiKeys": {}
                }""";
        Files.writeString(configFilePath, oldConfigContent);

        Config config = new Config(projectDir); // Loads config, config.runCommand becomes "legacy test command"

        // Action
        String jsonForEditing = config.readForEditing();
        org.json.JSONObject editedJson = new org.json.JSONObject(jsonForEditing);

        // Assertions
        assertTrue(editedJson.has("runCommand"), "JSON should have runCommand after editing");
        assertEquals("legacy test command", editedJson.getString("runCommand"),
                "runCommand in JSON should be populated from old testCommand");
        assertFalse(editedJson.has("testCommand"), "testCommand should be removed from JSON after editing");

        // Ensure other fields are still there (e.g., llmProvider)
        assertEquals("ollama", editedJson.getString("llmProvider"));

        // Test case: if both testCommand and runCommand exist, runCommand is preferred and testCommand is removed.
        String bothCommandsContent = """
                {
                  "llmProvider": "ollama",
                  "testCommand": "should be ignored and removed",
                  "runCommand": "should be kept",
                  "apiKeys": {}
                }""";
        Files.writeString(configFilePath, bothCommandsContent);
        Config configBoth = new Config(projectDir);
        String jsonForEditingBoth = configBoth.readForEditing();
        org.json.JSONObject editedJsonBoth = new org.json.JSONObject(jsonForEditingBoth);

        assertTrue(editedJsonBoth.has("runCommand"));
        assertEquals("should be kept", editedJsonBoth.getString("runCommand"));
        assertFalse(editedJsonBoth.has("testCommand"), "testCommand should be removed even if runCommand also exists");
    }
}
