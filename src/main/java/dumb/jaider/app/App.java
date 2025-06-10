package dumb.jaider.app;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dumb.jaider.agents.Agent;
import dumb.jaider.agents.ArchitectAgent;
import dumb.jaider.agents.AskAgent;
import dumb.jaider.agents.CoderAgent;
import dumb.jaider.commands.*;
import dumb.jaider.config.Config;
import dumb.jaider.llm.LlmProviderFactory;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.tools.StandardTools;
import dumb.jaider.ui.UI;
import dumb.jaider.vcs.GitService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


public class App {
    private final UI ui;
    private final JaiderModel model = new JaiderModel();
    private final Config config; // Initialized in constructor
    private final Map<String, Command> commands = new HashMap<>();
    private final Map<String, Agent> agents = new HashMap<>();
    private ChatMemory memory; // Initialized via DI
    private EmbeddingModel embedding;
    private State state = State.IDLE;
    private Tokenizer tokenizer;
    private Agent agent;
    private Boolean lastValidationPreference = null; // Added for remembering validation preference

    public enum State {IDLE, AGENT_THINKING, WAITING_USER_CONFIRMATION}

    public App(UI ui) {
        this.ui = ui;
        this.config = new Config(model.dir); // Config loads DI definitions

        var injector = config.getInjector();
        if (injector != null) {
            injector.registerSingleton("jaiderModel", model);
            injector.registerSingleton("ui", ui);
            injector.registerSingleton("app", this);
            // Assuming chatMemory will be defined in JSON.
            // If not, a fallback or ensuring definition in JSON is needed.
            try {
                this.memory = config.getComponent("chatMemory", ChatMemory.class);
            } catch (Exception e) {
                // Fallback if not defined in DI, or rethrow if critical
                System.err.println("Failed to get chatMemory from DI, falling back to default: " + e.getMessage());
                this.memory = MessageWindowChatMemory.withMaxMessages(20);
                injector.registerSingleton("chatMemory", this.memory); // Register the fallback
            }
        } else {
            // DI not initialized, use defaults (original behavior)
            System.err.println("DependencyInjector not available from Config. Using default initializations.");
            this.memory = MessageWindowChatMemory.withMaxMessages(20);
        }

        initializeCommands();
        update(); // This will now use DI for some components
    }

    private void initializeCommands() {
        commands.put("/add", new AddCommand());
        commands.put("/index", new IndexCommand());
        commands.put("/undo", new UndoCommand());
        commands.put("/edit-config", new EditConfigCommand());
        commands.put("/mode", new ModeCommand());
        commands.put("/help", new HelpCommand());
        commands.put("/exit", new ExitCommand());
    }

    public synchronized void update() {
        if (config.getInjector() == null) {
            System.err.println("DI not initialized in App.update(). Cannot fetch components. Reverting to manual creation (limited functionality).");
            // Fallback to minimal functionality or throw error
            // For now, let's try to mimic old behavior if DI fails, though this is not ideal.
            var llmFactoryManual = new LlmProviderFactory(this.config, this.model);
            var localChatModelManual = llmFactoryManual.createChatModel();
            this.tokenizer = llmFactoryManual.createTokenizer();
            this.embedding = llmFactoryManual.createEmbeddingModel();
            var toolsManual = new StandardTools(model, config, this.embedding);
            agents.put("Coder", new CoderAgent(localChatModelManual, memory, toolsManual)); // chatMemory should be the DI one or fallback
            agents.put("Architect", new ArchitectAgent(localChatModelManual, memory, toolsManual));
            agents.put("Ask", new AskAgent(localChatModelManual, memory));
        } else {
            try {
                var llmFactory = config.getComponent("llmProviderFactory", LlmProviderFactory.class);
                var localChatModel = llmFactory.createChatModel(); // Created by factory, not DI component itself
                this.tokenizer = llmFactory.createTokenizer(); // Created by factory
                if (this.tokenizer == null) {
                    model.addLog(AiMessage.from("[Jaider] CRITICAL: Tokenizer could not be initialized. This usually means the LLM provider (e.g., Ollama) failed to connect or load the model. Please check the LLM server status and Jaider's configuration. Some features will be unavailable."));
                    // Consider setting a state here to disable features requiring the tokenizer
                }
                this.embedding = llmFactory.createEmbeddingModel(); // Created by factory

                if (config.getInjector() != null) {
                    config.getInjector().registerSingleton("appChatLanguageModel", localChatModel);
                    config.getInjector().registerSingleton("appEmbeddingModel", this.embedding);
                    // appTokenizer is also available via this.appTokenizer if needed by any component by direct App reference
                }
                // StandardTools might need JaiderModel, Config, EmbeddingModel injected if it's a DI component.
                // Assuming StandardTools is defined in JSON and its dependencies are resolved by DI.
                var tools = config.getComponent("standardTools", StandardTools.class);

                // Register the created models if other DI components need them by these specific names
                // This is only if they are NOT already meant to be separate DI components themselves.
                // config.getInjector().registerSingleton("appEmbeddingModel", this.appEmbeddingModel);
                // config.getInjector().registerSingleton("appTokenizer", this.appTokenizer);


                agents.clear(); // Clear old agent instances
                agents.put("Coder", config.getComponent("coderAgent", Agent.class));
                agents.put("Architect", config.getComponent("architectAgent", Agent.class));
                agents.put("Ask", config.getComponent("askAgent", Agent.class));
            } catch (Exception e) {
                System.err.println("Error updating components from DI: " + e.getMessage());
                model.addLog(AiMessage.from("[Jaider] CRITICAL ERROR: Failed to update components using DI. Application might be unstable. Check config. " + e.getClass().getSimpleName() + ": " + e.getMessage()));
                // Optionally, could fall back to manual creation like above if critical components fail
            }
        }

        if (this.agent == null || !agents.containsKey(this.agent.name())) {
            this.agent = agents.get("Coder"); // Default to Coder if current is null or no longer exists
            if (this.agent != null) {
                this.model.mode = this.agent.name();
             }
        }
    }

