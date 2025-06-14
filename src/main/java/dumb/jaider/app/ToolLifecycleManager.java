package dumb.jaider.app;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dumb.jaider.agents.Agent;
import dumb.jaider.config.Config; // For config.runCommand
import dumb.jaider.model.JaiderModel; // For model.addLog
import dumb.jaider.tools.StandardTools; // For runValidationCommand
import dumb.jaider.ui.UI; // For ui.diffInteraction, ui.confirm
import org.jetbrains.annotations.NotNull; // For @NotNull on getString
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ToolLifecycleManager {
    private static final Logger logger = LoggerFactory.getLogger(ToolLifecycleManager.class);

    private final App app; // To access state, finishTurn, UI, Config, Model etc.
    private final AgentService agentService; // To get current agent and tools

    public ToolLifecycleManager(App app, AgentService agentService) {
        this.app = app;
        this.agentService = agentService;
    }

    // Method to execute a tool - was executeToolPublic in App
    public String executeTool(ToolExecutionRequest request) {
        Agent currentAgent = agentService.getCurrentAgent();
        if (currentAgent == null || currentAgent.tools() == null) {
            String errorMsg = "[ToolLifecycleManager] Cannot execute tool: current agent or its tools are null. Request: " + request.name();
            logger.error(errorMsg);
            // Log to JaiderModel as well, so user sees it.
            app.getModel().addLog(AiMessage.from(errorMsg));
            return errorMsg;
        }
        // DefaultToolExecutor requires a Collection<Object> for tools.
        // Agent.tools() returns Collection<Object>, so this should be compatible.
        try {
            return new DefaultToolExecutor(currentAgent.tools(), request).execute(request, currentAgent.tools());
        } catch (Exception e) {
            logger.error("Exception during tool execution for tool '{}': {}", request.name(), e.getMessage(), e);
            return "[Tool Execution Error: " + request.name() + "] " + e.getMessage();
        }
    }

    public void handleToolExecution(ToolExecutionRequest request) {
        if ("applyDiff".equals(request.name())) {
            app.setStatePublic(App.State.WAITING_USER_CONFIRMATION);
            app.getUi().diffInteraction(request.arguments()).thenAccept(result -> {
                if (result.accepted()) {
                    // If diff was edited, build a new request with the edited diff.
                    ToolExecutionRequest finalRequest = result.edited() ?
                        ToolExecutionRequest.builder().name(request.name()).id(request.id()).arguments(result.newDiff()).build() :
                        request;
                    executeAndContinue(finalRequest);
                } else {
                    // Pass null for ChatMessage as finishTurnPublic(ToolExecutionRequest, String) will handle it
                    app.finishTurnPublic(request, "User rejected the diff.");
                }
            });
        } else {
            executeAndContinue(request);
        }
    }

    private void executeAndContinue(ToolExecutionRequest request) {
        // Log tool execution attempt to JaiderModel
        app.getModel().addLog(AiMessage.from(String.format("[ToolLifecycleManager] Attempting to execute tool: %s with args: %s", request.name(), request.arguments())));
        app.getUi().redraw(app.getModel()); // Update UI to show tool attempt

        app.setStatePublic(App.State.AGENT_THINKING); // Set state to thinking while tool executes
        app.getModel().statusBarText = "Executing tool: " + request.name() + "...";
        app.getUi().redraw(app.getModel());

        String toolResult = executeTool(request); // Calls the local executeTool method

        // Log tool result to JaiderModel - use a more distinct message
        app.getModel().addLog(AiMessage.from(String.format("[ToolLifecycleManager Result: %s]\n%s", request.name(), toolResult)));
        app.getUi().redraw(app.getModel()); // Update UI to show tool result

        boolean diffApplied = "applyDiff".equals(request.name()) && toolResult.startsWith("Diff applied");
        Config currentConfig = app.getConfig(); // Get config from App

        if (diffApplied && currentConfig.runCommand != null && !currentConfig.runCommand.isBlank()) {
            app.setStatePublic(App.State.WAITING_USER_CONFIRMATION);
            String confirmationQuery = getValidationConfirmationQuery(currentConfig);
            app.getUi().confirm("Run Validation?", confirmationQuery).thenAccept(approved -> {
                app.setLastValidationPreferencePublic(approved); // Store user's choice via App method
                if (approved) {
                    runValidationAndContinue(request, toolResult);
                } else {
                    app.finishTurnPublic(request, toolResult + "\nUser chose not to run validation command.");
                }
            });
        } else {
            app.finishTurnPublic(request, toolResult);
        }
    }

    private void runValidationAndContinue(ToolExecutionRequest originalRequest, String originalResult) {
        Agent currentAgent = agentService.getCurrentAgent();
        JaiderModel currentModel = app.getModel(); // Get model from App

        if (currentAgent == null || currentAgent.tools() == null || currentAgent.tools().isEmpty()) {
            String errorMsg = "[ToolLifecycleManager] Error running validation: No agent or tools available.";
            currentModel.addLog(AiMessage.from(errorMsg));
            app.finishTurnPublic(originalRequest, originalResult + "\n---VALIDATION-COMMAND-RESULT---\n" + errorMsg);
            return;
        }

        StandardTools standardTools = null;
        for (Object tool : currentAgent.tools()) {
            if (tool instanceof StandardTools) {
                standardTools = (StandardTools) tool;
                break;
            }
        }

        if (standardTools == null) {
            String errorMsg = "[ToolLifecycleManager] Error running validation: StandardTools not found for current agent.";
            currentModel.addLog(AiMessage.from(errorMsg));
            app.finishTurnPublic(originalRequest, originalResult + "\n---VALIDATION-COMMAND-RESULT---\n" + errorMsg);
            return;
        }

        String validationResult;
        try {
            validationResult = standardTools.runValidationCommand("");
        } catch (Exception e) {
            logger.error("Exception during validation command execution: {}", e.getMessage(), e);
            validationResult = "[Validation Command Error] " + e.getMessage();
        }
        currentModel.addLog(AiMessage.from("[Validation Result]\n" + validationResult));
        app.finishTurnPublic(originalRequest, originalResult + "\n---VALIDATION-COMMAND-RESULT---\n" + validationResult);
    }

    @NotNull
    private String getValidationConfirmationQuery(Config currentConfig) {
        String confirmationQuery;
        Boolean lastPref = app.getLastValidationPreferencePublic(); // Get pref via App method
        if (lastPref == null) {
            confirmationQuery = String.format("Agent applied a diff. Run configured validation command (`%s`)?", currentConfig.runCommand);
        } else {
            confirmationQuery = String.format("Agent applied a diff. Your previous choice was to %s validation. Run configured validation command (`%s`)?",
                    (lastPref ? "run" : "not run"), currentConfig.runCommand);
        }
        return confirmationQuery;
    }
}
