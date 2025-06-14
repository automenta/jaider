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
import dev.langchain4j.model.chat.ChatLanguageModel;
import dumb.jaider.agents.Agent;
import dumb.jaider.commands.*;
import dumb.jaider.config.Config;
import dumb.jaider.llm.LlmProviderFactory;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.suggestion.ProactiveSuggestionService;
import dumb.jaider.toolmanager.ToolManager;
import dumb.jaider.ui.UI;
import dumb.jaider.app.exceptions.ChatModelInitializationException;
import dumb.jaider.app.exceptions.TokenizerInitializationException;
import dumb.jaider.app.exceptions.ComponentNotFoundException;
import dumb.jaider.app.exceptions.ComponentInstantiationException;
import dumb.jaider.app.exceptions.CircularDependencyException;
import dumb.jaider.app.exceptions.InvalidComponentDefinitionException;


import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private final UI ui;
    private final JaiderModel model = new JaiderModel();
    private final Config config;
    private final Map<String, Command> commands = new HashMap<>();
    private ChatMemory memory; // Will be DI-injected or default
    private EmbeddingModel embedding; // Will be created by LlmProviderFactory
    private State state = State.IDLE;
    private Tokenizer tokenizer; // Will be created by LlmProviderFactory

    // Services to be DI-injected
    private AgentService agentService;
    private ToolLifecycleManager toolLifecycleManager;
    private SessionManager sessionManager;
    private SelfUpdateService selfUpdateService;
    private ProactiveSuggestionService proactiveSuggestionService;
    private UserInputHandler userInputHandler;
    private AgentInteractionService agentInteractionService;

    private Boolean lastValidationPreference = null;

    public enum State {IDLE, AGENT_THINKING, WAITING_USER_CONFIRMATION, WAITING_USER_PLAN_APPROVAL}

    public App(UI ui, String... originalArgs) {
        this.ui = ui;
        this.config = new Config(model.dir); // Config constructor initializes its own injector
        this.model.setOriginalArgs(originalArgs);

        // Initial DI setup for ChatMemory (already done by Config if defined, this is fallback)
        // App, JaiderModel, UI are registered in update() to ensure they are on the *current* injector.
        try {
            this.memory = config.getComponent("chatMemory", ChatMemory.class);
        } catch (ComponentNotFoundException | ComponentInstantiationException e) {
            logger.warn("ChatMemory not found via DI or failed to instantiate, falling back to default: {}", e.getMessage());
            DependencyInjector injector = config.getInjector(); // Get injector for fallback registration
            if (injector != null) { // Should not be null due to Config constructor guarantees
                 this.memory = MessageWindowChatMemory.withMaxMessages(20);
                 injector.registerSingleton("chatMemory", this.memory);
            } else {
                 logger.error("CRITICAL: Injector is null even after Config init. Cannot register fallback chatMemory.");
                 throw new IllegalStateException("Injector unavailable for fallback ChatMemory registration.");
            }
        }

        initializeCommands();

        // SessionManager is instantiated early as it needs model, memory, ui which are available.
        // It's not retrieved from DI yet, but could be in the future if needed.
        // For now, direct instantiation is fine as its deps are fundamental.
        this.sessionManager = new SessionManager(this.model, this.memory, this.ui);

        try {
            update(); // Initialize/update all DI-managed services
        } catch (RuntimeException e) {
            logger.error("CRITICAL FAILURE DURING APP INITIALIZATION (from update call in constructor): {}", e.getMessage(), e);
            model.addLog(AiMessage.from("[Jaider] CRITICAL STARTUP ERROR: " + e.getMessage() + ". Application cannot continue."));
            if(this.ui != null && this.model != null) this.ui.redraw(this.model);
            throw e;
        }
    }

    private void initializeCommands() {
        commands.put("/add", new AddCommand());
        commands.put("/index", new IndexCommand());
        commands.put("/undo", new UndoCommand());
        commands.put("/edit-config", new EditConfigCommand());
        commands.put("/mode", new ModeCommand());
        commands.put("/help", new HelpCommand());
        commands.put("/exit", new ExitCommand());
        commands.put("/summarize", new SummarizeCommand());
        commands.put("/accept", new AcceptSuggestionCommand());
        commands.put("/a", new AcceptSuggestionCommand());
        commands.put("/run", new RunCommand());
        commands.put("/self-develop", new SelfDevelopCommand());
    }

    public synchronized void update() {
        DependencyInjector injector = config.getInjector();
        if (injector == null) { // Should be prevented by Config constructor
            String fatalMsg = "DependencyInjector is null in update(). Critical configuration error.";
            logger.error(fatalMsg);
            model.addLog(AiMessage.from("[Jaider] " + fatalMsg));
            throw new IllegalStateException(fatalMsg);
        }

        // Register/Re-register core App-managed singletons on the current injector
        injector.registerSingleton("app", this);
        injector.registerSingleton("jaiderModel", model);
        injector.registerSingleton("ui", ui);
        injector.registerSingleton("commandsMap", commands); // Register commands map for UserInputHandler


        LlmProviderFactory llmFactory;
        ToolManager toolManager;

        try {
            // Retrieve foundational components first
            llmFactory = config.getComponent("llmProviderFactory", LlmProviderFactory.class);
            toolManager = config.getComponent("toolManager", ToolManager.class);

            ChatLanguageModel localChatModel = llmFactory.createChatModel();
            this.tokenizer = llmFactory.createTokenizer();
            this.embedding = llmFactory.createEmbeddingModel(); // Fallbacks to NoOpEmbeddingModel

            if (localChatModel != null) {
                injector.registerSingleton("appChatLanguageModel", localChatModel);
            } else {
                throw new ChatModelInitializationException("Chat model resolved to null from factory.");
            }
            if (this.tokenizer == null) {
                throw new TokenizerInitializationException("Tokenizer resolved to null from factory.");
            }
            if (this.embedding != null) {
                injector.registerSingleton("appEmbeddingModel", this.embedding);
            } else {
                logger.warn("Embedding model is null after initialization attempt. Features requiring embeddings may fail.");
                model.addLog(AiMessage.from("[Jaider] WARNING: Embedding model is null."));
            }
        } catch (ChatModelInitializationException | TokenizerInitializationException |
                   ComponentNotFoundException | ComponentInstantiationException |
                   CircularDependencyException | InvalidComponentDefinitionException |
                   IllegalArgumentException e) { // Catch specific DI and critical LLM exceptions
            String errorMsg = "CRITICAL: Core LLM/DI components could not be initialized. Application cannot function. Error: " + e.getMessage();
            logger.error(errorMsg, e);
            model.addLog(AiMessage.from("[Jaider] " + errorMsg));
            throw new RuntimeException(errorMsg, e);
        } catch (Exception e) { // Catch any other unexpected errors during this critical phase
            String errorMsg = "CRITICAL: Unexpected error during core component initialization. Application cannot function. Error: " + e.getMessage();
            logger.error(errorMsg, e);
            model.addLog(AiMessage.from("[Jaider] " + errorMsg));
            throw new RuntimeException(errorMsg, e);
        }

        // Initialize/Re-initialize services using DI
        try {
            this.agentService = config.getComponent("agentService", AgentService.class);
            this.agentService.updateAgents(); // Ensure agents are loaded/reloaded based on current config

            this.toolLifecycleManager = config.getComponent("toolLifecycleManager", ToolLifecycleManager.class);
            this.selfUpdateService = config.getComponent("selfUpdateService", SelfUpdateService.class);

            // SessionManager is already initialized in constructor, but if it were DI-managed:
            // this.sessionManager = config.getComponent("sessionManager", SessionManager.class);

            this.agentInteractionService = config.getComponent("agentInteractionService", AgentInteractionService.class);
            this.proactiveSuggestionService = config.getComponent("proactiveSuggestionService", ProactiveSuggestionService.class);
            this.userInputHandler = config.getComponent("userInputHandler", UserInputHandler.class);

        } catch (ComponentNotFoundException | ComponentInstantiationException | CircularDependencyException | InvalidComponentDefinitionException e) {
            String errorMsg = "CRITICAL: Essential application service initialization failed via DI. Application cannot function. Error: " + e.getMessage();
            logger.error(errorMsg, e);
            model.addLog(AiMessage.from("[Jaider] " + errorMsg));
            throw new RuntimeException(errorMsg, e);
        }

        logger.info("App services updated/initialized using DI.");
        if (toolManager != null) {
            // Logging for toolManager remains same
             Map<String, dumb.jaider.toolmanager.ToolDescriptor> descriptors = toolManager.getToolDescriptors();
            if (descriptors != null && !descriptors.isEmpty()) {
                logger.info("ToolManager loaded descriptors: " + String.join(", ", descriptors.keySet()));
            } else {
                logger.info("ToolManager loaded 0 external tool descriptors.");
            }
        } else {
            logger.info("ToolManager was not initialized (null). External tool descriptors not loaded.");
        }
    }

    // --- Getters for services and core components ---
    public JaiderModel getModel() { return this.model; }
    public Config getConfig() { return this.config; }
    public UI getUi() { return this.ui; }
    public ChatMemory getChatMemory() { return this.memory; }
    public Agent getCurrentAgent() {
        return this.agentService != null ? this.agentService.getCurrentAgent() : null;
    }
    public Boolean getLastValidationPreferencePublic() { return this.lastValidationPreference; }
    public void setLastValidationPreferencePublic(Boolean preference) { this.lastValidationPreference = preference; }
    public EmbeddingModel getEmbeddingModel() { return this.embedding; }
    public Tokenizer getTokenizer() { return this.tokenizer; }
    public Path getProjectDir() { return model.dir; }
    public Set<String> getAvailableAgentNames() {
        return this.agentService != null ? this.agentService.getAvailableAgentNames() : Collections.emptySet();
    }
    public Agent getAgent(String name) {
        return this.agentService != null ? this.agentService.getAgent(name) : null;
    }
    public State getState() { return this.state; }
    public void setStatePublic(State newState) { this.state = newState; }

    // --- Delegated methods to services ---
    public void finishTurnPublic(ChatMessage message) {
        if (agentInteractionService != null) {
            agentInteractionService.finishTurn(message);
        } else {
            logger.error("AgentInteractionService not initialized. Cannot finish turn.");
            this.state = State.IDLE; this.model.statusBarText = "Awaiting input. AIS Error."; if(this.ui != null) this.ui.redraw(this.model);
        }
    }
    public void finishTurnPublic(ToolExecutionRequest request, String result) {
        if (agentInteractionService != null) {
            agentInteractionService.finishTurn(request, result);
        } else {
            logger.error("AgentInteractionService not initialized. Cannot finish turn.");
            this.state = State.IDLE; this.model.statusBarText = "Awaiting input. AIS Error."; if(this.ui != null) this.ui.redraw(this.model);
        }
    }

    public void processAgentTurnPublic(boolean expectPlan) {
        if (agentInteractionService != null) {
            agentInteractionService.processAgentTurnPublic(expectPlan);
        } else {
            logger.error("AgentInteractionService not initialized. Cannot process agent turn.");
            this.state = State.IDLE; this.model.addLog(AiMessage.from("[Error] AgentInteractionService not available.")); if(this.ui != null) this.ui.redraw(this.model);
        }
    }

    public String executeToolPublic(ToolExecutionRequest request) {
        if (toolLifecycleManager != null) {
            return toolLifecycleManager.executeTool(request);
        }
        logger.error("ToolLifecycleManager not initialized. Cannot execute tool.");
        return "[Error] ToolLifecycleManager not initialized. Cannot execute tool: " + request.name();
    }

    public void checkAndTriggerSelfUpdateConfirmationPublic() {
        if (this.selfUpdateService != null) {
            this.selfUpdateService.checkAndTriggerSelfUpdateConfirmation();
        } else {
            logger.warn("SelfUpdateService not initialized. Cannot check for self-updates.");
        }
    }

    // --- Other public methods ---
    public void updateAppConfigPublic(String newConfigStr) throws IOException {
        config.save(newConfigStr);
        update();
        model.addLog(AiMessage.from("[Jaider] Configuration saved and application updated."));
    }

    public void setAgentInternalPublic(String mode) {
        if (this.agentService != null) {
            this.agentService.switchAgent(mode);
        } else {
            model.addLog(AiMessage.from("[Jaider] AgentService not initialized. Cannot switch mode."));
        }
    }

    public void exitAppInternalPublic() {
        try {
            if (sessionManager != null) sessionManager.saveSession();
            if (ui != null) ui.close();
        } catch (IOException e) {
            logger.error("Error during exit sequence", e);
        }
        System.exit(0);
    }

    public void handleUserInput(String input) {
        if (userInputHandler != null) {
            this.userInputHandler.handleUserInput(input);
        } else {
            logger.error("UserInputHandler not initialized. Cannot handle input.");
            if(this.model != null && this.ui != null) { // Ensure model and ui are available
                this.model.addLog(AiMessage.from("[Error] UserInputHandler not available. Cannot process input."));
                this.ui.redraw(this.model);
            }
        }
    }

    public void run() throws IOException {
        if (this.selfUpdateService != null) {
            boolean proceedNormalStartup = this.selfUpdateService.performStartupValidation();
            if (!proceedNormalStartup) {
                return;
            }
        } else {
            logger.error("CRITICAL: SelfUpdateService is not initialized at run time. Self-update validation checks will be skipped.");
            this.model.addLog(dev.langchain4j.data.message.AiMessage.from("[App.run] CRITICAL: SelfUpdateService not initialized. Validation skipped."));
        }

        if (!isGitRepoClean()) return;
        if (sessionManager != null) sessionManager.loadSession();
        if (ui != null) ui.init(this); else logger.error("UI is null, cannot initialize UI.");
    }

    public void updateTokenCountPublic() {
        if (tokenizer == null) {
            model.addLog(AiMessage.from("[Jaider] ALERT: Tokenizer is not initialized. Token count cannot be updated."));
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

    private boolean isGitRepoClean() {
        var userProjectGitService = new dumb.jaider.vcs.GitService(this.model.dir);
        return userProjectGitService.isGitRepoClean();
    }
}
