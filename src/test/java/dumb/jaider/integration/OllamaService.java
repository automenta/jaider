package dumb.jaider.integration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OllamaService {
    private static final Logger logger = LoggerFactory.getLogger(OllamaService.class);
    private final TestConfig testConfig;
    public ChatModel chatModel; // Made public for the test check in BeforeAll

    public OllamaService(TestConfig testConfig) {
        this.testConfig = testConfig;
        initializeChatModel();
    }

    private void initializeChatModel() {
        try {
            this.chatModel = OllamaChatModel.builder()
                    .baseUrl(testConfig.getOllamaBaseUrl())
                    .modelName(testConfig.getOllamaModelName())
                    .timeout(java.time.Duration.ofSeconds(120)) // Increased timeout
                    .build();
            logger.info("OllamaChatModel initialized successfully with base URL: {} and model: {}", testConfig.getOllamaBaseUrl(), testConfig.getOllamaModelName());
        } catch (Exception e) {
            logger.error("Failed to initialize OllamaChatModel", e);
            // In a real test, might throw a runtime exception to fail fast
            // For now, log and proceed, tests will fail if chatModel is null
        }
    }

    public String generateCode(String description) {
        if (chatModel == null) {
            logger.error("Ollama chat model is not initialized. Cannot generate code.");
            return ""; // Or throw exception
        }
        logger.info("Generating code for description: '{}'", description);
        // Actual prompt might need to be more specific for Java Swing
        String prompt = "Generate a complete, runnable, single-file Java Swing application for: " + description + ". The code should be self-contained in one file. Ensure all necessary imports are included. The main class should be launchable.";
        try {
            String response = chatModel.chat(prompt);
            logger.info("Code generation response received from Ollama.");
            logger.debug("Ollama response: {}", response);
            return response;
        } catch (Exception e) {
            logger.error("Error during Ollama code generation: {}", e.getMessage(), e);
            return ""; // Or throw
        }
    }

    public String enhanceCode(String existingCode, String enhancementDescription) {
        if (chatModel == null) {
            logger.error("Ollama chat model is not initialized. Cannot enhance code.");
            return "";
        }
        logger.info("Enhancing code with description: '{}'", enhancementDescription);
        String prompt = "Given the following Java code:\n```java\n" + existingCode + "\n```\nEnhance it with the following features: " + enhancementDescription + ". Provide the complete updated Java code. Ensure all necessary imports are included.";
        try {
            String response = chatModel.chat(prompt);
            logger.info("Code enhancement response received from Ollama.");
            logger.debug("Ollama enhancement response: {}", response);
            return response;
        } catch (Exception e) {
            logger.error("Error during Ollama code enhancement: {}", e.getMessage(), e);
            return "";
        }
    }

    public String askQuestion(String codeContext, String question) {
        if (chatModel == null) {
            logger.error("Ollama chat model is not initialized. Cannot ask question.");
            return "";
        }
        logger.info("Asking question: '{}'", question);
        String prompt = "Regarding the following Java code:\n```java\n" + codeContext + "\n```\nQuestion: " + question;
        try {
            String response = chatModel.chat(prompt);
            logger.info("Answer received from Ollama.");
            logger.debug("Ollama answer: {}", response);
            return response;
        } catch (Exception e) {
            logger.error("Error during Ollama question asking: {}", e.getMessage(), e);
            return "";
        }
    }
}
