package dumb.integrationhandler.demo;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.data.message.UserMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.InputMismatchException;
import java.util.Scanner;
import java.util.stream.Stream;

public class InteractiveDemo {

    private String languageModelChoice;
    private String apiKey;
    private String projectDescription;
    Path temporaryDirectoryPath; // Made package-private for testing
    private Path generatedFilePath;

    private static final String DEFAULT_GEMINI_MODEL_NAME = "gemini-pro";
    private static final String GENERATED_FILE_NAME = "generated_project_output.txt";
    private static final String OLLAMA_PLACEHOLDER_FILE_NAME = "ollama_placeholder.txt";

    public InteractiveDemo() {
        // Constructor logic, if any
    }

    public boolean setupTemporaryDirectory() { // Made public for testing
        try {
            temporaryDirectoryPath = Files.createTempDirectory("interactiveDemoProject_");
            System.out.println("Setting up temporary directory at: " + temporaryDirectoryPath.toString());
            return true;
        } catch (IOException e) {
            System.err.println("Error: Failed to create temporary directory. " + e.getMessage());
            // e.printStackTrace(); // Optionally print stack trace for debugging
            return false;
        }
    }

    public void cleanupTemporaryDirectory() { // Made public for testing
        if (temporaryDirectoryPath != null && Files.exists(temporaryDirectoryPath)) {
            System.out.println("Cleaning up temporary directory: " + temporaryDirectoryPath.toString() + "...");
            try (Stream<Path> walk = Files.walk(temporaryDirectoryPath)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println("Warning: Failed to delete path during cleanup: " + path + " - " + e.getMessage());
                        }
                    });
                System.out.println("Temporary directory cleaned up successfully.");
            } catch (IOException e) {
                System.err.println("Error: Failed to walk temporary directory for cleanup: " + e.getMessage());
                // e.printStackTrace();
            }
        } else {
            // This is not an error, just a state, so info level or debug.
            // System.out.println("No temporary directory to clean up (path was null or directory did not exist).");
        }
    }

    public boolean verifyProjectGeneration(Path outputFile) { // Made public for testing
        System.out.println("Verifying project generation...");
        if (outputFile == null) {
            System.err.println("Verification failed: Output file path is null (generation might have failed or been skipped).");
            return false;
        }
        if (Files.exists(outputFile)) {
            try {
                long fileSize = Files.size(outputFile);
                if (fileSize > 0) {
                    System.out.println("Verification successful: Project content generated and verified in " + outputFile.toString() + " (Size: " + fileSize + " bytes)");
                    return true;
                } else {
                    System.err.println("Verification failed: Output file is empty. Path: " + outputFile.toString());
                    return false;
                }
            } catch (IOException e) {
                System.err.println("Verification failed: Error accessing file attributes for " + outputFile.toString() + " - " + e.getMessage());
                // e.printStackTrace();
                return false;
            }
        } else {
            System.err.println("Verification failed: Output file not found at path: " + outputFile.toString());
            return false;
        }
    }

    private boolean generateProjectUsingAPI(String lmChoice, String key, String description, Path outputDirectory) {
        System.out.println("Generating project using " + lmChoice + "...");
        generatedFilePath = null;

        if ("Gemini".equalsIgnoreCase(lmChoice)) {
            System.out.println("Initializing Gemini API...");
            String originalApiKeyEnv = System.getenv("GOOGLE_API_KEY"); // Check env for existing key
            String originalApiKeyProp = System.getProperty("GOOGLE_API_KEY");

            try {
                if (key != null && !key.isEmpty()) {
                    System.setProperty("GOOGLE_API_KEY", key); // Temporarily set for this call
                    System.out.println("Using user-provided API key for Gemini call.");
                } else if (originalApiKeyEnv != null && !originalApiKeyEnv.isEmpty()) {
                    System.out.println("Using GOOGLE_API_KEY from environment variable for Gemini call.");
                     // No need to set property if env var is picked up by library
                } else if (originalApiKeyProp != null && !originalApiKeyProp.isEmpty()) {
                     System.out.println("Using GOOGLE_API_KEY from system property for Gemini call.");
                }
                else {
                    System.err.println("Warning: Gemini API key not provided and not found in GOOGLE_API_KEY environment/system property. API call might fail.");
                }

                ChatModel chatModel = GoogleAiGeminiChatModel.builder()
                        .modelName(DEFAULT_GEMINI_MODEL_NAME)
                        .apiKey(key) // Pass user-provided key; SDK might use env/property if this is null/empty
                        .build();

                System.out.println("Calling Gemini API with model " + DEFAULT_GEMINI_MODEL_NAME + "...");
                String prompt = "Generate a simple 'Hello World' style application based on the following description: "
                                + description +
                                ". The output should be the content for a single file. For example, if it's a Python app, provide the content of the .py file.";

                UserMessage userMessage = UserMessage.from(prompt);
                String response = chatModel.generate(userMessage).content().text();
                System.out.println("Gemini response received.");

                generatedFilePath = outputDirectory.resolve(GENERATED_FILE_NAME);
                Files.writeString(generatedFilePath, response, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("Saving generated content to " + generatedFilePath.toString());
                System.out.println("Project generation with Gemini complete.");
                return true;

            } catch (Exception e) {
                System.err.println("Error: During Gemini project generation: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                // e.printStackTrace();
                return false;
            } finally {
                // Restore original GOOGLE_API_KEY system property if it was changed
                if (key != null && !key.isEmpty()) { // Only restore if we set it from user input
                    if (originalApiKeyProp != null) {
                        System.setProperty("GOOGLE_API_KEY", originalApiKeyProp);
                    } else {
                        System.clearProperty("GOOGLE_API_KEY");
                    }
                }
            }
        } else if ("Ollama".equalsIgnoreCase(lmChoice)) {
            System.out.println("Ollama integration is not yet implemented. Creating a placeholder file.");
            generatedFilePath = outputDirectory.resolve(OLLAMA_PLACEHOLDER_FILE_NAME);
            try {
                String placeholderContent = "Ollama generation is not yet implemented for project: " + description + "\nAPI/Config used: " + key;
                Files.writeString(generatedFilePath, placeholderContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("Saving generated content to " + generatedFilePath.toString());
                return true;
            } catch (IOException e) {
                System.err.println("Error: Failed to write Ollama placeholder file: " + e.getMessage());
                // e.printStackTrace();
                return false;
            }
        } else {
            System.err.println("Error: Unknown Language Model selected for API generation: " + lmChoice);
            return false;
        }
    }

    public void runDemo() {
        System.out.println("Starting interactive demo...");
        Scanner scanner = new Scanner(System.in);

        try {
            System.out.println("\nCollecting user input...");
            // LM Selection
            while (true) {
                try {
                    System.out.println("Select Language Model:");
                    System.out.println("1. Gemini");
                    System.out.println("2. Ollama");
                    System.out.print("Enter your choice (1 or 2): ");
                    int lmInputChoice = scanner.nextInt();
                    scanner.nextLine();

                    if (lmInputChoice == 1) {
                        languageModelChoice = "Gemini";
                        System.out.print("Enter Gemini API Key (or leave blank if GOOGLE_API_KEY env var is set): ");
                        apiKey = scanner.nextLine();
                        break;
                    } else if (lmInputChoice == 2) {
                        languageModelChoice = "Ollama";
                        System.out.print("Enter Ollama Configuration (e.g., base URL or model name): ");
                        apiKey = scanner.nextLine();
                        break;
                    } else {
                        System.err.println("Invalid choice. Please enter 1 for Gemini or 2 for Ollama.");
                    }
                } catch (InputMismatchException e) {
                    System.err.println("Invalid input. Please enter a number (1 or 2).");
                    scanner.nextLine(); // Consume the invalid input
                }
            }
            System.out.println("Selected Language Model: " + languageModelChoice);

            // Project Description
            while (true) {
                try {
                    System.out.println("\nSelect Project Source:");
                    System.out.println("1. Spring Boot Pet Clinic (Sample Description)");
                    System.out.println("2. Custom Project Description");
                    System.out.print("Enter your choice (1 or 2): ");
                    int projectChoice = scanner.nextInt();
                    scanner.nextLine();

                    if (projectChoice == 1) {
                        projectDescription = "A simple Spring Boot application to manage pets in a clinic. Include basic CRUD operations for Pets.";
                        break;
                    } else if (projectChoice == 2) {
                        System.out.print("Enter Custom Project Description: ");
                        projectDescription = scanner.nextLine();
                        if (projectDescription.trim().isEmpty()) {
                            System.err.println("Custom project description cannot be empty.");
                        } else {
                            break;
                        }
                    } else {
                        System.err.println("Invalid choice. Please enter 1 or 2.");
                    }
                } catch (InputMismatchException e) {
                    System.err.println("Invalid input. Please enter a number (1 or 2).");
                    scanner.nextLine(); // Consume the invalid input
                }
            }
            System.out.println("Project Description: " + projectDescription);

            System.out.println("\n--- Configuration Summary ---");
            System.out.println("Language Model: " + languageModelChoice);
            System.out.println("API Key/Config: " + (apiKey.isEmpty() ? "[Not Provided/Using Environment]" : "[Provided]"));
            System.out.println("Project Description: " + projectDescription);
            System.out.println("-----------------------------");

            if (!setupTemporaryDirectory()) {
                System.err.println("Exiting demo due to failure in setting up temporary directory.");
                return; // Exit runDemo early
            }

            boolean generationSucceeded = generateProjectUsingAPI(languageModelChoice, apiKey, projectDescription, temporaryDirectoryPath);

            if (generationSucceeded && generatedFilePath != null) {
                verifyProjectGeneration(generatedFilePath);
            } else if (generationSucceeded && generatedFilePath == null) {
                 System.err.println("Warning: Generation reported success, but no output file path was set.");
            } else {
                System.err.println("Project generation failed or was skipped. Verification not performed.");
            }

        } catch (Exception e) { // Catch-all for unexpected errors in runDemo's main flow
            System.err.println("An unexpected error occurred during the demo: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace(); // For dev debugging
        } finally {
            scanner.close();
            cleanupTemporaryDirectory();
            System.out.println("\nInteractive demo finished.");
        }
    }

    public static void main(String[] args) {
        InteractiveDemo demo = new InteractiveDemo();
        demo.runDemo();
    }
}
