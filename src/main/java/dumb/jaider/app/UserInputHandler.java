package dumb.jaider.app;

import dumb.jaider.model.JaiderModel; // Corrected package
import dumb.jaider.suggestion.ProactiveSuggestionService; // Corrected package
import dumb.jaider.config.Config;
import dumb.jaider.agents.Agent;
import dumb.jaider.ui.UI;
import dumb.jaider.toolmanager.ToolManager;
import dumb.jaider.suggestion.ActiveSuggestion;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dumb.jaider.commands.Command;
import dumb.jaider.commands.AppContext;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserInputHandler {
    private static final Logger logger = LoggerFactory.getLogger(UserInputHandler.class);

    private final App app;
    private final JaiderModel jaiderModel;
    private final Config config;
    private final UI ui;
    private final Agent agent;
    private final ProactiveSuggestionService proactiveSuggestionService;
    private final Map<String, Command> commands;

    public UserInputHandler(App app,
                            JaiderModel jaiderModel,
                            Config config,
                            UI ui,
                            Agent agent,
                            ProactiveSuggestionService proactiveSuggestionService,
                            Map<String, Command> commands) {
        this.app = app;
        this.jaiderModel = jaiderModel;
        this.config = config;
        this.ui = ui;
        this.agent = agent;
        this.proactiveSuggestionService = proactiveSuggestionService;
        this.commands = commands;
    }

    public void handleUserInput(String input) {
        if (app.getState() != App.State.IDLE && app.getState() != App.State.WAITING_USER_PLAN_APPROVAL) {
            jaiderModel.addLog(AiMessage.from("[Jaider] Please wait, I'm already working or waiting for approval."));
            ui.redraw(jaiderModel);
            return;
        }
        jaiderModel.addLog(UserMessage.from(input));
        jaiderModel.clearActiveSuggestions();

        if (input.startsWith("!")) {
            handleDirectToolInvocation(input);
        } else if (input.startsWith("/")) {
            execute(input);
        } else {
            ToolManager toolManager = null;
            try {
                toolManager = config.getComponent("toolManager", ToolManager.class);
            } catch (Exception e) {
                // Consider logging this warning if a logger was available here
            }

            List<Object> internalToolInstances = new ArrayList<>();
            if(agent != null && agent.tools() != null) {
                internalToolInstances.addAll(agent.tools());
            }

            List<ActiveSuggestion> activeSuggestions = proactiveSuggestionService.generateSuggestions(input, internalToolInstances);
            if (!activeSuggestions.isEmpty()) {
                jaiderModel.setActiveSuggestions(activeSuggestions);
                for (ActiveSuggestion activeSuggestion : activeSuggestions) {
                    jaiderModel.addLog(AiMessage.from("[Jaider Suggests] " + activeSuggestion.originalSuggestion().suggestionText()));
                }
            }
            app.processAgentTurnPublic(true);
        }
        ui.redraw(jaiderModel);
    }

    private void handleDirectToolInvocation(String input) {
        String[] parts = input.substring(1).split("\\s+", 2);
        String toolName = parts[0];
        String toolArgsJson = (parts.length > 1) ? parts[1] : "{}";

        if (agent == null || agent.tools() == null || agent.tools().isEmpty()) {
            jaiderModel.addLog(AiMessage.from("[Jaider] No agent active or agent has no tools. Cannot execute: " + toolName));
            app.finishTurnPublic(null);
            return;
        }
        try {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name(toolName)
                .arguments(toolArgsJson)
                .build();
            jaiderModel.addLog(AiMessage.from(String.format("[Jaider] User directly invoked tool: %s with args: %s", toolName, toolArgsJson)));
            ui.redraw(jaiderModel);

            app.setStatePublic(App.State.AGENT_THINKING);
            jaiderModel.statusBarText = "Executing tool: " + toolName + "...";
            ui.redraw(jaiderModel);

            String result = app.executeToolPublic(toolExecutionRequest);
            jaiderModel.addLog(AiMessage.from(String.format("[Tool Result: %s]\n%s", toolName, result)));
            app.finishTurnPublic(null);
        } catch (Exception e) {
            logger.error("Error during direct tool invocation for tool '{}': {}", toolName, e.getMessage(), e); // Added SLF4J logging
            jaiderModel.addLog(AiMessage.from(String.format("[Jaider] Error invoking tool '%s': %s. Ensure the tool name is correct and arguments are a valid JSON string if needed.", toolName, e.getMessage())));
            e.printStackTrace(); // Keep for local debugging if desired, though SLF4J is preferred for structured logs
            app.finishTurnPublic(null);
        }
    }

    private void execute(String input) {
        var parts = input.split("\\s+", 2);
        var commandName = parts[0];
        var args = parts.length > 1 ? parts[1] : "";

        var command = commands.get(commandName);
        if (command != null) {
            var appContext = new AppContext(this.jaiderModel, this.config, this.ui, this.app);
            command.execute(args, appContext);
        } else {
            jaiderModel.addLog(AiMessage.from("[Jaider] Unknown command: " + commandName));
        }
    }
}
