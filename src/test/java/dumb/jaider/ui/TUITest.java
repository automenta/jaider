package dumb.jaider.ui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.TerminalSize; // Added for TextBox size
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.commands.AppContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TUITest {

    @Mock
    MultiWindowTextGUI gui;
    @Mock
    TextGUIThread textGUIThread;
    @Mock
    ActionListBox contextListBox;
    @Mock
    Panel logListBoxPanel;
    @Mock
    Label statusBar;

    @Spy
    JaiderModel model = new JaiderModel(Paths.get("/test/project"));

    @Spy
    @InjectMocks
    TUI tui;

    @Captor
    ArgumentCaptor<Window> windowCaptor;

    @BeforeEach
    void setUp() {
        tui.gui = gui;
        tui.contextListBox = contextListBox;
        tui.logListBox = logListBoxPanel;
        tui.statusBar = statusBar;

        lenient().when(gui.getGUIThread()).thenReturn(textGUIThread);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(textGUIThread).invokeLater(any(Runnable.class));
    }

    // --- redraw tests ---
    @Test
    void redraw_shouldClearAndRepopulateListsAndSetStatusBar() {
        model.filesInContext.clear();
        model.filesInContext.addAll(new HashSet<>(Arrays.asList(
                Paths.get("/test/project/file1.txt"),
                Paths.get("/test/project/subdir/file2.java")
        )));

        model.logMessages.clear();
        UserMessage userMsg = UserMessage.from("User query");
        AiMessage aiMsgPlain = AiMessage.from("AI response");
        AiMessage aiMsgWithTool = AiMessage.from(ToolExecutionRequest.builder().name("testTool").id("123").arguments("{}").build());
        model.logMessages.addAll(Arrays.asList(userMsg, aiMsgPlain, aiMsgWithTool));

        model.agentMode = "Coder";
        model.statusBarText = "Ready";
        model.currentTokenCount = 123;

        tui.redraw(model);

        verify(contextListBox).clearItems();
        ArgumentCaptor<String> contextItemCaptor = ArgumentCaptor.forClass(String.class);
        verify(contextListBox, times(2)).addItem(contextItemCaptor.capture(), isNull());
        List<String> capturedContextItems = contextItemCaptor.getAllValues();
        assertTrue(capturedContextItems.contains("file1.txt"));
        assertTrue(capturedContextItems.contains("subdir/file2.java"));

        verify(logListBoxPanel).removeAllComponents();
        ArgumentCaptor<Label> labelCaptor = ArgumentCaptor.forClass(Label.class);
        verify(logListBoxPanel, times(2)).addComponent(labelCaptor.capture());

        List<Label> capturedLabels = labelCaptor.getAllValues();
        assertEquals("[USER] User query", capturedLabels.get(0).getText());
        assertEquals("[AI] AI response", capturedLabels.get(1).getText());

        verify(statusBar).setText(" | Mode: Coder | Ready | Tokens: 123");
    }

    @Test
    void redraw_whenGuiIsNull_shouldDoNothing() {
        tui.gui = null;
        tui.redraw(model);
        verifyNoInteractions(contextListBox, logListBoxPanel, statusBar, textGUIThread);
    }

    @Test
    void redraw_withEmptyFilesInContext_shouldAddNoItemsToContextList() {
        model.filesInContext.clear();
        model.logMessages.clear();
        tui.redraw(model);
        verify(contextListBox).clearItems();
        verify(contextListBox, never()).addItem(anyString(), any(Runnable.class));
    }

    @Test
    void redraw_withEmptyLogMessages_shouldAddNoComponentsToLogList() {
        model.filesInContext.clear();
        model.logMessages.clear();
        tui.redraw(model);
        verify(logListBoxPanel).removeAllComponents();
        verify(logListBoxPanel, never()).addComponent(any(Component.class));
    }

    @Test
    void redraw_withNullOrBlankLogMessageText_shouldSkipMessage() {
        model.filesInContext.clear();
        model.logMessages.clear();
        model.logMessages.add(UserMessage.from("Valid message"));
        model.logMessages.add(AiMessage.from(ToolExecutionRequest.builder().name("skippedTool").id("456").arguments("{}").build()));
        tui.redraw(model);
        verify(logListBoxPanel).removeAllComponents();
        ArgumentCaptor<Label> labelCaptor = ArgumentCaptor.forClass(Label.class);
        verify(logListBoxPanel, times(1)).addComponent(labelCaptor.capture());
        assertEquals("[USER] Valid message", labelCaptor.getValue().getText());
    }

    // --- requestConfirmation tests ---
    @Test
    void requestConfirmation_whenYesClicked_completesFutureWithTrue() throws ExecutionException, InterruptedException {
        String title = "Confirm?";
        String text = "Are you sure?";
        try (MockedStatic<MessageDialog> mockedMessageDialog = mockStatic(MessageDialog.class)) {
            mockedMessageDialog.when(() -> MessageDialog.showMessageDialog(any(WindowBasedTextGUI.class), eq(title), eq(text), eq(MessageDialogButton.Yes), eq(MessageDialogButton.No)))
                .thenReturn(MessageDialogButton.Yes);
            CompletableFuture<Boolean> future = tui.requestConfirmation(title, text);
            assertTrue(future.isDone() && future.get());
        }
    }

    @Test
    void requestConfirmation_whenNoClicked_completesFutureWithFalse() throws ExecutionException, InterruptedException {
        String title = "Confirm?";
        String text = "Are you sure?";
        try (MockedStatic<MessageDialog> mockedMessageDialog = mockStatic(MessageDialog.class)) {
            mockedMessageDialog.when(() -> MessageDialog.showMessageDialog(any(WindowBasedTextGUI.class), eq(title), eq(text), eq(MessageDialogButton.Yes), eq(MessageDialogButton.No)))
                .thenReturn(MessageDialogButton.No);
            CompletableFuture<Boolean> future = tui.requestConfirmation(title, text);
            assertTrue(future.isDone() && !future.get());
        }
    }

    // --- requestDiffInteraction tests ---
    @Test
    void requestDiffInteraction_addsWindowAndAcceptReturnsCorrectly() throws ExecutionException, InterruptedException {
        String diff = "--- a/file.txt\n+++ b/file.txt\n@@ -1 +1 @@\n-old\n+new";
        List<Runnable> capturedActions = new ArrayList<>();
        try (MockedConstruction<Button> mockedButton = mockConstruction(Button.class, (mock, context) -> {
            if (context.arguments().size() > 1 && context.arguments().get(1) instanceof Runnable) {
                capturedActions.add((Runnable) context.arguments().get(1));
            }
        })) {
            CompletableFuture<DiffInteractionResult> future = tui.requestDiffInteraction(diff);
            verify(gui).addWindow(windowCaptor.capture());
            assertNotNull(windowCaptor.getValue());
            assertEquals("Apply Diff?", windowCaptor.getValue().getTitle());
            assertTrue(capturedActions.size() >= 1);
            capturedActions.get(0).run(); // Accept action
            assertTrue(future.isDone());
            DiffInteractionResult result = future.get();
            assertTrue(result.accepted());
            assertFalse(result.edited());
            assertEquals(diff, result.newDiff());
        }
    }

    @Test
    void requestDiffInteraction_rejectReturnsCorrectly() throws ExecutionException, InterruptedException {
        String diff = "diff content";
        List<Runnable> capturedActions = new ArrayList<>();
        try (MockedConstruction<Button> mockedButton = mockConstruction(Button.class, (mock, context) -> {
            if (context.arguments().size() > 1 && context.arguments().get(1) instanceof Runnable) {
                capturedActions.add((Runnable) context.arguments().get(1));
            }
        })) {
            CompletableFuture<DiffInteractionResult> future = tui.requestDiffInteraction(diff);
            assertTrue(capturedActions.size() >= 2);
            capturedActions.get(1).run(); // Reject action
            assertTrue(future.isDone());
            DiffInteractionResult result = future.get();
            assertFalse(result.accepted());
            assertFalse(result.edited());
            assertNull(result.newDiff());
        }
    }

    @Test
    void requestDiffInteraction_editCallsRequestConfigEditAndCompletesFuture() throws ExecutionException, InterruptedException {
        String originalDiff = "original diff";
        String editedDiff = "edited diff";
        CompletableFuture<String> editFuture = CompletableFuture.completedFuture(editedDiff);
        doReturn(editFuture).when(tui).requestConfigEdit(originalDiff);
        List<Runnable> capturedActions = new ArrayList<>();
        try (MockedConstruction<Button> mockedButton = mockConstruction(Button.class, (mock, context) -> {
            if (context.arguments().size() > 1 && context.arguments().get(1) instanceof Runnable) {
                capturedActions.add((Runnable) context.arguments().get(1));
            }
        })) {
            CompletableFuture<DiffInteractionResult> interactionFuture = tui.requestDiffInteraction(originalDiff);
            assertTrue(capturedActions.size() >= 3);
            capturedActions.get(2).run(); // Edit action
            assertTrue(interactionFuture.isDone());
            DiffInteractionResult result = interactionFuture.get();
            assertTrue(result.accepted());
            assertTrue(result.edited());
            assertEquals(editedDiff, result.newDiff());
            verify(tui).requestConfigEdit(originalDiff);
        }
    }

     @Test
    void requestDiffInteraction_editCallsRequestConfigEditAndHandlesNullReturn() throws ExecutionException, InterruptedException {
        String originalDiff = "original diff";
        CompletableFuture<String> editFuture = CompletableFuture.completedFuture(null);
        doReturn(editFuture).when(tui).requestConfigEdit(originalDiff);
        List<Runnable> capturedActions = new ArrayList<>();
        try (MockedConstruction<Button> mockedButton = mockConstruction(Button.class, (mock, context) -> {
            if (context.arguments().size() > 1 && context.arguments().get(1) instanceof Runnable) {
                capturedActions.add((Runnable) context.arguments().get(1));
            }
        })) {
            CompletableFuture<DiffInteractionResult> interactionFuture = tui.requestDiffInteraction(originalDiff);
            assertTrue(capturedActions.size() >= 3);
            capturedActions.get(2).run(); // Edit action
            assertTrue(interactionFuture.isDone());
            DiffInteractionResult result = interactionFuture.get();
            assertFalse(result.accepted());
            assertFalse(result.edited());
            assertNull(result.newDiff());
            verify(tui).requestConfigEdit(originalDiff);
        }
    }

    // --- requestConfigEdit tests ---
    @Test
    void requestConfigEdit_addsWindowAndTextBoxShowsCurrentConfig() {
        String currentConfig = "{\"key\": \"value\"}";
        List<TextBox> capturedTextBoxes = new ArrayList<>();

        try (MockedConstruction<TextBox> mockedTextBox = mockConstruction(TextBox.class, (mock, context) -> {
            // Capture TextBox instance
            capturedTextBoxes.add(mock);
            // Verify initial text if possible, or do it after future call
            assertEquals(currentConfig, context.arguments().get(0));
            assertEquals(TextBox.Style.MULTI_LINE, context.arguments().get(1));
        })) {
            tui.requestConfigEdit(currentConfig);
            verify(gui).addWindow(windowCaptor.capture());
            BasicWindow window = (BasicWindow) windowCaptor.getValue();
            assertNotNull(window);
            assertEquals("Editor", window.getTitle());
            assertFalse(capturedTextBoxes.isEmpty());
        }
    }

    @Test
    void requestConfigEdit_saveButtonCompletesFutureWithTextBoxContent() throws ExecutionException, InterruptedException {
        String currentConfig = "initial config";
        String newConfig = "new edited config";
        List<Runnable> capturedActions = new ArrayList<>();
        List<TextBox> capturedTextBoxes = new ArrayList<>();

        try (MockedConstruction<Button> mockedButton = mockConstruction(Button.class, (mock, context) -> {
            if (context.arguments().size() > 1 && context.arguments().get(1) instanceof Runnable) {
                capturedActions.add((Runnable) context.arguments().get(1));
            }
        });
        MockedConstruction<TextBox> mockedTextBox = mockConstruction(TextBox.class, (mock, context) -> {
            capturedTextBoxes.add(mock);
            when(mock.getText()).thenReturn(newConfig); // Stub getText() on the mocked TextBox
        })) {
            CompletableFuture<String> future = tui.requestConfigEdit(currentConfig);

            assertTrue(capturedActions.size() >= 1, "Save button action should have been captured.");
            // Assuming "Save" is the first button
            capturedActions.get(0).run();

            assertTrue(future.isDone());
            assertEquals(newConfig, future.get());
            assertFalse(capturedTextBoxes.isEmpty());
            verify(capturedTextBoxes.get(0)).getText(); // Verify getText was called by the Save button's lambda
        }
    }

    @Test
    void requestConfigEdit_cancelButtonCompletesFutureWithNull() throws ExecutionException, InterruptedException {
        String currentConfig = "initial config";
        List<Runnable> capturedActions = new ArrayList<>();

        try (MockedConstruction<Button> mockedButton = mockConstruction(Button.class, (mock, context) -> {
             if (context.arguments().size() > 1 && context.arguments().get(1) instanceof Runnable) {
                capturedActions.add((Runnable) context.arguments().get(1));
            }
        })) {
            CompletableFuture<String> future = tui.requestConfigEdit(currentConfig);

            assertTrue(capturedActions.size() >= 2, "Cancel button action should have been captured.");
            // Assuming "Cancel" is the second button
            capturedActions.get(1).run();

            assertTrue(future.isDone());
            assertNull(future.get());
        }
    }
}
