package dumb.jaider.demo;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

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

    private static final String DEFAULT_GEMINI_MODEL_NAME = "gemini-2.5-flash-preview-05-20";
    private static final String GENERATED_FILE_NAME = "generated_project_output.txt";
    private static final String OLLAMA_PLACEHOLDER_FILE_NAME = "ollama_placeholder.txt";
    boolean cleanup = false;

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
                    System.out.println("Verification successful: Project content generated and verified in " + outputFile + " (Size: " + fileSize + " bytes)");
                    return true;
                } else {
                    System.err.println("Verification failed: Output file is empty. Path: " + outputFile);
                    return false;
                }
            } catch (IOException e) {
                System.err.println("Verification failed: Error accessing file attributes for " + outputFile + " - " + e.getMessage());
                // e.printStackTrace();
                return false;
            }
        } else {
            System.err.println("Verification failed: Output file not found at path: " + outputFile);
            return false;
        }
    }

    private boolean generateProjectUsingAPI(String lmChoice, String key, String description, Path outputDirectory) {
        System.out.println("Generating project using " + lmChoice + "...");
        generatedFilePath = null;

        if ("Gemini".equalsIgnoreCase(lmChoice)) {
            System.out.println("Initializing Gemini API...");

            try {
                if (key != null && !key.isEmpty()) {
                    System.out.println("Attempting to use user-provided API key for Gemini call.");
                } else {
                    System.out.println("No API key provided by user. Attempting to use credentials from environment (e.g., GOOGLE_API_KEY or Application Default Credentials).");
                    // The warning about potential failure if none are found can remain or be part of the catch block.
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
                String response = chatModel.chat(userMessage).aiMessage().text();
                System.out.println("Gemini response received.");

                generatedFilePath = outputDirectory.resolve(GENERATED_FILE_NAME);
                Files.writeString(generatedFilePath, response, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("Saving generated content to " + generatedFilePath);
                System.out.println("Project generation with Gemini complete.");
                return true;

            } catch (Exception e) {
                System.err.println("Error: During Gemini project generation: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                // e.printStackTrace();
                return false;
            }
            // No finally block needed for API key restoration anymore
        } else if ("Ollama".equalsIgnoreCase(lmChoice)) {
            System.out.println("Full Ollama integration is available in the main Jaider application. This demo will create a placeholder file for Ollama selection.");
            generatedFilePath = outputDirectory.resolve(OLLAMA_PLACEHOLDER_FILE_NAME);
            try {
                String placeholderContent = "Ollama integration is not demonstrated in this simplified demo. Full Ollama support is available in the main Jaider application.\nProject description for demo: " + description + "\nConfig entered in demo: " + key;
                Files.writeString(generatedFilePath, placeholderContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("Saving generated content to " + generatedFilePath);
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
        System.out.println("\n*** Disclaimer ***");
        System.out.println("This is a simplified interactive demo showcasing the basic capability of generating a single file using a Large Language Model (LLM).");
        System.out.println("The full Jaider application provides a much richer and more powerful AI-assisted development experience,");
        System.out.println("including features like applying changes via diffs, running validation commands, working with your project's full context, and more.");
        System.out.println("For the complete feature set, please run the main Jaider application.");
        System.out.println("******************\n");

        try (Scanner scanner = new Scanner(System.in)) {
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
            } else if (generationSucceeded) {
                System.err.println("Warning: Generation reported success, but no output file path was set.");
            } else {
                System.err.println("Project generation failed or was skipped. Verification not performed.");
            }

        } catch (Exception e) { // Catch-all for unexpected errors in runDemo's main flow
            System.err.println("An unexpected error occurred during the demo: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace(); // For dev debugging
        } finally {
            if (cleanup)
                cleanupTemporaryDirectory();
            System.out.println("\n*** Demo Scope Reminder ***");
            System.out.println("This simplified demo focused on single-file generation.");
            System.out.println("To explore Jaider's full capabilities for AI-assisted coding, please try the main application.");
            System.out.println("*************************");
            System.out.println("\nInteractive demo finished.");
        }
    }

    public static void main(String[] args) {
        InteractiveDemo demo = new InteractiveDemo();
        demo.runDemo();
    }
}
