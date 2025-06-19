package dumb.jaider.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dumb.jaider.app.Jaider;
import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LlmProviderFactory {
    private static final Logger logger = LoggerFactory.getLogger(LlmProviderFactory.class);
    private final Config config;
    private final JaiderModel model;

    private ChatModel chatModel; // Changed from ChatModel
    //    private Tokenizer tokenizer;
    private EmbeddingModel embeddingModel;

    public LlmProviderFactory(Config config, JaiderModel model) {
        this.config = config;
        this.model = model;
    }

    public ChatModel createChatModel() { // Changed from ChatModel
        if ("ollama".equalsIgnoreCase(config.getLlm())) {
            setupOllama();
        } else if ("genericOpenai".equalsIgnoreCase(config.getLlm())) {
            setupGenericOpenAI();
        } else if ("openai".equalsIgnoreCase(config.getLlm())) {
            setupOpenAI();
        } else if ("gemini".equalsIgnoreCase(config.getLlm())) {
            setupGemini();
        } else {
            model.addLog(AiMessage.from(String.format("[Jaider] WARNING: Unknown llmProvider '%s' in config. Defaulting to Ollama.", config.getLlm())));
            setupOllama(); // Defaulting to Ollama
        }

        if (this.chatModel == null) {
            throw new dumb.jaider.app.exceptions.ChatModelInitializationException("Failed to initialize any chat model provider after trying all configured options.");
        }
        return this.chatModel;
    }

//    public Tokenizer createTokenizer() {
//        if (this.tokenizer == null) {
//            if (this.chatModel == null) {
//                createChatModel();
//            }
////            if (this.chatModel instanceof Tokenizer) {
//                this.tokenizer = (Tokenizer) this.chatModel;

    ////            } else { // ADDED FALLBACK
    ////                model.addLog(AiMessage.from("[Jaider] WARNING: Chat model is not a Tokenizer or is null after createChatModel(). Using a NoOpTokenizer."));
    ////                this.tokenizer = new NoOpTokenizer();
    ////            }
//        }
//        if (this.tokenizer == null) {
//            // This path should ideally not be reached if NoOpTokenizer instantiation is successful.
//            throw new dumb.jaider.app.exceptions.TokenizerInitializationException("Failed to initialize tokenizer even after attempting fallback to NoOpTokenizer.");
//        }
//        return this.tokenizer;
//    }

    public EmbeddingModel createEmbeddingModel() {
        if (this.embeddingModel == null) {
            var provider = config.getLlm(); // Safe to call config.getLlm() here as it's a simple getter
            if ("ollama".equalsIgnoreCase(provider)) {
                setupOllamaEmbeddingModel();
            } else if ("genericOpenai".equalsIgnoreCase(provider)) {
                setupGenericOpenAIEmbeddingModel();
            } else if ("openai".equalsIgnoreCase(provider)) {
                setupOpenAIEmbeddingModel();
            } else if ("gemini".equalsIgnoreCase(provider)) {
                setupGeminiEmbeddingModel();
            } else {
                model.addLog(AiMessage.from(String.format("[Jaider] WARNING: Unknown llmProvider '%s' for embedding model. No embedding model initialized.", provider)));
                 this.embeddingModel = new dumb.jaider.llm.NoOpEmbeddingModel();
            }
        }
        if (this.embeddingModel == null) { // Ensure fallback if specific setup failed silently
             model.addLog(AiMessage.from("[Jaider] WARNING: Embedding model was still null after setup attempts. Defaulting to NoOpEmbeddingModel."));
             this.embeddingModel = new dumb.jaider.llm.NoOpEmbeddingModel();
        }
        return this.embeddingModel;
    }

    private void setupOllamaEmbeddingModel() {
        var baseUrl = "UNKNOWN";
        var modelName = "UNKNOWN";
        try {
            baseUrl = config.getOllamaBaseUrl();
            modelName = config.getOllamaModelName();
            this.embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .build();
            if (this.embeddingModel == null) {
                model.addLog(AiMessage.from(String.format("[Jaider] WARNING: Ollama Embedding model builder returned null for '%s' from %s. Falling back to NoOpEmbeddingModel.", modelName, baseUrl)));
                this.embeddingModel = new dumb.jaider.llm.NoOpEmbeddingModel();
            } else {
                model.addLog(AiMessage.from(String.format("[Jaider] Ollama Embedding model '%s' initialized successfully from %s.", modelName, baseUrl)));
            }
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Ollama Embedding model '%s' from %s. Error: %s. Falling back to local NoOpEmbeddingModel.", modelName, baseUrl, e.getMessage())));
            this.embeddingModel = new dumb.jaider.llm.NoOpEmbeddingModel();
        }
    }

    private void setupGenericOpenAIEmbeddingModel() {
        String apiKey = null;
        var embeddingModelName = "UNKNOWN";
        var baseUrl = "UNKNOWN";
        try {
            apiKey = config.getGenericOpenaiApiKey();
            embeddingModelName = config.getGenericOpenaiEmbeddingModelName();
            baseUrl = config.getGenericOpenaiBaseUrl();

            if (apiKey == null || apiKey.isEmpty()) {
                model.addLog(AiMessage.from("[Jaider] INFO: Generic OpenAI API key is not configured. This might be required for the embedding endpoint."));
            }

            this.embeddingModel = OpenAiEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .modelName(embeddingModelName)
                    .build();
            if (this.embeddingModel == null) {
                model.addLog(AiMessage.from(String.format("[Jaider] WARNING: Generic OpenAI-compatible Embedding model builder returned null for '%s' from %s. Falling back to NoOpEmbeddingModel.", embeddingModelName, baseUrl)));
                this.embeddingModel = new dumb.jaider.llm.NoOpEmbeddingModel();
            } else {
                model.addLog(AiMessage.from(String.format("[Jaider] Generic OpenAI-compatible Embedding model '%s' (using OpenAiEmbeddingModel client) attempted initialization from %s.", embeddingModelName, baseUrl)));
            }
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Generic OpenAI-compatible Embedding model '%s' from %s using OpenAiEmbeddingModel client. Error: %s. Falling back to NoOpEmbeddingModel.", embeddingModelName, baseUrl, e.getMessage())));
            this.embeddingModel = new dumb.jaider.llm.NoOpEmbeddingModel();
        }
    }

    private void setupOllama() {
        var baseUrl = "UNKNOWN";
        var modelName = "UNKNOWN";
        try {
            baseUrl = config.getOllamaBaseUrl();
            modelName = config.getOllamaModelName();
            this.chatModel = OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .build();
//            if (this.chatModel instanceof Tokenizer) {
//                this.tokenizer = (Tokenizer) this.chatModel;
//            }
            model.addLog(AiMessage.from(String.format("[Jaider] Ollama model '%s' initialized successfully from %s.", modelName, baseUrl)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Ollama model '%s' from %s. Error: %s. Jaider's functionality will be severely limited. Check Ollama server and config.", modelName, baseUrl, e.getMessage())));
        }
    }

    private void setupGenericOpenAI() {
        String apiKey = null;
        var baseUrl = "UNKNOWN";
        var modelName = "UNKNOWN";
        try {
            apiKey = config.getGenericOpenaiApiKey();
            baseUrl = config.getGenericOpenaiBaseUrl();
            modelName = config.getGenericOpenaiModelName();

            if (apiKey == null || apiKey.isEmpty()) {
                // model.addLog(AiMessage.from("[Jaider] INFO: Generic OpenAI API key is not configured in .jaider.json or related environment variables. The endpoint might require an API key."));
                LlmProviderFactory.logger.error("Generic OpenAI API key is not configured for provider at {}. Please set GENERIC_OPENAI_API_KEY or configure in .jaider.json if required by the provider.", baseUrl);
                throw new dumb.jaider.app.exceptions.ChatModelInitializationException("Generic OpenAI API key is missing for provider at " + baseUrl + ". Configure if required.");
            }

            this.chatModel = OpenAiChatModel.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .logRequests(true)
                    .logResponses(true)
                    .build();

//            if (this.chatModel instanceof Tokenizer) {
//                this.tokenizer = (Tokenizer) this.chatModel;
//            }
            model.addLog(AiMessage.from(String.format("[Jaider] Generic OpenAI-compatible model '%s' (using OpenAiChatModel client) initialized from %s.", modelName, baseUrl)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Generic OpenAI-compatible model '%s' from %s using OpenAiChatModel client. Error: %s. Functionality severely limited.", modelName, baseUrl, e.getMessage())));
        }
    }

    private void setupGemini() {
        var modelName = "UNKNOWN";
        try {
            modelName = config.getGeminiModelName();

            this.chatModel = GoogleAiGeminiChatModel.builder()
                    .modelName(modelName)
                    .build();

            model.addLog(AiMessage.from(String.format("[Jaider] Vertex AI Gemini model '%s' initialized successfully.", modelName)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Vertex AI Gemini model '%s'. Error: %s. Functionality severely limited.", modelName, e.getMessage())));
        }
    }

    private void setupGeminiEmbeddingModel() {
        var embeddingModelName = "UNKNOWN";
        try {
            embeddingModelName = config.getGeminiEmbeddingModelName();

            if (embeddingModelName == null || embeddingModelName.trim().isEmpty()){
                embeddingModelName = "textembedding-gecko";
                model.addLog(AiMessage.from(String.format("[Jaider] Gemini embedding model name not specified in config, defaulting to '%s'.", embeddingModelName)));
            }

            this.embeddingModel = GoogleAiEmbeddingModel.builder()
                    .modelName(embeddingModelName)
                    .build();
            if (this.embeddingModel == null) {
                model.addLog(AiMessage.from(String.format("[Jaider] WARNING: Vertex AI Gemini Embedding model builder returned null for '%s'. Falling back to NoOpEmbeddingModel.", embeddingModelName)));
                this.embeddingModel = new dumb.jaider.llm.NoOpEmbeddingModel();
            } else {
                model.addLog(AiMessage.from(String.format("[Jaider] Vertex AI Gemini Embedding model '%s' initialized successfully.", embeddingModelName)));
            }
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Vertex AI Gemini Embedding model '%s'. Error: %s. Falling back to NoOpEmbeddingModel.", embeddingModelName, e.getMessage())));
            this.embeddingModel = new dumb.jaider.llm.NoOpEmbeddingModel();
        }
    }

    private void setupOpenAI() {
        String apiKey = null;
        var modelName = "UNKNOWN";
        try {
            apiKey = config.getOpenaiApiKey();
            modelName = config.getOpenaiModelName();

            if (apiKey == null || apiKey.trim().isEmpty()) {
                // model.addLog(AiMessage.from("[Jaider] INFO: OpenAI API key is not configured. Langchain4j might attempt to find it in environment variables or system properties."));
                LlmProviderFactory.logger.error("OpenAI API key is not configured. Please set OPENAI_API_KEY environment variable or add 'openai' to 'apiKeys' in .jaider.json.");
                throw new dumb.jaider.app.exceptions.ChatModelInitializationException("OpenAI API key is missing. Set OPENAI_API_KEY env var or configure in .jaider.json.");
            }

            this.chatModel = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .logRequests(true)
                    .logResponses(true)
                    .build();

            model.addLog(AiMessage.from(String.format("[Jaider] OpenAI Chat model '%s' initialized successfully.", modelName)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize OpenAI Chat model '%s'. Error: %s. Jaider's functionality might be limited.", modelName, e.getMessage())));
        }
    }

    private void setupOpenAIEmbeddingModel() {
        String apiKey = null;
        var embeddingModelName = "text-embedding-ada-002"; // Hardcoded default
        try {
            apiKey = config.getOpenaiApiKey();
            // Using hardcoded default for embeddingModelName as getOpenaiEmbeddingModelName() doesn't exist on Config
            // If a new config option were added for this, it would be fetched here.
            // model.addLog(AiMessage.from(String.format("[Jaider] OpenAI Embedding model name using default: '%s'.", embeddingModelName)));

            if (apiKey == null || apiKey.isEmpty()) {
                model.addLog(AiMessage.from("[Jaider] INFO: OpenAI API key is not configured for embedding model. Langchain4j might attempt to find it elsewhere."));
            }

            this.embeddingModel = OpenAiEmbeddingModel.builder()
                    .apiKey(apiKey)
                    .modelName(embeddingModelName)
                    .build();
            if (this.embeddingModel == null) {
                model.addLog(AiMessage.from(String.format("[Jaider] WARNING: OpenAI Embedding model builder returned null for '%s'. Falling back to NoOpEmbeddingModel.", embeddingModelName)));
                this.embeddingModel = new dumb.jaider.llm.NoOpEmbeddingModel();
            } else {
                model.addLog(AiMessage.from(String.format("[Jaider] OpenAI Embedding model '%s' initialized successfully.", embeddingModelName)));
            }
        } catch (Exception e) {
            // Log with embeddingModelName which is the default here.
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize OpenAI Embedding model '%s'. Error: %s. Falling back to NoOpEmbeddingModel.", embeddingModelName, e.getMessage())));
            this.embeddingModel = new dumb.jaider.llm.NoOpEmbeddingModel();
        }
    }
}
