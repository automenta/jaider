package dumb.jaider.app;

import dumb.jaider.config.Config;
import dumb.jaider.llm.LlmProviderFactory;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.ui.TUI; // Assuming TUI is the default, or use UI interface
import dumb.jaider.ui.UI;
import dumb.jaider.ui.DiffInteractionResult; // Needed by UI interface for requestDiffInteraction
import dumb.jaider.agents.Agent;
import dumb.jaider.agents.CoderAgent;
import dumb.jaider.agents.ArchitectAgent;
import dumb.jaider.agents.AskAgent;
import dumb.jaider.tools.StandardTools;
import dumb.jaider.commands.*; // All commands + AppContext

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader; // For IndexCommand context via App
import dev.langchain4j.data.document.splitter.DocumentSplitters; // For IndexCommand context via App
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore; // For IndexCommand context via App

import org.json.JSONArray;
import org.json.JSONObject;
// import org.eclipse.jgit.api.Git; // No longer used directly in App.java
import dumb.jaider.vcs.GitService; // Added import

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


public class App {
    private final UI ui;
    private final JaiderModel model = new JaiderModel();
    private final Config config = new Config(model.projectDir);
    private final ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);
    private final Map<String, Agent> agents = new HashMap<>();
    private EmbeddingModel appEmbeddingModel;
    private State currentState = State.IDLE;
    private Tokenizer appTokenizer;
    private Agent currentAgent;

    private final Map<String, Command> commandMap = new HashMap<>();

    // Public enum for State, accessible by commands
    public enum State {IDLE, AGENT_THINKING, WAITING_USER_CONFIRMATION}

    public App(UI ui) {
        this.ui = ui;
        initializeCommands();
        update();
    }

    private void initializeCommands() {
        commandMap.put("/add", new AddCommand());
        commandMap.put("/index", new IndexCommand());
        commandMap.put("/undo", new UndoCommand());
        commandMap.put("/edit-config", new EditConfigCommand());
        commandMap.put("/mode", new ModeCommand());
        commandMap.put("/help", new HelpCommand());
        commandMap.put("/exit", new ExitCommand());
    }

    public synchronized void update() {
        LlmProviderFactory llmFactory = new LlmProviderFactory(this.config, this.model);
        ChatLanguageModel localChatModel = llmFactory.createChatModel();
        this.appTokenizer = llmFactory.createTokenizer();
        this.appEmbeddingModel = llmFactory.createEmbeddingModel();

        var tools = new StandardTools(model, config, this.appEmbeddingModel);
        agents.put("Coder", new CoderAgent(localChatModel, chatMemory, tools));
        agents.put("Architect", new ArchitectAgent(localChatModel, chatMemory, tools));
        agents.put("Ask", new AskAgent(localChatModel, chatMemory));
        if (this.currentAgent == null) {
             this.currentAgent = agents.get("Coder");
             if (this.currentAgent != null) {
                this.model.agentMode = this.currentAgent.name();
             }
        }
    }

    // Getters for AppContext
    public JaiderModel getModel() { return this.model; }
    public Config getConfig() { return this.config; }
    public UI getUi() { return this.ui; }
    public EmbeddingModel getEmbeddingModel() { return this.appEmbeddingModel; }
    public Tokenizer getTokenizer() { return this.appTokenizer; }
    public Path getProjectDir() { return model.projectDir; }

    // State and Turn Management for Commands
    public void setStatePublic(State newState) { this.currentState = newState; }
    public void finishTurnPublic(ChatMessage message) { this.finishTurn(message); }
    public void finishTurnPublic(ToolExecutionRequest request, String result) { this.finishTurn(request, result); }

    // App specific actions callable by commands
    public void updateAppConfigPublic(String newConfigStr) throws IOException {
        config.save(newConfigStr);
        update();
        model.addLog(AiMessage.from("[Jaider] Configuration saved and application updated."));
    }

    public void setAgentInternalPublic(String mode) {
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

    public void exitAppInternalPublic() {
        try {
            saveSession();
            ui.close();
        } catch (IOException e) {
            // Log error or handle
        }
        System.exit(0);
    }

    public void run() throws IOException {
        if (!isGitRepoClean()) return;
        loadSession();
        ui.init(this); // Pass this App instance to UI
    }

    public void handleUserInput(String input) { // Made public for TUI
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
        updateTokenCountPublic();
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
        var toolResult = executeTool(request); // Renamed from execute to avoid conflict
        model.addLog(AiMessage.from(String.format("[Tool Result: %s]", request.name())));

        var diffApplied = "applyDiff".equals(request.name()) && toolResult.startsWith("Diff applied");
        if (diffApplied && config.runCommand != null && !config.runCommand.isBlank()) {
            currentState = State.WAITING_USER_CONFIRMATION;
            ui.requestConfirmation("Run Validation?", String.format("Agent applied a diff. Run configured validation command (`%s`)?", config.runCommand)).thenAccept(approved -> {
                if (approved) runValidationAndContinue(request, toolResult);
                else finishTurn(request, toolResult + "\nUser chose not to run validation command.");
            });
        } else finishTurn(request, toolResult);
    }

    private String executeTool(ToolExecutionRequest request) { // Renamed from execute
        return new DefaultToolExecutor(currentAgent.getTools(), request).execute(request, currentAgent.getTools());
    }

    private void runValidationAndContinue(ToolExecutionRequest originalRequest, String originalResult) {
        var validationResult = ((StandardTools) currentAgent.getTools().iterator().next()).runValidationCommand("");
        model.addLog(AiMessage.from("[Validation Result]\n" + validationResult));
        finishTurn(originalRequest, originalResult + "\n---VALIDATION-COMMAND-RESULT---\n" + validationResult);
    }

    private void finishTurn(ToolExecutionRequest request, String result) {
        if (request != null)
            chatMemory.add(ToolExecutionResultMessage.from(request, result));
        processAgentTurn();
    }

    private void finishTurn(ChatMessage message) {
        if (message != null) model.addLog(message);
        currentState = State.IDLE;
        model.statusBarText = "Awaiting input.";
        saveSession();
        ui.redraw(model);
    }

    private void execute(String input) { // This is for slash commands
        var parts = input.split("\\s+", 2);
        String commandName = parts[0];
        String args = parts.length > 1 ? parts[1] : "";

        Command command = commandMap.get(commandName);
        if (command != null) {
            AppContext appContext = new AppContext(this.model, this.config, this.ui, this);
            command.execute(args, appContext);
        } else {
            model.addLog(AiMessage.from("[Jaider] Unknown command: " + commandName));
        }
    }

    public void updateTokenCountPublic() {
        if (appTokenizer == null) {
            model.addLog(AiMessage.from("[Jaider] ALERT: Tokenizer is not initialized. Token count cannot be updated. Model setup might have failed."));
            model.currentTokenCount = -1;
            return;
        }
        try {
            model.currentTokenCount = appTokenizer.estimateTokenCountInMessages(chatMemory.messages());
        } catch (Exception e) {
            model.addLog(AiMessage.from("[Jaider] ERROR: Failed to estimate token count: " + e.getMessage()));
            model.currentTokenCount = -1;
        }
    }

    public void saveSession() {
        try {
            var sessionDir = model.projectDir.resolve(".jaider");
            Files.createDirectories(sessionDir);
            var session = new JSONObject();
            session.put("filesInContext", new JSONArray(model.filesInContext.stream().map(p -> model.projectDir.relativize(p).toString()).collect(Collectors.toList())));
            session.put("chatMemory", new JSONArray(chatMemory.messages().stream()
                .map(m -> new JSONObject().put("type", m.type().name()).put("text", m.text())) // Simplified text access
                .collect(Collectors.toList())));
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
                ui.redraw(model); // Redraw after attempting to load session or if user declines
            });
        }
    }

    private boolean isGitRepoClean() {
        GitService gitService = new GitService(this.model.projectDir);
        return gitService.isGitRepoClean();
    }
}
