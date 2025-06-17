package dumb.jaider.demo.steps.interactive;

import dumb.jaider.core.DemoContext;
import dumb.jaider.core.DemoStep;
import dumb.jaider.core.llms.DummyOllamaService;
import dumb.jaider.core.llms.GeminiService;
import dumb.jaider.core.llms.LanguageModelService;
import dumb.jaider.core.llms.OllamaService;
import dumb.jaider.core.workflows.CodeGenerationWorkflow;
import dumb.jaider.ui.TUI;
import dumb.jaider.utils.FileUtils;
import dumb.jaider.utils.ShellUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


public class InteractiveDemoSteps {

    private static final Logger logger = LoggerFactory.getLogger(InteractiveDemoSteps.class);

    // DisplayMessageStep has been moved to dumb.jaider.demo.steps.common.DisplayMessageStep

    /**
     * A DemoStep that prompts the user to select a Language Model (LLM).
     * This step demonstrates how Jaider can support multiple LLMs and allow user selection.
     * The choice is stored in the DemoContext.
     *
     * Expects: Nothing from DemoContext.
     * Puts:
     *   - "llmChoice" (String): The name of the selected Language Model (e.g., "Gemini").
     */
    public static class SelectLLMStep implements DemoStep {
        @Override
        public boolean execute(TUI tui, CodeGenerationWorkflow workflow, DemoContext context) throws Exception {
            List<String> choices = Arrays.asList("Gemini", "Ollama (dummy)");
            // Prompt the user to choose a Language Model.
            CompletableFuture<String> choiceFuture = tui.getUserChoice("Select Language Model",
                    "Jaider can use different Language Models. Choose one for this demo:",
                    choices, null);
            String choice = choiceFuture.join(); // Wait for user input

            if (choice == null) {
                logger.info("User cancelled Language Model selection.");
                return false; // User cancelled, stop the demo flow.
            }
            // Store the user's choice in the DemoContext for subsequent steps.
            context.put("llmChoice", choice);
            logger.info("User selected Language Model: {}", choice);
            return true;
        }
    }

    /**
     * A DemoStep that prompts the user to enter an API key, specifically if "Gemini" was chosen as the LLM.
     * This demonstrates how Jaider handles API key input for specific services.
     * The API key is stored in the DemoContext.
     *
     * Expects:
     *   - "llmChoice" (String) from DemoContext: The previously selected Language Model.
     * Puts:
     *   - "apiKey" (String): The entered API key or a dummy key for non-Gemini choices.
     */
    public static class EnterApiKeyStep implements DemoStep {
        @Override
        public boolean execute(TUI tui, CodeGenerationWorkflow workflow, DemoContext context) throws Exception {
            String llmChoice = (String) context.get("llmChoice");

            if ("Gemini".equalsIgnoreCase(llmChoice)) {
                // Prompt for Gemini API key.
                CompletableFuture<String> apiKeyFuture = tui.getUserInput("Enter Gemini API Key",
                        "Jaider needs a Gemini API Key to generate code using the Gemini Language Model.\nPlease enter your key:",
                        "");
                String apiKey = apiKeyFuture.join();

                if (apiKey == null || apiKey.trim().isEmpty()) {
                    tui.showModalMessage("API Key Error", "A Gemini API Key is required to proceed with Gemini. This demo step cannot continue without it.");
                    logger.warn("User did not provide a Gemini API key.");
                    return false; // API key is required for Gemini.
                }
                context.put("apiKey", apiKey);
                logger.info("Gemini API Key entered.");
            } else {
                // For Ollama (dummy) or other models not requiring an explicit key in this demo.
                context.put("apiKey", "ollama_dummy_key"); // Placeholder
                logger.info("Using placeholder API Key for {}.", llmChoice);
            }
            return true;
        }
    }

    /**
     * A DemoStep that prompts the user to select or enter a project description.
     * This demonstrates Jaider's ability to take a natural language description as input for code generation.
     * The project description is stored in the DemoContext.
     *
     * Expects: Nothing from DemoContext.
     * Puts:
     *   - "projectDescription" (String): The chosen or entered project description.
     */
    public static class SelectProjectDescriptionStep implements DemoStep {
        private static final String CUSTOM_DESCRIPTION_CHOICE = "Enter a custom project description";
        private static final String SAMPLE_DESCRIPTION_1 = "A simple Java calculator with basic arithmetic operations like add, subtract, multiply, divide.";
        private static final String SAMPLE_DESCRIPTION_2 = "A Python script that sorts files in a specified directory by their file extension into subdirectories.";


