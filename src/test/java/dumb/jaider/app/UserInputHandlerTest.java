package dumb.jaider.app;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dumb.jaider.commands.*;
import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.suggestion.ActiveSuggestion;
import dumb.jaider.suggestion.ProactiveSuggestionService;
import dumb.jaider.ui.UI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
    // AgentInteractionService is not directly used by UserInputHandler based on its constructor,
    // but App uses it. So keeping it for mockApp setup if needed by other tests.
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

    @Mock // Changed from @Spy to @Mock to avoid interaction issues with clear()
    private Map<String, Command> commandsMap;

    @InjectMocks
    private UserInputHandler userInputHandler;

    @BeforeEach
    void setUp() {
        // Reset mocks if needed, or ensure clean state.
        // For commandsMap, since it's @Mock, it's reset automatically by MockitoExtension.
        // If it were initialized here (e.g. new HashMap<>()), it would need manual clearing.
        // We will stub its behavior (e.g., when(commandsMap.get(...))) in individual tests.
    }

    // --- Command Handling Tests ---

    @Test
    void testHandleUserInput_knownCommand_executesCommand() {
        when(mockApp.getState()).thenReturn(App.State.IDLE);
        when(commandsMap.get("/add")).thenReturn(mockAddCommand);
        userInputHandler.handleUserInput("/add file.txt");
        verify(mockAddCommand).execute(eq("file.txt"), any(AppContext.class));
    }

    @Test
    void testHandleUserInput_knownCommand_noArgs_executesCommand() {
        when(mockApp.getState()).thenReturn(App.State.IDLE);
        when(commandsMap.get("/help")).thenReturn(mockHelpCommand);
        userInputHandler.handleUserInput("/help");
        verify(mockHelpCommand).execute(eq(""), any(AppContext.class));
    }

    @Test
    void testHandleUserInput_knownCommand_extraSpaces_executesCommandAndTrimsArgs() {
        when(mockApp.getState()).thenReturn(App.State.IDLE);
        when(commandsMap.get("/add")).thenReturn(mockAddCommand);
        userInputHandler.handleUserInput("/add   file.txt  ");
        verify(mockAddCommand).execute(eq("file.txt"), any(AppContext.class)); // Argument should be trimmed
    }

    @Test
    void testHandleUserInput_knownCommand_extraSpacesBetweenArgs_executesCommandAndTrimsArgs() {
        when(mockApp.getState()).thenReturn(App.State.IDLE);
        when(commandsMap.get("/add")).thenReturn(mockAddCommand);
        userInputHandler.handleUserInput("/add   file.txt   another.txt  ");
        verify(mockAddCommand).execute(eq("file.txt   another.txt"), any(AppContext.class)); // Inner spaces preserved, outer trimmed
    }


    @Test
    void testHandleUserInput_unknownCommand_logsMessage() {
        when(mockApp.getState()).thenReturn(App.State.IDLE);
        when(commandsMap.get("/unknowncmd")).thenReturn(null); // Ensure it's not in the map
        userInputHandler.handleUserInput("/unknowncmd args");

        // UserInputHandler logs the user message first
        verify(mockJaiderModel).addLog(UserMessage.from("/unknowncmd args"));
        // Then logs the "Unknown command" message
        verify(mockJaiderModel).addLog(AiMessage.from("[Jaider] Unknown command: /unknowncmd"));
        verify(mockApp, never()).processAgentTurnPublic(anyBoolean()); // Should not go to agent
        // Verify no command.execute was called on any known mock command
        verify(mockAddCommand, never()).execute(anyString(), any(AppContext.class));
        verify(mockHelpCommand, never()).execute(anyString(), any(AppContext.class));
    }

    // --- Non-Command Input (Agent Interaction) Tests ---

    @Test
    void testHandleUserInput_nonCommandInput_delegatesToAgentInteractionService() {
        when(mockApp.getState()).thenReturn(App.State.IDLE);
        var agentMessage = "This is a message for the agent.";
        // Ensure the input does not start with '/' so it's not treated as a command
        userInputHandler.handleUserInput(agentMessage);

        verify(mockJaiderModel).addLog(UserMessage.from(agentMessage));
        verify(mockApp).processAgentTurnPublic(true);
        verify(commandsMap, never()).get(anyString()); // No command lookup should happen
    }

    @Test
    void testHandleUserInput_emptyInput_isIgnored() {
        var input = "";
        userInputHandler.handleUserInput(input);
        // UserInputHandler should now return early for blank input before logging.
        verifyNoInteractions(mockJaiderModel);
        verifyNoInteractions(mockApp);
        verifyNoInteractions(mockProactiveSuggestionService);
        verifyNoInteractions(commandsMap);
    }

    @Test
    void testHandleUserInput_whitespaceOnlyInput_isIgnored() {
        var input = "   \t   ";
        userInputHandler.handleUserInput(input);
        // UserInputHandler should now return early for blank input before logging.
        verifyNoInteractions(mockJaiderModel);
        verifyNoInteractions(mockApp);
        verifyNoInteractions(mockProactiveSuggestionService);
        verifyNoInteractions(commandsMap);
    }

    // --- State-Dependent Input Handling Tests ---

    @Test
    void testHandleUserInput_whenWaitingForConfirmation_yes_routesToConfirmPlanContinuation() {
        when(mockApp.getState()).thenReturn(App.State.WAITING_USER_CONFIRMATION);
        var input = "yes";
        userInputHandler.handleUserInput(input);
        verify(mockJaiderModel).addLog(UserMessage.from(input));
        verify(mockApp).processAgentTurnPublic(true);

        input = "YES";
        userInputHandler.handleUserInput(input); // Case-insensitivity
        verify(mockJaiderModel).addLog(UserMessage.from(input));
        verify(mockApp, times(2)).processAgentTurnPublic(true);
    }

    @Test
    void testHandleUserInput_whenWaitingForConfirmation_no_routesToConfirmPlanContinuation() {
        when(mockApp.getState()).thenReturn(App.State.WAITING_USER_CONFIRMATION);
        var input = "no";
        userInputHandler.handleUserInput(input);
        verify(mockJaiderModel).addLog(UserMessage.from(input));
        verify(mockApp).processAgentTurnPublic(true);

        input = "NO";
        userInputHandler.handleUserInput(input); // Case-insensitivity
        verify(mockJaiderModel).addLog(UserMessage.from(input));
        verify(mockApp, times(2)).processAgentTurnPublic(true);
    }

    @Test
    void testHandleUserInput_whenWaitingForConfirmation_otherInput_routesToConfirmPlanContinuationWithFalse() {
        // UserInputHandler passes the input to the app, it doesn't interpret "maybe" as "false" directly for confirmPlanContinuation
        when(mockApp.getState()).thenReturn(App.State.WAITING_USER_CONFIRMATION);
        var input = "maybe";
        userInputHandler.handleUserInput(input);
        verify(mockJaiderModel).addLog(UserMessage.from(input));
        verify(mockApp).processAgentTurnPublic(true);
    }

    @Test
    void testHandleUserInput_whenWaitingForConfirmation_commandInput_isNotInterceptedIfHandlerPrioritizesState() {
        // This test depends on UserInputHandler's internal logic order.
        // If state check comes before command check, commands might be ignored in this state.
        // If command check is first, then /help should execute.
        // The UserInputHandler logic: if state is WAITING_USER_CONFIRMATION, it will log and call processAgentTurnPublic.
        // It does NOT bypass this for commands. The command execution path is only hit if not in specific states OR if a suggestion is active.
        // The UserInputHandler logic for WAITING_USER_CONFIRMATION:
        // 1. Logs UserMessage.
        // 2. Clears suggestions (if any, or if not an accept command - "/help" is not).
        // 3. Executes the command (since it starts with "/").
        // It does NOT call app.processAgentTurnPublic(true) in the command execution path.
        when(mockApp.getState()).thenReturn(App.State.WAITING_USER_CONFIRMATION);
        when(commandsMap.get("/help")).thenReturn(mockHelpCommand);
        var input = "/help";

        userInputHandler.handleUserInput(input);

        verify(mockJaiderModel).addLog(UserMessage.from(input));
        verify(mockHelpCommand).execute(eq(""), any(AppContext.class)); // Command should execute
        verify(mockApp, never()).processAgentTurnPublic(anyBoolean()); // This should not be called if command executes
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
        when(mockApp.getState()).thenReturn(App.State.IDLE);
        // when(mockProactiveSuggestionService.getActiveSuggestion()).thenReturn(mockActiveSuggestion); // Replaced by mockJaiderModel.getActiveSuggestions()
        // Assuming /accept is a command that gets mapped
        when(commandsMap.get("/accept")).thenReturn(mockAcceptSuggestionCommand);
        when(mockJaiderModel.getActiveSuggestions()).thenReturn(List.of(mockActiveSuggestion)); // Suggestion is active

        userInputHandler.handleUserInput("/accept");

        verify(mockJaiderModel).addLog(UserMessage.from("/accept")); // Input is logged
        // With new logic, clearActiveSuggestions is NOT called if command is /accept or /a AND suggestions are active
        verify(mockJaiderModel, never()).clearActiveSuggestions();
        // Verify the AcceptSuggestionCommand is executed
        verify(mockAcceptSuggestionCommand).execute(eq(""), any(AppContext.class));
    }

    @Test
    void testHandleUserInput_acceptAliasCommand_a_whenSuggestionActive_acceptsSuggestion() {
        when(mockApp.getState()).thenReturn(App.State.IDLE);
        when(mockJaiderModel.getActiveSuggestions()).thenReturn(List.of(mockActiveSuggestion));
        when(commandsMap.get("/a")).thenReturn(mockAcceptSuggestionCommand); // Assuming /a maps to AcceptSuggestionCommand

        userInputHandler.handleUserInput("/a");
        verify(mockJaiderModel).addLog(UserMessage.from("/a")); // Input is logged
         // With new logic, clearActiveSuggestions is NOT called if command is /a or /accept AND suggestions are active
        verify(mockJaiderModel, never()).clearActiveSuggestions();
        verify(mockAcceptSuggestionCommand).execute(eq(""), any(AppContext.class));
    }

    @Test
    void testHandleUserInput_acceptAliasCommand_y_whenSuggestionActive_acceptsSuggestion() {
        // Test if "y" is treated as an alias for accept when a suggestion is active,
        // but NOT when in WAITING_USER_CONFIRMATION state (that's a different path).
        // This requires that normal input processing (which includes suggestion checking) happens
        // if not in a special state like WAITING_USER_CONFIRMATION.
        when(mockApp.getState()).thenReturn(App.State.IDLE); // Ensuring READY is replaced with IDLE
        when(mockJaiderModel.getActiveSuggestions()).thenReturn(List.of(mockActiveSuggestion)); // Ensuring this is used
        // No command mapping for "y", it's treated as a special alias for suggestions.
        // The UserInputHandler directly calls acceptSuggestion on ProactiveSuggestionService for "y" or "yes"
        // if a suggestion is active and not in a confirmation state.
        // MODIFIED: "y" is not a special command for accepting suggestions directly.
        // It should clear suggestions and be processed as normal input.
        when(mockApp.getState()).thenReturn(App.State.IDLE); // Changed from READY
        when(mockJaiderModel.getActiveSuggestions()).thenReturn(List.of(mockActiveSuggestion));


        userInputHandler.handleUserInput("y");

        verify(mockJaiderModel).addLog(UserMessage.from("y"));
        verify(mockJaiderModel).clearActiveSuggestions();
        verify(mockApp).processAgentTurnPublic(true);
        verifyNoInteractions(mockAcceptSuggestionCommand);
        verify(commandsMap, never()).get(anyString());
    }

    @Test
    void testHandleUserInput_yesAliasCommand_whenSuggestionActive_acceptsSuggestion() {
        // MODIFIED: "yes" is not a special command for accepting suggestions directly.
        // It should clear suggestions and be processed as normal input.
        when(mockApp.getState()).thenReturn(App.State.IDLE);
        when(mockJaiderModel.getActiveSuggestions()).thenReturn(List.of(mockActiveSuggestion));

        userInputHandler.handleUserInput("yes");

        verify(mockJaiderModel).addLog(UserMessage.from("yes"));
        verify(mockJaiderModel).clearActiveSuggestions();
        verify(mockApp).processAgentTurnPublic(true);
        verifyNoInteractions(mockAcceptSuggestionCommand);
    }


    @Test
    void testHandleUserInput_nonAcceptCommand_whenSuggestionActive_cancelsSuggestionAndProcessesCommand() {
        when(mockApp.getState()).thenReturn(App.State.IDLE);
        when(mockJaiderModel.getActiveSuggestions()).thenReturn(List.of(mockActiveSuggestion));
        when(commandsMap.get("/help")).thenReturn(mockHelpCommand); // A known, non-accept command

        userInputHandler.handleUserInput("/help");

        verify(mockJaiderModel).addLog(UserMessage.from("/help")); // Input is logged
        verify(mockJaiderModel).clearActiveSuggestions(); // Suggestion is cleared because /help is not /accept or /a
        verify(mockJaiderModel).addLog(AiMessage.from("[Jaider] Suggestions cleared due to new input."));
        verify(mockHelpCommand).execute(eq(""), any(AppContext.class)); // Command still executes
    }

    @Test
    void testHandleUserInput_nonAcceptNonCommandMessage_whenSuggestionActive_cancelsSuggestionAndProcessesMessage() {
        when(mockApp.getState()).thenReturn(App.State.IDLE);
        when(mockJaiderModel.getActiveSuggestions()).thenReturn(List.of(mockActiveSuggestion));
        var agentMessage = "This is a normal message.";

        userInputHandler.handleUserInput(agentMessage);

        verify(mockJaiderModel).addLog(UserMessage.from(agentMessage)); // Input is logged
        verify(mockJaiderModel).clearActiveSuggestions(); // Suggestion is cleared
        verify(mockJaiderModel).addLog(AiMessage.from("[Jaider] Suggestions cleared due to new input."));
        verify(mockApp).processAgentTurnPublic(true);
    }


    @Test
    void testHandleUserInput_anyCommand_whenNoSuggestionActive_processesCommandNormally() {
        when(mockApp.getState()).thenReturn(App.State.IDLE);
        when(mockJaiderModel.getActiveSuggestions()).thenReturn(java.util.Collections.emptyList()); // No active suggestion
        when(commandsMap.get("/help")).thenReturn(mockHelpCommand);

        userInputHandler.handleUserInput("/help");

        verify(mockJaiderModel).addLog(UserMessage.from("/help")); // Input is logged
        // clearActiveSuggestions IS called in the new logic path when suggestions are empty and not an accept command
        verify(mockJaiderModel).clearActiveSuggestions();
        verify(mockHelpCommand).execute(eq(""), any(AppContext.class));
    }

    @Test
    void testHandleUserInput_nonCommandMessage_whenNoSuggestionActive_processesMessageNormally() {
        when(mockApp.getState()).thenReturn(App.State.IDLE);
        when(mockJaiderModel.getActiveSuggestions()).thenReturn(java.util.Collections.emptyList()); // No active suggestion
        var agentMessage = "Another normal message.";

        userInputHandler.handleUserInput(agentMessage);

        verify(mockJaiderModel).addLog(UserMessage.from(agentMessage));
        // clearActiveSuggestions IS called in the new logic path when suggestions are empty and not an accept command
        verify(mockJaiderModel).clearActiveSuggestions();
        verify(mockApp).processAgentTurnPublic(true);
    }
}
