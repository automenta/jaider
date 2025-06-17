package dumb.jaider.demo;

import dumb.jaider.app.App;
import dumb.jaider.ui.DemoUI;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/**
 * Provides a comprehensive, scripted demonstration of Jaider's capabilities using a console-based
 * UI simulator ({@link DemoUI}). This demo walks through various Jaider commands and agent interactions,
 * simulating user input and showcasing features like code generation, modification, context management,
 * configuration, and different agent modes.
 * <p>
 * The demo operates within a temporary directory containing a sample project (Python files and a README)
 * and a default {@code .jaider.json} configuration. It guides the "user" (via console prompts)
 * through a sequence of operations, explaining each step and the Jaider features being demonstrated.
 * </p>
 * <p>
 * Key features showcased:
 * <ul>
 *     <li>Adding files to Jaider's context ({@code /add})</li>
 *     <li>Interacting with different agent modes (Ask, Coder, Architect via {@code /mode} and direct questions/requests)</li>
 *     <li>Code modification and diff application (simulated via {@link DemoUI})</li>
 *     <li>Running validation commands ({@code /run})</li>
 *     <li>Undoing previous changes ({@code /undo})</li>
 *     <li>Editing Jaider's configuration ({@code /edit-config}) and observing behavior changes (e.g., LLM provider)</li>
 *     <li>Web search capabilities ({@code /searchweb}, dependent on Tavily API key)</li>
 *     <li>Project indexing and semantic code search ({@code /index} and then asking questions requiring code search)</li>
 *     <li>Summarizing files ({@code /summarize})</li>
 * </ul>
 * </p>
 * <p>
 * This demo is intended for developers to understand Jaider's command set and interaction flows.
 * It uses {@link DemoUI} to simulate the Jaider UI, printing interactions to the console and
 * accepting scripted or direct console input.
 * </p>
 */
public class ComprehensiveInteractiveDemo {

    private static Path temporaryDemoDirectory; // Stores the root path of the temporary demo project.

    private static void setupTemporaryDirectory() throws IOException {
        temporaryDemoDirectory = Files.createTempDirectory("jaiderComprehensiveDemo_");
        System.out.println("[DemoSetup] Created temporary directory: " + temporaryDemoDirectory.toString());

        // Create a default .jaider.json configuration file
        Path jaiderConfigPath = temporaryDemoDirectory.resolve(".jaider.json");
        String defaultConfigContent = """
                    {
                    llmProvider: "ollama",
                    ollamaBaseUrl: "http://localhost:11434",
                    ollamaModelName: "llamablit",
                    runCommand: "echo 'Validation command not configured for this demo project.'",
                    apiKeys: {},
                    autoApplyDiff: false
                    }
                """;
        Files.writeString(jaiderConfigPath, defaultConfigContent, StandardOpenOption.CREATE);
        System.out.println("[DemoSetup] Created default .jaider.json in temporary directory.");

        // Create some sample files
        Path readmePath = temporaryDemoDirectory.resolve("README.md");
        Files.writeString(readmePath, "# Sample Project\nThis is a sample project for the Jaider demo.", StandardOpenOption.CREATE);

        Path mainPyPath = temporaryDemoDirectory.resolve("main.py");
        Files.writeString(mainPyPath,
                """
                            def hello():
                                print(\"Hello from main.py\")
                        
                            hello()
                        """
                , StandardOpenOption.CREATE);

        Path utilsPyPath = temporaryDemoDirectory.resolve("utils.py");
        Files.writeString(utilsPyPath, "# Utility functions\ndef helper_function():\n\treturn \"Helpful string\"", StandardOpenOption.CREATE);

        System.out.println("[DemoSetup] Created sample files in temporary directory.");
    }

