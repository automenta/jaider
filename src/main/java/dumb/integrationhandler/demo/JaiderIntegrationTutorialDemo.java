package dumb.integrationhandler.demo;

import dev.langchain4j.model.chat.ChatLanguageModel; // Corrected import
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.data.message.UserMessage;
import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.agents.AskAgent;
import dev.langchain4j.data.message.AiMessage;
import java.util.Collections; // For Collections.singletonList
import dumb.jaider.tools.DiffApplier; // For DiffApplier
import com.github.difflib.DiffUtils; // For diff generation
import com.github.difflib.patch.Patch; // For Patch object
import com.github.difflib.text.UnifiedDiffUtils; // For generating viewable diff
import java.util.List; // For List
import java.util.stream.Collectors; // For Collectors
import javax.tools.JavaCompiler; // For Java compilation
import javax.tools.ToolProvider; // For Java compilation
import java.io.ByteArrayOutputStream; // For capturing compiler output
import java.io.BufferedReader; // For capturing process output
import java.io.InputStreamReader; // For capturing process output
import java.nio.charset.StandardCharsets; // For compiler output decoding

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
    private Config config;
    private JaiderModel jaiderModel;

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
        System.out.println("Welcome to the Jaider Integration Tutorial Demo!");

        initializeChatModel();

        if (!setupTemporaryDirectory()) {
            System.err.println("Exiting demo due to temporary directory setup failure.");
            return;
        }

        this.config = new Config(this.temporaryDirectoryPath);
        this.jaiderModel = new JaiderModel(this.temporaryDirectoryPath);
        this.config.getInjector().registerSingleton("jaiderModel", this.jaiderModel);
        this.config.getInjector().registerSingleton("appChatModel", this.chatModel);
        System.out.println("Initialized and registered Config, JaiderModel, and appChatModel with DI.");

        try {
            // Phase 1: Project Generation will go here
            generateProjectFromScratch();

            // Phase 2: Enhancing the Generated Project will go here
            enhanceGeneratedProject();

            // Phase 3: Overview of Other Capabilities will go here
            explainOtherCapabilities();

        } finally {
            cleanupTemporaryDirectory();
            scanner.close();
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
            "IMPORTANT FOR THE DEMONSTRATION: Please **omit** %s for now. We will add it later.\n" +
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

            this.jaiderModel.files.add(filePath.toAbsolutePath());
            System.out.println(suggestedFilename + " added to Jaider's (simulated) context for the demo. Context: " + this.jaiderModel.files);

        } catch (java.io.IOException e) {
            System.err.println("Error writing generated file: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("An error occurred during LLM call for project generation: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            // e.printStackTrace(); // Uncomment for debugging
        }
    }

    private void enhanceGeneratedProject() {
        System.out.println("\n--- Phase 2: Enhancing the Generated Project ---");
        System.out.println("Now, we'll use some of Jaider's features to interact with and improve the project we generated.");

        Path generatedFilePath = null;
        if (this.jaiderModel != null && this.jaiderModel.files != null && !this.jaiderModel.files.isEmpty()) {
            generatedFilePath = this.jaiderModel.files.iterator().next(); // Get the first (and assumed only) file
        } else {
            System.err.println("Error: No project file was found from Phase 1 (JaiderModel or its files set is null/empty).");
            System.err.println("Cannot proceed with Phase 2: Enhancing Project.");
            return;
        }
        String actualFilename = generatedFilePath.getFileName().toString();

        System.out.println("\nSimulating Jaider's file context management:");
        System.out.println("The command `/add " + actualFilename + "` would typically be used in Jaider to add this file to its active context.");
        System.out.println("For this demo, '" + actualFilename + "' is already considered in context from its generation (added to a JaiderModel instance).");

        demonstrateAskMode();

        // Placeholder for Coder Mode demonstration
        demonstrateCoderMode(generatedFilePath);

        // Placeholder for Validation command demonstration
        demonstrateValidationCommand(generatedFilePath);

        // Placeholder for Commit changes (conceptual)
        demonstrateCommitConcept();
    }

    private void demonstrateAskMode() {
        System.out.println("\n--- Ask Mode Demonstration ---");
        System.out.println("Jaider's 'Ask' mode lets you ask general questions or get information without modifying code.");
        System.out.println("Simulating command: /mode Ask");

        try {
            dumb.jaider.agents.AskAgent askAgent = this.config.getComponent("askAgent", dumb.jaider.agents.AskAgent.class);
            System.out.println("Successfully retrieved AskAgent instance via DI.");

            System.out.print("Ask the AskAgent a general knowledge question (e.g., 'What is the capital of France?'): ");
            String question = scanner.nextLine();
            if (question.trim().isEmpty()) {
                System.out.println("No question asked, skipping AskAgent interaction.");
                return;
            }

            System.out.println("Sending question to AskAgent...");
            dev.langchain4j.data.message.UserMessage userChatMessage = UserMessage.from(question);
            dev.langchain4j.data.message.AiMessage agentResponse = askAgent.act(java.util.Collections.singletonList(userChatMessage));
            String responseText = (agentResponse != null && agentResponse.text() != null) ? agentResponse.text() : "No text response from agent, or agent returned null.";

            System.out.println("\nAskAgent's Response:");
            System.out.println(responseText);

        } catch (Exception e) {
            System.err.println("\nError demonstrating Ask Mode with DI-retrieved AskAgent: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            System.err.println("This could be due to configuration issues for the LLM used by the agent, problems with DI setup (e.g., missing dependencies like 'appChatModel' or 'chatMemory' not being registered correctly), or an issue within the AskAgent itself.");
            // e.printStackTrace(); // Uncomment for detailed debugging

            System.out.println("\nAs a fallback for this demo, let's try a direct LLM call for your question.");
            System.out.print("Please re-enter your question for a direct LLM call (or press Enter to skip): ");
            String fallbackQuestion = scanner.nextLine();
            if (fallbackQuestion.trim().isEmpty()) {
                System.out.println("Skipping direct LLM call.");
                return;
            }
            try {
                 String directResponse = this.chatModel.generate(java.util.Collections.singletonList(UserMessage.from(fallbackQuestion))).content().text();
                 System.out.println("\nDirect LLM Response (Fallback):");
                 System.out.println(directResponse);
            } catch (Exception directLlmError) {
                 System.err.println("Error during direct LLM call: " + directLlmError.getClass().getSimpleName() + " - " + directLlmError.getMessage());
            }
        }
    }

    private void demonstrateCoderMode(Path targetFilePath) {
        System.out.println("\n--- Coder Mode Demonstration ---");
        System.out.println("Jaider's 'Coder' mode helps with direct code modifications, applying changes via diffs.");
        System.out.println("Simulating command: /mode Coder");

        if (targetFilePath == null || !java.nio.file.Files.exists(targetFilePath)) {
            System.err.println("Error: Target file path is null or does not exist. Cannot demonstrate Coder mode.");
            return;
        }
        String filename = targetFilePath.getFileName().toString();
        System.out.println("We will now try to fix the deliberate omission in '" + filename + "'.");

        try {
            String originalContent = java.nio.file.Files.readString(targetFilePath);

            // 1. Prompt LLM for the corrected full code
            //    The prompt needs to clearly state the original problem/omission.
            String languageHint = "";
            if (filename.endsWith(".py")) languageHint = "Python";
            else if (filename.endsWith(".java")) languageHint = "Java";

            String omissionDescription = "it was missing a critical feature or had a deliberate simple bug.";
            if (languageHint.equals("Python") && originalContent.contains("def add")) omissionDescription = "it was missing a multiplication function.";
            else if (languageHint.equals("Java") && originalContent.contains("class ToDo")) omissionDescription = "it was missing a method to list all tasks.";


            String coderPrompt = String.format(
                "The following is the content of a %s file named '%s':\n" +
                "```%s\n%s\n```\n" +
                "This code is incomplete because %s \n" +
                "Please provide the complete and corrected version of the entire file content for '%s'.\n" +
                "Include all original working parts plus the fix/addition.\n" +
                "Output only the raw, complete source code for the file. Do not include explanations or markdown.",
                languageHint, filename, languageHint.toLowerCase(), originalContent, omissionDescription, filename
            );

            System.out.println("\nPrompting LLM for the corrected version of '" + filename + "'...");
            System.out.println("Omission to fix: " + omissionDescription);

            String correctedFullCode = this.chatModel.generate(java.util.Collections.singletonList(UserMessage.from(coderPrompt))).content().text();

            if (correctedFullCode == null || correctedFullCode.trim().isEmpty()) {
                System.err.println("LLM did not provide corrected code. Cannot proceed with Coder mode demo.");
                return;
            }

            System.out.println("\nLLM's suggested corrected code for '" + filename + "':");
            System.out.println("------------------------------------");
            System.out.println(correctedFullCode);
            System.out.println("------------------------------------");

            // 2. Generate Diff
            System.out.println("\nGenerating a diff between the original and corrected code...");
            java.util.List<String> originalLines = originalContent.lines().collect(java.util.stream.Collectors.toList());
            java.util.List<String> correctedLines = correctedFullCode.lines().collect(java.util.stream.Collectors.toList());

            com.github.difflib.patch.Patch<String> patch = com.github.difflib.DiffUtils.diff(originalLines, correctedLines);
            java.util.List<String> diffOutput = com.github.difflib.text.UnifiedDiffUtils.generateUnifiedDiff(filename, filename + ".fixed", originalLines, patch, 3);

            if (diffOutput.isEmpty()) {
                System.out.println("No differences found between original and corrected code. The file might already be correct or LLM provided identical code.");
            } else {
                System.out.println("Proposed Diff:");
                diffOutput.forEach(System.out::println);
                System.out.println("------------------------------------");
            }

            // 3. Apply Diff using DiffApplier
            if (!diffOutput.isEmpty()) {
                System.out.println("\nJaider's Coder agent would now present this diff for your approval.");
                System.out.println("For this demo, we'll simulate accepting it and use Jaider's DiffApplier logic.");

                dumb.jaider.tools.DiffApplier diffApplier = new dumb.jaider.tools.DiffApplier();
                // The JaiderModel 'dir' and 'files' context are used by DiffApplier.
                // 'this.jaiderModel' was initialized with the temporaryDirectoryPath and 'files' contains the targetFilePath.
                String applyResult = diffApplier.apply(this.jaiderModel, patch, filename, filename); // original and revised are same for in-place update

                System.out.println("DiffApplier result: " + applyResult);

                if (applyResult.startsWith("Diff applied successfully")) {
                    String finalContent = java.nio.file.Files.readString(targetFilePath);
                    System.out.println("\nContent of '" + filename + "' after applying diff:");
                    System.out.println("------------------------------------");
                    System.out.println(finalContent);
                    System.out.println("------------------------------------");
                } else {
                    System.err.println("Failed to apply diff. Original file content:");
                    System.out.println(originalContent); // Show original if patch failed
                }
            } else {
                 System.out.println("Since no diff was generated, no changes were applied.");
            }

        } catch (Exception e) {
            System.err.println("\nAn error occurred during Coder Mode demonstration: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace(); // For detailed debugging
        }
    }

    private void demonstrateValidationCommand(Path targetFilePath) {
        System.out.println("\n--- Validation Command (`/run`) Demonstration ---");
        System.out.println("Jaider uses a configurable 'runCommand' (from .jaider.json) to execute tests or validation.");
        System.out.println("Simulating command: /run");

        if (targetFilePath == null || !java.nio.file.Files.exists(targetFilePath)) {
            System.err.println("Error: Target file for validation is null or does not exist.");
            return;
        }
        String filename = targetFilePath.getFileName().toString();

        // Display the configured runCommand from Config
        String configuredRunCommand = (this.config != null) ? this.config.getRunCommand() : " (Config not available) ";
        System.out.println("Jaider's configured 'runCommand' is currently: '" + configuredRunCommand + "' (This demo will perform a language-specific basic check instead).");


        boolean success = false;
        String validationOutput = "";

        System.out.println("Attempting a basic validation for '" + filename + "'...");

        if (filename.endsWith(".py")) {
            System.out.println("Performing Python syntax check (py_compile)...");
            try {
                ProcessBuilder pb = new ProcessBuilder("python", "-m", "py_compile", targetFilePath.toString());
                pb.redirectErrorStream(true); // Combine stdout and stderr
                Process process = pb.start();

                // Capture output using a StringBuilder
                StringBuilder outputBuilder = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputBuilder.append(line).append(System.lineSeparator());
                    }
                }

                int exitCode = process.waitFor();
                validationOutput = outputBuilder.toString();
                if (exitCode == 0 && validationOutput.trim().isEmpty()) { // py_compile is silent on success
                    success = true;
                    validationOutput = "Python file syntax OK (py_compile successful).";
                } else if (exitCode == 0 && !validationOutput.trim().isEmpty()) {
                     // Sometimes py_compile might print warnings/info even with exit code 0
                     success = true; // Treat as success if exit code is 0
                     validationOutput = "Python file syntax OK (py_compile exit code 0, with output):\n" + validationOutput;
                }
                else {
                    validationOutput = "Python syntax check failed (py_compile exit code " + exitCode + "):\n" + validationOutput;
                }
            } catch (java.io.IOException | InterruptedException e) {
                validationOutput = "Error running Python validation: " + e.getMessage();
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        } else if (filename.endsWith(".java")) {
            System.out.println("Performing Java compilation check...");
            javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                validationOutput = "Java compiler not found. Ensure JDK is installed and on path, not just JRE.";
            } else {
                java.io.ByteArrayOutputStream errStream = new java.io.ByteArrayOutputStream();
                int compilationResult = compiler.run(null, null, errStream, targetFilePath.toString()); // stdin, stdout, stderr
                if (compilationResult == 0) {
                    success = true;
                    validationOutput = "Java file compiled successfully.";
                } else {
                    validationOutput = "Java compilation failed:\n" + errStream.toString(java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        } else {
            validationOutput = "No specific validation implemented for '." + filename.substring(filename.lastIndexOf('.') + 1) + "' files in this demo.";
            System.out.println(validationOutput);
            // For unknown file types, maybe don't proceed to show "Validation Result" block as if it were a formal validation.
            return;
        }

        System.out.println("\nValidation Result for '" + filename + "':");
        System.out.println("Success: " + success);
        System.out.println("Output:");
        System.out.println("------------------------------------");
        System.out.println(validationOutput.trim());
        System.out.println("------------------------------------");
    }

    private void demonstrateCommitConcept() {
        System.out.println("\n--- Commit Changes (Conceptual) ---");
        System.out.println("After successful validation, Jaider's Coder agent can commit the changes to version control.");
        System.out.println("This is typically done using a tool that interacts with Git.");
        System.out.println("For example, the agent might use a `commitChanges(commit_message)` tool.");

        String commitMessage = "Fix: Add missing feature and apply improvements via Jaider demo.";
        System.out.println("Simulated agent action: Commit changes with message: '" + commitMessage + "'");

        System.out.println("In a real Jaider session, this would execute `git add .` and `git commit -m \"" + commitMessage + "\"` or similar commands.");
        System.out.println("For this demo, we won't perform actual Git operations on your system.");
        System.out.println("The modified file(s) exist in the temporary directory: " + this.temporaryDirectoryPath.toString());
    }

    private void explainOtherCapabilities() {
        System.out.println("\n--- Phase 3: Overview of Other Jaider Capabilities ---");
        System.out.println("Jaider has many other powerful features. Here's a brief overview:");

        // Web Search (searchWeb tool)
        System.out.println("\n1. Web Search (`searchWeb` tool):");
        System.out.println("   - Agents can use the `searchWeb` tool to find information online using services like Tavily.");
        System.out.println("   - Example (Conceptual): If an agent needs to know about a specific library or error message.");
        System.out.println("   - Agent query: \"how to parse json in python\"");
        System.out.println("   - Mocked `searchWeb` result: \"Python's `json` module provides `json.loads()` for strings and `json.load()` for files...\"");
        // Light Integration: Could show Tavily API key from config if set.
        if (this.config != null && this.config.getTavilyApiKey() != null && !this.config.getTavilyApiKey().isEmpty()) {
            System.out.println("   - (Demo Info: A Tavily API key IS configured in .jaider.json or environment.)");
        } else if (this.config != null) {
            System.out.println("   - (Demo Info: Tavily API key is NOT configured. `searchWeb` would require it.)");
        }

        // Project Indexing (/index) & Code Search (findRelevantCode tool)
        System.out.println("\n2. Project Indexing & Code Search (`/index`, `findRelevantCode` tool):");
        System.out.println("   - Jaider can create a semantic index of your project's codebase using the `/index` command.");
        System.out.println("   - Agents can then use the `findRelevantCode(query)` tool to find code snippets related to their task.");
        System.out.println("   - This is very useful for understanding large codebases or finding where to make changes.");
        System.out.println("   - (Demo Info: This uses embedding models, which can be configured for providers like OpenAI, Gemini, or Ollama in .jaider.json)");

        // Undo (/undo command)
        System.out.println("\n3. Undo (`/undo` command):");
        System.out.println("   - Jaider's `/undo` command attempts to revert the last applied diff.");
        System.out.println("   - For modified files, it uses `git checkout <file>`.");
        System.out.println("   - For newly created files by a patch, it deletes them.");
        System.out.println("   - This provides a safety net during development.");

        // Multiple LLM Providers & Configuration (.jaider.json, /edit-config command)
        System.out.println("\n4. Multiple LLM Providers & Configuration (`.jaider.json`, `/edit-config`):");
        System.out.println("   - Jaider supports various LLM providers: Ollama (local models), OpenAI, Google Gemini, and generic OpenAI-compatible APIs.");
        System.out.println("   - Configuration is managed via the `.jaider.json` file in your project root.");
        if (this.config != null) {
            System.out.println("   - (Demo Info: Current demo is using LLM provider: '" + this.config.getLlm() + "' as per its loaded config.)");
            System.out.println("     Default .jaider.json runCommand: '" + this.config.getRunCommand() + "'");
        }
        System.out.println("   - The `/edit-config` command in Jaider allows you to easily modify this configuration file.");

        // Architect Mode
        System.out.println("\n5. Architect Mode (`/mode Architect`):");
        System.out.println("   - Besides 'Coder' and 'Ask' modes, Jaider has an 'Architect' mode.");
        System.out.println("   - This mode is designed for high-level codebase analysis, design discussions, and understanding complex systems.");
        System.out.println("   - Agents in Architect mode typically have read-only access to tools like `readFile` and `findRelevantCode`.");

        // Self-Development (/self-develop command)
        System.out.println("\n6. Self-Development (`/self-develop` command):");
        System.out.println("   - A powerful feature where Jaider's CoderAgent can attempt to modify Jaider's own source code to fulfill a task.");
        System.out.println("   - Example: `/self-develop Add a new configuration option to .jaider.json`");
        System.out.println("   - This involves proposing a diff, user approval, building, testing, committing, and restarting Jaider.");

        // Session Management
        System.out.println("\n7. Session Management:");
        System.out.println("   - Jaider can save your current session (files in context, chat history) and restore it later.");
        System.out.println("   - This helps you pick up where you left off.");

        System.out.println("\nThis demo has showcased some core features. The full Jaider application offers a much richer, interactive experience!");
    }
}