        @Override
        public boolean execute(TUI tui, CodeGenerationWorkflow workflow, DemoContext context) throws Exception {
            List<String> choices = Arrays.asList(
                    SAMPLE_DESCRIPTION_1,
                    SAMPLE_DESCRIPTION_2,
                    CUSTOM_DESCRIPTION_CHOICE
            );
            // Prompt user to select a sample description or provide a custom one.
            CompletableFuture<String> choiceFuture = tui.getUserChoice("Project Description",
                    "Provide a description for the single-file project you want Jaider to generate:",
                    choices, null);
            String choice = choiceFuture.join();

            if (choice == null) {
                logger.info("User cancelled project description selection.");
                return false; // User cancelled.
            }

            String projectDescription;
            if (CUSTOM_DESCRIPTION_CHOICE.equals(choice)) {
                // Prompt for custom project description.
                CompletableFuture<String> customDescFuture = tui.getUserInput("Custom Project Description",
                        "Enter your detailed project description (e.g., 'a Python script to find all prime numbers up to 100'):",
                        "");
                projectDescription = customDescFuture.join();
                if (projectDescription == null || projectDescription.trim().isEmpty()) {
                    tui.showModalMessage("Input Error", "The project description cannot be empty.");
                    logger.warn("User provided an empty custom project description.");
                    return false;
                }
            } else {
                projectDescription = choice; // User selected a sample description.
            }
            // Store the project description in DemoContext.
            context.put("projectDescription", projectDescription);
            logger.info("Project description set to: {}", projectDescription);
            return true;
        }
    }

    /**
     * A DemoStep that encapsulates the core logic of generating code based on user inputs and then verifying it.
     * This step demonstrates Jaider's end-to-end capability of taking a description,
     * interacting with an LLM for code generation, saving the code, and performing basic verification (like compilation).
     *
     * Expects from DemoContext:
     *   - "llmChoice" (String): The selected Language Model.
     *   - "apiKey" (String): The API key for the selected LLM (if applicable).
     *   - "projectDescription" (String): The description of the project to generate.
     * Puts into DemoContext:
     *   - "generatedCode" (String): The code generated by the Language Model.
     */
    public static class GenerateAndVerifyStep implements DemoStep {
        private Path temporaryDirectoryPath; // Path to the temporary directory for generated files.
        private Path generatedFilePath;      // Path to the actual generated file.

        @Override
        public boolean execute(TUI tui, CodeGenerationWorkflow workflow, DemoContext context) throws Exception {
            // Retrieve necessary data from DemoContext, set by previous steps.
            String llmChoice = (String) context.get("llmChoice");
            String apiKey = (String) context.get("apiKey");
            String projectDescription = (String) context.get("projectDescription");

            tui.showModalMessage("Code Generation & Verification",
                    "Jaider will now attempt to generate code based on your description and chosen Language Model.\n" +
                    "After generation, it will try to verify the code (e.g., compile Java).\n\nPlease wait...");

            try {
                // Setup a temporary directory for the generated project files.
                setupTemporaryDirectory(tui);
                // Call the method to generate code using the selected LLM and project description.
                String generatedCode = generateProjectUsingAPI(tui, llmChoice, apiKey, projectDescription);
                // Store the generated code in DemoContext for potential use by other steps or for inspection.
                context.put("generatedCode", generatedCode);

                // Perform basic verification on the generated code.
                boolean verificationResult = verifyProjectGeneration(tui);

                String resultMessage = verificationResult ?
                        "Code generation and verification successful! Files are in: " + temporaryDirectoryPath.toString() :
                        "Code verification failed. Please check the logs for more details.";
                tui.showModalMessage("Generation & Verification Result", resultMessage);

                if (verificationResult && generatedFilePath != null) {
                    // If verification was successful, show the generated code to the user.
                    tui.showScrollableText("Generated Code (" + generatedFilePath.getFileName() + ")", generatedCode);
                }
                return verificationResult; // Step succeeds if verification is successful.
            } catch (Exception e) {
                logger.error("Error during code generation and verification: {}", e.getMessage(), e);
                tui.showModalMessage("Error", "An error occurred during the code generation or verification process: " + e.getMessage());
                return false;
            } finally {
                cleanupTemporaryDirectory(tui);
            }
        }

        private void setupTemporaryDirectory(TUI tui) throws IOException {
            temporaryDirectoryPath = Files.createTempDirectory("interactiveDemo");
            logger.info("Temporary directory created at: {}", temporaryDirectoryPath);
        }

        private void cleanupTemporaryDirectory(TUI tui) {
            if (temporaryDirectoryPath != null) {
                try {
                    FileUtils.deleteDirectory(temporaryDirectoryPath);
                    logger.info("Temporary directory cleaned up: {}", temporaryDirectoryPath);
                } catch (IOException e) {
                    logger.error("Failed to clean up temporary directory: {}", temporaryDirectoryPath, e);
                    tui.showModalMessage("Cleanup Error", "Failed to clean up temporary directory: " + e.getMessage());
                }
            }
        }

        private String generateProjectUsingAPI(TUI tui, String llmChoice, String apiKey, String projectDescription) throws Exception {
            LanguageModelService llmService;
            if ("Gemini".equalsIgnoreCase(llmChoice)) {
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    throw new IllegalArgumentException("Gemini API key is required.");
                }
                llmService = new GeminiService(apiKey);
            } else { // Ollama (dummy)
                llmService = new DummyOllamaService(); // Use a dummy/mock for Ollama if real one not needed
            }