    public JaiderModel getModel() { return this.model; }
    public Config getConfig() { return this.config; }
    public UI getUi() { return this.ui; }

    public EmbeddingModel getEmbeddingModel() {
        return this.embedding;
    }

    public Tokenizer getTokenizer() {
        return this.tokenizer;
    }

    public Path getProjectDir() {
        return model.dir;
    }
    public Set<String> getAvailableAgentNames() { return agents.keySet(); } // Added method

    public void setStatePublic(State newState) {
        this.state = newState;
    }
    public void finishTurnPublic(ChatMessage message) { this.finishTurn(message); }
    public void finishTurnPublic(ToolExecutionRequest request, String result) { this.finishTurn(request, result); }

    public void updateAppConfigPublic(String newConfigStr) throws IOException {
        config.save(newConfigStr);
        update();
        model.addLog(AiMessage.from("[Jaider] Configuration saved and application updated."));
    }

    public void setAgentInternalPublic(String mode) {
        var newAgent = agents.values().stream().filter(a -> a.name().equalsIgnoreCase(mode)).findFirst();
        if (newAgent.isPresent()) {
            agent = newAgent.get();
            model.mode = agent.name();
            model.addLog(AiMessage.from("[Jaider] Switched to " + model.mode + " mode."));
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

        }
        System.exit(0);
    }

    public void run() throws IOException {
        if (!isGitRepoClean()) return;
        loadSession();
        ui.init(this);
    }

