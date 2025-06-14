package dumb.jaider.app;

import dumb.jaider.commands.Command;
// Import specific command mocks if needed for type safety, or use general Command mock.
import dumb.jaider.commands.AddCommand;
import dumb.jaider.commands.HelpCommand;
import dumb.jaider.commands.AcceptSuggestionCommand;

import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.suggestion.ProactiveSuggestionService;
import dumb.jaider.suggestion.ActiveSuggestion; // Assuming this class exists
import dumb.jaider.ui.UI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserInputHandlerTest {

    @Mock
    private App mockApp;
    @Mock
    private JaiderModel mockJaiderModel;
    @Mock
    private Config mockConfig; // Though UserInputHandler constructor doesn't directly take it, AppContext might
    @Mock
    private UI mockUI; // Same as Config
    @Mock
    private AgentService mockAgentService;
    @Mock
    private ProactiveSuggestionService mockProactiveSuggestionService;
    @Mock
    private AgentInteractionService mockAgentInteractionService;

    // Individual command mocks
    @Mock
    private AddCommand mockAddCommand;
    @Mock
    private HelpCommand mockHelpCommand;
    @Mock
    private AcceptSuggestionCommand mockAcceptSuggestionCommand;
    @Mock
    private Command mockGenericCommand; // For unknown command tests or general command behavior

    @Spy
    private Map<String, Command> commandsMap = new HashMap<>(); // Spy to allow partial mocking if needed, or just use @Mock

    @InjectMocks
    private UserInputHandler userInputHandler;

    @BeforeEach
    void setUp() {
        // It's crucial that UserInputHandler gets the mocked commandsMap.
        // If UserInputHandler's constructor takes the map directly:
        // userInputHandler = new UserInputHandler(mockApp, mockJaiderModel, mockConfig, mockUI, mockAgentService, mockProactiveSuggestionService, mockAgentInteractionService, commandsMap);
        // If it's injected via @InjectMocks, ensure the field name in UserInputHandler matches 'commandsMap'.

        // Resetting spy map for clarity in each test, and re-associating it with the handler.
        commandsMap.clear();
        // This re-injection or re-construction might be needed if the map is final in UserInputHandler
        // For now, assume @InjectMocks handles the map correctly or it's passed in constructor for tests.
        // If UserInputHandler initializes its own map, testing becomes harder without refactoring it to accept a map.
        // Let's assume UserInputHandler has a field `commandsMap` that @InjectMocks can fill.

        // Common AppContext setup if UserInputHandler creates it internally,
        // otherwise this is not needed here but when commands are executed.
        // For now, we assume AppContext is created by UserInputHandler or commands when needed.
    }

    // --- Command Handling Tests ---

    @Test
    void testHandleUserInput_knownCommand_executesCommand() {
        when(commandsMap.get("/add")).thenReturn(mockAddCommand);
        userInputHandler.handleUserInput("/add file.txt");
        verify(mockAddCommand).execute(eq("file.txt"), any(AppContext.class));
    }

    @Test
    void testHandleUserInput_knownCommand_noArgs_executesCommand() {
        when(commandsMap.get("/help")).thenReturn(mockHelpCommand);
        userInputHandler.handleUserInput("/help");
        verify(mockHelpCommand).execute(eq(""), any(AppContext.class));
    }

    @Test
    void testHandleUserInput_knownCommand_extraSpaces_executesCommandAndTrimsArgs() {
        when(commandsMap.get("/add")).thenReturn(mockAddCommand);
        userInputHandler.handleUserInput("/add   file.txt  ");
        verify(mockAddCommand).execute(eq("file.txt"), any(AppContext.class));
    }

    @Test
    void testHandleUserInput_knownCommand_extraSpacesBetweenArgs_executesCommandAndTrimsArgs() {
        when(commandsMap.get("/add")).thenReturn(mockAddCommand);
        userInputHandler.handleUserInput("/add   file.txt   another.txt  ");
        verify(mockAddCommand).execute(eq("file.txt   another.txt"), any(AppContext.class));
    }


    @Test
    void testHandleUserInput_unknownCommand_logsMessage() {
        when(commandsMap.get("/unknowncmd")).thenReturn(null); // Ensure it's not in the map
        userInputHandler.handleUserInput("/unknowncmd args");

        verify(mockJaiderModel).addLog("Unknown command: /unknowncmd. Type /help for available commands.");
        verifyNoInteractions(mockAgentInteractionService); // Should not go to agent
        // Verify no command.execute was called on any known mock command
        verify(mockAddCommand, never()).execute(anyString(), any(AppContext.class));
        verify(mockHelpCommand, never()).execute(anyString(), any(AppContext.class));
    }

    // --- Non-Command Input (Agent Interaction) Tests ---

    @Test
    void testHandleUserInput_nonCommandInput_delegatesToAgentInteractionService() {
        String agentMessage = "This is a message for the agent.";
        // Ensure the input does not start with '/' so it's not treated as a command
        userInputHandler.handleUserInput(agentMessage);

        verify(mockAgentInteractionService).processUserInputUnconditional(agentMessage);
        verify(commandsMap, never()).get(anyString()); // No command lookup should happen
        verify(mockJaiderModel, never()).addLog(startsWith("Unknown command:"));
    }

    @Test
    void testHandleUserInput_emptyInput_isIgnored() {
        userInputHandler.handleUserInput("");
        verifyNoInteractions(commandsMap, mockJaiderModel, mockAgentInteractionService, mockProactiveSuggestionService);
    }

    @Test
    void testHandleUserInput_whitespaceOnlyInput_isIgnored() {
        userInputHandler.handleUserInput("   \t   ");
        verifyNoInteractions(commandsMap, mockJaiderModel, mockAgentInteractionService, mockProactiveSuggestionService);
    }

    // --- State-Dependent Input Handling Tests ---

    @Test
    void testHandleUserInput_whenWaitingForConfirmation_yes_routesToConfirmPlanContinuation() {
        when(mockApp.getState()).thenReturn(App.State.WAITING_USER_CONFIRMATION);
        userInputHandler.handleUserInput("yes");
        verify(mockAgentInteractionService).confirmPlanContinuation(true);

        userInputHandler.handleUserInput("YES"); // Case-insensitivity
        verify(mockAgentInteractionService, times(2)).confirmPlanContinuation(true);
    }

    @Test
    void testHandleUserInput_whenWaitingForConfirmation_no_routesToConfirmPlanContinuation() {
        when(mockApp.getState()).thenReturn(App.State.WAITING_USER_CONFIRMATION);
        userInputHandler.handleUserInput("no");
        verify(mockAgentInteractionService).confirmPlanContinuation(false);

        userInputHandler.handleUserInput("NO"); // Case-insensitivity
        verify(mockAgentInteractionService, times(2)).confirmPlanContinuation(false);
    }

    @Test
    void testHandleUserInput_whenWaitingForConfirmation_otherInput_routesToConfirmPlanContinuationWithFalse() {
        // Assuming any input other than "yes" is treated as "no" in this state.
        when(mockApp.getState()).thenReturn(App.State.WAITING_USER_CONFIRMATION);
        userInputHandler.handleUserInput("maybe");
        verify(mockAgentInteractionService).confirmPlanContinuation(false);
    }

    @Test
    void testHandleUserInput_whenWaitingForConfirmation_commandInput_isNotInterceptedIfHandlerPrioritizesState() {
        // This test depends on UserInputHandler's internal logic order.
        // If state check comes before command check, commands might be ignored in this state.
        // If command check is first, then /help should execute.
        // The provided UserInputHandler code prioritizes state.
        when(mockApp.getState()).thenReturn(App.State.WAITING_USER_CONFIRMATION);
        when(commandsMap.get("/help")).thenReturn(mockHelpCommand); // Make /help a known command

        userInputHandler.handleUserInput("/help");

        // Expectation: Input is treated as a "no" to confirmation due to state priority.
        verify(mockAgentInteractionService).confirmPlanContinuation(false);
        verify(mockHelpCommand, never()).execute(anyString(), any(AppContext.class)); // Command should not execute
    }


    // Assuming a similar state for plan approval exists, like WAITING_USER_PLAN_APPROVAL
    // If it's the same as WAITING_USER_CONFIRMATION, these tests might be redundant or need adjustment.
    // For now, let's assume a distinct state and method calls for plan approval.
    // If App.State.WAITING_USER_PLAN_APPROVAL does not exist, these tests would need to be removed or adapted.
    // Based on current App.State, there isn't a specific plan approval state distinct from general confirmation.
    // These tests will be commented out unless such a state is confirmed.
    /*
    @Test
    void testHandleUserInput_whenWaitingForPlanApproval_approve_routesToApproveMethod() {
        // Assuming App.State.WAITING_USER_PLAN_APPROVAL exists
        // when(mockApp.getState()).thenReturn(App.State.WAITING_USER_PLAN_APPROVAL);
        // userInputHandler.handleUserInput("approve");
        // verify(mockAgentInteractionService).approvePlanContinuation(true); // or similar method
    }
    */

    // --- Proactive Suggestion Interaction Tests ---
    @Mock
    private ActiveSuggestion mockActiveSuggestion;

    @Test
    void testHandleUserInput_acceptSuggestionCommand_whenSuggestionActive_acceptsSuggestion() {
        when(mockProactiveSuggestionService.getActiveSuggestion()).thenReturn(mockActiveSuggestion);
        // Assuming /accept is a command that gets mapped
        when(commandsMap.get("/accept")).thenReturn(mockAcceptSuggestionCommand);

        userInputHandler.handleUserInput("/accept");

        // Verify that the suggestion is NOT cancelled because /accept itself handles it.
        verify(mockProactiveSuggestionService, never()).cancelSuggestion();
        // Verify the AcceptSuggestionCommand is executed
        verify(mockAcceptSuggestionCommand).execute(eq(""), any(AppContext.class));
    }

    @Test
    void testHandleUserInput_acceptAliasCommand_a_whenSuggestionActive_acceptsSuggestion() {
        when(mockProactiveSuggestionService.getActiveSuggestion()).thenReturn(mockActiveSuggestion);
        when(commandsMap.get("/a")).thenReturn(mockAcceptSuggestionCommand); // Assuming /a maps to AcceptSuggestionCommand

        userInputHandler.handleUserInput("/a");
        verify(mockProactiveSuggestionService, never()).cancelSuggestion();
        verify(mockAcceptSuggestionCommand).execute(eq(""), any(AppContext.class));
    }

    @Test
    void testHandleUserInput_acceptAliasCommand_y_whenSuggestionActive_acceptsSuggestion() {
        // Test if "y" is treated as an alias for accept when a suggestion is active,
        // but NOT when in WAITING_USER_CONFIRMATION state (that's a different path).
        // This requires that normal input processing (which includes suggestion checking) happens
        // if not in a special state like WAITING_USER_CONFIRMATION.
        when(mockApp.getState()).thenReturn(App.State.READY); // Not in confirmation state
        when(mockProactiveSuggestionService.getActiveSuggestion()).thenReturn(mockActiveSuggestion);
        // No command mapping for "y", it's treated as a special alias for suggestions.
        // The UserInputHandler directly calls acceptSuggestion on ProactiveSuggestionService for "y" or "yes"
        // if a suggestion is active and not in a confirmation state.

        userInputHandler.handleUserInput("y");

        verify(mockProactiveSuggestionService).acceptSuggestion();
        verify(mockProactiveSuggestionService, never()).cancelSuggestion();
        verifyNoInteractions(mockAgentInteractionService); // Should not go to agent as normal message
        verify(commandsMap, never()).get(anyString()); // Should not be treated as a command
    }

    @Test
    void testHandleUserInput_yesAliasCommand_whenSuggestionActive_acceptsSuggestion() {
        when(mockApp.getState()).thenReturn(App.State.READY);
        when(mockProactiveSuggestionService.getActiveSuggestion()).thenReturn(mockActiveSuggestion);

        userInputHandler.handleUserInput("yes");

        verify(mockProactiveSuggestionService).acceptSuggestion();
        verify(mockProactiveSuggestionService, never()).cancelSuggestion();
        verifyNoInteractions(mockAgentInteractionService);
    }


    @Test
    void testHandleUserInput_nonAcceptCommand_whenSuggestionActive_cancelsSuggestionAndProcessesCommand() {
        when(mockProactiveSuggestionService.getActiveSuggestion()).thenReturn(mockActiveSuggestion);
        when(commandsMap.get("/help")).thenReturn(mockHelpCommand); // A known, non-accept command

        userInputHandler.handleUserInput("/help");

        verify(mockProactiveSuggestionService).cancelSuggestion();
        verify(mockHelpCommand).execute(eq(""), any(AppContext.class)); // Command still executes
    }

    @Test
    void testHandleUserInput_nonAcceptNonCommandMessage_whenSuggestionActive_cancelsSuggestionAndProcessesMessage() {
        when(mockApp.getState()).thenReturn(App.State.READY); // Ensure not in a confirmation state
        when(mockProactiveSuggestionService.getActiveSuggestion()).thenReturn(mockActiveSuggestion);
        String agentMessage = "This is a normal message.";

        userInputHandler.handleUserInput(agentMessage);

        verify(mockProactiveSuggestionService).cancelSuggestion();
        verify(mockAgentInteractionService).processUserInputUnconditional(agentMessage); // Message still processed
    }


    @Test
    void testHandleUserInput_anyCommand_whenNoSuggestionActive_processesCommandNormally() {
        when(mockProactiveSuggestionService.getActiveSuggestion()).thenReturn(null); // No active suggestion
        when(commandsMap.get("/help")).thenReturn(mockHelpCommand);

        userInputHandler.handleUserInput("/help");

        verify(mockProactiveSuggestionService, never()).cancelSuggestion();
        verify(mockHelpCommand).execute(eq(""), any(AppContext.class));
    }

    @Test
    void testHandleUserInput_nonCommandMessage_whenNoSuggestionActive_processesMessageNormally() {
        when(mockProactiveSuggestionService.getActiveSuggestion()).thenReturn(null); // No active suggestion
        String agentMessage = "Another normal message.";

        userInputHandler.handleUserInput(agentMessage);

        verify(mockProactiveSuggestionService, never()).cancelSuggestion();
        verify(mockAgentInteractionService).processUserInputUnconditional(agentMessage);
    }
}
