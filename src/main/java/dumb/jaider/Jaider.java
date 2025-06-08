import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static dev.langchain4j.model.openai.OpenAiModelName.GPT_4_TURBO_PREVIEW;

public class Jaider {

    public static void main(String[] args) {
        try {
            Controller controller = new Controller(new LanternaTui());
            controller.run();
        } catch (Exception e) {
            System.err.println("Jaider failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- 1. Core Abstractions (UI & Agent) ---
    interface UserInterface {
        void init(Controller controller) throws IOException;
        void redraw(Model model);
        CompletableFuture<Boolean> requestConfirmation(String title, String text);
        CompletableFuture<DiffInteractionResult> requestDiffInteraction(String diff);
        CompletableFuture<String> requestConfigEdit(String currentConfig);
        void close() throws IOException;
    }

    record DiffInteractionResult(boolean accepted, boolean edited, String newDiff) {}

    interface Agent {
        String getModeName();
        Response<AiMessage> act(List<ChatMessage> messages);
        Set<Object> getTools();
    }

    // --- 2. UI Implementation (Lanterna) ---
    static class LanternaTui implements UserInterface {
        private MultiWindowTextGUI gui;
        private ActionListBox contextListBox, logListBox;
        private Label statusBar;

        @Override
        public void init(Controller controller) throws IOException {
            Terminal terminal = new DefaultTerminalFactory().createTerminal();
            Screen screen = new TerminalScreen(terminal);
            screen.startScreen();
            BasicWindow window = new BasicWindow("Jaider - The AI Programming Assistant");
            window.setHints(Arrays.asList(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));

            Panel mainPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
            contextListBox = new ActionListBox(new TerminalSize(35, 0));
            mainPanel.addComponent(contextListBox.withBorder(Borders.singleLine("Context")));
            logListBox = new ActionListBox();
            mainPanel.addComponent(logListBox.withBorder(Borders.singleLine("Log")));

            TextBox inputBox = new TextBox(new TerminalSize(100, 1));
            inputBox.setListener((text, user) -> { if (user) { controller.handleUserInput(text); inputBox.setText(""); }});
            statusBar = new Label("");
            Panel bottomPanel = new Panel(new LinearLayout(Direction.HORIZONTAL)).addComponent(inputBox).addComponent(statusBar);
            
            Panel contentPane = new Panel(new BorderLayout()).addComponent(mainPanel, BorderLayout.Position.CENTER).addComponent(bottomPanel, BorderLayout.Position.BOTTOM);
            window.setComponent(contentPane);
            window.setFocusedInteractable(inputBox);
            
            gui = new MultiWindowTextGUI(new SeparateWindowGuiThread.Factory(), screen);
            gui.addWindowAndWait(window);
        }

        @Override
        public void redraw(Model model) {
            if (gui == null) return;
            gui.getGUIScreen().doInEventThread(() -> {
                contextListBox.clearItems();
                model.filesInContext.forEach(p -> contextListBox.addItem(model.projectDir.relativize(p).toString(), null));
                logListBox.clearItems();
                for (ChatMessage msg : model.logMessages) {
                    String text = msg.text();
                    if (text == null || text.isBlank()) continue;

                    Label label = new Label(String.format("[%s] %s", msg.type().name(), text));
                    if (msg instanceof UserMessage) label.setForegroundColor(TextColor.ANSI.CYAN);
                    else if (msg instanceof AiMessage) {
                        if (((AiMessage) msg).hasToolExecutionRequests()) {
                            label.setText(String.format("[Agent] Wants to use tool: %s", ((AiMessage) msg).toolExecutionRequests().get(0).name()));
                            label.setForegroundColor(TextColor.ANSI.YELLOW);
                        } else label.setForegroundColor(TextColor.ANSI.GREEN);
                    }
                    logListBox.addItem(label, null);
                }
                if (logListBox.getItemCount() > 0) logListBox.setSelectedIndex(logListBox.getItemCount() - 1);
                statusBar.setText(String.format(" | Mode: %s | %s | Tokens: %d", model.agentMode, model.statusBarText, model.currentTokenCount));
            });
        }

        @Override
        public CompletableFuture<Boolean> requestConfirmation(String title, String text) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            gui.getGUIScreen().doInEventThread(() -> future.complete(MessageDialog.showMessageDialog(gui, title, text, MessageDialogButton.Yes, MessageDialogButton.No) == MessageDialog.Result.Yes));
            return future;
        }

        @Override
        public CompletableFuture<DiffInteractionResult> requestDiffInteraction(String diff) {
            CompletableFuture<DiffInteractionResult> future = new CompletableFuture<>();
            gui.getGUIScreen().doInEventThread(() -> {
                DialogWindow dialog = new BasicWindow("Apply Diff?");
                Panel content = new Panel(new LinearLayout(Direction.VERTICAL));
                content.addComponent(new Label("Agent wants to apply these changes:"));

                Panel diffPanel = new Panel();
                diff.lines().forEach(line -> {
                    Label lineLabel = new Label(line);
                    if (line.startsWith("+")) lineLabel.setForegroundColor(TextColor.ANSI.GREEN);
                    else if (line.startsWith("-")) lineLabel.setForegroundColor(TextColor.ANSI.RED);
                    else if (line.startsWith("@")) lineLabel.setForegroundColor(TextColor.ANSI.CYAN);
                    diffPanel.addComponent(lineLabel);
                });
                content.addComponent(diffPanel.withBorder(Borders.singleLine()));
                
                Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                buttonPanel.addComponent(new Button("Accept", () -> { future.complete(new DiffInteractionResult(true, false, diff)); dialog.close(); }));
                buttonPanel.addComponent(new Button("Reject", () -> { future.complete(new DiffInteractionResult(false, false, null)); dialog.close(); }));
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
            CompletableFuture<String> future = new CompletableFuture<>();
            gui.getGUIScreen().doInEventThread(() -> {
                DialogWindow editorDialog = new BasicWindow("Editor");
                Panel editorPanel = new Panel(new BorderLayout());
                TextBox configTextBox = new TextBox(currentConfig, TextBox.Style.MULTI_LINE);
                configTextBox.setPreferredSize(new TerminalSize(80, 25));
                editorPanel.addComponent(configTextBox, BorderLayout.Position.CENTER);
                
                Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
                buttonPanel.addComponent(new Button("Save", () -> { future.complete(configTextBox.getText()); editorDialog.close(); }));
                buttonPanel.addComponent(new Button("Cancel", () -> { future.complete(null); editorDialog.close(); }));
                editorPanel.addComponent(buttonPanel, BorderLayout.Position.BOTTOM);
                editorDialog.setComponent(editorPanel);
                gui.addWindow(editorDialog);
            });
            return future;
        }

        @Override
        public void close() throws IOException { if (gui != null) gui.getScreen().stopScreen(); }
    }

    // --- 3. Data & Configuration Models ---
    static class Config {
        final Path configFile;
        String llmProvider = "openai", testCommand;
        Map<String, String> apiKeys = new HashMap<>();

        Config(Path projectDir) {
            this.configFile = projectDir.resolve(".jaider.json");
            load();
        }

        void load() {
            try {
                if (!Files.exists(configFile)) createDefaultConfig();
                JSONObject json = new JSONObject(Files.readString(configFile));
                llmProvider = json.optString("llmProvider", "openai");
                testCommand = json.optString("testCommand", null);
                JSONObject keys = json.optJSONObject("apiKeys");
                if (keys != null) keys.keySet().forEach(key -> apiKeys.put(key, keys.getString(key)));
            } catch (Exception e) { /* Use defaults on failure */ }
        }

        private void createDefaultConfig() throws IOException {
            JSONObject defaultKeys = new JSONObject();
            defaultKeys.put("openai", "YOUR_OPENAI_API_KEY");
            defaultKeys.put("anthropic", "YOUR_ANTHROPIC_API_KEY");
            defaultKeys.put("google", "YOUR_GOOGLE_API_KEY");
            JSONObject defaultConfig = new JSONObject();
            defaultConfig.put("llmProvider", "openai");
            defaultConfig.put("testCommand", "");
            defaultConfig.put("apiKeys", defaultKeys);
            Files.writeString(configFile, defaultConfig.toString(2));
        }

        void save(String newConfig) throws IOException {
            Files.writeString(configFile, new JSONObject(newConfig).toString(2));
            load();
        }

        String getApiKey(String provider) { return apiKeys.getOrDefault(provider, System.getenv(provider.toUpperCase() + "_API_KEY")); }
        String readForEditing() throws IOException { return Files.exists(configFile) ? new JSONObject(Files.readString(configFile)).toString(2) : "{}"; }
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
            if (message.text() == null || message.text().isBlank()) return;
            logMessages.add(message);
            if (logMessages.size() > 200) logMessages.remove(0);
        }
        String getFileContext() {
            if (filesInContext.isEmpty()) return "No files are in context. Use /add or the `findRelevantCode` tool.";
            return filesInContext.stream().map(this::readFileContent).collect(Collectors.joining("\n\n"));
        }
        private String readFileContent(Path path) {
            try { return String.format("--- %s ---\n%s", projectDir.relativize(path), Files.readString(path));
            } catch (IOException e) { return String.format("Error reading file %s: %s", path, e.getMessage()); }
        }
    }

    // --- 4. Agent Implementations ---
    static abstract class BaseAgent implements Agent {
        protected interface JaiderAiService { Response<AiMessage> act(List<ChatMessage> messages); }
        protected final JaiderAiService aiService;
        protected final Set<Object> tools;

        public BaseAgent(ChatLanguageModel model, ChatMemory memory, Set<Object> tools, String systemPrompt) {
            this.tools = tools;
            this.aiService = AiServices.builder(JaiderAiService.class)
                    .chatLanguageModel(model)
                    .chatMemory(memory)
                    .tools(tools.toArray())
                    .systemMessageProvider(vars -> SystemMessage.from(systemPrompt))
                    .build();
        }
        @Override public Response<AiMessage> act(List<ChatMessage> messages) { return aiService.act(messages); }
        @Override public Set<Object> getTools() { return this.tools; }
    }

    static class CoderAgent extends BaseAgent {
        public CoderAgent(ChatLanguageModel model, ChatMemory memory, Tools availableTools) {
            super(model, memory, Set.of(availableTools),
                "You are Jaider, an expert AI programmer. Your goal is to fully complete the user's request.\n" +
                "Follow this sequence rigidly:\n" +
                "1. THINK: First, write down your step-by-step plan. Use tools like `findRelevantCode` and `readFile` to understand the project.\n" +
                "2. MODIFY: Propose a change by using the `applyDiff` tool. This is the only way you can alter code.\n" +
                "3. VERIFY: After the user approves your diff, you MUST use the `runTests` tool to verify your changes, if a test suite is available.\n" +
                "4. FIX: If tests fail, analyze the error and go back to step 2.\n" +
                "5. COMMIT: Once the request is complete and verified, your final action MUST be to use the `commitChanges` tool with a clear, conventional commit message.");
        }
        @Override public String getModeName() { return "Coder"; }
    }

    static class ArchitectAgent extends BaseAgent {
        public ArchitectAgent(ChatLanguageModel model, ChatMemory memory, Tools availableTools) {
            super(model, memory, availableTools.getReadOnlyTools(),
                "You are a principal software architect. Your goal is to answer questions about the codebase, suggest design patterns, and discuss high-level architectural trade-offs.\n" +
                "You should use tools like `findRelevantCode` to analyze the codebase. You MUST NOT modify any files or run any tests.");
        }
        @Override public String getModeName() { return "Architect"; }
    }

    static class AskAgent extends BaseAgent {
        public AskAgent(ChatLanguageModel model, ChatMemory memory) {
            super(model, memory, Set.of(), "You are a helpful assistant. Answer the user's questions clearly and concisely. You do not have access to any tools.");
        }
        @Override public String getModeName() { return "Ask"; }
    }

    // --- 5. Toolset Implementation ---
    static class Tools {
        private final Model model;
        private final Config config;
        private final EmbeddingModel embeddingModel;

        Tools(Model model, Config config, EmbeddingModel embeddingModel) { this.model = model; this.config = config; this.embeddingModel = embeddingModel; }

        public Set<Object> getReadOnlyTools() { return Set.of(this); } // Simplified for this example
        
        @dev.langchain4j.agent.tool.Tool("Applies a code change using the unified diff format.")
        public String applyDiff(String diff) {
            try {
                Patch<String> patch = DiffUtils.parseUnifiedDiff(Arrays.asList(diff.split("\n")));
                for (var delta : patch.getDeltas()) {
                    String fileName = delta.getOriginal().getFile().getPath();
                    Path filePath = model.projectDir.resolve(fileName);
                    if (!model.filesInContext.contains(filePath)) return "Error: Cannot apply diff to a file not in context: " + fileName;
                    List<String> originalLines = Files.readAllLines(filePath);
                    List<String> patchedLines = DiffUtils.patch(originalLines, patch);
                    Files.write(filePath, patchedLines);
                }
                model.lastAppliedDiff = diff;
                return "Diff applied successfully.";
            } catch (Exception e) { model.lastAppliedDiff = null; return "Error applying diff: " + e.getMessage(); }
        }

        @dev.langchain4j.agent.tool.Tool("Reads the complete content of a file.")
        public String readFile(String fileName) { return model.readFileContent(model.projectDir.resolve(fileName)); }

        @dev.langchain4j.agent.tool.Tool("Runs the project's test suite, if configured.")
        public String runTests() {
            if (config.testCommand == null || config.testCommand.isBlank()) return "No test command configured.";
            try {
                ProcessBuilder pb = new ProcessBuilder(config.testCommand.split("\\s+")).directory(model.projectDir.toFile()).redirectErrorStream(true);
                return new BufferedReader(new InputStreamReader(pb.start().getInputStream())).lines().collect(Collectors.joining("\n"));
            } catch (Exception e) { return "Failed to run tests: " + e.getMessage(); }
        }

        @dev.langchain4j.agent.tool.Tool("Commits all staged changes with a given message.")
        public String commitChanges(String message) {
            try (Git git = Git.open(model.projectDir.resolve(".git").toFile())) {
                git.add().addFilepattern(".").call();
                git.commit().setMessage(message).call();
                return "Changes committed successfully.";
            } catch (Exception e) { return "Git commit failed: " + e.getMessage(); }
        }
        
        @dev.langchain4j.agent.tool.Tool("Finds relevant code snippets from the entire indexed codebase.")
        public String findRelevantCode(String query) {
            if (model.embeddingStore == null) return "Project not indexed. Run /index first.";
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            List<EmbeddingMatch<TextSegment>> relevant = model.embeddingStore.findRelevant(queryEmbedding, 3);
            return relevant.stream()
                .map(match -> String.format("--- From %s ---\n%s", match.embedded().metadata("file_path"), match.embedded().text()))
                .collect(Collectors.joining("\n\n"));
        }
    }

    // --- 6. Main Application Controller ---
    static class Controller {
        private enum State { IDLE, AGENT_THINKING, WAITING_USER_CONFIRMATION }
        private State currentState = State.IDLE;
        
        private final UserInterface ui;
        private final Model model = new Model();
        private final Config config = new Config(model.projectDir);
        private final ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);
        private final OpenAiTokenizer tokenizer = new OpenAiTokenizer(GPT_4_TURBO_PREVIEW);
        private final Map<String, Agent> agents = new HashMap<>();
        private Agent currentAgent;

        Controller(UserInterface ui) {
            this.ui = ui;
            setupAgents();
            this.currentAgent = agents.get("Coder");
        }

        private void setupAgents() {
            String apiKey = config.getApiKey(config.llmProvider);
            if (apiKey == null || apiKey.contains("YOUR_")) {
                model.addLog(AiMessage.from("[Jaider] WARNING: LLM API key not found. Functionality will be limited. Use /edit-config to set it."));
                apiKey = "DUMMY_KEY";
            }
            ChatLanguageModel chatModel = OpenAiChatModel.builder().apiKey(apiKey).build();
            EmbeddingModel embeddingModel = OpenAiEmbeddingModel.withApiKey(apiKey).build();
            Tools availableTools = new Tools(model, config, embeddingModel);
            
            agents.put("Coder", new CoderAgent(chatModel, chatMemory, availableTools));
            agents.put("Architect", new ArchitectAgent(chatModel, chatMemory, availableTools));
            agents.put("Ask", new AskAgent(chatModel, chatMemory));
        }

        void run() throws IOException, GitAPIException {
            if (!isGitRepoClean()) return;
            loadSession();
            ui.init(this);
        }

        private boolean isGitRepoClean() { /* ... Implementation from previous version ... */ return true; }

        void handleUserInput(String input) {
            if (currentState != State.IDLE) { model.addLog(AiMessage.from("[Jaider] Please wait, I'm already working.")); ui.redraw(model); return; }
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
                Response<AiMessage> response = currentAgent.act(chatMemory.messages());
                AiMessage aiMessage = response.content();
                model.addLog(aiMessage);
                
                if (aiMessage.hasToolExecutionRequests()) handleToolExecution(aiMessage.toolExecutionRequests().get(0));
                else finishTurn(null);
            }).exceptionally(e -> { finishTurn(AiMessage.from("[Error] " + e.getMessage())); return null; });
        }
        
        private void handleToolExecution(ToolExecutionRequest request) {
            ToolExecutor executor = new ToolExecutor(currentAgent.getTools(), request);
            if (!executor.isToolFound()) {
                finishTurn(request, String.format("Error: Tool '%s' is not available in the current '%s' mode.", request.name(), currentAgent.getModeName()));
                return;
            }
            
            if ("applyDiff".equals(request.name())) {
                currentState = State.WAITING_USER_CONFIRMATION;
                ui.requestDiffInteraction(request.arguments()).thenAccept(result -> {
                    if (result.accepted()) {
                        ToolExecutionRequest finalRequest = result.edited() ? request.toBuilder().arguments(result.newDiff()).build() : request;
                        executeAndContinue(finalRequest);
                    } else finishTurn(request, "User rejected the diff.");
                });
            } else executeAndContinue(request);
        }

        private void executeAndContinue(ToolExecutionRequest request) {
            ToolExecutor executor = new ToolExecutor(currentAgent.getTools(), request);
            String toolResult = executor.execute().text();
            model.addLog(AiMessage.from(String.format("[Tool Result: %s]", request.name())));

            boolean diffApplied = "applyDiff".equals(request.name()) && toolResult.startsWith("Diff applied");
            if (diffApplied && config.testCommand != null) {
                currentState = State.WAITING_USER_CONFIRMATION;
                ui.requestConfirmation("Run Tests?", "Agent applied a diff. Run configured test command?").thenAccept(approved -> {
                    if (approved) runTestsAndContinue(request, toolResult);
                    else finishTurn(request, toolResult + "\nUser chose not to run tests.");
                });
            } else finishTurn(request, toolResult);
        }

        private void runTestsAndContinue(ToolExecutionRequest originalRequest, String originalResult) {
            String testResult = tools.runTests();
            model.addLog(AiMessage.from("[Test Result] " + testResult));
            finishTurn(originalRequest, originalResult + "\n---AUTO-TEST-RESULT---\n" + testResult);
        }

        private void finishTurn(ToolExecutionRequest request, String result) {
            if (request != null) chatMemory.add(dev.langchain4j.data.message.ToolExecutionResultMessage.from(request, result));
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
            String[] parts = input.split("\\s+", 2);
            switch (parts[0]) {
                case "/add" -> Arrays.stream(parts[1].split("\\s+")).map(model.projectDir::resolve).forEach(model.filesInContext::add);
                case "/undo" -> undoLastChange();
                case "/index" -> indexProject();
                case "/edit-config" -> editConfig();
                case "/mode" -> switchMode(parts.length > 1 ? parts[1] : "");
                case "/help" -> showHelp();
                case "/exit" -> { try { saveSession(); ui.close(); } catch (IOException e) {} System.exit(0); }
                default -> model.addLog(AiMessage.from("[Jaider] Unknown command."));
            }
            updateTokenCount();
        }

        private void undoLastChange() { /* ... Implementation from previous version ... */ }
        private void indexProject() { /* ... Implementation from previous version ... */ }
        private void editConfig() {
            currentState = State.WAITING_USER_CONFIRMATION;
            try {
                ui.requestConfigEdit(config.readForEditing()).thenAccept(newConfigStr -> {
                    if (newConfigStr != null) {
                        try { config.save(newConfigStr); setupAgents(); model.addLog(AiMessage.from("[Jaider] Configuration saved."));
                        } catch (IOException e) { model.addLog(AiMessage.from("[Error] Failed to save config.")); }
                    }
                    finishTurn(null);
                });
            } catch (IOException e) { finishTurn(AiMessage.from("[Error] Could not read config file.")); }
        }

        private void saveSession() { /* ... Implementation from previous version ... */ }
        private void loadSession() { /* ... Implementation from previous version ... */ }
        
        private void showHelp() {
            String modes = agents.keySet().stream().collect(Collectors.joining(", "));
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

        private void updateTokenCount() { model.currentTokenCount = tokenizer.estimateTokenCountInMessages(chatMemory.messages()); }
    }
}