    public void handleUserInput(String input) {
        if (state != State.IDLE) {
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
        state = State.AGENT_THINKING;
        model.statusBarText = "Agent is thinking...";
        updateTokenCountPublic();
        ui.redraw(model);

        CompletableFuture.runAsync(() -> {
            var response = agent.act(memory.messages());
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
            state = State.WAITING_USER_CONFIRMATION;
            ui.diffInteraction(request.arguments()).thenAccept(result -> {
                if (result.accepted())
                    executeAndContinue(result.edited() ? ToolExecutionRequest.builder().arguments(result.newDiff()).build() : request);
                else
                    finishTurn(request, "User rejected the diff.");
            });
        } else
            executeAndContinue(request);
    }

    private void executeAndContinue(ToolExecutionRequest request) {
        var toolResult = executeTool(request);
        model.addLog(AiMessage.from(String.format("[Tool Result: %s]", request.name())));

        var diffApplied = "applyDiff".equals(request.name()) && toolResult.startsWith("Diff applied");
        if (diffApplied && config.runCommand != null && !config.runCommand.isBlank()) {
            state = State.WAITING_USER_CONFIRMATION;

            String confirmationQuery;
            if (this.lastValidationPreference == null) {
                confirmationQuery = String.format("Agent applied a diff. Run configured validation command (`%s`)?", config.runCommand);
            } else {
                confirmationQuery = String.format("Agent applied a diff. Your previous choice was to %s validation. Run configured validation command (`%s`)?",
                                (this.lastValidationPreference ? "run" : "not run"), config.runCommand);
            }

            ui.confirm("Run Validation?", confirmationQuery).thenAccept(approved -> {
                this.lastValidationPreference = approved; // Store the user's current choice
                if (approved) {
                    runValidationAndContinue(request, toolResult);
                } else {
                    finishTurn(request, toolResult + "\nUser chose not to run validation command.");
                }
            });
        } else {
            finishTurn(request, toolResult);
        }
    }

    private String executeTool(ToolExecutionRequest request) {
        return new DefaultToolExecutor(agent.tools(), request).execute(request, agent.tools());
    }

    private void runValidationAndContinue(ToolExecutionRequest originalRequest, String originalResult) {
        var validationResult = ((StandardTools) agent.tools().iterator().next()).runValidationCommand("");
        model.addLog(AiMessage.from("[Validation Result]\n" + validationResult));
        finishTurn(originalRequest, originalResult + "\n---VALIDATION-COMMAND-RESULT---\n" + validationResult);
    }

    private void finishTurn(ToolExecutionRequest request, String result) {
        if (request != null)
            memory.add(ToolExecutionResultMessage.from(request, result));
        processAgentTurn();
    }

    private void finishTurn(ChatMessage message) {
        if (message != null) model.addLog(message);
        state = State.IDLE;
        model.statusBarText = "Awaiting input.";
        saveSession();
        ui.redraw(model);
    }

    private void execute(String input) {
        var parts = input.split("\\s+", 2);
        var commandName = parts[0];
        var args = parts.length > 1 ? parts[1] : "";

        var command = commands.get(commandName);
        if (command != null) {
            var appContext = new AppContext(this.model, this.config, this.ui, this);
            command.execute(args, appContext);
        } else {
            model.addLog(AiMessage.from("[Jaider] Unknown command: " + commandName));
        }
    }

    public void updateTokenCountPublic() {
        if (tokenizer == null) {
            model.addLog(AiMessage.from("[Jaider] ALERT: Tokenizer is not initialized. Token count cannot be updated. Model setup might have failed."));
            model.currentTokenCount = -1;
            return;
        }
        try {
            model.currentTokenCount = tokenizer.estimateTokenCountInMessages(memory.messages());
        } catch (Exception e) {
            model.addLog(AiMessage.from("[Jaider] ERROR: Failed to estimate token count: " + e.getMessage()));
            model.currentTokenCount = -1;
        }
    }

    public void saveSession() {
        try {
            var sessionDir = model.dir.resolve(".jaider");
            Files.createDirectories(sessionDir);
            var session = new JSONObject();
            session.put("filesInContext", new JSONArray(model.files.stream().map(p -> model.dir.relativize(p).toString()).collect(Collectors.toList())));
            session.put("chatMemory", new JSONArray(memory.messages().stream()
                .map(m -> {
                    var text = dumb.jaider.utils.Util.chatMessageToText(m);
                    return new JSONObject().put("type", m.type().name()).put("text", text == null ? "" : text);
                })
                .collect(Collectors.toList())));
            Files.writeString(sessionDir.resolve("session.json"), session.toString());
        } catch (IOException e) { model.addLog(AiMessage.from("[Jaider] ERROR: Failed to save session: " + e.getMessage())); }
    }

    private void loadSession() {
        var sessionFile = model.dir.resolve(".jaider/session.json");
        if (Files.exists(sessionFile)) {
            ui.confirm("Session Found", "Restore previous session?").thenAccept(restore -> {
                if (restore) {
                    try {
                        var sessionData = new JSONObject(Files.readString(sessionFile));
                        sessionData.getJSONArray("filesInContext").forEach(f -> model.files.add(model.dir.resolve(f.toString())));
                        sessionData.getJSONArray("chatMemory").forEach(m -> {
                            var msg = (JSONObject) m;
                            var text = msg.getString("text");
                            memory.add("USER".equals(msg.getString("type")) ? UserMessage.from(text) : AiMessage.from(text));
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
        var gitService = new GitService(this.model.dir);
        return gitService.isGitRepoClean();
    }
}