            String prompt = String.format(
                "Generate a simple, single-file project based on the following description:\n\"%s\"\n" +
                "The project should be self-contained in one file. For Java, produce a Main.java. For Python, a script.py. " +
                "Include basic error handling and comments. The code should compile and run if applicable. " +
                "Output ONLY the code for the file.",
                projectDescription
            );

            // tui.showModalMessage("Generating Code", "Sending request to Language Model: " + llmChoice);
            String generatedCode = llmService.generateCode(prompt).join(); // Using CompletableFuture.join for simplicity

            if (generatedCode == null || generatedCode.trim().isEmpty()) {
                throw new IOException("Language Model returned empty or null code.");
            }

            // Heuristic to determine file extension based on description or common languages
            String fileName = "GeneratedProject.java"; // Default to Java
            if (projectDescription.toLowerCase().contains("python")) {
                fileName = "generated_script.py";
            } else if (projectDescription.toLowerCase().contains("java")) {
                 fileName = "Main.java";
            }


            generatedFilePath = temporaryDirectoryPath.resolve(fileName);
            Files.writeString(generatedFilePath, generatedCode);
            logger.info("Generated code written to: {}", generatedFilePath);
            return generatedCode; // Return the generated code
        }

        private boolean verifyProjectGeneration(TUI tui) { // Verification is now primarily of the file system state
            if (generatedFilePath == null || !Files.exists(generatedFilePath)) {
                logger.error("Verification failed: Generated file path is null or file does not exist at {}", generatedFilePath);
                tui.showModalMessage("Verification Error", "Generated file not found for verification.");
                return false;
            }

            String fileName = generatedFilePath.getFileName().toString();
            // String fileContent; // Code content is already in context if needed by other steps

            try {
                if (Files.size(generatedFilePath) == 0) {
                    logger.error("Verification failed: Generated file is empty at {}.", generatedFilePath);
                    tui.showModalMessage("Verification Error", "Generated file is empty.");
                    return false;
                }
            } catch (IOException e) {
                logger.error("Verification failed: Error accessing generated file attributes at {}.", generatedFilePath, e);
                tui.showModalMessage("Verification Error", "Error accessing generated file: " + e.getMessage());
                return false;
            }

            // Simple verification: compile and run Java, or just run Python
            try {
                if (fileName.endsWith(".java")) {
                    // Compile
                    String classPath = System.getProperty("java.class.path");
                    String[] compileCommand = {"javac", "-cp", classPath, generatedFilePath.toString()};
                     // tui.showModalMessage("Compiling", "Compiling: " + Arrays.toString(compileCommand));
                    ShellUtils.CommandResult compileResult = ShellUtils.runCommand(temporaryDirectoryPath.toString(), compileCommand);
                    if (compileResult.exitCode() != 0) {
                        // tui.showModalMessage("Compilation Failed", compileResult.output());
                        logger.error("Compilation failed: {}", compileResult.output());
                        return false;
                    }
                     // tui.showModalMessage("Compilation Succeeded", "Java code compiled successfully.");

                    // Optionally run if it's a runnable Main class
                    // String mainClass = fileName.substring(0, fileName.lastIndexOf('.'));
                    // String[] runCommand = {"java", "-cp", temporaryDirectoryPath.toString(), mainClass};
                    // ShellUtils.CommandResult runResult = ShellUtils.runCommand(temporaryDirectoryPath.toString(), runCommand);
                    // if (runResult.exitCode() != 0) {
                    //     tui.showModalMessage("Execution Failed", runResult.output());
                    //     return false;
                    // }
                    // tui.showModalMessage("Execution Succeeded", "Java code ran successfully (basic check).");

                } else if (fileName.endsWith(".py")) {
                    String[] runCommand = {"python3", generatedFilePath.toString()};
                    // tui.showModalMessage("Executing Python", "Executing: " + Arrays.toString(runCommand));
                    ShellUtils.CommandResult runResult = ShellUtils.runCommand(temporaryDirectoryPath.toString(), runCommand);
                    if (runResult.exitCode() != 0) {
                        // tui.showModalMessage("Python Execution Failed", runResult.output());
                        logger.error("Python execution failed: {}", runResult.output());
                        return false;
                    }
                    // tui.showModalMessage("Python Execution Succeeded", "Python script ran successfully (basic check).");
                }
                logger.info("Verification successful for {}", generatedFilePath.getFileName());
                return true;
            } catch (IOException | InterruptedException e) {
                // tui.showModalMessage("Verification Exception", "Exception during verification: " + e.getMessage());
                logger.error("Exception during verification: {}", e.getMessage(), e);
                Thread.currentThread().interrupt(); // Restore interrupt status
                return false;
            }
        }
    }
}
