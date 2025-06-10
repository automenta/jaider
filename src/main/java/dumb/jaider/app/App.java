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
import dumb.jaider.commands.AcceptSuggestionCommand; // Added
import dumb.jaider.commands.SummarizeCommand;
import dumb.jaider.config.Config;
import dumb.jaider.llm.LlmProviderFactory;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.suggestion.ProactiveSuggestionService;
import dumb.jaider.suggestion.ActiveSuggestion; // Changed from Suggestion
import dumb.jaider.toolmanager.ToolManager;
import dumb.jaider.tools.JaiderTools; // Assuming JaiderTools might be needed for internal tools
import dumb.jaider.tools.StandardTools;
import dumb.jaider.ui.UI;
// Note: dumb.jaider.vcs.GitService is different from org.jaider.service.GitService
// We need org.jaider.service.GitService for the self-update features.
// If dumb.jaider.vcs.GitService is still used by isGitRepoClean(), it should remain.
// For now, assuming it's okay to add the new one.
import org.jaider.service.BuildManagerService;
import org.jaider.service.GitService; // For self-update
import org.jaider.service.RestartService;
import org.jaider.service.SelfUpdateOrchestratorService;
import dumb.jaider.app.StartupService;
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
    private AiMessage agentMessageWithPlan;
    private Boolean lastValidationPreference = null; // Added for remembering validation preference
    private org.jaider.service.SelfUpdateOrchestratorService selfUpdateOrchestratorService;
    private StartupService startupService;
    private org.jaider.service.BuildManagerService buildManagerService;
    private org.jaider.service.GitService gitService; // For self-update
    private org.jaider.service.RestartService restartService;
    private ProactiveSuggestionService proactiveSuggestionService;


    public enum State {IDLE, AGENT_THINKING, WAITING_USER_CONFIRMATION, WAITING_USER_PLAN_APPROVAL}

    public App(UI ui, String[] originalArgs) {
        this.ui = ui;
        this.config = new Config(model.dir); // Config loads DI definitions
        this.model.setOriginalArgs(originalArgs);
        // StartupService instantiation moved to update() method

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
        // ProactiveSuggestionService will now be initialized in update() after ToolManager is available from DI
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
        commands.put("/summarize", new SummarizeCommand());
        commands.put("/accept", new AcceptSuggestionCommand()); // Added /accept
        commands.put("/a", new AcceptSuggestionCommand());      // Added /a as alias
    }

    public synchronized void update() {
        ToolManager toolManager = null; // Initialize toolManager to null
        if (config.getInjector() == null) {
            System.err.println("DI not initialized in App.update(). Cannot fetch components. Reverting to manual creation (limited functionality).");
            // Fallback to minimal functionality or throw error
            // For now, let's try to mimic old behavior if DI fails, though this is not ideal.
            // If DI is not available, ToolManager might not be available either unless manually created.
            // For now, ProactiveSuggestionService might be initialized with a null ToolManager in this case.
            var llmFactoryManual = new LlmProviderFactory(this.config, this.model);
            var localChatModelManual = llmFactoryManual.createChatModel();
            this.tokenizer = llmFactoryManual.createTokenizer();
            this.embedding = llmFactoryManual.createEmbeddingModel();
            var standardToolsManual = new StandardTools(model, config, this.embedding);
            // Manual instantiation for fallback - ProactiveSuggestionService needs ToolManager
            // ToolManager needs a path, ParserRegistry needs nothing, RefactoringService needs ParserRegistry
            // SmartRenameTool needs RefactoringService.
            // This manual setup can get complex. For fallback, CoderAgent might not get SmartRenameTool.
            // Or, we simplify: if DI fails, SmartRenameTool is not available.
            // For now, let's reflect that CoderAgent expects it, but it might be null in this manual path.
            JaiderTools jaiderToolsManual = null; // Assuming JaiderTools might also be complex to manually init here
            SmartRenameTool smartRenameToolManual = null; // Likely null in this fallback path
            AnalysisTools analysisToolsManual = null; // Likely null in this fallback path
            agents.put("Coder", new CoderAgent(localChatModelManual, memory, standardToolsManual, jaiderToolsManual, smartRenameToolManual, analysisToolsManual));
            agents.put("Architect", new ArchitectAgent(localChatModelManual, memory, standardToolsManual));
            agents.put("Ask", new AskAgent(localChatModelManual, memory));
            // toolManager would likely be null here, or manually created if essential for fallback
        } else {
            try {
                toolManager = config.getComponent("toolManager", ToolManager.class); // Get ToolManager from DI
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
                agents.clear(); // Clear old agent instances
                agents.put("Coder", config.getComponent("coderAgent", Agent.class));
                agents.put("Architect", config.getComponent("architectAgent", Agent.class));
                agents.put("Ask", config.getComponent("askAgent", Agent.class));

                if (config.getInjector() != null) { // Re-check injector just in case
                    try {
                        this.selfUpdateOrchestratorService = config.getComponent("selfUpdateOrchestratorService", org.jaider.service.SelfUpdateOrchestratorService.class);
                    } catch (Exception e) {
                        System.err.println("Error fetching SelfUpdateOrchestratorService from DI: " + e.getMessage());
                        this.model.addLog(AiMessage.from("[Jaider] CRITICAL ERROR: Failed to initialize SelfUpdateOrchestratorService. Self-update features will be unavailable. " + e.getClass().getSimpleName() + ": " + e.getMessage()));
                    }

                    try {
                        this.buildManagerService = config.getComponent("buildManagerService", org.jaider.service.BuildManagerService.class);
                    } catch (Exception e) {
                        System.err.println("Error fetching BuildManagerService from DI: " + e.getMessage());
                        this.model.addLog(AiMessage.from("[Jaider] CRITICAL ERROR: Failed to initialize BuildManagerService. Some features might be unavailable. " + e.getClass().getSimpleName() + ": " + e.getMessage()));
                    }
                    try {
                        this.gitService = config.getComponent("gitService", org.jaider.service.GitService.class);
                    } catch (Exception e) {
                        System.err.println("Error fetching GitService from DI: " + e.getMessage());
                        this.model.addLog(AiMessage.from("[Jaider] CRITICAL ERROR: Failed to initialize GitService for self-updates. Some features might be unavailable. " + e.getClass().getSimpleName() + ": " + e.getMessage()));
                    }
                    try {
                        this.restartService = config.getComponent("restartService", org.jaider.service.RestartService.class);
                    } catch (Exception e) {
                        System.err.println("Error fetching RestartService from DI: " + e.getMessage());
                        this.model.addLog(AiMessage.from("[Jaider] CRITICAL ERROR: Failed to initialize RestartService. Some features might be unavailable. " + e.getClass().getSimpleName() + ": " + e.getMessage()));
                    }

                    // Instantiate StartupService after its dependencies are fetched
                    if (this.model != null && this.config != null && this.buildManagerService != null && this.gitService != null && this.restartService != null) {
                        this.startupService = new StartupService(this.model, this.config, this.buildManagerService, this.gitService, this.restartService);
                        System.out.println("[App.update] StartupService initialized successfully.");
                    } else {
                        System.err.println("[App.update] CRITICAL: Could not initialize StartupService due to missing core dependencies (model, config, BuildManager, Git, or Restart services). Post-update validation will be skipped.");
                        this.model.addLog(AiMessage.from("[Jaider] CRITICAL ERROR: StartupService not initialized due to missing dependencies. Post-update validation will be skipped."));
                        this.startupService = null; // Ensure it's null if not properly initialized
                    }
                }

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
        // Initialize ProactiveSuggestionService with ToolManager (which might be null if DI failed)
        this.proactiveSuggestionService = new ProactiveSuggestionService(toolManager);

        if (toolManager != null) {
            Map<String, dumb.jaider.toolmanager.ToolDescriptor> descriptors = toolManager.getToolDescriptors();
            if (descriptors != null && !descriptors.isEmpty()) {
                String loadedToolsLog = "[Jaider Init] ToolManager loaded descriptors: " + String.join(", ", descriptors.keySet());
                model.addLog(AiMessage.from(loadedToolsLog));
                System.out.println(loadedToolsLog);
            } else {
                String noToolsLog = "[Jaider Init] ToolManager loaded 0 external tool descriptors.";
                model.addLog(AiMessage.from(noToolsLog));
                System.out.println(noToolsLog);
            }
        } else {
            String noTmLog = "[Jaider Init] ToolManager was not initialized (null). No external tool descriptors loaded.";
            model.addLog(AiMessage.from(noTmLog));
            System.out.println(noTmLog);
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
        if (this.startupService != null) {
            boolean proceedNormalStartup = this.startupService.performStartupValidationChecks();
            if (!proceedNormalStartup) {
                // A restart was (or should have been) triggered by the startup service (e.g., after a successful rollback and restart sequence).
                // The current JVM instance should ideally already be terminating if RestartService called System.exit().
                // If execution somehow reaches here, it's an unexpected state.
                System.err.println("[App.run] CRITICAL: StartupService indicated a restart was (or should have been) triggered, but execution continued in the old instance. Forcing exit to prevent unexpected behavior.");
                this.model.addLog(dev.langchain4j.data.message.AiMessage.from("[App.run] CRITICAL: Post-validation restart did not terminate instance. Forcing exit.")); // Log to model
                // ui.redraw(model); // Optional: attempt a final redraw if UI is up, though risky.
                System.exit(1); // Force exit.
                return; // Should be unreachable.
            }
            // If proceedNormalStartup is true, continue with the rest of the run() method.
        } else {
            System.err.println("[App.run] CRITICAL: StartupService is not initialized. Self-update validation checks will be skipped.");
            this.model.addLog(dev.langchain4j.data.message.AiMessage.from("[App.run] CRITICAL: StartupService not initialized. Validation skipped.")); // Log to model
        }

        if (!isGitRepoClean()) return;
        loadSession();
        ui.init(this);
    }

    public void handleUserInput(String input) {
        if (state != State.IDLE && state != State.WAITING_USER_PLAN_APPROVAL) { // Allow input if waiting for plan approval
            model.addLog(AiMessage.from("[Jaider] Please wait, I'm already working or waiting for approval."));
            ui.redraw(model);
            return;
        }
        model.addLog(UserMessage.from(input));
        model.clearActiveSuggestions(); // Changed from clearSuggestions()

        if (input.startsWith("!")) {
            handleDirectToolInvocation(input);
        } else if (input.startsWith("/")) {
            execute(input);
        } else {
            // Generate and log suggestions before processing agent turn
            // Need ToolManager and internal tool instances.
            // ToolManager might come from DI or be constructed if not available.
            // For now, let's assume we can get it from config or construct a new one.
            ToolManager toolManager = null;
            try {
                toolManager = config.getComponent("toolManager", ToolManager.class);
            } catch (Exception e) {
                System.err.println("Could not get ToolManager from DI for suggestions: " + e.getMessage());
                // toolManager = new ToolManager(...); // Or handle absence
            }

            List<Object> internalToolInstances = new ArrayList<>();
            try {
                 // agent.tools() returns Collection<Object> which are the tool instances.
                 // This might include StandardTools, JaiderTools etc.
                if(agent != null && agent.tools() != null) {
                    internalToolInstances.addAll(agent.tools());
                } else {
                     // Fallback or specific instantiation if agent or its tools are null
                    System.err.println("Agent or agent tools are null. Suggestions based on internal tools might be limited.");
                    // Optionally, could try to get StandardTools/JaiderTools from DI directly if needed as a fallback.
                    // internalToolInstances.add(config.getComponent("standardTools", StandardTools.class));
                    // internalToolInstances.add(config.getComponent("jaiderTools", JaiderTools.class));
                }
            } catch (Exception e) {
                System.err.println("Error fetching internal tool instances for suggestions: " + e.getMessage());
            }

            // Pass only internalToolInstances to generateSuggestions as ToolManager is now a class field
            List<ActiveSuggestion> activeSuggestions = proactiveSuggestionService.generateSuggestions(input, internalToolInstances);
            if (!activeSuggestions.isEmpty()) {
                model.setActiveSuggestions(activeSuggestions); // Store in model
                for (ActiveSuggestion activeSuggestion : activeSuggestions) {
                    // Log suggestion to chat - might need a specific message type or formatting
                    // For now, using the original suggestion's text
                    model.addLog(AiMessage.from("[Jaider Suggests] " + activeSuggestion.getOriginalSuggestion().getSuggestionText()));
                }
            }

            // Initial call to processAgentTurn for a new user query.
            // Assumes this is the first interaction in a sequence, so agent should propose a plan.
            processAgentTurn(true);
        }
        ui.redraw(model);
    }

    private void handleDirectToolInvocation(String input) {
        String[] parts = input.substring(1).split("\\s+", 2);
        String toolName = parts[0];
        String toolArgsJson = (parts.length > 1) ? parts[1] : "{}";

        if (agent == null || agent.tools() == null || agent.tools().isEmpty()) {
            model.addLog(AiMessage.from("[Jaider] No agent active or agent has no tools. Cannot execute: " + toolName));
            finishTurn(null);
            return;
        }
        try {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name(toolName)
                .arguments(toolArgsJson)
                .build();
            model.addLog(AiMessage.from(String.format("[Jaider] User directly invoked tool: %s with args: %s", toolName, toolArgsJson)));
            ui.redraw(model);

            state = State.AGENT_THINKING;
            model.statusBarText = "Executing tool: " + toolName + "...";
            ui.redraw(model);

            String result = executeTool(toolExecutionRequest);
            model.addLog(AiMessage.from(String.format("[Tool Result: %s]\n%s", toolName, result)));
            finishTurn(null);
        } catch (Exception e) {
            model.addLog(AiMessage.from(String.format("[Jaider] Error invoking tool '%s': %s. Ensure the tool name is correct and arguments are a valid JSON string if needed.", toolName, e.getMessage())));
            e.printStackTrace();
            finishTurn(null);
        }
    }

    private void processAgentTurn() {
        processAgentTurn(false); // Default to not expecting a plan
    }

    private void processAgentTurn(boolean expectPlan) {
        state = State.AGENT_THINKING;
        model.statusBarText = "Agent is thinking...";
        updateTokenCountPublic();
        ui.redraw(model);

        CompletableFuture.runAsync(() -> {
            var response = agent.act(memory.messages());
            var aiMessage = response.content();
            model.addLog(aiMessage);

            if (expectPlan) {
                this.agentMessageWithPlan = aiMessage;
                state = State.WAITING_USER_PLAN_APPROVAL;
                String fullMessageText = aiMessage.text();
                String planText = extractPlan(fullMessageText);
                String logMessage;

                if (planText.equals(fullMessageText)) {
                    logMessage = "[Jaider] No specific plan section found. Using full message for plan approval.";
                } else {
                    logMessage = "[Jaider] Extracted plan section for approval.";
                }
                model.addLog(AiMessage.from(logMessage));
                System.out.println(logMessage); // Also print to console for easier debugging during manual tests

                ui.confirmPlan("Agent's Proposed Plan", planText, aiMessage)
                  .thenAccept(approved -> handlePlanApproval(this.agentMessageWithPlan, approved));
            } else {
                if (aiMessage.hasToolExecutionRequests()) {
                    handleToolExecution(aiMessage.toolExecutionRequests().getFirst());
                } else {
                    finishTurn(null);
                }
            }
        }).exceptionally(e -> {
            finishTurn(AiMessage.from("[Error] " + e.getMessage()));
            return null;
        });
    }

    public void handlePlanApproval(AiMessage agentMessageWithPlan, boolean planApproved) {
        if (planApproved) {
            memory.add(UserMessage.from("Plan approved. Proceed."));
            if (agentMessageWithPlan.hasToolExecutionRequests()) {
                handleToolExecution(agentMessageWithPlan.toolExecutionRequests().getFirst());
            } else {
                processAgentTurn(false);
            }
        } else {
            memory.add(UserMessage.from("Plan rejected. Propose a new one."));
            processAgentTurn(true);
        }
        ui.redraw(model);
    }

    private String extractPlan(String messageText) {
        if (messageText == null || messageText.isBlank()) {
            return ""; // Or return the original if that's preferred for blank messages
        }

        String[] planMarkers = {
            "Here's my plan:",
            "My plan is:",
            "Here is my plan:"
            // Add more markers as needed, e.g., "Plan:", "Steps:"
        };

        String lowerCaseMessageText = messageText.toLowerCase();

        for (String marker : planMarkers) {
            int markerIndex = lowerCaseMessageText.indexOf(marker.toLowerCase());
            if (markerIndex != -1) {
                // Found a marker, try to extract from there until END_OF_PLAN
                String fromMarker = messageText.substring(markerIndex + marker.length()).trim();
                int endOfPlanIndex = fromMarker.indexOf("END_OF_PLAN");
                if (endOfPlanIndex != -1) {
                    return fromMarker.substring(0, endOfPlanIndex).trim();
                }
                return fromMarker; // Return from marker to end if END_OF_PLAN is not there
            }
        }

        // Heuristic for numbered or bulleted lists (simple version)
        // This looks for lines starting with "1.", "2.", "* ", "- "
        // and considers a sequence of such lines as a plan.
        String[] lines = messageText.split("\\r?\\n");
        StringBuilder planBuilder = new StringBuilder();
        boolean inPlanList = false;
        int planLines = 0;
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.matches("^\\d+\\.\\s+.*") || // Matches "1. ", "2. " etc.
                trimmedLine.matches("^\\*\\s+.*") ||    // Matches "* "
                trimmedLine.matches("^-\\s+.*")) {     // Matches "- "
                if (!inPlanList) {
                    inPlanList = true;
                }
                planBuilder.append(line).append(System.lineSeparator());
                planLines++;
            } else {
                if (inPlanList) {
                    // If we were in a list and found a non-list line, stop.
                    // Only consider it a plan if there are at least 2 items.
                    if (planLines >= 2) break;
                    else { // Reset if too short
                        planBuilder.setLength(0);
                        inPlanList = false;
                        planLines = 0;
                    }
                }
            }
        }

        if (inPlanList && planLines >= 2) { // Check planLines condition again for plans ending the message
             String extracted = planBuilder.toString().trim();
             // Also check for END_OF_PLAN within the list context
             int endOfPlanIndex = extracted.indexOf("END_OF_PLAN");
             if (endOfPlanIndex != -1) {
                 return extracted.substring(0, endOfPlanIndex).trim();
             }
             return extracted;
        }

        // If no specific section found, return the full message
        return messageText;
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
        checkAndTriggerSelfUpdateConfirmation();
        ui.redraw(model);
    }

    private void checkAndTriggerSelfUpdateConfirmation() {
        if (this.selfUpdateOrchestratorService != null &&
            this.selfUpdateOrchestratorService.getPendingUpdate() != null &&
            !this.selfUpdateOrchestratorService.isUpdateInProgress() &&
            this.state == State.IDLE) {
            // TODO: Integrate properly with TUI for user confirmation.
            // For now, this call assumes UserInterfaceService handles interaction.
            // The actual confirmation UI flow will be addressed in Step 1.4.
            System.out.println("[App] Triggering self-update confirmation process via orchestrator..."); // Temporary log
            this.selfUpdateOrchestratorService.triggerUserConfirmationProcess();
        }
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
