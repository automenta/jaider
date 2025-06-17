package dumb.integrationhandler.demo;

import dev.langchain4j.model.chat.ChatLanguageModel; // Corrected import
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.data.message.UserMessage;
// import dumb.jaider.config.Config; // Removed
// import dumb.jaider.model.JaiderModel; // Removed
// import dumb.jaider.agents.AskAgent; // Removed
// import dev.langchain4j.data.message.AiMessage; // Removed
import java.util.Collections; // For Collections.singletonList - RETAINED
// import dumb.jaider.tools.DiffApplier; // Removed
// import com.github.difflib.DiffUtils; // Removed
// import com.github.difflib.patch.Patch; // Removed
// import com.github.difflib.text.UnifiedDiffUtils; // Removed
// import java.util.List; // Removed
// import java.util.stream.Collectors; // Removed
// import javax.tools.JavaCompiler; // Removed
// import javax.tools.ToolProvider; // Removed
// import java.io.ByteArrayOutputStream; // Removed
// import java.io.BufferedReader; // Removed
// import java.io.InputStreamReader; // Removed
// import java.nio.charset.StandardCharsets; // Removed

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Scanner;
import java.util.stream.Stream;

public class JaiderIntegrationTutorialDemo {

    private ChatLanguageModel chatModel; // Corrected type
    private Path temporaryDirectoryPath;
    private Scanner scanner;
    // private Config config; // Removed
    // private JaiderModel jaiderModel; // Removed

    private static final String DEFAULT_GEMINI_MODEL_NAME = "gemini-pro"; // Consider making this configurable or using Jaider's config

    public JaiderIntegrationTutorialDemo() {
        this.scanner = new Scanner(System.in);
        // Initialize ChatModel - API key will be handled during actual model building
    }

