package dumb.jaider.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
        if ("ollama".equalsIgnoreCase(config.llmProvider)) {
            setupOllama();
        } else if ("genericOpenai".equalsIgnoreCase(config.llmProvider)) {
            setupGenericOpenAI();
        } else if ("openai".equalsIgnoreCase(config.llmProvider)) {
            model.addLog(AiMessage.from("[Jaider] OpenAI provider selected but setupOpenAI() is currently commented out. No model initialized."));
        } else {
            model.addLog(AiMessage.from(String.format("[Jaider] WARNING: Unknown llmProvider '%s' in config. Defaulting to Ollama.", config.llmProvider)));
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
        if ("openai".equalsIgnoreCase(config.llmProvider)) {
        }
        return this.embeddingModel;
    }

    private void setupOllama() {
        try {
            this.chatModel = OllamaChatModel.builder()
                    .baseUrl(config.ollamaBaseUrl)
                    .modelName(config.ollamaModelName)
                    .build();
            this.tokenizer = (Tokenizer) this.chatModel;
            model.addLog(AiMessage.from(String.format("[Jaider] Ollama model '%s' initialized successfully from %s.", config.ollamaModelName, config.ollamaBaseUrl)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Ollama model '%s' from %s. Error: %s. Jaider's functionality will be severely limited. Check Ollama server and config.", config.ollamaModelName, config.ollamaBaseUrl, e.getMessage())));
        }
    }

    private void setupGenericOpenAI() {
        try {
            var builder = OllamaChatModel.builder()
                    .baseUrl(config.genericOpenaiBaseUrl)
                    .modelName(config.genericOpenaiModelName);

            if (config.genericOpenaiApiKey != null && !config.genericOpenaiApiKey.isEmpty()) {
                model.addLog(AiMessage.from("[Jaider] INFO: genericOpenaiApiKey is set in config, but the current OllamaChatModel builder may not use it for Bearer token authentication. API key might need to be included in baseUrl or handled by a proxy if required by the endpoint."));
            }

            this.chatModel = builder.build();
            this.tokenizer = (Tokenizer) this.chatModel;
            model.addLog(AiMessage.from(String.format("[Jaider] Generic OpenAI-compatible model '%s' initialized from %s.", config.genericOpenaiModelName, config.genericOpenaiBaseUrl)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Generic OpenAI-compatible model '%s' from %s. Error: %s. Functionality severely limited.", config.genericOpenaiModelName, config.genericOpenaiBaseUrl, e.getMessage())));
        }
    }
}
