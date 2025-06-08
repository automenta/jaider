package dumb.jaider;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.unifieddiff.UnifiedDiff;
import com.github.difflib.unifieddiff.UnifiedDiffFile;
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
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
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

    public static void main(String[] args) {
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
        String getModeName();

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

                    var label = new Label(String.format("[%s] %s", msg.type().name(), text));
                    if (msg instanceof UserMessage)
                        label.setForegroundColor(TextColor.ANSI.CYAN);
                    else if (msg instanceof AiMessage aim) {
                        if (aim.hasToolExecutionRequests()) {
                            label.setText(String.format("[Agent] Wants to use tool: %s", aim.toolExecutionRequests().getFirst().name()));
                            label.setForegroundColor(TextColor.ANSI.YELLOW);
                        } else
                            label.setForegroundColor(TextColor.ANSI.GREEN);
                    }
                    logListBox.addComponent(label);
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

    // --- 3. Data & Configuration Models ---
    static class Config {
        final Path configFile;
        final Map<String, String> apiKeys = new HashMap<>();
        String llmProvider = "openai", testCommand;

        Config(Path projectDir) {
            this.configFile = projectDir.resolve(".jaider.json");
            load();
        }

        void load() {
            try {
                if (!Files.exists(configFile)) createDefaultConfig();
                var json = new JSONObject(Files.readString(configFile));
                llmProvider = json.optString("llmProvider", "openai");
                testCommand = json.optString("testCommand", null);
                var keys = json.optJSONObject("apiKeys");
                if (keys != null) keys.keySet().forEach(key -> apiKeys.put(key, keys.getString(key)));
            } catch (Exception e) { /* Use defaults on failure */ }
        }

        private void createDefaultConfig() throws IOException {
            var defaultKeys = new JSONObject();
            defaultKeys.put("openai", "YOUR_OPENAI_API_KEY");
            defaultKeys.put("anthropic", "YOUR_ANTHROPIC_API_KEY");
            defaultKeys.put("google", "YOUR_GOOGLE_API_KEY");
            var defaultConfig = new JSONObject();
            defaultConfig.put("llmProvider", "openai");
            defaultConfig.put("testCommand", "");
            defaultConfig.put("apiKeys", defaultKeys);
            Files.writeString(configFile, defaultConfig.toString(2));
        }

        void save(String newConfig) throws IOException {
            Files.writeString(configFile, new JSONObject(newConfig).toString(2));
            load();
        }

        String getApiKey(String provider) {
            return apiKeys.getOrDefault(provider, System.getenv(provider.toUpperCase() + "_API_KEY"));
        }

        String readForEditing() throws IOException {
            return Files.exists(configFile) ? new JSONObject(Files.readString(configFile)).toString(2) : "{}";
        }
    }

    static class Model {
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
            if (logMessages.size() > 200) logMessages.removeFirst();
        }

        String getFileContext() {
            if (filesInContext.isEmpty()) return "No files are in context. Use /add or the `findRelevantCode` tool.";
            return filesInContext.stream().map(this::readFileContent).collect(Collectors.joining("\n\n"));
        }

        private String readFileContent(Path path) {
            try {
                return String.format("--- %s ---\n%s", projectDir.relativize(path), Files.readString(path));
            } catch (IOException e) {
                return String.format("Error reading file %s: %s", path, e.getMessage());
            }
        }
    }

    // --- 4. Agent Implementations ---
    static abstract class BaseAgent implements Agent {
        protected final JaiderAiService aiService;
        protected final Set<Object> tools;

        public BaseAgent(ChatLanguageModel model, ChatMemory memory, Set<Object> tools, String systemPrompt) {
            this.tools = tools;
            this.aiService = AiServices.builder(JaiderAiService.class)
                    .chatLanguageModel(model)
                    .chatMemory(memory)
                    .tools(tools.toArray())
                    .systemMessageProvider(vars ->
                                    systemPrompt
                            //SystemMessage.from(systemPrompt)
                    )
                    .build();
        }

        @Override
        public Response<AiMessage> act(List<ChatMessage> messages) {
            return aiService.act(messages);
        }

        @Override
        public Set<Object> getTools() {
            return this.tools;
        }

        protected interface JaiderAiService {
            Response<AiMessage> act(List<ChatMessage> messages);
        }
    }

    static class CoderAgent extends BaseAgent {
        public CoderAgent(ChatLanguageModel model, ChatMemory memory, Tools availableTools) {
            super(model, memory, Set.of(availableTools),
                    """
                            You are Jaider, an expert AI programmer. Your goal is to fully complete the user's request.
                            Follow this sequence rigidly:
                            1. THINK: First, write down your step-by-step plan. Use tools like `findRelevantCode` and `readFile` to understand the project.
                            2. MODIFY: Propose a change by using the `applyDiff` tool. This is the only way you can alter code.
                            3. VERIFY: After the user approves your diff, you MUST use the `runTests` tool to verify your changes, if a test suite is available.
                            4. FIX: If tests fail, analyze the error and go back to step 2.
                            5. COMMIT: Once the request is complete and verified, your final action MUST be to use the `commitChanges` tool with a clear, conventional commit message.""");
        }

        @Override
        public String getModeName() {
            return "Coder";
        }
    }

    static class ArchitectAgent extends BaseAgent {
        public ArchitectAgent(ChatLanguageModel model, ChatMemory memory, Tools availableTools) {
            super(model, memory, availableTools.getReadOnlyTools(),
                    "You are a principal software architect. Your goal is to answer questions about the codebase, suggest design patterns, and discuss high-level architectural trade-offs.\n" +
                            "You should use tools like `findRelevantCode` to analyze the codebase. You MUST NOT modify any files or run any tests.");
        }

        @Override
        public String getModeName() {
            return "Architect";
        }
    }

    static class AskAgent extends BaseAgent {
        public AskAgent(ChatLanguageModel model, ChatMemory memory) {
            super(model, memory, Set.of(), "You are a helpful assistant. Answer the user's questions clearly and concisely. You do not have access to any tools.");
        }

        @Override
        public String getModeName() {
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
            return Set.of(this);
        } // Simplified for this example

        @dev.langchain4j.agent.tool.Tool("Applies a code change using the unified diff format.")
        public String applyDiff(String diff) {
            try {
                var unifiedDiff = diffReader(diff);

                for (UnifiedDiffFile file : unifiedDiff.getFiles()) {
                    String fileName = file.getFromFile();
                    Path filePath = model.projectDir.resolve(fileName);

                    // Ensure the file is in context, unless it's a new file
                    boolean isNewFile = !Files.exists(filePath);
                    if (!isNewFile && !model.filesInContext.contains(filePath)) {
                        return "Error: Cannot apply diff to a file not in context: " + fileName;
                    }

                    List<String> originalLines = isNewFile ? new ArrayList<>() : Files.readAllLines(filePath);

                    // The patch is already specific to this file
                    Patch<String> patch = file.getPatch();
                    List<String> patchedLines = DiffUtils.patch(originalLines, patch);

                    // If the file is new, add it to the context
                    if (isNewFile) {
                        model.filesInContext.add(filePath);
                    }

                    Files.write(filePath, patchedLines);
                }

                model.lastAppliedDiff = diff;
                return "Diff applied successfully.";
            } catch (Exception e) {
                model.lastAppliedDiff = null;
                // Provide more specific error details
                return "Error applying diff: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            }
        }

        @dev.langchain4j.agent.tool.Tool("Reads the complete content of a file.")
        public String readFile(String fileName) {
            return model.readFileContent(model.projectDir.resolve(fileName));
        }

        @dev.langchain4j.agent.tool.Tool("Runs the project's test suite, if configured.")
        public String runTests() {
            if (config.testCommand == null || config.testCommand.isBlank()) return "No test command configured.";
            try {
                var pb = new ProcessBuilder(config.testCommand.split("\\s+")).directory(model.projectDir.toFile()).redirectErrorStream(true);
                return new BufferedReader(new InputStreamReader(pb.start().getInputStream())).lines().collect(Collectors.joining("\n"));
            } catch (Exception e) {
                return "Failed to run tests: " + e.getMessage();
            }
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
            if (model.embeddingStore == null) return "Project not indexed. Run /index first.";
            var queryEmbedding = embeddingModel.embed(query).content();
            var r = EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding).build();
            var relevant = model.embeddingStore.search(r);
            return relevant.matches().stream()
                    .map(match -> String.format("--- From %s ---\n%s",
                            match.embedded().metadata().getString("file_path"), match.embedded().text()))
                    .collect(Collectors.joining("\n\n"));
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
            setupOllama("http://localhost:11434", "llamablit");
            //setupOpenAI();

            var tools = new Tools(model, config, embeddingModel);
            agents.put("Coder", new CoderAgent(chatModel, chatMemory, tools));
            agents.put("Architect", new ArchitectAgent(chatModel, chatMemory, tools));
            agents.put("Ask", new AskAgent(chatModel, chatMemory));
            this.currentAgent = agents.get("Coder");
        }

        private void setupOllama(String baseURL, String modelName) {
            try {
                chatModel = OllamaChatModel.builder()
                        .baseUrl(baseURL)
                        .modelName(modelName)
                        .build();
                model.addLog(AiMessage.from("[Jaider] Ollama model initialized successfully."));
            } catch (Exception e) {
                model.addLog(AiMessage.from("[Jaider] WARNING: Failed to initialize Ollama model. Functionality will be limited. Check Ollama server."));
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
            if (input.startsWith("/")) handleCommand(input);
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
            if (diffApplied && config.testCommand != null) {
                currentState = State.WAITING_USER_CONFIRMATION;
                ui.requestConfirmation("Run Tests?", "Agent applied a diff. Run configured test command?").thenAccept(approved -> {
                    if (approved) runTestsAndContinue(request, toolResult);
                    else finishTurn(request, toolResult + "\nUser chose not to run tests.");
                });
            } else finishTurn(request, toolResult);
        }

        private String execute(ToolExecutionRequest request) {
            return new DefaultToolExecutor(currentAgent.getTools(), request).execute(request, currentAgent.getTools());
        }

        private void runTestsAndContinue(ToolExecutionRequest originalRequest, String originalResult) {
            var testResult = ((Tools) currentAgent.getTools()).runTests();
            model.addLog(AiMessage.from("[Test Result] " + testResult));
            finishTurn(originalRequest, originalResult + "\n---AUTO-TEST-RESULT---\n" + testResult);
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

        private void handleCommand(String input) {
            var parts = input.split("\\s+", 2);
            switch (parts[0]) {
                case "/add" ->
                        Arrays.stream(parts[1].split("\\s+")).map(model.projectDir::resolve).forEach(model.filesInContext::add);
                case "/undo" -> undoLastChange();
                case "/index" -> indexProject();
                case "/edit-config" -> editConfig();
                case "/mode" -> switchMode(parts.length > 1 ? parts[1] : "");
                case "/help" -> showHelp();
                case "/exit" -> {
                    try {
                        saveSession();
                        ui.close();
                    } catch (IOException e) {
                    }
                    System.exit(0);
                }
                default -> model.addLog(AiMessage.from("[Jaider] Unknown command."));
            }
            updateTokenCount();
        }

        private void switchMode(String modeName) {
            var newAgent = agents.values().stream().filter(a -> a.getModeName().equalsIgnoreCase(modeName)).findFirst();
            if (newAgent.isPresent()) {
                currentAgent = newAgent.get();
                model.agentMode = currentAgent.getModeName();
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
            var modes = String.join(", ", agents.keySet());
            model.addLog(AiMessage.from("[Jaider] --- Jaider Help ---\n" +
                    "Jaider is an AI assistant with multiple modes of operation.\n\n" +
                    "MODES:\n" +
                    "Switch modes with `/mode <ModeName>`. Available modes: " + modes + ".\n" +
                    "- Coder: The default mode for writing and fixing code.\n" +
                    "- Architect: A read-only mode for high-level questions about the codebase.\n" +
                    "- Ask: A simple Q&A mode with no access to your files.\n\n" +
                    "COMMANDS:\n" +
                    "/add <files...>: Add files to the context.\n" +
                    "/undo: Revert the last applied change.\n" +
                    "/index: Create a searchable index of your project (for RAG).\n" +
                    "/edit-config: Open the .jaider.json configuration file.\n" +
                    "/help, /exit"));
        }

        private void updateTokenCount() {
            model.currentTokenCount = tokenizer.estimateTokenCountInMessages(chatMemory.messages());
        }

        private void undoLastChange() {
            if (model.lastAppliedDiff == null) {
                model.addLog(AiMessage.from("[Jaider] No change to undo."));
                return;
            }
            try (var git = Git.open(model.projectDir.resolve(".git").toFile())) {
                // This undo logic is simple and might not work for all cases (e.g., new files).
                // A more robust implementation would reverse-apply the patch.
                // For now, we use git checkout which reverts to the last committed state.
                var unifiedDiff = diffReader(model.lastAppliedDiff);
                for (UnifiedDiffFile file : unifiedDiff.getFiles()) {
                    git.checkout().addPath(file.getFromFile()).call();
                }
                model.lastAppliedDiff = null;
                model.addLog(AiMessage.from("[Jaider] Last applied diff has been reverted using git checkout."));
            } catch (Exception e) {
                model.addLog(AiMessage.from("[Error] Failed to undo changes: " + e.getMessage()));
            }
        }


        private void indexProject() {
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
                                if ("USER".equals(msg.getString("type")))
                                    chatMemory.add(UserMessage.from(msg.getString("text")));
                                else chatMemory.add(AiMessage.from(msg.getString("text")));
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