    private void initializeChatModel() {
        System.out.print("Enter your Gemini API Key (or leave blank if GOOGLE_API_KEY env var is set): ");
        String apiKey = scanner.nextLine();

        System.out.println("Initializing Gemini Chat Model (" + DEFAULT_GEMINI_MODEL_NAME + ")...");
        try {
            this.chatModel = GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey) // SDK handles null/empty by checking env var
                    .modelName(DEFAULT_GEMINI_MODEL_NAME)
                    .build();
            System.out.println("Gemini Chat Model initialized successfully.");
        } catch (Exception e) {
            System.err.println("Failed to initialize Gemini Chat Model: " + e.getMessage());
            System.err.println("Please ensure your API key is correct and environment variables (GOOGLE_CLOUD_PROJECT, GOOGLE_CLOUD_LOCATION) are set if using ADC without an explicit key.");
            System.err.println("Exiting demo as LLM is required.");
            System.exit(1);
        }
    }

    private boolean setupTemporaryDirectory() {
        try {
            temporaryDirectoryPath = Files.createTempDirectory("jaiderDemoProject_");
            System.out.println("Temporary directory for the demo project: " + temporaryDirectoryPath.toString());
            return true;
        } catch (IOException e) {
            System.err.println("Error: Failed to create temporary directory: " + e.getMessage());
            return false;
        }
    }

    private void cleanupTemporaryDirectory() {
        if (temporaryDirectoryPath != null && Files.exists(temporaryDirectoryPath)) {
            System.out.println("Cleaning up temporary directory: " + temporaryDirectoryPath.toString());
            try (Stream<Path> walk = Files.walk(temporaryDirectoryPath)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println("Warning: Failed to delete path during cleanup: " + path + " - " + e.getMessage());
                        }
                    });
                System.out.println("Temporary directory cleaned up.");
            } catch (IOException e) {
                System.err.println("Error: Failed to walk temporary directory for cleanup: " + e.getMessage());
            }
        }
    }

    public void runTutorial() {
        System.out.println("======================================================================");
        System.out.println("           Jaider Integration Tutorial Demo (Simplified)");
        System.out.println("======================================================================");
        System.out.println("This is a VERY simplified tutorial demonstrating basic LLM interaction ");
        System.out.println("for single file generation. It does NOT showcase most of Jaider's features.");
        System.out.println();
        System.out.println("For a FULL and INTERACTIVE demonstration of all major Jaider features,");
        System.out.println("please run: dumb.integrationhandler.demo.ComprehensiveInteractiveDemo");
        System.out.println("Command: mvn exec:java -Dexec.mainClass=\"dumb.integrationhandler.demo.ComprehensiveInteractiveDemo\"");
        System.out.println("======================================================================");
        System.out.println("\nPress Enter to continue with this simplified tutorial anyway...");
        scanner.nextLine();

        initializeChatModel();

        if (!setupTemporaryDirectory()) {
            System.err.println("Exiting demo due to temporary directory setup failure.");
            return;
        }

        // Config and JaiderModel instantiation removed as per simplification.
        // this.config = new Config(this.temporaryDirectoryPath);
        // this.jaiderModel = new JaiderModel(this.temporaryDirectoryPath);
        // if (this.config != null && this.chatModel != null) { // Guard against null config
        //    this.config.getInjector().registerSingleton("jaiderModel", this.jaiderModel);
        //    this.config.getInjector().registerSingleton("appChatModel", this.chatModel);
        //    System.out.println("Initialized and registered Config, JaiderModel, and appChatModel with DI.");
        // } else {
        //    System.out.println("Skipping DI registration due to null config or chatModel.");
        // }


        try {
            // Phase 1: Project Generation will go here
            generateProjectFromScratch();

            // Phase 2 & 3 (Enhancing and Other Capabilities) are removed in this simplified version.
            // enhanceGeneratedProject();
            // explainOtherCapabilities();
            System.out.println("\n--- Simplified Tutorial End ---");
            System.out.println("This simplified demo focused only on initial project generation.");
            System.out.println("No enhancements, validation, or other features were demonstrated.");

        } finally {
            cleanupTemporaryDirectory();
            scanner.close();
            System.out.println("\n======================================================================");
            System.out.println("      End of Simplified Jaider Integration Tutorial Demo");
            System.out.println("======================================================================");
            System.out.println("REMINDER: For a FULL and INTERACTIVE demonstration of all major Jaider features,");
            System.out.println("please run: dumb.integrationhandler.demo.ComprehensiveInteractiveDemo");
            System.out.println("Command: mvn exec:java -Dexec.mainClass=\"dumb.integrationhandler.demo.ComprehensiveInteractiveDemo\"");
            System.out.println("======================================================================");
            System.out.println("\nJaider Integration Tutorial Demo finished.");
        }
    }

    public static void main(String[] args) {
        JaiderIntegrationTutorialDemo demo = new JaiderIntegrationTutorialDemo();
        demo.runTutorial();
    }

    private void generateProjectFromScratch() {
        System.out.println("\n--- Phase 1: Project Generation from Scratch ---");
        System.out.println("Jaider can help bootstrap new projects or features using an LLM.");
        System.out.println("Let's generate a very simple project.");
        System.out.print("Enter a brief description for a simple, single-file project (e.g., 'Python calculator with add function', 'Java ToDo list class'): ");
        String description = scanner.nextLine();

        String languageHint = "";
        if (description.toLowerCase().contains("python")) {
            languageHint = "Python";
        } else if (description.toLowerCase().contains("java")) {
            languageHint = "Java";
        } else {
            System.out.println("Could not infer language from description, assuming generic code snippet.");
        }

        System.out.println("For this tutorial, we'll ask the LLM to generate the initial code with a small, deliberate omission, which we'll fix later.");

        String omissionFeature = "a multiplication function"; // Default
        String fileExtension = ".txt"; // Default
        if (languageHint.equals("Python")) {
            fileExtension = ".py";
            omissionFeature = "a multiplication function";
        } else if (languageHint.equals("Java")) {
            fileExtension = ".java";
            omissionFeature = "a method to list all tasks (if creating a ToDo list)";
        }

        String promptString = String.format(
            "You are a helpful coding assistant. Your task is to generate the code for a single file based on the following description: '%s'.\n" +
            "The code should be for a %s application/class if specified, otherwise a general code snippet.\n" +
            "For this simplified demonstration, please generate the code focusing only on the main feature described, and you can omit %s.\n" +
            "First, on a line by itself, write the suggested filename for this code (e.g., 'my_program%s'). The filename should be simple, using underscores for spaces if necessary, and have the correct extension.\n" +
            "Then, starting on the next line, provide only the complete code for this single file. Do not include any explanations, comments about the omission, or markdown formatting around the code block itself. Just the raw code.",
            description, languageHint, omissionFeature, fileExtension
        );

        System.out.println("\nSending a prompt to the LLM to generate code for:");
        System.out.println("  Description: '" + description + "'");
        System.out.println("  Language context: '" + (languageHint.isEmpty() ? "Generic" : languageHint) + "'");
        System.out.println("  Deliberate omission for tutorial: '" + omissionFeature + "'");
        System.out.println("  LLM should provide filename on first line, then raw code.");
        System.out.println("----------------------------------------------------");

        try {
            // Ensure this.chatModel is used, which is initialized in runTutorial()
            String fullResponse = this.chatModel.generate(java.util.Collections.singletonList(UserMessage.from(promptString))).content().text();

            if (fullResponse == null || fullResponse.trim().isEmpty()) {
                System.err.println("LLM returned an empty response. Cannot proceed with project generation.");
                return;
            }

            String[] lines = fullResponse.split("\\n", 2); // Use double backslash for literal
            String suggestedFilename = "generated_code" + (languageHint.isEmpty() ? ".txt" : (languageHint.equals("Python") ? ".py" : ".java"));
            String generatedCode;

            if (lines.length >= 1 && !lines[0].trim().isEmpty() && lines[0].trim().matches("[\\w.-]+\\.\\w+")) { // Adjust regex for file matching
                suggestedFilename = lines[0].trim();
                generatedCode = (lines.length > 1) ? lines[1] : "";
                System.out.println("LLM suggested filename: " + suggestedFilename);
            } else {
                System.out.println("Warning: LLM did not provide a filename on the first line as expected, or it was not in a recognized format. Using default: " + suggestedFilename);
                generatedCode = fullResponse;
            }

            if (generatedCode.trim().isEmpty()) {
                System.err.println("LLM did not provide any code content. Cannot create file.");
                return;
            }

            Path filePath = this.temporaryDirectoryPath.resolve(suggestedFilename);
            java.nio.file.Files.writeString(filePath, generatedCode, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

            System.out.println("\nLLM generated project code:");
            System.out.println("File created at: " + filePath.toString());
            System.out.println("--- Content of " + suggestedFilename + " ---");
            System.out.println(generatedCode);
            System.out.println("------------------------------------");

            // this.jaiderModel.files.add(filePath.toAbsolutePath()); // jaiderModel removed
            // System.out.println(suggestedFilename + " added to Jaider's (simulated) context for the demo. Context: " + (this.jaiderModel != null ? this.jaiderModel.files : "N/A"));
            System.out.println(suggestedFilename + " created. In a full Jaider app, it would be added to the model's context.");

        } catch (java.io.IOException e) {
            System.err.println("Error writing generated file: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("An error occurred during LLM call for project generation: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            // e.printStackTrace(); // Uncomment for debugging
        }
    }

    // private void enhanceGeneratedProject() { // Method removed
    // }

    // private void demonstrateAskMode() { // Method removed
    // }

    // private void demonstrateCoderMode(Path targetFilePath) { // Method removed
    // }

    // private void demonstrateValidationCommand(Path targetFilePath) { // Method removed
    // }

    // private void demonstrateCommitConcept() { // Method removed
    // }

    // private void explainOtherCapabilities() { // Method removed
    // }
}
