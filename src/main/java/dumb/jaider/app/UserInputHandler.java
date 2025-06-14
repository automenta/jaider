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

/**
 * Handles user input from the UI, parsing it to determine if it's a command
 * or a message intended for the AI agent.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Parses input to identify command invocations (lines starting with "/").</li>
 *     <li>Routes known commands to their respective {@link Command} objects for execution.</li>
 *     <li>Handles unknown commands by providing feedback to the user.</li>
 *     <li>Forwards non-command input to the {@link AgentInteractionService} (via {@link App})
 *         for processing by the current AI agent.</li>
 *     <li>Manages interactions with {@link ProactiveSuggestionService} to display and
 *         potentially accept or cancel suggestions based on user input.</li>
 *     <li>Handles special input cases based on the application's current state (e.g.,
 *         confirming a multi-step plan).</li>
 *     <li>Supports direct tool invocation (lines starting with "!").</li>
 * </ul>
 */
public class UserInputHandler {
    private static final Logger logger = LoggerFactory.getLogger(UserInputHandler.class);

    private final App app;
    private final JaiderModel jaiderModel;
    private final Config config;
    private final UI ui;
    private final Agent agent;
    private final ProactiveSuggestionService proactiveSuggestionService;
    private final Map<String, Command> commands;

    /**
     * Constructs a UserInputHandler.
     *
     * @param app The main application instance, used for state management and agent interaction.
     * @param jaiderModel The application's data model, for logging and state updates.
     * @param config The application configuration.
     * @param ui The user interface instance, for redrawing.
     * @param agent The current active agent (can be null if no agent is active).
     *              Note: The direct 'agent' field seems to be from an older version based on tests.
     *              Modern interaction likely goes through {@code App} or {@code AgentService}.
     *              This constructor signature might need review against current App/AgentService structure.
     * @param proactiveSuggestionService Service for generating and managing proactive suggestions.
     * @param commands A map of command names to {@link Command} instances.
     */
    public UserInputHandler(App app,
                            JaiderModel jaiderModel,
                            Config config,
                            UI ui,
                            Agent agent, // Review: Is direct agent still passed or obtained via App/AgentService?
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

    /**
     * Handles a line of text input from the user.
     * <p>
     * The method first checks the application's current state. If the app is busy or
     * waiting for a specific type of confirmation not related to general input,
     * it may inform the user and return.
     * <p>
     * Otherwise, it processes the input:
     * <ul>
     *     <li>Input starting with "!" is treated as a direct tool invocation.</li>
     *     <li>Input starting with "/" is treated as a command.</li>
     *     <li>Other input is considered a message for the AI agent, which may also
     *         trigger proactive suggestions.</li>
     * </ul>
     * After processing, the UI is typically redrawn.
     *
     * @param input The raw string input from the user.
     */
    public void handleUserInput(String input) {
        // Check app state; if busy or in a specific confirmation loop, might defer general input.
        // The original check was: app.getState() != App.State.IDLE && app.getState() != App.State.WAITING_USER_PLAN_APPROVAL
        // This needs to be re-evaluated based on all possible App.State values and their meaning for input handling.
        // For example, if in WAITING_USER_CONFIRMATION, specific inputs like "yes"/"no" are expected.
        // The current UserInputHandlerTest implies that some states (like WAITING_USER_CONFIRMATION) have special handlers
        // that might take precedence or alter behavior for "yes"/"no".
        // The provided code snippet for UserInputHandler does not show that specific state handling for "yes"/"no"
        // before command/agent routing, which might be a discrepancy or handled within App/AgentInteractionService.
        // For this Javadoc, we assume the general flow as per the provided method body.

        if (app.getState() != App.State.IDLE && app.getState() != App.State.WAITING_USER_PLAN_APPROVAL && app.getState() != App.State.WAITING_USER_CONFIRMATION) { // Adjusted to allow input during WAITING_USER_CONFIRMATION
            jaiderModel.addLog(AiMessage.from("[Jaider] Please wait, I'm currently busy or waiting for a different type of response."));
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
