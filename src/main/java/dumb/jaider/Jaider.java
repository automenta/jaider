package dumb.jaider;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.PatchFailedException; // Added for specific patch error handling
import com.github.difflib.unifieddiff.UnifiedDiff;
import com.github.difflib.unifieddiff.UnifiedDiffReader;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.google.vertexai.VertexAiGeminiChatModel; // Added for Gemini
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.tools.web.search.WebSearchEngine;
import dev.langchain4j.tools.web.search.WebSearchResults;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.eclipse.jgit.api.Git;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


public class Jaider {

    static void main(String[] args) {
        try {
            new App(new TUI()).run();
        } catch (Exception e) {
            System.err.println("Jaider failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String text(ChatMessage msg) {
        return (msg instanceof UserMessage u) ? u.singleText() : ((AiMessage) msg).text();
    }

    private static UnifiedDiff diffReader(String diff) throws IOException {
        return UnifiedDiffReader.parseUnifiedDiff(new ByteArrayInputStream(diff.getBytes()));
    }

    interface UI {
        void init(App app) throws IOException;

        void redraw(Model model);

        CompletableFuture<Boolean> requestConfirmation(String title, String text);

        CompletableFuture<DiffInteractionResult> requestDiffInteraction(String diff);

        CompletableFuture<String> requestConfigEdit(String currentConfig);

        void close() throws IOException;
    }

    interface Agent {
        String name();

        Response<AiMessage> act(List<ChatMessage> messages);

        Set<Object> getTools();
    }

    record DiffInteractionResult(boolean accepted, boolean edited, String newDiff) {
    }

    // Text-UI Implementation (Lanterna) ---
    static class TUI implements UI {
        private MultiWindowTextGUI gui;
        private ActionListBox contextListBox;
        private Panel logListBox;
        private Label statusBar;

        @Override
        public void init(App app) throws IOException {
            var terminal = new DefaultTerminalFactory().createTerminal();
            Screen screen = new TerminalScreen(terminal);
            screen.startScreen();
            var window = new BasicWindow("Jaider - The AI Programming Assistant");
            window.setHints(Arrays.asList(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));

            var mainPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
            contextListBox = new ActionListBox(new TerminalSize(35, 0));
            mainPanel.addComponent(contextListBox.withBorder(Borders.singleLine("Context")));
            logListBox = Panels.vertical();
            mainPanel.addComponent(logListBox.withBorder(Borders.singleLine("Log")));

            var inputBox = new TextBox(new TerminalSize(100, 1));
            inputBox.setInputFilter((interactable, keyStroke) -> {
                if (keyStroke.getKeyType() == KeyType.Enter) {
                    app.handleUserInput(inputBox.getText());
                    inputBox.setText("");
                }
                return true;
            });
            statusBar = new Label("");
            var bottomPanel = new Panel(new LinearLayout(Direction.HORIZONTAL)).addComponent(inputBox).addComponent(statusBar);

            var contentPane = new Panel(new BorderLayout()).addComponent(mainPanel, BorderLayout.Location.CENTER).addComponent(bottomPanel, BorderLayout.Location.BOTTOM);
            window.setComponent(contentPane);
            window.setFocusedInteractable(inputBox);

            gui = new MultiWindowTextGUI(new SeparateTextGUIThread.Factory(), screen);
            gui.addWindowAndWait(window);
        }

        @Override
        public void redraw(Model model) {
            if (gui == null) return;
            gui.getGUIThread().invokeLater(() -> {
                contextListBox.clearItems();
                model.filesInContext.forEach(p -> contextListBox.addItem(model.projectDir.relativize(p).toString(), null));
                logListBox.removeAllComponents();
                for (var msg : model.logMessages) {
                    var text = text(msg);
                    if (text == null || text.isBlank()) continue;

                    var l = new Label(String.format("[%s] %s", msg.type().name(), text));
                    if (msg instanceof UserMessage)
                        l.setForegroundColor(TextColor.ANSI.CYAN);
                    else if (msg instanceof AiMessage aim) {
                        if (aim.hasToolExecutionRequests()) {
                            l.setText(String.format("[Agent] Wants to use tool: %s", aim.toolExecutionRequests().getFirst().name()));
                            l.setForegroundColor(TextColor.ANSI.YELLOW);
                        } else
                            l.setForegroundColor(TextColor.ANSI.GREEN);
                    }
                    logListBox.addComponent(l);
                }
                //if (logListBox.getChildrenList().size() > 0) logListBox.setSelectedIndex(logListBox.getItemCount() - 1);
                statusBar.setText(String.format(" | Mode: %s | %s | Tokens: %d", model.agentMode, model.statusBarText, model.currentTokenCount));
            });
        }

        @Override
        public CompletableFuture<Boolean> requestConfirmation(String title, String text) {
            var future = new CompletableFuture<Boolean>();
            gui.getGUIThread().invokeLater(() -> future.complete(
                    MessageDialog.showMessageDialog(gui, title, text, MessageDialogButton.Yes, MessageDialogButton.No) == MessageDialogButton.Yes));
            return future;
        }

        @Override
        public CompletableFuture<DiffInteractionResult> requestDiffInteraction(String diff) {
            var future = new CompletableFuture<DiffInteractionResult>();
            gui.getGUIThread().invokeLater(() -> {
                var dialog = new BasicWindow("Apply Diff?");
                var content = new Panel(new LinearLayout(Direction.VERTICAL));
                content.addComponent(new Label("Agent wants to apply these changes:"));

                var diffPanel = new Panel();
                diff.lines().forEach(line -> {
                    var lineLabel = new Label(line);
                    if (line.startsWith("+")) lineLabel.setForegroundColor(TextColor.ANSI.GREEN);
                    else if (line.startsWith("-")) lineLabel.setForegroundColor(TextColor.ANSI.RED);
                    else if (line.startsWith("@")) lineLabel.setForegroundColor(TextColor.ANSI.CYAN);
                    diffPanel.addComponent(lineLabel);
                });
                content.addComponent(diffPanel.withBorder(Borders.singleLine()));

                var buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                buttonPanel.addComponent(new Button("Accept", () -> {
                    future.complete(new DiffInteractionResult(true, false, diff));
                    dialog.close();
                }));
                buttonPanel.addComponent(new Button("Reject", () -> {
                    future.complete(new DiffInteractionResult(false, false, null));
                    dialog.close();
                }));
                buttonPanel.addComponent(new Button("Edit", () -> {
                    dialog.close();
                    requestConfigEdit(diff).thenAccept(editedDiff -> {
                        if (editedDiff != null) future.complete(new DiffInteractionResult(true, true, editedDiff));
                        else future.complete(new DiffInteractionResult(false, false, null)); // User cancelled edit
                    });
                }));
                content.addComponent(buttonPanel);
                dialog.setComponent(content);
                gui.addWindow(dialog);
            });
            return future;
        }

        @Override
        public CompletableFuture<String> requestConfigEdit(String currentConfig) {
            var future = new CompletableFuture<String>();
            gui.getGUIThread().invokeLater(() -> {
                var editorDialog = new BasicWindow("Editor");
                var editorPanel = new Panel(new BorderLayout());
                var configTextBox = new TextBox(currentConfig, TextBox.Style.MULTI_LINE);
                configTextBox.setPreferredSize(new TerminalSize(80, 25));
                editorPanel.addComponent(configTextBox, BorderLayout.Location.CENTER);

                var buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                buttonPanel.addComponent(new Button("Save", () -> {
                    future.complete(configTextBox.getText());
                    editorDialog.close();
                }));
                buttonPanel.addComponent(new Button("Cancel", () -> {
                    future.complete(null);
                    editorDialog.close();
                }));
                editorPanel.addComponent(buttonPanel, BorderLayout.Location.BOTTOM);
                editorDialog.setComponent(editorPanel);
                gui.addWindow(editorDialog);
            });
            return future;
        }

        @Override
        public void close() throws IOException {
            if (gui != null) gui.getScreen().stopScreen();
        }
    }

    static class Config {
        final Path configFile;
        final Map<String, String> apiKeys = new HashMap<>();
        String llmProvider = "ollama", runCommand; // Renamed testCommand to runCommand
        String ollamaBaseUrl = "http://localhost:11434";
        String ollamaModelName = "llamablit";
        String genericOpenaiBaseUrl = "http://localhost:8080/v1";
        String genericOpenaiModelName = "local-model";
        String genericOpenaiApiKey = ""; // Typically "EMPTY" or "NA" for local models if header is mandatory
        String geminiApiKey = "";
        String geminiModelName = "gemini-1.5-flash-latest"; // Default Gemini model
        String tavilyApiKey = "";

        Config(Path projectDir) {
            this.configFile = projectDir.resolve(".jaider.json");
            load();
        }

        void load() {
            try {
                if (!Files.exists(configFile)) createDefaultConfig();
                var j = new JSONObject(Files.readString(configFile));
                llmProvider = j.optString("llmProvider", this.llmProvider);
                ollamaBaseUrl = j.optString("ollamaBaseUrl", this.ollamaBaseUrl);
                ollamaModelName = j.optString("ollamaModelName", this.ollamaModelName);
                genericOpenaiBaseUrl = j.optString("genericOpenaiBaseUrl", this.genericOpenaiBaseUrl);
                genericOpenaiModelName = j.optString("genericOpenaiModelName", this.genericOpenaiModelName);
                genericOpenaiApiKey = j.optString("genericOpenaiApiKey", this.genericOpenaiApiKey);
                geminiApiKey = j.optString("geminiApiKey", this.geminiApiKey);
                geminiModelName = j.optString("geminiModelName", this.geminiModelName);
                tavilyApiKey = j.optString("tavilyApiKey", this.tavilyApiKey);
                // Backward compatibility: read "testCommand" if "runCommand" is not present
                if (j.has("runCommand")) {
                    runCommand = j.optString("runCommand", "");
                } else {
                    runCommand = j.optString("testCommand", ""); // Fallback to testCommand
                }
                var keys = j.optJSONObject("apiKeys");
                if (keys != null) keys.keySet().forEach(key -> apiKeys.put(key, keys.getString(key)));
            } catch (Exception e) { /* Use defaults on failure, fields already have defaults */ }
        }

        private void createDefaultConfig() throws IOException {
            var defaultKeys = new JSONObject();
            defaultKeys.put("openai", "YOUR_OPENAI_API_KEY");
            defaultKeys.put("anthropic", "YOUR_ANTHROPIC_API_KEY");
            defaultKeys.put("google", "YOUR_GOOGLE_API_KEY");
            // Note: genericOpenaiApiKey is stored separately, not in the 'apiKeys' map by default
            var defaultConfig = new JSONObject();
            defaultConfig.put("llmProvider", llmProvider);
            defaultConfig.put("ollamaBaseUrl", ollamaBaseUrl);
            defaultConfig.put("ollamaModelName", ollamaModelName);
            defaultConfig.put("genericOpenaiBaseUrl", genericOpenaiBaseUrl);
            defaultConfig.put("genericOpenaiModelName", genericOpenaiModelName);
            defaultConfig.put("genericOpenaiApiKey", genericOpenaiApiKey);
            defaultConfig.put("geminiApiKey", geminiApiKey);
            defaultConfig.put("geminiModelName", geminiModelName);
            defaultConfig.put("tavilyApiKey", tavilyApiKey);
            defaultConfig.put("runCommand", runCommand == null ? "" : runCommand); // Use runCommand
            defaultConfig.put("apiKeys", defaultKeys); // For OpenAI, Anthropic, Google keys (geminiApiKey is separate)
            Files.writeString(configFile, defaultConfig.toString(2));
        }

        void save(String newConfig) throws IOException {
            // When saving, parse the newConfig string and write it.
            // Then, reload to ensure the in-memory config reflects the saved file.
            Files.writeString(configFile, new JSONObject(newConfig).toString(2));
            load();
        }

        String getApiKey(String provider) {
            return apiKeys.getOrDefault(provider, System.getenv(provider.toUpperCase() + "_API_KEY"));
        }

        String readForEditing() throws IOException {
            // If file exists, load it to show the most accurate current state for editing.
            // Otherwise, construct a JSON string from current in-memory config settings.
            JSONObject configToEdit;
            if (Files.exists(configFile)) {
                configToEdit = new JSONObject(Files.readString(configFile));
                // Ensure all potentially new fields are present, falling back to current in-memory defaults if missing in file
                if (!configToEdit.has("llmProvider")) configToEdit.put("llmProvider", llmProvider);
                if (!configToEdit.has("ollamaBaseUrl")) configToEdit.put("ollamaBaseUrl", ollamaBaseUrl);
                if (!configToEdit.has("ollamaModelName")) configToEdit.put("ollamaModelName", ollamaModelName);
                if (!configToEdit.has("genericOpenaiBaseUrl")) configToEdit.put("genericOpenaiBaseUrl", genericOpenaiBaseUrl);
                if (!configToEdit.has("genericOpenaiModelName")) configToEdit.put("genericOpenaiModelName", genericOpenaiModelName);
                if (!configToEdit.has("genericOpenaiApiKey")) configToEdit.put("genericOpenaiApiKey", genericOpenaiApiKey);
                if (!configToEdit.has("geminiApiKey")) configToEdit.put("geminiApiKey", geminiApiKey);
                if (!configToEdit.has("geminiModelName")) configToEdit.put("geminiModelName", geminiModelName);
                if (!configToEdit.has("tavilyApiKey")) configToEdit.put("tavilyApiKey", tavilyApiKey);
                if (configToEdit.has("testCommand") && !configToEdit.has("runCommand")) { // Backward compatibility display
                    configToEdit.put("runCommand", configToEdit.getString("testCommand"));
                    configToEdit.remove("testCommand");
                }
                if (!configToEdit.has("runCommand")) configToEdit.put("runCommand", runCommand == null ? "" : runCommand);
                if (!configToEdit.has("apiKeys")) configToEdit.put("apiKeys", new JSONObject(apiKeys));
            } else {
                configToEdit = new JSONObject();
                configToEdit.put("llmProvider", llmProvider);
                configToEdit.put("ollamaBaseUrl", ollamaBaseUrl);
                configToEdit.put("ollamaModelName", ollamaModelName);
                configToEdit.put("genericOpenaiBaseUrl", genericOpenaiBaseUrl);
                configToEdit.put("genericOpenaiModelName", genericOpenaiModelName);
                configToEdit.put("genericOpenaiApiKey", genericOpenaiApiKey);
                configToEdit.put("geminiApiKey", geminiApiKey);
                configToEdit.put("geminiModelName", geminiModelName);
                configToEdit.put("tavilyApiKey", tavilyApiKey);
                configToEdit.put("runCommand", runCommand == null ? "" : runCommand);
                configToEdit.put("apiKeys", new JSONObject(apiKeys));
            }
            return configToEdit.toString(2);
        }
    }

    static class Model {
        public static final int LOG_CAPACITY = 200;

        final Path projectDir = Paths.get("").toAbsolutePath();
        final Set<Path> filesInContext = new HashSet<>();
        final List<ChatMessage> logMessages = new ArrayList<>();
        String statusBarText = "Jaider initialized. /help for commands.";
        int currentTokenCount = 0;
        EmbeddingStore<TextSegment> embeddingStore;
        boolean isIndexed = false;
        String lastAppliedDiff = null;
        String agentMode = "Coder";

        void addLog(ChatMessage message) {
            var t = ((AiMessage) message).text();
            if (t == null || t.isBlank()) return;
            logMessages.add(message);
            if (logMessages.size() > LOG_CAPACITY) logMessages.removeFirst();
        }

        String getFileContext() {
            return filesInContext.isEmpty() ?
                "No files are in context. Use /add or the `findRelevantCode` tool." :
                filesInContext.stream().map(this::readFileContent).collect(Collectors.joining("\n\n"));
        }

        private String readFileContent(Path path) {
            try {
                return String.format("--- %s ---\n%s", projectDir.relativize(path), Files.readString(path));
            } catch (IOException e) {
                return String.format("Error reading file %s: %s", path, e.getMessage());
            }
        }
    }

    static abstract class AbstractAgent implements Agent {
        protected final JaiderAiService ai;
        protected final Set<Object> tools;

        public AbstractAgent(ChatLanguageModel model, ChatMemory memory, Set<Object> tools, String systemPrompt) {
            this.tools = tools;
            this.ai = AiServices.builder(JaiderAiService.class)
                .chatLanguageModel(model)
                .chatMemory(memory)
                .tools(tools.toArray())
                .systemMessageProvider(vars -> systemPrompt)
                .build();
        }

        @Override
        public Response<AiMessage> act(List<ChatMessage> messages) {
            return ai.act(messages);
        }

        @Override
        public Set<Object> getTools() {
            return this.tools;
        }

        protected interface JaiderAiService {
            Response<AiMessage> act(List<ChatMessage> messages);
        }
    }

    static class CoderAgent extends AbstractAgent {
        public CoderAgent(ChatLanguageModel model, ChatMemory memory, Tools availableTools) {
            super(model, memory, Set.of(availableTools),
                    """
                            You are Jaider, an expert AI programmer. Your goal is to fully complete the user's request.
                            Follow this sequence rigidly:
                            1. THINK: First, write down your step-by-step plan. Use tools like `findRelevantCode`, `readFile` and `searchWeb` to understand the project and gather information.
                            2. MODIFY: Propose a change by using the `applyDiff` tool. This is the only way you can alter code.
                            3. VERIFY: After the user approves your diff, you MUST use the `runValidationCommand` tool to verify your changes, if a validation command is configured.
                               The `runValidationCommand` tool will return a JSON string. This JSON will contain:
                               `exitCode`: An integer representing the command's exit code.
                               `success`: A boolean, true if exitCode is 0, false otherwise.
                               `output`: A string containing the standard output and error streams from the command.
                               Analyze this JSON to determine if your changes were successful. If 'success' is false or exitCode is non-zero, use the 'output' to debug.
                            4. FIX: If validation fails, analyze the error and go back to step 2 (MODIFY).
                            5. COMMIT: Once the request is complete and verified (e.g. validation passed or was not applicable), your final action MUST be to use the `commitChanges` tool with a clear, conventional commit message.""");
        }

        @Override
        public String name() {
            return "Coder";
        }
    }

    static class ArchitectAgent extends AbstractAgent {
        public ArchitectAgent(ChatLanguageModel model, ChatMemory memory, Tools availableTools) {
            super(model, memory, availableTools.getReadOnlyTools(),
                    "You are a principal software architect. Your goal is to answer questions about the codebase, suggest design patterns, and discuss high-level architectural trade-offs.\n" +
                            "You should use tools like `findRelevantCode` to analyze the codebase. You MUST NOT modify any files or run any tests.");
        }

        @Override
        public String name() {
            return "Architect";
        }
    }

    static class AskAgent extends AbstractAgent {
        public AskAgent(ChatLanguageModel model, ChatMemory memory) {
            super(model, memory, Set.of(), "You are a helpful assistant. Answer the user's questions clearly and concisely. You do not have access to any tools.");
        }

        @Override
        public String name() {
            return "Ask";
        }
    }

    static class Tools {
        private final Model model;
        private final Config config;
        private final EmbeddingModel embeddingModel;

        Tools(Model model, Config config, EmbeddingModel embeddingModel) {
            this.model = model;
            this.config = config;
            this.embeddingModel = embeddingModel;
        }

        public Set<Object> getReadOnlyTools() {
            // findRelevantCode and searchWeb are considered read-only.
            // Other tools like readFile might also be, but this is a specific list for ArchitectAgent.
            // For now, returning 'this' means all @Tool methods in this class are available.
            // If more fine-grained control is needed, specific tool instances can be returned.
            return Set.of(this);
        }

        @dev.langchain4j.agent.tool.Tool("Searches the web for the given query using Tavily.")
        public String searchWeb(String query) {
            if (config.tavilyApiKey == null || config.tavilyApiKey.isBlank() || config.tavilyApiKey.contains("YOUR_")) {
                return "Error: Tavily API key not configured. Please set tavilyApiKey in .jaider.json.";
            }
            try {
                WebSearchEngine tavilySearchEngine = TavilyWebSearchEngine.builder()
                        .apiKey(config.tavilyApiKey)
                        .build();
                WebSearchResults results = tavilySearchEngine.search(query);
                if (results == null || results.answers() == null || results.answers().isEmpty()) {
                    return "No results found for: " + query;
                }
                return results.answers().stream()
                        .map(answer -> "Source: " + answer.url() + "\nTitle: " + answer.title() + "\nSnippet: " + answer.snippet())
                        .collect(Collectors.joining("\n\n---\n\n"));
            } catch (Exception e) {
                return "Error performing web search for '" + query + "': " + e.getClass().getSimpleName() + " - " + e.getMessage();
            }
        }

        @dev.langchain4j.agent.tool.Tool("Applies a code change using the unified diff format.")
        public String applyDiff(String diff) {
            try {
                var unifiedDiff = diffReader(diff);

                for (var fileDiff : unifiedDiff.getFiles()) {
                    var fileName = fileDiff.getFromFile();
                    var filePath = model.projectDir.resolve(fileName);

                    // Ensure the file is in context, unless it's a new file
                    var isNewFile = !Files.exists(filePath);
                    if (!isNewFile && !model.filesInContext.contains(filePath)) {
                        return "Error: Cannot apply diff to a file not in context: " + fileName;
                    }

                    List<String> originalLines;
                    try {
                        originalLines = isNewFile ? new ArrayList<>() : Files.readAllLines(filePath);
                    } catch (IOException e) {
                        model.lastAppliedDiff = null;
                        return "Error reading original file '" + fileName + "' for diff application: " + e.getMessage();
                    }

                    var patch = fileDiff.getPatch();
                    try {
                        List<String> patchedLines = DiffUtils.patch(originalLines, patch);
                        Files.write(filePath, patchedLines); // Write the patched lines

                        if (isNewFile) { // If the file was new, add it to context.
                            model.filesInContext.add(filePath);
                        }
                    } catch (PatchFailedException pfe) {
                        model.lastAppliedDiff = null;
                        return "Error applying diff to file '" + fileName + "': Patch application failed. Details: " + pfe.getMessage();
                    } catch (IOException e) {
                        model.lastAppliedDiff = null;
                        return "Error writing patched file '" + fileName + "': " + e.getMessage();
                    }
                }

                model.lastAppliedDiff = diff; // Set only if all files in the diff are processed successfully
                return "Diff applied successfully to all specified files.";
            } catch (IOException e) { // Catch errors from diffReader or other general IO
                model.lastAppliedDiff = null;
                return "Error processing diff input: " + e.getMessage();
            } catch (Exception e) { // Catch any other unexpected errors
                model.lastAppliedDiff = null;
                return "An unexpected error occurred while applying diff: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            }
        }

        @dev.langchain4j.agent.tool.Tool("Reads the complete content of a file.")
        public String readFile(String fileName) {
            return model.readFileContent(model.projectDir.resolve(fileName));
        }

        @dev.langchain4j.agent.tool.Tool("Runs the project's configured validation command (e.g., tests, linter, build). Usage: runValidationCommand <optional_arguments_for_command>")
        public String runValidationCommand(String commandArgs) { // commandArgs currently unused, future enhancement
            JSONObject resultJson = new JSONObject();
            if (config.runCommand == null || config.runCommand.isBlank()) {
                resultJson.put("error", "No validation command configured in .jaider.json (key: runCommand).");
                resultJson.put("success", false);
                resultJson.put("exitCode", -1);
                return resultJson.toString();
            }

            String commandToExecute = config.runCommand;
            // TODO: Consider how commandArgs should be integrated if provided.
            // For now, we execute the command from config directly.

            try {
                ProcessBuilder pb = new ProcessBuilder(commandToExecute.split("\\s+"))
                        .directory(model.projectDir.toFile())
                        .redirectErrorStream(true);
                Process process = pb.start();

                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                int exitCode = process.waitFor();

                resultJson.put("exitCode", exitCode);
                resultJson.put("success", exitCode == 0);
                resultJson.put("output", output.toString().trim());

            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interruption status
                resultJson.put("error", "Failed to run command '" + commandToExecute + "': " + e.getClass().getSimpleName() + " - " + e.getMessage());
                resultJson.put("success", false);
                resultJson.put("exitCode", -1); // Indicate execution failure
            } catch (Exception e) {
                 resultJson.put("error", "An unexpected error occurred while running command '" + commandToExecute + "': " + e.getClass().getSimpleName() + " - " + e.getMessage());
                 resultJson.put("success", false);
                 resultJson.put("exitCode", -1); // Indicate execution failure
            }
            return resultJson.toString();
        }

        @dev.langchain4j.agent.tool.Tool("Commits all staged changes with a given message.")
        public String commitChanges(String message) {
            try (var git = Git.open(model.projectDir.resolve(".git").toFile())) {
                git.add().addFilepattern(".").call();
                git.commit().setMessage(message).call();
                return "Changes committed successfully.";
            } catch (Exception e) {
                return "Git commit failed: " + e.getMessage();
            }
        }

        @dev.langchain4j.agent.tool.Tool("Finds relevant code snippets from the entire indexed codebase.")
        public String findRelevantCode(String query) {
            if (embeddingModel == null) {
                return "Error: Embedding model is not available. Cannot search code. Ensure LLM provider that supports embeddings is configured (e.g. OpenAI).";
            }
            if (model.embeddingStore == null) {
                return "Project not indexed. Run /index first.";
            }
            try {
                var queryEmbedding = embeddingModel.embed(query).content();
                var r = EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding).build();
                var relevant = model.embeddingStore.search(r);
                if (relevant == null || relevant.matches().isEmpty()) {
                    return "No relevant code found in the index for: " + query;
                }
                return relevant.matches().stream()
                        .map(match -> String.format("--- From %s (Score: %.4f) ---\n%s",
                                match.embedded().metadata().getString("file_path"),
                                match.score(),
                                match.embedded().text()))
                        .collect(Collectors.joining("\n\n---\n\n"));
            } catch (Exception e) {
                return "Error searching for relevant code: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            }
        }
    }

    static class App {
        private final UI ui;
        private final Model model = new Model();
        private final Config config = new Config(model.projectDir);
        private final ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);
        private final Map<String, Agent> agents = new HashMap<>();
        private EmbeddingModel embeddingModel;
        private State currentState = State.IDLE;
        private Tokenizer tokenizer;
        private Agent currentAgent;
        private ChatLanguageModel chatModel;

        App(UI ui) {
            this.ui = ui;
            update();
        }

        private synchronized void update() {
            if ("ollama".equalsIgnoreCase(config.llmProvider)) {
                setupOllama();
            } else if ("genericOpenai".equalsIgnoreCase(config.llmProvider)) {
                setupGenericOpenAI();
            } else if ("gemini".equalsIgnoreCase(config.llmProvider)) {
                setupGemini();
            } else if ("openai".equalsIgnoreCase(config.llmProvider)) {
                // setupOpenAI(); // This is still commented out
                model.addLog(AiMessage.from("[Jaider] OpenAI provider selected but setupOpenAI() is currently commented out. No model initialized."));
                initializeFallbackTokenizer(); // Ensure tokenizer is not null
            } else {
                model.addLog(AiMessage.from(String.format("[Jaider] WARNING: Unknown llmProvider '%s' in config. Defaulting to Ollama.", config.llmProvider)));
                setupOllama(); // Default fallback
            }

            // TODO: Initialize embeddingModel based on provider, or make it configurable separately.
            // For now, it remains null unless setupOpenAI() was to be uncommented and run.

            var tools = new Tools(model, config, embeddingModel); // embeddingModel might be null
            agents.put("Coder", new CoderAgent(chatModel, chatMemory, tools));
            agents.put("Architect", new ArchitectAgent(chatModel, chatMemory, tools));
            agents.put("Ask", new AskAgent(chatModel, chatMemory));
            this.currentAgent = agents.get("Coder");
        }

        private void setupOllama() {
            try {
                chatModel = OllamaChatModel.builder()
                        .baseUrl(config.ollamaBaseUrl)
                        .modelName(config.ollamaModelName)
                        .build();
                // OllamaChatModel itself implements Tokenizer (from langchain4j 0.27.0+)
                this.tokenizer = chatModel;
                model.addLog(AiMessage.from(String.format("[Jaider] Ollama model '%s' initialized successfully from %s.", config.ollamaModelName, config.ollamaBaseUrl)));
            } catch (Exception e) {
                model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Ollama model '%s' from %s. Error: %s. Jaider's functionality will be severely limited. Check Ollama server and config.", config.ollamaModelName, config.ollamaBaseUrl, e.getMessage())));
                initializeFallbackTokenizer();
            }
        }

        private void setupGenericOpenAI() {
            try {
                // OllamaChatModel can be used for OpenAI-compatible APIs.
                // API key handling: If genericOpenaiApiKey is non-empty, it implies Bearer token auth.
                // However, OllamaChatModel.builder() doesn't have a direct .apiKey() or .customHeader() method.
                // This would require a custom client or a different Langchain4j model class for robust API key support.
                // For now, this setup assumes the generic API either needs no key, or uses one via other means (e.g. baked into URL or proxy).
                // If config.genericOpenaiApiKey is set, a log message will indicate it might not be used by this builder.
                var builder = OllamaChatModel.builder()
                        .baseUrl(config.genericOpenaiBaseUrl)
                        .modelName(config.genericOpenaiModelName);

                // TODO: Proper API Key handling for genericOpenai.
                // The OllamaChatModel builder does not directly support arbitrary headers for API keys.
                // This might require using a different client or extending functionality.
                // For instance, one might need to implement a custom dev.langchain4j.model.chat.ChatLanguageModel
                // or use lower-level HTTP client configurations if available.
                if (config.genericOpenaiApiKey != null && !config.genericOpenaiApiKey.isEmpty()) {
                    model.addLog(AiMessage.from("[Jaider] INFO: genericOpenaiApiKey is set in config, but the current OllamaChatModel builder may not use it for Bearer token authentication. API key might need to be included in baseUrl or handled by a proxy if required by the endpoint."));
                }

                chatModel = builder.build();
                this.tokenizer = chatModel; // Assuming the compatible model also provides Tokenizer
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
                // Assuming VertexAiGeminiChatModel is the correct class from langchain4j-google-vertex-ai
                // and it requires project ID and location, which are not in config yet.
                // This is a common setup for Vertex AI.
                // For direct Gemini API (genai.googleapis.com), the setup might be simpler,
                // but langchain4j-google-gemini might be needed.
                // For now, let's assume a simplified builder or one that infers project/location.
                // This part may need adjustment based on actual langchain4j-google-vertex-ai API.
                // A common pattern is .apiKey() for direct Gemini, or service account for Vertex.
                // Let's try a common pattern for VertexAI Gemini:
                this.chatModel = VertexAiGeminiChatModel.builder()
                        .project(System.getenv("GOOGLE_CLOUD_PROJECT")) // Or make this configurable
                        .location(System.getenv("GOOGLE_CLOUD_LOCATION")) // Or make this configurable
                        .modelName(config.geminiModelName) // e.g., "gemini-1.5-flash-latest", "gemini-pro"
                        // .apiKey(apiKey) // VertexAI often uses Application Default Credentials, but some new direct Gemini clients might use apiKey.
                                        // The VertexAiGeminiChatModel builder in 0.35.0 might not have .apiKey().
                                        // It might rely on GOOGLE_APPLICATION_CREDENTIALS env var.
                                        // Forcing use of apiKey here might be incorrect for VertexAI.
                                        // Let's assume for now that if geminiApiKey is set, it implies a direct Gemini endpoint behavior
                                        // which might not be what VertexAiGeminiChatModel is for.
                                        // This is a known point of complexity with Langchain4j Google integrations.
                                        // For now, we will proceed as if a simple .apiKey() setup is available or ADC works.
                        .build();

                // Tokenizer for Gemini: VertexAiGeminiChatModel should implement Tokenizer.
                this.tokenizer = this.chatModel;
                model.addLog(AiMessage.from(String.format("[Jaider] Gemini model '%s' initialized.", config.geminiModelName)));
                // A more specific log if API key was explicitly used by builder:
                // model.addLog(AiMessage.from(String.format("[Jaider] Gemini model '%s' initialized using configured API key.", config.geminiModelName)));

            } catch (Exception e) {
                model.addLog(AiMessage.from(String.format("[Jaider] CRITICAL ERROR: Failed to initialize Gemini model '%s'. Error: %s. Check API key, project/location settings, and GCP authentication.", config.geminiModelName, e.getMessage())));
                initializeFallbackTokenizer();
            }
        }

        private void initializeFallbackTokenizer() {
            if (this.tokenizer == null) {
                this.tokenizer = new Tokenizer() {
                    @Override public int estimateTokenCount(String text) { return text.length() / 4; } // Rough estimate
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
        //        private void setupOpenAI() {
        //            var apiKey = config.getApiKey(config.llmProvider);
        //            if (apiKey == null || apiKey.contains("YOUR_")) {
        //                model.addLog(AiMessage.from("[Jaider] WARNING: LLM API key not found. Functionality will be limited. Use /edit-config to set it."));
        //                apiKey = "DUMMY_KEY";
        //            }
        //
        //            chatModel = OpenAiChatModel.builder().apiKey(apiKey).build();
        //            embeddingModel = OpenAiEmbeddingModel.builder().apiKey(apiKey).build();
        //            var availableTools = new Tools(model, config, embeddingModel);
        //
        //            agents.put("Coder", new CoderAgent(chatModel, chatMemory, availableTools));
        //            agents.put("Architect", new ArchitectAgent(chatModel, chatMemory, availableTools));
        //            agents.put("Ask", new AskAgent(chatModel, chatMemory));
        //            tokenizer = new OpenAiTokenizer(); //TODO what about for other models?
        //        }

        void run() throws IOException {
            if (!isGitRepoClean()) return;
            loadSession();
            ui.init(this);
        }

        void handleUserInput(String input) {
            if (currentState != State.IDLE) {
                model.addLog(AiMessage.from("[Jaider] Please wait, I'm already working."));
                ui.redraw(model);
                return;
            }
            model.addLog(UserMessage.from(input));
            if (input.startsWith("/")) execute(input);
            else processAgentTurn();
            ui.redraw(model);
        }

        private void processAgentTurn() {
            currentState = State.AGENT_THINKING;
            model.statusBarText = "Agent is thinking...";
            updateTokenCount();
            ui.redraw(model);

            CompletableFuture.runAsync(() -> {
                var response = currentAgent.act(chatMemory.messages());
                var aiMessage = response.content();
                model.addLog(aiMessage);

                if (aiMessage.hasToolExecutionRequests())
                    handleToolExecution(aiMessage.toolExecutionRequests().getFirst());
                else finishTurn(null);
            }).exceptionally(e -> {
                finishTurn(AiMessage.from("[Error] " + e.getMessage()));
                return null;
            });
        }

        private void handleToolExecution(ToolExecutionRequest request) {
//            var executor = new DefaultToolExecutor(currentAgent.getTools(), request);
//            if (!executor.isToolFound()) {
//                finishTurn(request, String.format("Error: Tool '%s' is not available in the current '%s' mode.", request.name(), currentAgent.getModeName()));
//                return;
//            }

            if ("applyDiff".equals(request.name())) {
                currentState = State.WAITING_USER_CONFIRMATION;
                ui.requestDiffInteraction(request.arguments()).thenAccept(result -> {
                    if (result.accepted())
                        executeAndContinue(result.edited() ? ToolExecutionRequest.builder().arguments(result.newDiff()).build() : request);
                    else
                        finishTurn(request, "User rejected the diff.");
                });
            } else
                executeAndContinue(request);
        }

        private void executeAndContinue(ToolExecutionRequest request) {
            var toolResult = execute(request);
            model.addLog(AiMessage.from(String.format("[Tool Result: %s]", request.name())));

            var diffApplied = "applyDiff".equals(request.name()) && toolResult.startsWith("Diff applied");
            if (diffApplied && config.runCommand != null && !config.runCommand.isBlank()) { // Check runCommand
                currentState = State.WAITING_USER_CONFIRMATION;
                // Updated confirmation message
                ui.requestConfirmation("Run Validation?", String.format("Agent applied a diff. Run configured validation command (`%s`)?", config.runCommand)).thenAccept(approved -> {
                    if (approved) runValidationAndContinue(request, toolResult); // Renamed method
                    else finishTurn(request, toolResult + "\nUser chose not to run validation command.");
                });
            } else finishTurn(request, toolResult);
        }

        private String execute(ToolExecutionRequest request) {
            return new DefaultToolExecutor(currentAgent.getTools(), request).execute(request, currentAgent.getTools());
        }

        // Renamed from runTestsAndContinue to runValidationAndContinue
        private void runValidationAndContinue(ToolExecutionRequest originalRequest, String originalResult) {
            // Assuming runValidationCommand takes optional args, pass null or empty for now
            var validationResult = ((Tools) currentAgent.getTools()).runValidationCommand("");
            model.addLog(AiMessage.from("[Validation Result]\n" + validationResult)); // Log format updated for clarity
            finishTurn(originalRequest, originalResult + "\n---VALIDATION-COMMAND-RESULT---\n" + validationResult);
        }

        private void finishTurn(ToolExecutionRequest request, String result) {
            if (request != null)
                chatMemory.add(dev.langchain4j.data.message.ToolExecutionResultMessage.from(request, result));
            processAgentTurn();
        }

        private void finishTurn(ChatMessage message) {
            if (message != null) model.addLog(message);
            currentState = State.IDLE;
            model.statusBarText = "Awaiting input.";
            saveSession();
            ui.redraw(model);
        }

        private void execute(String input) {
            var parts = input.split("\\s+", 2);
            switch (parts[0]) {
                case "/add" -> add(parts[1].split("\\s+"));
                case "/undo" -> undo();
                case "/index" -> indexUpdate();
                case "/edit-config" -> editConfig();
                case "/mode" -> setAgent(parts.length > 1 ? parts[1] : "");
                case "/help" -> showHelp();
                case "/exit" -> exit();
                default -> model.addLog(AiMessage.from("[Jaider] Unknown command."));
            }
            updateTokenCount();
        }

        public synchronized void add(String[] pp) {
            Arrays.stream(pp).map(model.projectDir::resolve).forEach(model.filesInContext::add);
        }

        private void exit() {
            try {
                saveSession();
                ui.close();
            } catch (IOException e) {
            }
            System.exit(0);
        }

        public synchronized void setAgent(String mode) {
            var newAgent = agents.values().stream().filter(a -> a.name().equalsIgnoreCase(mode)).findFirst();
            if (newAgent.isPresent()) {
                currentAgent = newAgent.get();
                model.agentMode = currentAgent.name();
                model.addLog(AiMessage.from("[Jaider] Switched to " + model.agentMode + " mode."));
            } else {
                var availableModes = String.join(", ", agents.keySet());
                model.addLog(AiMessage.from("[Jaider] Unknown mode. Available modes: " + availableModes));
            }
        }

        private void editConfig() {
            currentState = State.WAITING_USER_CONFIRMATION;
            try {
                ui.requestConfigEdit(config.readForEditing()).thenAccept(newConfigStr -> {
                    if (newConfigStr != null) {
                        try {
                            config.save(newConfigStr);
                            update();
                            model.addLog(AiMessage.from("[Jaider] Configuration saved."));
                        } catch (IOException e) {
                            model.addLog(AiMessage.from("[Error] Failed to save config."));
                        }
                    }
                    finishTurn(null);
                });
            } catch (IOException e) {
                finishTurn(AiMessage.from("[Error] Could not read config file."));
            }
        }

        private void showHelp() {
            var helpTxt = """
                [Jaider] --- Jaider Help ---
                Jaider is an AI assistant with multiple modes of operation.

                MODES:
                Switch modes with `/mode <ModeName>`. Available modes: ?.
                - Coder: The default mode for writing and fixing code.
                - Architect: A read-only mode for high-level questions about the codebase.
                - Ask: A simple Q&A mode with no access to your files.

                COMMANDS:
                /add <files...>: Add files to the context.
                /undo: Attempts to revert the last applied change. For modified files, this uses `git checkout`
                       to revert them to their last committed state. Files newly created by the last diff
                       will be deleted. This may not perfectly reverse all patch types. Use with caution and review changes.
                /index: Create a searchable index of your project (for RAG).
                /edit-config: Open the .jaider.json configuration file.
                /help, /exit
            """;
            model.addLog(AiMessage.from(helpTxt));
        }

        /** TODO only update if messages has changed */
        private void updateTokenCount() {
            if (tokenizer == null) {
                model.addLog(AiMessage.from("[Jaider] ALERT: Tokenizer is not initialized. Token count cannot be updated. Model setup might have failed."));
                model.currentTokenCount = -1; // Indicate an error or unknown state
                return;
            }
            try {
                model.currentTokenCount = tokenizer.estimateTokenCountInMessages(chatMemory.messages());
            } catch (Exception e) {
                model.addLog(AiMessage.from("[Jaider] ERROR: Failed to estimate token count: " + e.getMessage()));
                model.currentTokenCount = -1; // Indicate an error
            }
        }

        public void undo() {
            if (model.lastAppliedDiff == null) {
                model.addLog(AiMessage.from("[Jaider] No change to undo."));
                return;
            }
            try (var git = Git.open(model.projectDir.resolve(".git").toFile())) {
                var unifiedDiff = diffReader(model.lastAppliedDiff);
                for (var fileDiff : unifiedDiff.getFiles()) {
                    Path filePath = model.projectDir.resolve(fileDiff.getFromFile());
                    String fileName = fileDiff.getFromFile();
                    boolean isNewFile = "/dev/null".equals(fileDiff.getOldName()); // Check if it was a new file

                    if (isNewFile) {
                        // If the file was newly created by the patch, undo means deleting it.
                        try {
                            if (Files.deleteIfExists(filePath)) {
                                model.addLog(AiMessage.from("[Jaider] Reverted (deleted) newly created file: " + fileName));
                                model.filesInContext.remove(filePath); // Also remove from context if it was added
                            } else {
                                model.addLog(AiMessage.from("[Jaider] Undo: File intended for deletion was already gone: " + fileName));
                            }
                        } catch (IOException e) {
                            model.addLog(AiMessage.from("[Error] Failed to delete newly created file for undo: " + fileName + " - " + e.getMessage()));
                        }
                    } else {
                        // If it was an existing file, revert it using git checkout.
                        try {
                            git.checkout().addPath(fileName).call();
                            model.addLog(AiMessage.from("[Jaider] Reverted to last commit for file: " + fileName));
                        } catch (Exception e) {
                            model.addLog(AiMessage.from("[Error] Failed to 'git checkout' file for undo: " + fileName + " - " + e.getMessage()));
                        }
                    }
                }
                model.addLog(AiMessage.from("[Jaider] Undo attempt finished. Note: For existing files, this reverts them to their last committed state. For files newly created by the last diff, they are deleted. Please review changes carefully."));
                model.lastAppliedDiff = null; // Clear the last diff after attempting undo
            } catch (Exception e) {
                model.addLog(AiMessage.from("[Error] Failed to process undo operation: " + e.getMessage()));
                // Still clear lastAppliedDiff as the undo attempt was made, though it might have failed globally.
                model.lastAppliedDiff = null;
            }
        }

        private void indexUpdate() {
            if (model.isIndexed) {
                model.addLog(AiMessage.from("[Jaider] Project is already indexed."));
                return;
            }
            currentState = State.AGENT_THINKING;
            model.statusBarText = "Indexing project...";
            ui.redraw(model);
            CompletableFuture.runAsync(() -> {
                try {
                    var documents = FileSystemDocumentLoader.loadDocuments(model.projectDir, (Path path) -> !path.toString().contains(".git"));
                    var splitter = DocumentSplitters.recursive(500, 100);
                    var segments = splitter.splitAll(documents);
                    model.embeddingStore = new InMemoryEmbeddingStore<>();
                    var embeddings = embeddingModel.embedAll(segments).content();
                    model.embeddingStore.addAll(embeddings, segments);
                    model.isIndexed = true;
                    finishTurn(AiMessage.from("[Jaider] Project successfully indexed with " + segments.size() + " segments."));
                } catch (Exception e) {
                    finishTurn(AiMessage.from("[Error] Failed to index project: " + e.getMessage()));
                }
            });
        }

        private void saveSession() {
            try {
                var sessionDir = model.projectDir.resolve(".jaider");
                Files.createDirectories(sessionDir);
                var session = new JSONObject();
                session.put("filesInContext", new JSONArray(model.filesInContext.stream().map(p -> model.projectDir.relativize(p).toString()).collect(Collectors.toList())));
                session.put("chatMemory", new JSONArray(chatMemory.messages().stream().map(m ->
                        new JSONObject().put("type", m.type().name()).put("text", text(m))).collect(Collectors.toList())));
                Files.writeString(sessionDir.resolve("session.json"), session.toString());
            } catch (IOException e) { /* Failed to save session */ }
        }

        private void loadSession() {
            var sessionFile = model.projectDir.resolve(".jaider/session.json");
            if (Files.exists(sessionFile)) {
                ui.requestConfirmation("Session Found", "Restore previous session?").thenAccept(restore -> {
                    if (restore) {
                        try {
                            var session = new JSONObject(Files.readString(sessionFile));
                            session.getJSONArray("filesInContext").forEach(f -> model.filesInContext.add(model.projectDir.resolve(f.toString())));
                            session.getJSONArray("chatMemory").forEach(m -> {
                                var msg = (JSONObject) m;
                                var text = msg.getString("text");
                                chatMemory.add("USER".equals(msg.getString("type")) ? UserMessage.from(text) : AiMessage.from(text));
                            });
                            model.addLog(AiMessage.from("[Jaider] Session restored."));
                        } catch (IOException e) {
                            model.addLog(AiMessage.from("[Error] Failed to load session."));
                        }
                    }
                    ui.redraw(model);
                });
            }
        }

        private boolean isGitRepoClean() {
            try (var git = Git.open(model.projectDir.resolve(".git").toFile())) {
                if (!git.status().call().isClean()) {
                    System.err.println("Your repository has uncommitted changes. Please commit or stash them before running Jaider.");
                    return false;
                }
            } catch (Exception e) {
                System.err.println("Not a git repository or git error. Jaider works best with git.");
            }
            return true;
        }

        private enum State {IDLE, AGENT_THINKING, WAITING_USER_CONFIRMATION}
    }
}