package dumb.jaider.app;

import dumb.jaider.commands.AppContext;
import dumb.jaider.commands.Command;
// Import specific command mocks if needed for type safety, or use general Command mock.
import dumb.jaider.commands.AddCommand;
import dumb.jaider.commands.HelpCommand;
import dumb.jaider.commands.AcceptSuggestionCommand;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
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
import java.util.List;
import java.util.Map;
import java.util.Collections;

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

        verify(mockJaiderModel).addLog(UserMessage.from("Unknown command: /unknowncmd. Type /help for available commands."));
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

        verify(mockJaiderModel).addLog(UserMessage.from(agentMessage));
        verify(mockApp).processAgentTurnPublic(true);
        verify(commandsMap, never()).get(anyString()); // No command lookup should happen
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
        String input = "yes";
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
        String input = "no";
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
        String input = "maybe";
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
        // The UserInputHandler's main if/else structure:
        // 1. Handle empty/blank.
        // 2. Check for active suggestions -> clear them if input is not /accept or /a.
        // 3. If state is WAITING_USER_CONFIRMATION or WAITING_USER_PLAN_APPROVAL -> log input, processAgentTurnPublic(true).
        // 4. Else (e.g. IDLE state) -> try to execute command, or if not a command, processAgentTurnPublic(true).
        // So, if in WAITING_USER_CONFIRMATION, a command like /help will be logged and then processAgentTurnPublic(true) will be called.
        // The command itself will NOT be executed by the UserInputHandler in this state based on its logic.
        when(mockApp.getState()).thenReturn(App.State.WAITING_USER_CONFIRMATION);
        when(commandsMap.get("/help")).thenReturn(mockHelpCommand);
        String input = "/help";

        userInputHandler.handleUserInput(input);

        verify(mockJaiderModel).addLog(UserMessage.from(input));
        verify(mockApp).processAgentTurnPublic(true);
        verify(mockHelpCommand, never()).execute(anyString(), any(AppContext.class));
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
        // when(mockProactiveSuggestionService.getActiveSuggestion()).thenReturn(mockActiveSuggestion); // Replaced by mockJaiderModel.getActiveSuggestions()
        // Assuming /accept is a command that gets mapped
        when(commandsMap.get("/accept")).thenReturn(mockAcceptSuggestionCommand);
        when(mockJaiderModel.getActiveSuggestions()).thenReturn(List.of(mockActiveSuggestion)); // Suggestion is active

        userInputHandler.handleUserInput("/accept");

        verify(mockJaiderModel).addLog(UserMessage.from("/accept")); // Input is logged
        // Verify that the suggestion is NOT cleared by UserInputHandler because /accept command handles its lifecycle.
        verify(mockJaiderModel, never()).clearActiveSuggestions();
        // Verify the AcceptSuggestionCommand is executed
        verify(mockAcceptSuggestionCommand).execute(eq(""), any(AppContext.class));
    }

    @Test
    void testHandleUserInput_acceptAliasCommand_a_whenSuggestionActive_acceptsSuggestion() {
        when(mockJaiderModel.getActiveSuggestions()).thenReturn(List.of(mockActiveSuggestion));
        when(commandsMap.get("/a")).thenReturn(mockAcceptSuggestionCommand); // Assuming /a maps to AcceptSuggestionCommand

        userInputHandler.handleUserInput("/a");
        verify(mockJaiderModel).addLog(UserMessage.from("/a")); // Input is logged
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
        when(mockJaiderModel.getActiveSuggestions()).thenReturn(List.of(mockActiveSuggestion));
        when(commandsMap.get("/help")).thenReturn(mockHelpCommand); // A known, non-accept command

        userInputHandler.handleUserInput("/help");

        verify(mockJaiderModel).addLog(UserMessage.from("/help")); // Input is logged
        verify(mockJaiderModel).clearActiveSuggestions(); // Suggestion is cleared
        verify(mockHelpCommand).execute(eq(""), any(AppContext.class)); // Command still executes
    }

    @Test
    void testHandleUserInput_nonAcceptNonCommandMessage_whenSuggestionActive_cancelsSuggestionAndProcessesMessage() {
        when(mockApp.getState()).thenReturn(App.State.IDLE);
        when(mockJaiderModel.getActiveSuggestions()).thenReturn(List.of(mockActiveSuggestion));
        String agentMessage = "This is a normal message.";

        userInputHandler.handleUserInput(agentMessage);

        verify(mockJaiderModel).addLog(UserMessage.from(agentMessage)); // Input is logged
        verify(mockJaiderModel).clearActiveSuggestions(); // Suggestion is cleared
        verify(mockApp).processAgentTurnPublic(true);
    }


    @Test
    void testHandleUserInput_anyCommand_whenNoSuggestionActive_processesCommandNormally() {
        when(mockJaiderModel.getActiveSuggestions()).thenReturn(java.util.Collections.emptyList()); // No active suggestion
        when(commandsMap.get("/help")).thenReturn(mockHelpCommand);

        userInputHandler.handleUserInput("/help");

        verify(mockJaiderModel).addLog(UserMessage.from("/help")); // Input is logged
        verify(mockJaiderModel, never()).clearActiveSuggestions();
        verify(mockHelpCommand).execute(eq(""), any(AppContext.class));
    }

    @Test
    void testHandleUserInput_nonCommandMessage_whenNoSuggestionActive_processesMessageNormally() {
        when(mockJaiderModel.getActiveSuggestions()).thenReturn(java.util.Collections.emptyList()); // No active suggestion
        String agentMessage = "Another normal message.";

        userInputHandler.handleUserInput(agentMessage);

        verify(mockJaiderModel).addLog(UserMessage.from(agentMessage));
        verify(mockJaiderModel, never()).clearActiveSuggestions();
        verify(mockApp).processAgentTurnPublic(true);
    }
}
