package dumb.jaider.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.google.vertexai.VertexAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
// import dev.langchain4j.model.openai.OpenAiChatModel; // For when setupOpenAI is uncommented
// import dev.langchain4j.model.openai.OpenAiEmbeddingModel; // For when setupOpenAI is uncommented
// import dev.langchain4j.model.openai.OpenAiTokenizer; // For when setupOpenAI is uncommented
import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class LlmProviderFactory {
    private final Config config;
    private final JaiderModel model; // For logging during setup

    private ChatLanguageModel chatModel;
    private Tokenizer tokenizer;
    private EmbeddingModel embeddingModel; // Currently only set by the commented out setupOpenAI

    public LlmProviderFactory(Config config, JaiderModel model) {
        this.config = config;
        this.model = model;
    }

    public ChatLanguageModel createChatModel() {
        if ("ollama".equalsIgnoreCase(config.llmProvider)) {
            setupOllama();
        } else if ("genericOpenai".equalsIgnoreCase(config.llmProvider)) {
            setupGenericOpenAI();
        } else if ("gemini".equalsIgnoreCase(config.llmProvider)) {
            setupGemini();
        } else if ("openai".equalsIgnoreCase(config.llmProvider)) {
            // setupOpenAI(); // This is still commented out
            model.addLog(AiMessage.from("[Jaider] OpenAI provider selected but setupOpenAI() is currently commented out. No model initialized."));
            initializeFallbackTokenizer(); // Ensure tokenizer is not null if chat model setup fails
        } else {
            model.addLog(AiMessage.from(String.format("[Jaider] WARNING: Unknown llmProvider '%s' in config. Defaulting to Ollama.", config.llmProvider)));
            setupOllama(); // Default fallback
        }
        // Ensure tokenizer is created/updated after chatModel is potentially set
        if (this.tokenizer == null && this.chatModel instanceof Tokenizer) {
            this.tokenizer = (Tokenizer) this.chatModel;
        } else if (this.tokenizer == null) {
            initializeFallbackTokenizer();
        }
        return this.chatModel;
    }

    public Tokenizer createTokenizer() {
        // Tokenizer is typically initialized along with the chat model
        // or via initializeFallbackTokenizer if model setup fails or model isn't a tokenizer.
        if (this.tokenizer == null) {
            // Attempt to create chat model if not already done, which should also set tokenizer
            if (this.chatModel == null) createChatModel();
            // If still null, use fallback
            if (this.tokenizer == null) initializeFallbackTokenizer();
        }
        return this.tokenizer;
    }

    public EmbeddingModel createEmbeddingModel() {
        // Currently, only the commented-out setupOpenAI initializes an embedding model.
        // If other providers are added that support it, this method should be updated.
        if ("openai".equalsIgnoreCase(config.llmProvider)) {
            // If setupOpenAI were active, it would set this.embeddingModel
            // model.addLog(AiMessage.from("[Jaider] OpenAI provider selected. Embedding model would be initialized if setupOpenAI was active."));
        }
        // Return whatever embeddingModel might have been set (e.g., by a future setupOpenAI)
        // or null if no provider set one.
        return this.embeddingModel;
    }

    private void setupOllama() {
        try {
            this.chatModel = OllamaChatModel.builder()
                    .baseUrl(config.ollamaBaseUrl)
                    .modelName(config.ollamaModelName)
                    .build();
            this.tokenizer = (Tokenizer) this.chatModel; // OllamaChatModel implements Tokenizer
            model.addLog(AiMessage.from(String.format("[Jaider] Ollama model '%s' initialized successfully from %s.", config.ollamaModelName, config.ollamaBaseUrl)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Ollama model '%s' from %s. Error: %s. Jaider's functionality will be severely limited. Check Ollama server and config.", config.ollamaModelName, config.ollamaBaseUrl, e.getMessage())));
            initializeFallbackTokenizer();
        }
    }

    private void setupGenericOpenAI() {
        try {
            var builder = OllamaChatModel.builder() // Using OllamaChatModel for generic OpenAI compatibility
                    .baseUrl(config.genericOpenaiBaseUrl)
                    .modelName(config.genericOpenaiModelName);

            if (config.genericOpenaiApiKey != null && !config.genericOpenaiApiKey.isEmpty()) {
                model.addLog(AiMessage.from("[Jaider] INFO: genericOpenaiApiKey is set in config, but the current OllamaChatModel builder may not use it for Bearer token authentication. API key might need to be included in baseUrl or handled by a proxy if required by the endpoint."));
            }

            this.chatModel = builder.build();
            this.tokenizer = (Tokenizer) this.chatModel; // Assuming compatible model also provides Tokenizer
            model.addLog(AiMessage.from(String.format("[Jaider] Generic OpenAI-compatible model '%s' initialized from %s.", config.genericOpenaiModelName, config.genericOpenaiBaseUrl)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Generic OpenAI-compatible model '%s' from %s. Error: %s. Functionality severely limited.", config.genericOpenaiModelName, config.genericOpenaiBaseUrl, e.getMessage())));
            initializeFallbackTokenizer();
        }
    }

    private void setupGemini() {
        String apiKey = config.geminiApiKey;
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("YOUR_")) {
            model.addLog(AiMessage.from("[Jaider] WARNING: Gemini API key not found or is a placeholder in config. Gemini provider will not be available."));
            initializeFallbackTokenizer();
            return;
        }
        try {
            this.chatModel = VertexAiGeminiChatModel.builder()
                    .project(System.getenv("GOOGLE_CLOUD_PROJECT"))
                    .location(System.getenv("GOOGLE_CLOUD_LOCATION"))
                    .modelName(config.geminiModelName)
                    .build();
            this.tokenizer = (Tokenizer) this.chatModel; // VertexAiGeminiChatModel implements Tokenizer
            model.addLog(AiMessage.from(String.format("[Jaider] Gemini model '%s' initialized.", config.geminiModelName)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Gemini model '%s'. Error: %s. Check API key, project/location settings, and GCP authentication.", config.geminiModelName, e.getMessage())));
            initializeFallbackTokenizer();
        }
    }

    private void initializeFallbackTokenizer() {
        if (this.tokenizer == null) {
            this.tokenizer = new Tokenizer() {
                @Override public int estimateTokenCount(String text) { return text.length() / 4; }
                @Override public List<Integer> encode(String text) { return Collections.emptyList(); }
                @Override public List<Integer> encode(String text, int maxTokens) { return Collections.emptyList(); }
                @Override public String decode(List<Integer> tokens) { return ""; }
                @Override public int estimateTokenCountInMessages(Collection<ChatMessage> messages) {
                    return messages.stream().mapToInt(message -> estimateTokenCount(message.text())).sum();
                }
            };
            model.addLog(AiMessage.from("[Jaider] INFO: Initialized a fallback tokenizer. Token counts will be rough estimates."));
        }
    }

    //BROKEN: complains about TokenCountEstimator missing
    // private void setupOpenAI() {
    //     var apiKey = config.getApiKey("openai"); // Assuming getApiKey can take "openai"
    //     if (apiKey == null || apiKey.contains("YOUR_")) {
    //         model.addLog(AiMessage.from("[Jaider] WARNING: OpenAI API key not found. Functionality will be limited. Use /edit-config to set it."));
    //         apiKey = "DUMMY_KEY"; // Needs to be valid format if not a real key for some builders
    //     }
    //
    //     this.chatModel = OpenAiChatModel.builder().apiKey(apiKey).build();
    //     this.embeddingModel = OpenAiEmbeddingModel.builder().apiKey(apiKey).build();
    //     // OpenAiTokenizer is a specific class, ensure it's available and compatible.
    //     // Or, rely on the chatModel if it implements Tokenizer, which OpenAiChatModel does.
    //     // this.tokenizer = new OpenAiTokenizer(this.config.openaiModelName); // modelName might be needed by tokenizer
    //     this.tokenizer = (Tokenizer) this.chatModel;
    //     model.addLog(AiMessage.from("[Jaider] OpenAI models (Chat, Embedding, Tokenizer) would be initialized here if uncommented."));
    // }
}
