package dumb.jaider.app;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.ui.UI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Manages interactions with the AI agent.
 * This class orchestrates the agent's turn, including handling asynchronous operations,
 * managing plan approvals from the user, and processing tool calls initiated by the agent.
 * It integrates with various services like {@link AgentService}, {@link ToolLifecycleManager},
 * {@link SessionManager}, and {@link UI} to provide a complete interaction loop.
 */
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

    /**
     * Constructs an {@code AgentInteractionService}.
     *
     * @param app                 The main application class for global state and UI updates.
     * @param model               The JaiderModel for managing application data.
     * @param chatMemory          The chat memory for storing conversation history.
     * @param ui                  The UI for user interactions.
     * @param agentService        The service for managing AI agents.
     * @param toolLifecycleManager The manager for handling tool execution.
     * @param sessionManager      The manager for session persistence.
     * @param selfUpdateService   The service for handling self-updates.
     */
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

    /**
     * Processes a single turn of the agent's interaction.
     * This method operates asynchronously. It sets the application state to {@code AGENT_THINKING},
     * updates the UI, and then invokes the current agent.
     * <p>
     * If {@code expectPlan} is true, the agent's response is checked for a plan. If a plan is found,
     * it is extracted and presented to the user for approval via the UI. The application state
     * transitions to {@code WAITING_USER_PLAN_APPROVAL}.
     * <p>
     * If {@code expectPlan} is false, or if a plan was approved and the agent's message contains
     * tool execution requests, those requests are handled by the {@link ToolLifecycleManager}.
     * Otherwise, the turn is finished.
     * <p>
     * Error handling is included to catch exceptions during the agent's action or subsequent processing,
     * ensuring the application state is reset correctly.
     *
     * @param expectPlan A boolean indicating whether to expect a plan from the agent in this turn.
     */
    public void processAgentTurnPublic(boolean expectPlan) {
        app.setStatePublic(App.State.AGENT_THINKING);
        model.statusBarText = "Agent is thinking...";
        app.updateTokenCountPublic(); // Call method on App
        ui.redraw(model);

        var currentAgent = agentService.getCurrentAgent();
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
                    var fullMessageText = aiMessage.text();
                    var planText = extractPlan(fullMessageText);
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

    /**
     * Handles the user's decision on a proposed plan.
     * If the plan is approved and the agent's message (which contained the plan)
     * has tool execution requests, these are handled. Otherwise, the agent
     * is prompted to proceed with the next step (which might be another thought cycle or action).
     * If the plan is rejected, the agent is informed and asked to propose a new plan.
     *
     * @param agentMessageWithPlan The AI message that contained the plan.
     * @param planApproved         {@code true} if the user approved the plan, {@code false} otherwise.
     */
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

    /**
     * Finishes the current agent turn after a tool execution.
     * Adds the tool execution result to the chat memory and triggers the agent
     * for the next step, not expecting a plan.
     * This method is typically called by the {@link ToolLifecycleManager} after a tool has finished executing.
     *
     * @param request The original tool execution request.
     * @param result  The result of the tool execution.
     */
    public void finishTurn(ToolExecutionRequest request, String result) {
        if (request != null) {
            chatMemory.add(ToolExecutionResultMessage.from(request, result));
        }
        processAgentTurnPublic(false);
    }

    /**
     * Finishes the current agent turn, typically when no tool execution is involved
     * or after an error.
     * Adds an optional message to the log, sets the application state to {@code IDLE},
     * updates the status bar, saves the session, and checks for self-updates.
     *
     * @param message An optional {@link ChatMessage} to log. Can be null.
     */
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

    /**
     * Extracts a plan from the agent's message text using heuristics.
     * <p>
     * The method first looks for explicit plan markers like "Here's my plan:".
     * If a marker is found, the text following the marker is considered the plan.
     * If an "END_OF_PLAN" marker is present, the plan is truncated at that point.
     * <p>
     * If no explicit markers are found, the method attempts to detect a numbered or bulleted list
     * (using digits followed by a period, asterisks, or hyphens) as a plan.
     * A list is considered a plan if it contains at least two items.
     * <p>
     * If neither heuristic yields a plan, the entire message text is returned.
     *
     * @param messageText The text of the agent's message.
     * @return The extracted plan, or the full messageText if no plan is identified.
     *         Returns an empty string if the input messageText is null or blank.
     */
    private String extractPlan(String messageText) {

        if (messageText == null || messageText.isBlank()) {
            return "";
        }


        var planMarkers = new String[]{
                "Here's my plan:",
                "My plan is:",
                "Here is my plan:"
        };

        var lowerCaseMessageText = messageText.toLowerCase();

        for (var marker : planMarkers) {
            var markerIndex = lowerCaseMessageText.indexOf(marker.toLowerCase());
            if (markerIndex != -1) {
                var fromMarker = messageText.substring(markerIndex + marker.length()).trim();
                var endOfPlanIndex = fromMarker.indexOf("END_OF_PLAN");
                if (endOfPlanIndex != -1) {
                    return fromMarker.substring(0, endOfPlanIndex).trim();
                }
                return fromMarker;
            }
        }

        var lines = messageText.split("\\r?\\n");
        var planBuilder = new StringBuilder();
        var inPlanList = false;
        var planLines = 0;
        for (var line : lines) {
            var trimmedLine = line.trim();
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
            var extracted = planBuilder.toString().trim();
            var endOfPlanIndex = extracted.indexOf("END_OF_PLAN");
             if (endOfPlanIndex != -1) { // Ensure "END_OF_PLAN" is respected for list-based plans too
                 return extracted.substring(0, endOfPlanIndex).trim();
             }
             return extracted;
        }
        // If no plan is found through markers or list detection, return the original message.
        return messageText;
    }
}
