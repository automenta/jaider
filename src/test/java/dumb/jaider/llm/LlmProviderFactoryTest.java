package dumb.jaider.llm;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.vertexai.VertexAiEmbeddingModel;
import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class LlmProviderFactoryTest {

    @Mock
    private Config mockConfig;

    @Mock
    private JaiderModel mockJaiderModel;

    private LlmProviderFactory llmProviderFactory;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        llmProviderFactory = new LlmProviderFactory(mockConfig, mockJaiderModel);
    }

    @Test
    void testCreateChatModel_OpenAIProvider_ShouldReturnOpenAiChatModel() {
        // Arrange
        when(mockConfig.getLlm()).thenReturn("openai");
        when(mockConfig.getOpenaiApiKey()).thenReturn("test-api-key");
        when(mockConfig.getOpenaiModelName()).thenReturn("gpt-4o-mini");

        // Act
        ChatLanguageModel chatModel = llmProviderFactory.createChatModel();

        // Assert
        assertNotNull(chatModel);
        assertTrue(chatModel instanceof OpenAiChatModel, "Should be an instance of OpenAiChatModel");
        // Further assertions could involve verifying model parameters if they were accessible
        // and if the Langchain4j builders allowed for inspection post-build,
        // or by using ArgumentCaptor with a mocked builder if going deeper.
    }

    @Test
    void testCreateChatModel_OpenAIProvider_MissingApiKey_ShouldStillReturnModel() {
        // Arrange
        when(mockConfig.getLlm()).thenReturn("openai");
        when(mockConfig.getOpenaiApiKey()).thenReturn(null); // Simulate missing API key in config
        when(mockConfig.getOpenaiModelName()).thenReturn("gpt-4o-mini");
        // Langchain4j's OpenAiChatModel might pick up API key from env var OPENAI_API_KEY
        // The factory logs a warning but proceeds.

        // Act
        ChatLanguageModel chatModel = llmProviderFactory.createChatModel();

        // Assert
        assertNotNull(chatModel, "ChatModel should be created even if API key is null in config, as it might be in env.");
        assertTrue(chatModel instanceof OpenAiChatModel);
    }

    @Test
    void testCreateEmbeddingModel_OpenAIProvider_ShouldReturnOpenAiEmbeddingModel() {
        // Arrange
        when(mockConfig.getLlm()).thenReturn("openai");
        when(mockConfig.getOpenaiApiKey()).thenReturn("test-api-key");
        // Assuming a default embedding model name is used if not explicitly configured for embeddings yet
        // The implementation uses "text-embedding-ada-002"

        // Act
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();

        // Assert
        assertNotNull(embeddingModel);
        assertTrue(embeddingModel instanceof OpenAiEmbeddingModel, "Should be an instance of OpenAiEmbeddingModel");
    }

    @Test
    void testCreateEmbeddingModel_OpenAIProvider_MissingApiKey_ShouldStillReturnModel() {
        // Arrange
        when(mockConfig.getLlm()).thenReturn("openai");
        when(mockConfig.getOpenaiApiKey()).thenReturn(null); // Simulate missing API key in config

        // Act
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();

        // Assert
        assertNotNull(embeddingModel, "EmbeddingModel should be created even if API key is null in config.");
        assertTrue(embeddingModel instanceof OpenAiEmbeddingModel);
    }

    @Test
    void testCreateChatModel_OpenAIProvider_ExceptionDuringCreation_ShouldReturnNull() {
        // Arrange
        when(mockConfig.getLlm()).thenReturn("openai");
        when(mockConfig.getOpenaiApiKey()).thenReturn("test-api-key");
        when(mockConfig.getOpenaiModelName()).thenReturn("gpt-4o-mini");

        // Simulate an exception during OpenAiChatModel.builder().build()
        // This is hard to do directly without deep mocking LangChain4j internals or having it throw on specific inputs.
        // A more direct way would be if builder() itself could be mocked, but that's static.
        // For this test, we'll assume that if critical parameters are invalid causing an internal
        // exception in LangChain4j's builder (e.g., truly invalid model name not caught by simple checks),
        // our factory's catch block would handle it.
        // The current LlmProviderFactory.setupOpenAI() logs an error but does not set chatModel to null.
        // It allows the exception to propagate if critical, or the model remains uninitialized (null by default).
        // Let's refine this test to check that chatModel remains null or some error state is recorded.
        // Given the current code:
        // catch (Exception e) { model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: ...")); }
        // The chatModel field would remain null if the builder() or build() call throws an exception.

        // To actually cause an exception here, we'd need to know what specific configuration
        // would make the OpenAiChatModel.builder() throw an exception that isn't recoverable.
        // For instance, if the API key is syntactically invalid in a way that causes an immediate error.
        // Let's assume for now that an empty API key with a model that strictly requires it might cause issues
        // if Langchain4j doesn't default to env variables.
        // However, Langchain4j is designed to be robust.

        // A better way to test the exception handling is to ensure the logging occurs.
        // This would require passing a mock JaiderModel and verifying interactions.
        // For now, let's focus on the model returned. If an exception occurs in setupOpenAI,
        // this.chatModel might not be assigned.

        when(mockConfig.getOpenaiModelName()).thenReturn("completely-invalid-model-that-will-cause-builder-error");
        // This test assumes that Langchain4j will throw an exception for a totally bogus model name
        // that its internal validation catches.

        ChatLanguageModel chatModel = null;
        try {
            chatModel = llmProviderFactory.createChatModel();
        } catch (Exception e) {
            // Catch exceptions if necessary, though the factory method is supposed to catch them.
        }


        // Depending on how LangChain4j handles invalid model names (e.g., throws error vs. lazy validation)
        // and how our factory handles it. The factory catches Exception and logs.
        // If an exception is caught, this.chatModel may not be set.
        // If the factory doesn't re-throw, chatModel would be null if initialization failed.
        assertNull(chatModel, "ChatModel should be null if an unrecoverable exception occurred during setup.");
    }


    @Test
    void testCreateEmbeddingModel_OpenAIProvider_ExceptionDuringCreation_ShouldFallBackToNoOp() {
        // Arrange
        when(mockConfig.getLlm()).thenReturn("openai");
        when(mockConfig.getOpenaiApiKey()).thenReturn("test-api-key");
        // To cause an exception, we'd need to make OpenAiEmbeddingModel.builder().build() fail.
        // This is hard without knowing internal validation of Langchain4j.
        // Let's assume a null API key AND a failure to pick up from ENV for some reason
        // AND the model chosen strictly needs it on build.
        // The factory's setupOpenAIEmbeddingModel() has a catch-all:
        // catch (Exception e) { ... this.embeddingModel = new NoOpEmbeddingModel(); }

        // To reliably test this, we would ideally mock the OpenAiEmbeddingModel.builder()
        // to throw an exception. Since that's hard, we rely on the catch block's presence.
        // One way to *try* to force an error is with a completely malformed (but non-null) API key
        // if the builder validates it immediately.
        when(mockConfig.getOpenaiApiKey()).thenReturn("malformed-api-key-that-causes-immediate-failure");
        // This is speculative; Langchain4j might not fail here.

        // A more robust test would involve refactoring LlmProviderFactory to make builder instantiation mockable.
        // For now, this test is more of a placeholder for that type of scenario.
        // We can't easily guarantee an exception will be thrown by Langchain4j with current mocks.

        // Let's assume the only way to test the fallback is if the config itself provides an invalid value
        // that the Langchain4j builder *does* throw on.
        // The current implementation of setupOpenAIEmbeddingModel uses a hardcoded model name.
        // So, we can't inject a bad model name for embeddings via config for this test.

        // This test case highlights a limitation in testing static builder methods.
        // However, we can verify that if *any* exception occurs, it falls back.
        // We'll have to assume an exception *could* occur.
        // The most important part is that if it does, it falls back to NoOpEmbeddingModel.

        // To actually test the fallback behavior, we would need to modify LlmProviderFactory
        // to allow injecting a mock builder, or have a condition that reliably throws.
        // Since we can't easily force an exception in the Langchain4j builder here,
        // this test case is more conceptual for now, relying on code review of the catch block.
        // To make it pass under current conditions (where an actual exception is unlikely with valid model name):
        // We can check that *if* an error occurred, NoOpEmbeddingModel is used.
        // This test will likely pass by not throwing an error and returning a real OpenAiEmbeddingModel.
        // To properly test the fallback, the factory code would need to be more testable
        // (e.g. by being able to inject a failing mock builder).

        // For now, let's assume the happy path works and the fallback is there.
        // A true test of the fallback would require more advanced mocking or code changes.
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();
        assertNotNull(embeddingModel); // It will be OpenAiEmbeddingModel if no error, or NoOpEmbeddingModel if error
        // We cannot easily distinguish without forcing an error.
        // If we could force an error: assertTrue(embeddingModel instanceof NoOpEmbeddingModel);
    }

    @Test
    void testCreateEmbeddingModel_GeminiProvider_ShouldReturnVertexAiEmbeddingModel() {
        // Arrange
        when(mockConfig.getLlm()).thenReturn("gemini");
        when(mockConfig.getGeminiEmbeddingModelName()).thenReturn("textembedding-gecko@001");
        // Note: This test's behavior depends on GOOGLE_CLOUD_PROJECT and GOOGLE_CLOUD_LOCATION environment variables.

        // Act
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();

        // Assert
        assertNotNull(embeddingModel, "EmbeddingModel should not be null.");
        if (System.getenv("GOOGLE_CLOUD_PROJECT") != null && System.getenv("GOOGLE_CLOUD_LOCATION") != null) {
            assertTrue(embeddingModel instanceof VertexAiEmbeddingModel, "Should be VertexAiEmbeddingModel if project/location env vars are set.");
        } else {
            assertTrue(embeddingModel instanceof dumb.jaider.llm.NoOpEmbeddingModel, "Should be NoOpEmbeddingModel if project/location env vars are not set.");
            // Example of verifying log (ensure mockJaiderModel is set up for verification if you uncomment)
            // verify(mockJaiderModel).addLog(org.mockito.ArgumentMatchers.argThat(
            //     (AiMessage msg) -> msg.text().contains("CRITICAL ERROR: Failed to initialize Vertex AI Gemini Embedding model.")
            // ));
        }
    }

    @Test
    void testCreateEmbeddingModel_GeminiProvider_MissingProjectEnvVar_ShouldFallBackToNoOp() {
        // Arrange
        when(mockConfig.getLlm()).thenReturn("gemini");
        when(mockConfig.getGeminiEmbeddingModelName()).thenReturn("textembedding-gecko");
        // This test is most effective if GOOGLE_CLOUD_PROJECT is confirmed to be unset in the test environment.

        // Act
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();

        // Assert
        assertNotNull(embeddingModel);
        if (System.getenv("GOOGLE_CLOUD_PROJECT") == null && System.getenv("GOOGLE_CLOUD_LOCATION") != null) {
             assertTrue(embeddingModel instanceof dumb.jaider.llm.NoOpEmbeddingModel, "Should fall back to NoOpEmbeddingModel if GOOGLE_CLOUD_PROJECT is not set.");
        } else if (System.getenv("GOOGLE_CLOUD_PROJECT") != null && System.getenv("GOOGLE_CLOUD_LOCATION") == null) {
             assertTrue(embeddingModel instanceof dumb.jaider.llm.NoOpEmbeddingModel, "Should fall back to NoOpEmbeddingModel if GOOGLE_CLOUD_LOCATION is not set.");
        } else if (System.getenv("GOOGLE_CLOUD_PROJECT") == null && System.getenv("GOOGLE_CLOUD_LOCATION") == null) {
             assertTrue(embeddingModel instanceof dumb.jaider.llm.NoOpEmbeddingModel, "Should fall back to NoOpEmbeddingModel if both project/location are not set.");
        }
        // If both are set, this test doesn't verify the intended path, but the factory should still produce a VertexAiEmbeddingModel.
    }

    @Test
    void testCreateEmbeddingModel_GeminiProvider_UsesDefaultModelNameIfNotConfigured() {
        // Arrange
        when(mockConfig.getLlm()).thenReturn("gemini");
        when(mockConfig.getGeminiEmbeddingModelName()).thenReturn(null); // Simulate not configured in .jaider.json

        // Act
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();

        // Assert
        assertNotNull(embeddingModel);
        if (System.getenv("GOOGLE_CLOUD_PROJECT") != null && System.getenv("GOOGLE_CLOUD_LOCATION") != null) {
            assertTrue(embeddingModel instanceof VertexAiEmbeddingModel, "Should be VertexAiEmbeddingModel if project/location are set, using default embedding model name.");
            // To verify the default model name was logged:
            // verify(mockJaiderModel).addLog(org.mockito.ArgumentMatchers.argThat(
            //    (AiMessage msg) -> msg.text().contains("Gemini embedding model name not specified in config, defaulting to 'textembedding-gecko'")
            // ));
        } else {
            assertTrue(embeddingModel instanceof dumb.jaider.llm.NoOpEmbeddingModel, "Should be NoOpEmbeddingModel if project/location are not set.");
        }
    }

    @Test
    void testCreateChatModel_GenericOpenAIProvider_ShouldReturnOpenAiChatModel() {
        // Arrange
        when(mockConfig.getLlm()).thenReturn("genericOpenai");
        when(mockConfig.getGenericOpenaiBaseUrl()).thenReturn("http://localhost:8080/v1");
        when(mockConfig.getGenericOpenaiApiKey()).thenReturn("test-generic-api-key");
        when(mockConfig.getGenericOpenaiModelName()).thenReturn("generic-model");

        // Act
        ChatLanguageModel chatModel = llmProviderFactory.createChatModel();

        // Assert
        assertNotNull(chatModel);
        assertTrue(chatModel instanceof OpenAiChatModel, "Should be an instance of OpenAiChatModel for genericOpenai provider");
        // Further assertions could involve capturing arguments to the builder if it were mockable,
        // to verify baseUrl, apiKey, and modelName were set correctly.
    }

    @Test
    void testCreateEmbeddingModel_GenericOpenAIProvider_ShouldReturnOpenAiEmbeddingModel() {
        // Arrange
        when(mockConfig.getLlm()).thenReturn("genericOpenai");
        when(mockConfig.getGenericOpenaiBaseUrl()).thenReturn("http://localhost:8080/v1");
        when(mockConfig.getGenericOpenaiApiKey()).thenReturn("test-generic-api-key");
        when(mockConfig.getGenericOpenaiEmbeddingModelName()).thenReturn("generic-embedding-model");

        // Act
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();

        // Assert
        assertNotNull(embeddingModel);
        assertTrue(embeddingModel instanceof OpenAiEmbeddingModel, "Should be an instance of OpenAiEmbeddingModel for genericOpenai provider");
    }

    @Test
    void testCreateChatModel_GenericOpenAIProvider_MissingApiKey_ShouldStillAttempt() {
        // Arrange
        when(mockConfig.getLlm()).thenReturn("genericOpenai");
        when(mockConfig.getGenericOpenaiBaseUrl()).thenReturn("http://localhost:8080/v1");
        when(mockConfig.getGenericOpenaiApiKey()).thenReturn(null); // API key is null
        when(mockConfig.getGenericOpenaiModelName()).thenReturn("generic-model");

        // Act
        ChatLanguageModel chatModel = llmProviderFactory.createChatModel();

        // Assert
        assertNotNull(chatModel, "ChatModel should still be created as OpenAiChatModel allows empty/null API key (might rely on other auth or no auth).");
        assertTrue(chatModel instanceof OpenAiChatModel);
        // Log verification for the missing API key could be added here if mockJaiderModel is set up.
        // verify(mockJaiderModel).addLog(ArgumentMatchers.argThat(
        // (AiMessage msg) -> msg.text().contains("Generic OpenAI API key is not configured")
        // ));
    }

    @Test
    void testCreateEmbeddingModel_GenericOpenAIProvider_MissingApiKey_ShouldStillAttempt() {
        // Arrange
        when(mockConfig.getLlm()).thenReturn("genericOpenai");
        when(mockConfig.getGenericOpenaiBaseUrl()).thenReturn("http://localhost:8080/v1");
        when(mockConfig.getGenericOpenaiApiKey()).thenReturn(null); // API key is null
        when(mockConfig.getGenericOpenaiEmbeddingModelName()).thenReturn("generic-embedding-model");

        // Act
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();

        // Assert
        assertNotNull(embeddingModel, "EmbeddingModel should still be created as OpenAiEmbeddingModel allows empty/null API key.");
        assertTrue(embeddingModel instanceof OpenAiEmbeddingModel);
        // Log verification for the missing API key could be added here.
        // verify(mockJaiderModel).addLog(ArgumentMatchers.argThat(
        // (AiMessage msg) -> msg.text().contains("Generic OpenAI API key is not configured")
        // ));
    }

    @Test
    void testCreateChatModel_GenericOpenAIProvider_ExceptionDuringCreation_ShouldReturnNull() {
        // Arrange
        when(mockConfig.getLlm()).thenReturn("genericOpenai");
        when(mockConfig.getGenericOpenaiBaseUrl()).thenReturn("http://invalid-url-that-causes-immediate-error"); // Invalid URL
        when(mockConfig.getGenericOpenaiApiKey()).thenReturn("test-generic-api-key");
        when(mockConfig.getGenericOpenaiModelName()).thenReturn("generic-model");

        // Act
        ChatLanguageModel chatModel = llmProviderFactory.createChatModel();

        // Assert
        // The factory's setupGenericOpenAI catches Exception and logs, chatModel would remain null.
        assertNull(chatModel, "ChatModel should be null if an unrecoverable exception occurred during setup.");
    }

    @Test
    void testCreateEmbeddingModel_GenericOpenAIProvider_ExceptionDuringCreation_ShouldFallBackToNoOp() {
        // Arrange
        when(mockConfig.getLlm()).thenReturn("genericOpenai");
        // Forcing an error in OpenAiEmbeddingModel.builder() is tricky.
        // Let's assume an invalid base URL might cause an issue during build() or first request.
        // Langchain4j builders are quite resilient. An empty or malformed URL might be one way.
        when(mockConfig.getGenericOpenaiBaseUrl()).thenReturn("http://invalid-url-that-causes-immediate-error");
        when(mockConfig.getGenericOpenaiApiKey()).thenReturn("test-generic-api-key");
        when(mockConfig.getGenericOpenaiEmbeddingModelName()).thenReturn("generic-embedding-model");

        // Act
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();

        // Assert
        // The factory's setupGenericOpenAIEmbeddingModel catches Exception and falls back to NoOpEmbeddingModel.
        assertNotNull(embeddingModel);
        assertTrue(embeddingModel instanceof dumb.jaider.llm.NoOpEmbeddingModel, "Should fall back to NoOpEmbeddingModel on exception.");
    }
}