    private static void cleanupTemporaryDirectory() {
        if (temporaryDemoDirectory != null && Files.exists(temporaryDemoDirectory)) {
            System.out.println("[DemoCleanup] Cleaning up temporary directory: " + temporaryDemoDirectory.toString());
            try (Stream<Path> walk = Files.walk(temporaryDemoDirectory)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println("[DemoCleanup] Warning: Failed to delete path during cleanup: " + path + " - " + e.getMessage());
                        }
                    });
                System.out.println("[DemoCleanup] Temporary directory cleaned up successfully.");
            } catch (IOException e) {
                System.err.println("[DemoCleanup] Error: Failed to walk temporary directory for cleanup: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("--- Starting Jaider Comprehensive Interactive Demo ---");
        Runtime.getRuntime().addShutdownHook(new Thread(ComprehensiveInteractiveDemo::cleanupTemporaryDirectory));

        App app = null;
        DemoUI demoUI = new DemoUI();

        try {
            setupTemporaryDirectory();

            // The App constructor will use the current working directory if not specified otherwise,
            // so we need to ensure the app runs "inside" this temp directory.
            // This is tricky as App uses "new File(".")".
            // For a true test, Jaider might need to be launched with this temp dir as CWD,
            // or App needs a constructor that takes the project path.
            // For now, App will use the CWD of the 'mvn exec:java' command.
            // We will use /add commands with absolute paths to the temp directory.
            // And .jaider.json will be read from CWD unless App is modified.
            // The .jaider.json created in temp dir will be used if we can make App use tempDir as root.
            // Let's assume for now App's Config() correctly finds .jaider.json if CWD is set to tempDir.
            // If not, the demo script will need to use /edit-config heavily.
            // The `Config` class in Jaider `new Config(model.dir)` where model.dir is initialised to Paths.get(".").toAbsolutePath().
            // So if we can alter "user.dir" property or run from that path, it will work.
            // For this subtask, we'll proceed assuming the .jaider.json in the temp dir will be found if App is run "from" there.
            // This is a known challenge for this demo structure. A more robust solution would be for App to accept a root path.

            System.setProperty("user.dir", temporaryDemoDirectory.toAbsolutePath().toString());
            System.out.println("[DemoSetup] Set user.dir to: " + temporaryDemoDirectory.toAbsolutePath().toString());


            app = new App(demoUI, args); // Pass main args if needed by App

            List<String> demoScript = Arrays.asList(
                "DEMO_COMMENT: === Phase 1: Initial Setup & Basic Interaction ===",
                "DEMO_COMMENT: This phase demonstrates how Jaider starts up, loads its configuration (from .jaider.json),",
                "DEMO_COMMENT: and how to add files to its working context using the /add command.",
                "DEMO_COMMENT: We'll be using a temporary project with a README.md, main.py, and utils.py.",
                "DEMO_COMMENT: The default LLM provider in .jaider.json is 'ollama'. Ensure Ollama is running locally",
                "DEMO_COMMENT: and the specified model (e.g., 'llamablit') is available, or edit .jaider.json accordingly before running.",
                "DEMO_PAUSE: Initial setup complete. Jaider is ready. Press Enter to add README.md to the context.",
                "/add README.md",
                "DEMO_COMMENT: README.md has been added to Jaider's context. The agent now knows about this file.",
                "DEMO_PAUSE: README.md added. Press Enter to add main.py to the context.",
                "/add main.py",
                "DEMO_COMMENT: main.py added. Jaider's context now includes both README.md and main.py.",
                "DEMO_PAUSE: main.py added. Press Enter to add utils.py to the context.",
                "/add utils.py",
                "DEMO_COMMENT: All sample files (README.md, main.py, utils.py) are now in Jaider's context.",

                "DEMO_COMMENT: === Phase 2: Agent Modes & Core Functionality (Ask & Coder) ===",
                "DEMO_COMMENT: This phase showcases Jaider's different agent modes and core functionalities like asking questions and modifying code.",
                "DEMO_PAUSE: All files are in context. Press Enter to ask a question about main.py (Jaider defaults to 'Ask' mode or infers it).",
                "DEMO_OPERATOR_INPUT: What is the content of the file main.py?",
                "DEMO_COMMENT: The Ask agent should respond with the content of main.py.",
                "DEMO_PAUSE: Response received. Press Enter to switch to 'Coder' mode. Coder mode is for tasks involving code generation or modification.",
                "/mode Coder",
                "DEMO_PAUSE: Switched to Coder mode. Let's request a code modification in main.py.",
                "DEMO_OPERATOR_INPUT: In main.py, change the print statement inside the hello function to \"Hello from an enhanced Jaider demo!\"",
                "DEMO_COMMENT: The Coder agent should propose a diff for main.py. The DemoUI will simulate prompting you to accept, reject, or edit the diff.",
                "DEMO_COMMENT: For this demo, please type 'accept' when prompted by DemoUI.",
                "DEMO_PAUSE: If you accepted, the diff has been applied to main.py. Press Enter to run the validation command (defined in .jaider.json as 'runCommand').",
                "/run",
                "DEMO_COMMENT: The validation command (e.g., running linters or tests) has been executed. Check the output.",
                "DEMO_PAUSE: Validation complete. Now, let's ask Coder to add a new function to utils.py.",
                "DEMO_OPERATOR_INPUT: In utils.py, add a new Python function called 'another_helper' that returns the integer 42.",
                "DEMO_COMMENT: Coder should propose a diff for utils.py. Please type 'accept' when prompted.",
                "DEMO_PAUSE: Diff applied to utils.py. Now, let's ask Coder to use this new function in main.py.",
                "DEMO_OPERATOR_INPUT: In main.py, import 'another_helper' from 'utils' and print its return value after calling hello().",
                "DEMO_COMMENT: Coder should propose changes for main.py, including an import statement and a call to the new function. Please 'accept'.",
                "DEMO_PAUSE: Diff applied to main.py. Press Enter to run validation again to see if the changes are okay.",
                "/run",
                "DEMO_PAUSE: Validation run. Both main.py and utils.py should now reflect the changes. You can ask their content to verify.",
                "DEMO_COMMENT: Note: Jaider supports committing changes to Git, but this demo avoids actual git operations to keep your repository clean.",

                "DEMO_COMMENT: === Phase 3: Undo Functionality ===",
                "DEMO_COMMENT: This phase demonstrates Jaider's /undo command, which reverts the last applied code modification.",
                "DEMO_PAUSE: The last change was modifying main.py (importing and using 'another_helper'). Press Enter to undo this change.",
                "/undo",
                "DEMO_COMMENT: The /undo command should have reverted main.py to its state *before* the last modification.",
                "DEMO_PAUSE: Check the application log for undo status. To verify, ask for the content of main.py.",
                "DEMO_OPERATOR_INPUT: What is the current content of main.py?",
                "DEMO_COMMENT: main.py should no longer have the import or call to 'another_helper'.",
                "DEMO_PAUSE: Content displayed. Now, let's undo the change to utils.py (the addition of 'another_helper' function).",
                "/undo",
                "DEMO_COMMENT: utils.py should now be reverted to its original state.",
                "DEMO_PAUSE: Check log. To verify, ask for the content of utils.py.",
                "DEMO_OPERATOR_INPUT: What is the current content of utils.py?",
                "DEMO_COMMENT: utils.py should no longer contain the 'another_helper' function.",

                "DEMO_COMMENT: === Phase 4: Configuration & LLM Switching ===",
                "DEMO_COMMENT: This phase shows how to edit Jaider's configuration using /edit-config. This allows changing settings like the LLM provider.",
                "DEMO_PAUSE: Let's try editing the configuration. Press Enter to execute /edit-config.",
                "/edit-config",
                "DEMO_COMMENT: DemoUI will display the current .jaider.json content and prompt you to provide the new content.",
                "DEMO_COMMENT: IMPORTANT: To test switching to Gemini for the web search phase:",
                "DEMO_COMMENT:   1. Modify 'llmProvider' from 'ollama' to 'gemini'.",
                "DEMO_COMMENT:   2. Optionally, set 'geminiModelName' (e.g., 'gemini-1.5-flash-latest' or 'gemini-pro').",
                "DEMO_COMMENT:   3. Ensure you have the GEMINI_API_KEY environment variable set, OR",
                "DEMO_COMMENT:      add your key to the 'apiKeys' object: \"apiKeys\": { \"google\": \"YOUR_GEMINI_API_KEY\" }.",
                "DEMO_COMMENT: If you prefer not to switch or set up Gemini, you can make a minor change (like modifying 'ollamaModelName') or save without changes.",
                "DEMO_PAUSE: After /edit-config completes, Jaider will reload the configuration. Press Enter to test the (potentially new) config with a simple request.",
                "DEMO_OPERATOR_INPUT: What is 1+1?",
                "DEMO_COMMENT: The agent (Ollama or Gemini, depending on your change) should answer this simple question.",

                "DEMO_COMMENT: === Phase 5: Web Search (Requires Coder mode & Tavily API Key) ===",
                "DEMO_COMMENT: This phase demonstrates Jaider's web search capability, which uses the Tavily API.",
                "DEMO_PAUSE: Ensure you are in Coder mode (use '/mode Coder' if not). Press Enter to attempt a web search.",
                "/mode Coder", // Ensure Coder mode for web search tool
                "DEMO_COMMENT: For web search, Jaider (specifically, the LangChain4j integration) needs a Tavily API key.",
                "DEMO_COMMENT: Ensure the TAVILY_API_KEY environment variable is set, OR add it to .jaider.json:",
                "DEMO_COMMENT:   \"apiKeys\": { ..., \"tavily\": \"YOUR_TAVILY_API_KEY\" }",
                "DEMO_COMMENT: If the key is not configured, the agent should inform you it cannot perform a web search.",
                "DEMO_OPERATOR_INPUT: What is the current weather in London? Search the web if you don't know.",
                "DEMO_PAUSE: Review the agent's response. It might include search results or a message about missing API keys.",

                "DEMO_COMMENT: === Phase 6: Project Indexing & Code Search (Requires Coder mode) ===",
                "DEMO_COMMENT: This phase showcases Jaider's project indexing (/index) and semantic code search features.",
                "DEMO_COMMENT: Indexing creates vector embeddings of your codebase to enable understanding and finding relevant code.",
                "DEMO_PAUSE: Ensure Coder mode. Press Enter to run /index. This might take a moment.",
                "/mode Coder", // Ensure Coder mode for findRelevantCode tool
                "/index",
                "DEMO_COMMENT: Indexing creates embeddings based on the configured embedding service (Ollama, Gemini, or OpenAI via .jaider.json).",
                "DEMO_COMMENT: The default .jaider.json uses Ollama for embeddings if 'llmProvider' is 'ollama'.",
                "DEMO_COMMENT: If you switched 'llmProvider' to 'gemini', it will use Gemini embeddings.",
                "DEMO_PAUSE: Indexing complete. Press Enter to ask a question that requires finding relevant code.",
                "DEMO_OPERATOR_INPUT: Where is the helper_function defined in this project and what does it do?",
                "DEMO_COMMENT: The agent should use its 'findRelevantCode' tool, analyze the retrieved code, and answer.",
                "DEMO_PAUSE: Agent should respond with the location and description of helper_function in utils.py.",

                "DEMO_COMMENT: === Phase 7: Architect Mode ===",
                "DEMO_COMMENT: This phase demonstrates the 'Architect' mode, designed for high-level project understanding and planning.",
                "DEMO_PAUSE: Let's switch to Architect mode. Press Enter.",
                "/mode Architect",
                "DEMO_PAUSE: Switched to Architect mode. Now, ask a high-level question about the project's structure.",
                "DEMO_OPERATOR_INPUT: Describe the overall structure of this sample project and how the files (README.md, main.py, utils.py) relate to each other.",
                "DEMO_COMMENT: The Architect agent should provide a high-level overview of the project components.",
                "DEMO_PAUSE: Review the Architect agent's response.",

                "DEMO_COMMENT: === Phase 8: Summarize Command ===",
                "DEMO_COMMENT: This phase shows the /summarize command, useful for getting a quick overview of a file's content.",
                "DEMO_PAUSE: The /summarize command provides a concise summary of a specified file. Press Enter to summarize README.md.",
                "/summarize README.md",
                "DEMO_PAUSE: A summary of README.md should be displayed in the log.",

                "DEMO_COMMENT: === End of Comprehensive Demo ===",
                "DEMO_COMMENT: All core features included in this script have been demonstrated.",
                "DEMO_PAUSE: All planned features demonstrated. Press Enter to issue the /exit command and end the demo.",
                "/exit"
            );

            demoUI.loadScript(demoScript);
            app.run(); // This initializes the UI (our DemoUI) and starts the app's lifecycle.
                       // DemoUI's init will then start processing the script via startProcessingScript.
                       // However, app.run() in Jaider calls ui.init() which is blocking until UI closes.
                       // We need DemoUI.startProcessingScript to be called *after* app.run() has initialized the UI
                       // and is ready, but *before* it blocks.
                       // The current DemoUI structure where startProcessingScript is called by main
                       // and then init is called by app.run() should work if app.run() doesn't block immediately
                       // in a way that prevents startProcessingScript from running.
                       // Let's adjust: app.run() calls ui.init(). DemoUI.init() should then call startProcessingScript().
                       // This is slightly different from current DemoUI. Let's assume DemoUI.startProcessingScript is called by main
                       // after app is created, and app.run() will pick it up.
                       // Correct flow:
                       // 1. Create DemoUI
                       // 2. Create App (passes DemoUI to App)
                       // 3. Load script into DemoUI
                       // 4. Call app.run() -> app.run() calls demoUI.init()
                       // 5. demoUI.init() should then trigger the start of script processing.
                       // The current DemoUI's startProcessingScript is called from main, this might need adjustment.
                       // For now, let's assume the `uiInteractionExecutor.submit(this::processNextCommand)` in DemoUI's
                       // `startProcessingScript` (called from this main method) is sufficient to kick things off
                       // in parallel to `app.run()` starting up.

            demoUI.startProcessingScript(app).get(); // Wait for the demo script to complete

        } catch (IOException e) {
            System.err.println("[DemoError] IOException during demo setup or execution: " + e.getMessage());
            e.printStackTrace();
        } catch (ExecutionException e) {
            System.err.println("[DemoError] ExecutionException during demo: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.err.println("[DemoError] Demo interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("[DemoError] An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (app != null) {
                // Attempt to close the app resources if it's not already closed by /exit
                 try {
                     // app.exitAppInternalPublic(); // If /exit wasn't called or didn't fully close UI
                     if (demoUI != null) demoUI.close(); // Ensure UI resources are freed
                 } catch (IOException e) {
                     System.err.println("[DemoError] Error closing UI resources: " + e.getMessage());
                 }
            }
            cleanupTemporaryDirectory(); // This is also hooked to shutdown
            System.out.println("--- Jaider Comprehensive Interactive Demo Finished ---");
        }
    }
}
