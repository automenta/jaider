package dumb.jaider.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;


public class LlmProviderFactory {
    private final Config config;
    private final JaiderModel model;

    private ChatLanguageModel chatModel;
    private Tokenizer tokenizer;
    private EmbeddingModel embeddingModel;

    public LlmProviderFactory(Config config, JaiderModel model) {
        this.config = config;
        this.model = model;
    }

    public ChatLanguageModel createChatModel() {
        if ("ollama".equalsIgnoreCase(config.llm)) {
            setupOllama();
        } else if ("genericOpenai".equalsIgnoreCase(config.llm)) {
            setupGenericOpenAI();
        } else if ("openai".equalsIgnoreCase(config.llm)) {
            model.addLog(AiMessage.from("[Jaider] OpenAI provider selected but setupOpenAI() is currently commented out. No model initialized."));
        } else {
            model.addLog(AiMessage.from(String.format("[Jaider] WARNING: Unknown llmProvider '%s' in config. Defaulting to Ollama.", config.llm)));
            setupOllama();
        }

        if (this.tokenizer == null && this.chatModel instanceof Tokenizer) {
            this.tokenizer = (Tokenizer) this.chatModel;
        } else if (this.tokenizer == null) {
        }
        return this.chatModel;
    }

    public Tokenizer createTokenizer() {
        if (this.tokenizer == null) {
            if (this.chatModel == null) createChatModel();
        }
        return this.tokenizer;
    }

    public EmbeddingModel createEmbeddingModel() {
        if (this.embeddingModel == null) { // Create only if not already created
            if ("ollama".equalsIgnoreCase(config.llm)) {
                setupOllamaEmbeddingModel();
            } else if ("genericOpenai".equalsIgnoreCase(config.llm)) {
                // Assuming genericOpenai might use a similar setup for embeddings if available
                // This part might need a specific GenericOpenAiEmbeddingModel or configuration
                // For now, let's try to adapt OllamaEmbeddingModel if the API is compatible
                // or log that it's not supported yet.
                // Setting to null or a specific implementation if available.
                // For demonstration, trying OllamaEmbeddingModel, but this might not be correct for all generic OpenAI endpoints.
                setupGenericOpenAIEmbeddingModel();
            } else if ("openai".equalsIgnoreCase(config.llm)) {
                // setupOpenAIEmbeddingModel(); // Placeholder
                model.addLog(AiMessage.from("[Jaider] OpenAI embedding model selected but setup is currently commented out. No embedding model initialized."));
            } else {
                model.addLog(AiMessage.from(String.format("[Jaider] WARNING: Unknown llmProvider '%s' for embedding model. No embedding model initialized.", config.llm)));
            }
        }
        return this.embeddingModel;
    }

    private void setupOllamaEmbeddingModel() {
        try {
            this.embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(config.ollamaBaseUrl)
                    .modelName(config.ollamaModelName) // Often, the same model can be used for embeddings or a specific one like 'nomic-embed-text'
                    .build();
            model.addLog(AiMessage.from(String.format("[Jaider] Ollama Embedding model '%s' initialized successfully from %s.", config.ollamaModelName, config.ollamaBaseUrl)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Ollama Embedding model '%s' from %s. Error: %s. Falling back to local NoOpEmbeddingModel.", config.ollamaModelName, config.ollamaBaseUrl, e.getMessage())));
            this.embeddingModel = new dumb.jaider.llm.NoOpEmbeddingModel(); // Use local NoOpEmbeddingModel
        }
    }

    private void setupGenericOpenAIEmbeddingModel() {
        // This is speculative. Generic OpenAI endpoints might not support embedding through the Ollama client
        // or might need a different client/builder.
        // For now, this attempts to use OllamaEmbeddingModel, which might fail for non-Ollama OpenAI-compatible endpoints.
        try {
            this.embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(config.genericOpenaiBaseUrl) // Assuming embedding endpoint is at same base
                    .modelName(config.genericOpenaiModelName) // Or a specific embedding model name
                    .build();
            model.addLog(AiMessage.from(String.format("[Jaider] Generic OpenAI-compatible Embedding model '%s' attempted initialization from %s.", config.genericOpenaiModelName, config.genericOpenaiBaseUrl)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Generic OpenAI-compatible Embedding model '%s' from %s. Error: %s. Falling back to local NoOpEmbeddingModel.", config.genericOpenaiModelName, config.genericOpenaiBaseUrl, e.getMessage())));
            this.embeddingModel = new dumb.jaider.llm.NoOpEmbeddingModel(); // Use local NoOpEmbeddingModel
        }
    }

    private void setupOllama() {
        try {
            this.chatModel = OllamaChatModel.builder()
                    .baseUrl(config.ollamaBaseUrl)
                    .modelName(config.ollamaModelName)
                    .build();
            this.tokenizer = (Tokenizer) this.chatModel; // Assuming chat model can also be tokenizer
            model.addLog(AiMessage.from(String.format("[Jaider] Ollama model '%s' initialized successfully from %s.", config.ollamaModelName, config.ollamaBaseUrl)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Ollama model '%s' from %s. Error: %s. Jaider's functionality will be severely limited. Check Ollama server and config.", config.ollamaModelName, config.ollamaBaseUrl, e.getMessage())));
            // Fallback for chat model might be needed if it's critical for tests that don't mock it.
            // For now, focusing on embedding model.
        }
    }

    private void setupGenericOpenAI() {
        try {
            var builder = OllamaChatModel.builder()
                    .baseUrl(config.genericOpenaiBaseUrl)
                    .modelName(config.genericOpenaiModelName);

            String apiKey = config.getGenericOpenaiApiKey();
            if (apiKey != null && !apiKey.isEmpty()) {
                model.addLog(AiMessage.from("[Jaider] INFO: Generic OpenAI API key found via configuration. Note: the current OllamaChatModel builder may not use it directly for Bearer token authentication. Ensure your generic OpenAI endpoint is secured appropriately or uses a different mechanism if it requires API key authentication. API key might need to be included in baseUrl or handled by a proxy if required by the endpoint."));
            }

            this.chatModel = builder.build();
            this.tokenizer = (Tokenizer) this.chatModel; // Assuming chat model can also be tokenizer
            model.addLog(AiMessage.from(String.format("[Jaider] Generic OpenAI-compatible model '%s' initialized from %s.", config.genericOpenaiModelName, config.genericOpenaiBaseUrl)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Generic OpenAI-compatible model '%s' from %s. Error: %s. Functionality severely limited.", config.genericOpenaiModelName, config.genericOpenaiBaseUrl, e.getMessage())));
            // Fallback for chat model might be needed.
        }
    }
}
