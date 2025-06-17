package dumb.jaider.app;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dumb.jaider.agents.Agent;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.ui.UI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class AgentInteractionService {
    private static final Logger logger = LoggerFactory.getLogger(AgentInteractionService.class);

    private final App app; // For global state, UI, redraws, getting other services if not directly injected
    private final JaiderModel model;
    private final ChatMemory chatMemory;
    private final UI ui;
    private final AgentService agentService;
    private final ToolLifecycleManager toolLifecycleManager;
    private final SessionManager sessionManager;
    private final SelfUpdateService selfUpdateService; // Added

    private AiMessage agentMessageWithPlan; // Moved from App

    public AgentInteractionService(App app, JaiderModel model, ChatMemory chatMemory, UI ui,
                                   AgentService agentService, ToolLifecycleManager toolLifecycleManager,
                                   SessionManager sessionManager, SelfUpdateService selfUpdateService) { // Added SelfUpdateService
        this.app = app;
        this.model = model;
        this.chatMemory = chatMemory;
        this.ui = ui;
        this.agentService = agentService;
        this.toolLifecycleManager = toolLifecycleManager;
        this.sessionManager = sessionManager;
        this.selfUpdateService = selfUpdateService; // Added
    }

    public void processAgentTurnPublic(boolean expectPlan) {
        app.setStatePublic(App.State.AGENT_THINKING);
        model.statusBarText = "Agent is thinking...";
        app.updateTokenCountPublic(); // Call method on App
        ui.redraw(model);

        Agent currentAgent = agentService.getCurrentAgent();
        if (currentAgent == null) {
            finishTurn(AiMessage.from("[Error] No active agent to process the turn."));
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                var response = currentAgent.act(chatMemory.messages());
                var aiMessage = response.content();
                model.addLog(aiMessage);

                if (expectPlan) {
                    this.agentMessageWithPlan = aiMessage;
                    app.setStatePublic(App.State.WAITING_USER_PLAN_APPROVAL);
                    String fullMessageText = aiMessage.text();
                    String planText = extractPlan(fullMessageText);
                    String logMessage;

                    if (planText.equals(fullMessageText)) {
                        logMessage = "[Jaider] No specific plan section found. Using full message for plan approval.";
                    } else {
                        logMessage = "[Jaider] Extracted plan section for approval.";
                    }
                    model.addLog(AiMessage.from(logMessage));
                    logger.info(logMessage);

                    ui.confirmPlan("Agent's Proposed Plan", planText, aiMessage)
                      .thenAccept(approved -> handlePlanApproval(this.agentMessageWithPlan, approved));
                } else {
                    if (aiMessage.hasToolExecutionRequests()) {
                        toolLifecycleManager.handleToolExecution(aiMessage.toolExecutionRequests().getFirst());
                    } else {
                        finishTurn(null);
                    }
                }
            } catch (Exception e) { // Catch exceptions from agent.act()
                logger.error("Exception during agent.act: {}", e.getMessage(), e);
                model.addLog(AiMessage.from("[Jaider] Error during agent interaction: " + e.getMessage()));
                // Ensure finishTurn is called to reset state, even if it's also in .exceptionally()
                // This provides more immediate state reset from this specific failure point.
                finishTurn(AiMessage.from("[Jaider] Agent turn failed: " + e.getMessage()));
            }
        }).exceptionally(e -> { // This will catch other unexpected errors in the async chain
            logger.error("Exception in agent turn (CompletableFuture.exceptionally): {}", e.getMessage(), e);
            // Check if the exception is already wrapped from our try-catch block
            // to avoid double logging or overly generic messages if possible.
            // However, finishTurn here ensures a fallback cleanup.
            finishTurn(AiMessage.from("[Error] Unexpected error in agent turn processing: " + e.getMessage()));
            return null;
        });
    }

    public void handlePlanApproval(AiMessage agentMessageWithPlan, boolean planApproved) {
        if (planApproved) {
            chatMemory.add(UserMessage.from("Plan approved. Proceed."));
            if (agentMessageWithPlan.hasToolExecutionRequests()) {
                toolLifecycleManager.handleToolExecution(agentMessageWithPlan.toolExecutionRequests().getFirst());
            } else {
                processAgentTurnPublic(false);
            }
        } else {
            chatMemory.add(UserMessage.from("Plan rejected. Propose a new one."));
            processAgentTurnPublic(true);
        }
        ui.redraw(model);
    }

    // Made public to be callable from App's finishTurnPublic
    public void finishTurn(ToolExecutionRequest request, String result) {
        if (request != null) {
            chatMemory.add(ToolExecutionResultMessage.from(request, result));
        }
        processAgentTurnPublic(false);
    }

    // Made public to be callable from App's finishTurnPublic
    public void finishTurn(ChatMessage message) {
        if (message != null) {
            model.addLog(message);
        }
        app.setStatePublic(App.State.IDLE);
        model.statusBarText = "Awaiting input.";
        sessionManager.saveSession();
        if (selfUpdateService != null) { // Call SelfUpdateService method
            selfUpdateService.checkAndTriggerSelfUpdateConfirmation();
        } else {
            logger.warn("SelfUpdateService not available in AgentInteractionService, cannot check for updates.");
        }
        ui.redraw(model);
    }

    private String extractPlan(String messageText) {

        if (messageText == null || messageText.isBlank()) {
            return "";
        }


        String[] planMarkers = {
            "Here's my plan:",
            "My plan is:",
            "Here is my plan:"
        };

        String lowerCaseMessageText = messageText.toLowerCase();

        for (String marker : planMarkers) {
            int markerIndex = lowerCaseMessageText.indexOf(marker.toLowerCase());
            if (markerIndex != -1) {
                String fromMarker = messageText.substring(markerIndex + marker.length()).trim();
                int endOfPlanIndex = fromMarker.indexOf("END_OF_PLAN");
                if (endOfPlanIndex != -1) {
                    return fromMarker.substring(0, endOfPlanIndex).trim();
                }
                return fromMarker;
            }
        }

        String[] lines = messageText.split("\\r?\\n");
        StringBuilder planBuilder = new StringBuilder();
        boolean inPlanList = false;
        int planLines = 0;
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.matches("^\\d+\\.\\s+.*") ||
                trimmedLine.matches("^\\*\\s+.*") ||
                trimmedLine.matches("^-\\s+.*")) {
                if (!inPlanList) {
                    inPlanList = true;
                }
                planBuilder.append(line).append(System.lineSeparator());
                planLines++;
            } else {
                if (inPlanList) {
                    if (planLines >= 2) break;
                    else {
                        planBuilder.setLength(0);
                        inPlanList = false;
                        planLines = 0;
                    }
                }
            }
        }

        if (inPlanList && planLines >= 2) {
             String extracted = planBuilder.toString().trim();
             int endOfPlanIndex = extracted.indexOf("END_OF_PLAN");
             if (endOfPlanIndex != -1) {
                 return extracted.substring(0, endOfPlanIndex).trim();
             }
             return extracted;
        }
        return messageText;
    }
}
