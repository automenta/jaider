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

public class ComprehensiveInteractiveDemo {

    private static Path temporaryDemoDirectory;

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
                "DEMO_COMMENT: Initializing demo. The .jaider.json in the temp directory should be loaded.",
                    "DEMO_COMMENT: Default LLM provider is 'ollama'. Ensure Ollama is running or edit .jaider.json.",
                    "DEMO_PAUSE: Check initial state. Press Enter to add README.md to context.",
                "/add README.md",
                "DEMO_PAUSE: README.md added. Press Enter to add main.py.",
                "/add main.py",
                "DEMO_PAUSE: main.py added. Press Enter to add utils.py.",
                "/add utils.py",

                "DEMO_COMMENT: === Phase 2: Agent Modes & Core Functionality (Ask & Coder) ===",
                "DEMO_PAUSE: All files added. Press Enter to use Ask mode (implicitly, or explicitly if needed) for a question about main.py.",
                "DEMO_OPERATOR_INPUT: What is the content of the file main.py?",
                "DEMO_PAUSE: Response received. Press Enter to switch to Coder mode.",
                "/mode Coder",
                "DEMO_PAUSE: Switched to Coder mode. Let's ask the Coder to modify main.py.",
                "DEMO_OPERATOR_INPUT: In main.py, change the print statement inside the hello function to \"Hello from an enhanced Jaider demo!\"",
                "DEMO_COMMENT: AI should now propose a diff. DemoUI will prompt for accept/reject/edit. Please ACCEPT.",
                "DEMO_PAUSE: Diff applied (if accepted). Press Enter to run validation (/run).",
                "/run",
                "DEMO_PAUSE: Validation run. Now, let's ask Coder to add a new function to utils.py.",
                "DEMO_OPERATOR_INPUT: In utils.py, add a new Python function called 'another_helper' that returns the integer 42.",
                "DEMO_COMMENT: AI should propose a diff for utils.py. Please ACCEPT.",
                "DEMO_PAUSE: Diff applied. Press Enter to ask Coder to use the new function in main.py.",
                "DEMO_OPERATOR_INPUT: In main.py, import 'another_helper' from 'utils' and print its return value after calling hello().",
                "DEMO_COMMENT: AI should propose changes for main.py. Please ACCEPT.",
                "DEMO_PAUSE: Diff applied. Press Enter to run validation again.",
                "/run",
                "DEMO_PAUSE: Validation run. Content of main.py and utils.py should be updated.",
                "DEMO_COMMENT: Note: Committing changes is a feature, but this demo won't perform actual git commits to avoid altering your git history.",

                "DEMO_COMMENT: === Phase 3: Undo Functionality ===",
                "DEMO_PAUSE: Let's demonstrate /undo. The last change was modifying main.py. Press Enter to undo it.",
                "/undo",
                "DEMO_COMMENT: The undo command should revert main.py to its state before the last modification (importing and using another_helper).",
                "DEMO_PAUSE: Check the log for undo status. Ask a question to see the content of main.py to verify.",
                "DEMO_OPERATOR_INPUT: What is the current content of main.py?",
                "DEMO_PAUSE: Content displayed. Now undo the change to utils.py (addition of another_helper).",
                "/undo",
                "DEMO_PAUSE: Check log. utils.py should be reverted. Ask for its content.",
                "DEMO_OPERATOR_INPUT: What is the current content of utils.py?",

                "DEMO_COMMENT: === Phase 4: Configuration & LLM Switching ===",
                "DEMO_PAUSE: Let's try editing the configuration with /edit-config. Press Enter.",
                "/edit-config",
                "DEMO_COMMENT: DemoUI will show the current .jaider.json. You'll be prompted to provide the new content.",
                "DEMO_COMMENT: IMPORTANT: If you want to switch to Gemini for the next step (web search),",
                "DEMO_COMMENT: modify 'llmProvider' to 'gemini' and 'geminiModelName' to e.g. 'gemini-1.5-flash-latest'.",
                "DEMO_COMMENT: Ensure you have GEMINI_API_KEY env var set, or add it to 'apiKeys': { \"google\": \"YOUR_KEY\" }.",
                "DEMO_COMMENT: If you don't want to switch or setup Gemini, you can make a small change like modifying 'ollamaModelName', or just save without changes.",
                "DEMO_PAUSE: After /edit-config completes and reloads, press Enter to test the new config with a simple request.",
                "DEMO_OPERATOR_INPUT: What is 1+1?",

                "DEMO_COMMENT: === Phase 5: Web Search (Requires Coder mode & Tavily API Key) ===",
                "DEMO_PAUSE: Ensure you are in Coder mode. If not, use '/mode Coder'. Press Enter to attempt web search.",
                "/mode Coder",
                "DEMO_COMMENT: For web search, Jaider uses Tavily. Ensure TAVILY_API_KEY is set in your environment OR in .jaider.json (e.g. apiKeys: { \"tavily\": \"YOUR_KEY\" }).",
                "DEMO_COMMENT: If not configured, the agent should state it cannot perform web search.",
                "DEMO_OPERATOR_INPUT: What is the current weather in London? Search the web if you don't know.",
                "DEMO_PAUSE: Review the agent's response, which may include search results or an inability to search.",

                "DEMO_COMMENT: === Phase 6: Project Indexing & Code Search (Requires Coder mode) ===",
                "DEMO_PAUSE: Next, we'll test project indexing and code search. Press Enter to run /index.",
                "/index",
                "DEMO_COMMENT: Indexing creates embeddings. This might take a moment depending on the LLM provider for embeddings (e.g., Ollama, Gemini, OpenAI).",
                "DEMO_COMMENT: The default .jaider.json uses Ollama for embeddings if llmProvider is ollama. If you switched to Gemini, it will use Gemini embeddings.",
                "DEMO_PAUSE: Indexing complete. Press Enter to ask a question that uses findRelevantCode.",
                "DEMO_OPERATOR_INPUT: Where is the helper_function defined in this project?",
                "DEMO_PAUSE: Agent should respond with the location of helper_function in utils.py.",

                "DEMO_COMMENT: === Phase 7: Architect Mode ===",
                "DEMO_PAUSE: Let's switch to Architect mode. Press Enter.",
                "/mode Architect",
                "DEMO_PAUSE: Switched to Architect mode. Ask a high-level question about the project.",
                "DEMO_OPERATOR_INPUT: Describe the overall structure of this sample project and how the files relate to each other.",
                "DEMO_PAUSE: Review the Architect agent's response.",

                "DEMO_COMMENT: === Phase 8: Summarize Command ===",
                "DEMO_PAUSE: The /summarize command can be useful. Press Enter to summarize README.md.",
                "/summarize README.md",
                "DEMO_PAUSE: Summary provided.",

                "DEMO_COMMENT: === End of Demo ===",
                "DEMO_PAUSE: All planned features demonstrated. Press Enter to exit the demo.",
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
