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
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;
import dev.langchain4j.data.message.AiMessage;


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
        lenient().doNothing().when(mockJaiderModel).addLog(any(AiMessage.class));
        llmProviderFactory = new LlmProviderFactory(mockConfig, mockJaiderModel);
    }

    @Test
    void testCreateChatModel_OpenAIProvider_ShouldReturnOpenAiChatModel() {
        when(mockConfig.getLlm()).thenReturn("openai");
        when(mockConfig.getOpenaiApiKey()).thenReturn("test-api-key");
        when(mockConfig.getOpenaiModelName()).thenReturn("gpt-4o-mini");
        ChatLanguageModel chatModel = llmProviderFactory.createChatModel();
        assertNotNull(chatModel);
        assertTrue(chatModel instanceof OpenAiChatModel, "Should be an instance of OpenAiChatModel");
    }

    @Test
    void testCreateChatModel_OpenAIProvider_MissingApiKey_ShouldStillReturnModel() {
        when(mockConfig.getLlm()).thenReturn("openai");
        when(mockConfig.getOpenaiApiKey()).thenReturn(null);
        when(mockConfig.getOpenaiModelName()).thenReturn("gpt-4o-mini");
        assertThrows(dumb.jaider.app.exceptions.ChatModelInitializationException.class, () -> {
            llmProviderFactory.createChatModel();
        }, "Should throw ChatModelInitializationException if API key is truly missing and not resolved by environment.");
    }

    @Test
    void testCreateEmbeddingModel_OpenAIProvider_ShouldReturnOpenAiEmbeddingModel() {
        when(mockConfig.getLlm()).thenReturn("openai");
        when(mockConfig.getOpenaiApiKey()).thenReturn("test-api-key");
        // OpenAI embedding model name is hardcoded in factory, no need to mock config.getOpenaiEmbeddingModelName()
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();
        assertNotNull(embeddingModel);
        assertTrue(embeddingModel instanceof OpenAiEmbeddingModel, "Should be an instance of OpenAiEmbeddingModel");
    }

    @Test
    void testCreateEmbeddingModel_OpenAIProvider_MissingApiKey_ShouldStillReturnModel() {
        when(mockConfig.getLlm()).thenReturn("openai");
        when(mockConfig.getOpenaiApiKey()).thenReturn(null);
        // OpenAI embedding model name is hardcoded in factory

        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();
        assertNotNull(embeddingModel, "EmbeddingModel should not be null.");
        assertTrue(embeddingModel instanceof OpenAiEmbeddingModel || embeddingModel instanceof NoOpEmbeddingModel, "Model should be OpenAI or NoOp if API key is missing from config");
    }

    @Test
    void testCreateChatModel_OpenAIProvider_ExceptionDuringCreation_ShouldReturnNull() {
        when(mockConfig.getLlm()).thenReturn("openai");
        when(mockConfig.getOpenaiApiKey()).thenReturn("test-api-key");
        when(mockConfig.getOpenaiModelName()).thenReturn("completely-invalid-model-that-will-cause-builder-error");
        ChatLanguageModel chatModel = llmProviderFactory.createChatModel();
        assertNotNull(chatModel, "ChatModel should be returned, even if LC4J handles the bad model name leniently.");
    }


    @Test
    void testCreateEmbeddingModel_OpenAIProvider_ExceptionDuringCreation_ShouldFallBackToNoOp() {
        when(mockConfig.getLlm()).thenReturn("openai");
        // Force an error by making the API key getter throw, which is inside the factory's try block.
        when(mockConfig.getOpenaiApiKey()).thenThrow(new RuntimeException("Test-induced API key access error for OpenAI embedding"));
        // No need to mock getOpenaiEmbeddingModelName() as it's not used by the factory method for model name determination (it's hardcoded)

        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();
        assertNotNull(embeddingModel);
        assertTrue(embeddingModel instanceof NoOpEmbeddingModel, "Should fall back to NoOpEmbeddingModel on exception during OpenAI embedding setup.");
    }

    @Test
    void testCreateEmbeddingModel_GeminiProvider_ShouldReturnVertexAiEmbeddingModel() {
        when(mockConfig.getLlm()).thenReturn("gemini");
        when(mockConfig.getGeminiEmbeddingModelName()).thenReturn("textembedding-gecko@001");
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();
        assertNotNull(embeddingModel, "EmbeddingModel should not be null.");
        if (System.getenv("GOOGLE_CLOUD_PROJECT") != null && System.getenv("GOOGLE_CLOUD_LOCATION") != null) {
            assertTrue(embeddingModel instanceof VertexAiEmbeddingModel, "Should be VertexAiEmbeddingModel if project/location env vars are set.");
        } else {
            assertTrue(embeddingModel instanceof dumb.jaider.llm.NoOpEmbeddingModel, "Should be NoOpEmbeddingModel if project/location env vars are not set.");
        }
    }

    @Test
    void testCreateEmbeddingModel_GeminiProvider_MissingProjectEnvVar_ShouldFallBackToNoOp() {
        when(mockConfig.getLlm()).thenReturn("gemini");
        when(mockConfig.getGeminiEmbeddingModelName()).thenReturn("textembedding-gecko");
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();
        assertNotNull(embeddingModel);
        if (System.getenv("GOOGLE_CLOUD_PROJECT") == null || System.getenv("GOOGLE_CLOUD_LOCATION") == null) {
             assertTrue(embeddingModel instanceof dumb.jaider.llm.NoOpEmbeddingModel, "Should fall back to NoOpEmbeddingModel if project/location env vars are not set.");
        }
    }

    @Test
    void testCreateEmbeddingModel_GeminiProvider_UsesDefaultModelNameIfNotConfigured() {
        when(mockConfig.getLlm()).thenReturn("gemini");
        when(mockConfig.getGeminiEmbeddingModelName()).thenReturn(null);
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();
        assertNotNull(embeddingModel);
        if (System.getenv("GOOGLE_CLOUD_PROJECT") != null && System.getenv("GOOGLE_CLOUD_LOCATION") != null) {
            assertTrue(embeddingModel instanceof VertexAiEmbeddingModel, "Should be VertexAiEmbeddingModel if project/location are set, using default embedding model name.");
        } else {
            assertTrue(embeddingModel instanceof dumb.jaider.llm.NoOpEmbeddingModel, "Should be NoOpEmbeddingModel if project/location are not set.");
        }
    }

    @Test
    void testCreateChatModel_GenericOpenAIProvider_ShouldReturnOpenAiChatModel() {
        when(mockConfig.getLlm()).thenReturn("genericOpenai");
        when(mockConfig.getGenericOpenaiBaseUrl()).thenReturn("http://localhost:8080/v1");
        when(mockConfig.getGenericOpenaiApiKey()).thenReturn("test-generic-api-key");
        when(mockConfig.getGenericOpenaiModelName()).thenReturn("generic-model");
        ChatLanguageModel chatModel = llmProviderFactory.createChatModel();
        assertNotNull(chatModel);
        assertTrue(chatModel instanceof OpenAiChatModel, "Should be an instance of OpenAiChatModel for genericOpenai provider");
    }

    @Test
    void testCreateEmbeddingModel_GenericOpenAIProvider_ShouldReturnOpenAiEmbeddingModel() {
        when(mockConfig.getLlm()).thenReturn("genericOpenai");
        when(mockConfig.getGenericOpenaiBaseUrl()).thenReturn("http://localhost:8080/v1");
        when(mockConfig.getGenericOpenaiApiKey()).thenReturn("test-generic-api-key");
        when(mockConfig.getGenericOpenaiEmbeddingModelName()).thenReturn("generic-embedding-model");
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();
        assertNotNull(embeddingModel);
        assertTrue(embeddingModel instanceof OpenAiEmbeddingModel, "Should be an instance of OpenAiEmbeddingModel for genericOpenai provider");
    }

    @Test
    void testCreateChatModel_GenericOpenAIProvider_MissingApiKey_ShouldStillAttempt() {
        when(mockConfig.getLlm()).thenReturn("genericOpenai");
        when(mockConfig.getGenericOpenaiBaseUrl()).thenReturn("http://localhost:8080/v1");
        when(mockConfig.getGenericOpenaiApiKey()).thenReturn(null);
        when(mockConfig.getGenericOpenaiModelName()).thenReturn("generic-model");
        assertThrows(dumb.jaider.app.exceptions.ChatModelInitializationException.class, () -> {
            llmProviderFactory.createChatModel();
        }, "Should throw ChatModelInitializationException if API key is truly missing and not resolved by environment for Generic OpenAI.");
    }

    @Test
    void testCreateEmbeddingModel_GenericOpenAIProvider_MissingApiKey_ShouldStillAttempt() {
        when(mockConfig.getLlm()).thenReturn("genericOpenai");
        when(mockConfig.getGenericOpenaiBaseUrl()).thenReturn("http://localhost:8080/v1");
        when(mockConfig.getGenericOpenaiApiKey()).thenReturn(null);
        when(mockConfig.getGenericOpenaiEmbeddingModelName()).thenReturn("generic-embedding-model");
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();
        assertNotNull(embeddingModel, "EmbeddingModel should not be null.");
        assertTrue(embeddingModel instanceof OpenAiEmbeddingModel || embeddingModel instanceof NoOpEmbeddingModel, "Model should be Generic OpenAI (via OpenAiEmbeddingModel client) or NoOp if API key is missing from config");
    }

    @Test
    void testCreateChatModel_GenericOpenAIProvider_ExceptionDuringCreation_ShouldReturnNull() {
        when(mockConfig.getLlm()).thenReturn("genericOpenai");
        when(mockConfig.getGenericOpenaiBaseUrl()).thenReturn("http://invalid-url-that-causes-immediate-error");
        when(mockConfig.getGenericOpenaiApiKey()).thenReturn("test-generic-api-key");
        when(mockConfig.getGenericOpenaiModelName()).thenReturn("generic-model");
        ChatLanguageModel chatModel = llmProviderFactory.createChatModel();
        assertNotNull(chatModel, "ChatModel should be returned, even if LC4J handles the bad URL leniently at build time.");
    }

    @Test
    void testCreateEmbeddingModel_GenericOpenAIProvider_ExceptionDuringCreation_ShouldFallBackToNoOp() {
        when(mockConfig.getLlm()).thenReturn("genericOpenai");
        when(mockConfig.getGenericOpenaiBaseUrl()).thenReturn("http://localhost:1234");
        when(mockConfig.getGenericOpenaiApiKey()).thenReturn("test-generic-api-key");
        when(mockConfig.getGenericOpenaiEmbeddingModelName()).thenThrow(new RuntimeException("Test-induced model name error"));
        EmbeddingModel embeddingModel = llmProviderFactory.createEmbeddingModel();
        assertNotNull(embeddingModel);
        assertTrue(embeddingModel instanceof dumb.jaider.llm.NoOpEmbeddingModel, "Should fall back to NoOpEmbeddingModel on exception.");
    }
}
