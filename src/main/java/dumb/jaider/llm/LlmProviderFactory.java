package dumb.jaider.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.vertexai.VertexAiChatModel;
import dev.langchain4j.model.vertexai.VertexAiEmbeddingModel; // Added import
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
            // model.addLog(AiMessage.from("[Jaider] OpenAI provider selected but setupOpenAI() is currently commented out. No model initialized."));
            setupOpenAI();
        } else if ("gemini".equalsIgnoreCase(config.llm)) {
            setupGemini();
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
                setupOpenAIEmbeddingModel();
            } else if ("gemini".equalsIgnoreCase(config.llm)) {
                setupGeminiEmbeddingModel();
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
        try {
            String apiKey = config.getGenericOpenaiApiKey(); // Reuse the same API key
             if (apiKey == null || apiKey.isEmpty()) {
                model.addLog(AiMessage.from("[Jaider] INFO: Generic OpenAI API key is not configured. This might be required for the embedding endpoint."));
            }

            // Using the new config field for generic embedding model name
            String embeddingModelName = config.getGenericOpenaiEmbeddingModelName();

            this.embeddingModel = OpenAiEmbeddingModel.builder()
                    .baseUrl(config.genericOpenaiBaseUrl) // Assume embedding endpoint is relative to the base URL
                    .apiKey(apiKey)
                    .modelName(embeddingModelName)
                    // .logRequests(true) // Optional
                    // .logResponses(true) // Optional
                    .build();
            model.addLog(AiMessage.from(String.format("[Jaider] Generic OpenAI-compatible Embedding model '%s' (using OpenAiEmbeddingModel client) attempted initialization from %s.", embeddingModelName, config.genericOpenaiBaseUrl)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Generic OpenAI-compatible Embedding model '%s' from %s using OpenAiEmbeddingModel client. Error: %s. Falling back to NoOpEmbeddingModel.", config.getGenericOpenaiEmbeddingModelName(), config.genericOpenaiBaseUrl, e.getMessage())));
            this.embeddingModel = new dumb.jaider.llm.NoOpEmbeddingModel();
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
            String apiKey = config.getGenericOpenaiApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                model.addLog(AiMessage.from("[Jaider] INFO: Generic OpenAI API key is not configured in .jaider.json or related environment variables. The endpoint might require an API key."));
            }

            this.chatModel = OpenAiChatModel.builder()
                    .baseUrl(config.genericOpenaiBaseUrl)
                    .apiKey(apiKey) // OpenAiChatModel should use this for Bearer token
                    .modelName(config.genericOpenaiModelName)
                    .logRequests(true) // Optional: for debugging
                    .logResponses(true) // Optional: for debugging
                    .build();

            if (this.chatModel instanceof Tokenizer) {
                this.tokenizer = (Tokenizer) this.chatModel;
            }
            model.addLog(AiMessage.from(String.format("[Jaider] Generic OpenAI-compatible model '%s' (using OpenAiChatModel client) initialized from %s.", config.genericOpenaiModelName, config.genericOpenaiBaseUrl)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Generic OpenAI-compatible model '%s' from %s using OpenAiChatModel client. Error: %s. Functionality severely limited.", config.genericOpenaiModelName, config.genericOpenaiBaseUrl, e.getMessage())));
            // chatModel will remain null or previous value if exception occurs
        }
    }

    private void setupGemini() {
        try {
            String project = System.getenv("GOOGLE_CLOUD_PROJECT");
            String location = System.getenv("GOOGLE_CLOUD_LOCATION");

            if (project == null || project.trim().isEmpty()) {
                throw new IllegalArgumentException("GOOGLE_CLOUD_PROJECT environment variable is not set.");
            }
            if (location == null || location.trim().isEmpty()) {
                throw new IllegalArgumentException("GOOGLE_CLOUD_LOCATION environment variable is not set.");
            }

            this.chatModel = VertexAiChatModel.builder()
                    .project(project)
                    .location(location)
                    .modelName(config.getGeminiModelName())
                    // .temperature(0.7f) // Example: Add other configurations as needed
                    // .maxOutputTokens(1024) // Example
                    .build();

            if (this.chatModel instanceof Tokenizer) {
                this.tokenizer = (Tokenizer) this.chatModel;
            }
            model.addLog(AiMessage.from(String.format("[Jaider] Vertex AI Gemini model '%s' (project: %s, location: %s) initialized successfully.", config.getGeminiModelName(), project, location)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Vertex AI Gemini model '%s'. Error: %s. Functionality severely limited.", config.getGeminiModelName(), e.getMessage())));
            // Consider a fallback chat model if necessary
        }
    }

    private void setupGeminiEmbeddingModel() {
        try {
            String project = System.getenv("GOOGLE_CLOUD_PROJECT");
            String location = System.getenv("GOOGLE_CLOUD_LOCATION");

            if (project == null || project.trim().isEmpty()) {
                throw new IllegalArgumentException("GOOGLE_CLOUD_PROJECT environment variable is not set for Gemini embedding model.");
            }
            if (location == null || location.trim().isEmpty()) {
                throw new IllegalArgumentException("GOOGLE_CLOUD_LOCATION environment variable is not set for Gemini embedding model.");
            }

            String embeddingModelName = config.getGeminiEmbeddingModelName();
            if (embeddingModelName == null || embeddingModelName.trim().isEmpty()){
                embeddingModelName = "textembedding-gecko";
                model.addLog(AiMessage.from(String.format("[Jaider] Gemini embedding model name not specified in config, defaulting to '%s'.", embeddingModelName)));
            }

            this.embeddingModel = VertexAiEmbeddingModel.builder()
                    .project(project)
                    .location(location)
                    .modelName(embeddingModelName)
                    // .maxRetries(3) // Example optional parameter
                    .build();
            model.addLog(AiMessage.from(String.format("[Jaider] Vertex AI Gemini Embedding model '%s' (project: %s, location: %s) initialized successfully.", embeddingModelName, project, location)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Vertex AI Gemini Embedding model. Error: %s. Falling back to NoOpEmbeddingModel.", e.getMessage())));
            this.embeddingModel = new dumb.jaider.llm.NoOpEmbeddingModel();
        }
    }

    private void setupOpenAI() {
        try {
            String apiKey = config.getOpenaiApiKey();
            String modelName = config.getOpenaiModelName(); // Assuming this method exists in Config.java

            if (apiKey == null || apiKey.trim().isEmpty()) {
                model.addLog(AiMessage.from("[Jaider] INFO: OpenAI API key is not configured. Langchain4j might attempt to find it in environment variables or system properties."));
            }

            this.chatModel = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .logRequests(true)
                    .logResponses(true)
                    .build();

            if (this.chatModel instanceof Tokenizer) {
                this.tokenizer = (Tokenizer) this.chatModel;
            }
            model.addLog(AiMessage.from(String.format("[Jaider] OpenAI Chat model '%s' initialized successfully.", modelName)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize OpenAI Chat model. Error: %s. Jaider's functionality might be limited.", e.getMessage())));
            // Optionally, fallback to a NoOp model or handle differently
        }
    }

    private void setupOpenAIEmbeddingModel() {
        try {
            String apiKey = config.getOpenaiApiKey();
            String embeddingModelName = "text-embedding-ada-002"; // Default, can be made configurable

            if (apiKey == null || apiKey.trim().isEmpty()) {
                model.addLog(AiMessage.from("[Jaider] INFO: OpenAI API key is not configured for embedding model. Langchain4j might attempt to find it elsewhere."));
            }

            this.embeddingModel = OpenAiEmbeddingModel.builder()
                    .apiKey(apiKey)
                    .modelName(embeddingModelName)
                    .build();
            model.addLog(AiMessage.from(String.format("[Jaider] OpenAI Embedding model '%s' initialized successfully.", embeddingModelName)));
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize OpenAI Embedding model. Error: %s. Falling back to NoOpEmbeddingModel.", e.getMessage())));
            this.embeddingModel = new dumb.jaider.llm.NoOpEmbeddingModel();
        }
    }
}
