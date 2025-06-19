package dumb.jaider.ui;

import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dumb.jaider.model.JaiderModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    @Mock
    Label suggestionsLabel; // Added mock for suggestionsLabel

    @Spy
    final
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
        tui.logListBoxPanel = logListBoxPanel;
        tui.statusBar = statusBar;
        tui.suggestionsLabel = suggestionsLabel; // Assign mock to the TUI instance

        lenient().when(gui.getGUIThread()).thenReturn(textGUIThread);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(textGUIThread).invokeLater(any(Runnable.class));
    }

    // --- redraw tests ---
    @Test
    void redraw_shouldClearAndRepopulateListsAndSetStatusBar() {
        model.files.clear();
        model.files.addAll(new HashSet<>(Arrays.asList(
                Paths.get("/test/project/file1.txt"),
                Paths.get("/test/project/subdir/file2.java")
        )));

        model.log.clear();
        var userMsg = UserMessage.from("User query");
        var aiMsgPlain = AiMessage.from("AI response");
        var aiMsgWithTool = AiMessage.from(ToolExecutionRequest.builder().name("testTool").id("123").arguments("{}").build());
        model.log.addAll(Arrays.asList(userMsg, aiMsgPlain, aiMsgWithTool));

        model.mode = "Coder";
        model.statusBarText = "Ready";
        model.currentTokenCount = 123;

        tui.redraw(model);

        verify(contextListBox).clearItems();
        var contextItemCaptor = ArgumentCaptor.forClass(String.class);
        verify(contextListBox, times(2)).addItem(contextItemCaptor.capture(), isNull());
        var capturedContextItems = contextItemCaptor.getAllValues();
        assertTrue(capturedContextItems.contains("file1.txt"));
        assertTrue(capturedContextItems.contains("subdir/file2.java"));

        verify(logListBoxPanel).removeAllComponents();
        var labelCaptor = ArgumentCaptor.forClass(Label.class);
        verify(logListBoxPanel, times(2)).addComponent(labelCaptor.capture());

        var capturedLabels = labelCaptor.getAllValues();
        assertEquals("[USER] User query", capturedLabels.get(0).getText());
        assertEquals("[AI] AI response", capturedLabels.get(1).getText());

        verify(statusBar).setText(" | Mode: Coder | Ready | Tokens: 123");
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void redraw_whenGuiIsNull_shouldDoNothing() {
        tui.gui = null;
        tui.redraw(model);
        // If gui is null, textGUIThread (obtained from the original gui mock)
        // will not be involved via tui.gui.getGUIThread().
        // So, its non-interaction is implied by gui being null.
        verifyNoInteractions(contextListBox, logListBoxPanel, statusBar);
    }

    @Test
    void redraw_withEmptyFilesInContext_shouldAddNoItemsToContextList() {
        model.files.clear();
        model.log.clear();
        tui.redraw(model);
        verify(contextListBox).clearItems();
        verify(contextListBox, never()).addItem(anyString(), any(Runnable.class));
    }

    @Test
    void redraw_withEmptyLogMessages_shouldAddNoComponentsToLogList() {
        model.files.clear();
        model.log.clear();
        tui.redraw(model);
        verify(logListBoxPanel).removeAllComponents();
        verify(logListBoxPanel, never()).addComponent(any(Component.class));
    }

    @Test
    void redraw_withNullOrBlankLogMessageText_shouldSkipMessage() {
        model.files.clear();
        model.log.clear();
        model.log.add(UserMessage.from("Valid message"));
        model.log.add(AiMessage.from(ToolExecutionRequest.builder().name("skippedTool").id("456").arguments("{}").build()));
        tui.redraw(model);
        verify(logListBoxPanel).removeAllComponents();
        var labelCaptor = ArgumentCaptor.forClass(Label.class);
        verify(logListBoxPanel, times(1)).addComponent(labelCaptor.capture());
        assertEquals("[USER] Valid message", labelCaptor.getValue().getText());
    }

    // --- requestConfirmation tests ---
    @Test
    void confirm_whenYesClicked_completesFutureWithTrue() throws ExecutionException, InterruptedException {
        var title = "Confirm?";
        var text = "Are you sure?";
        try (var mockedMessageDialog = mockStatic(MessageDialog.class)) {
            mockedMessageDialog.when(() -> MessageDialog.showMessageDialog(any(WindowBasedTextGUI.class), eq(title), eq(text), eq(MessageDialogButton.Yes), eq(MessageDialogButton.No)))
                .thenReturn(MessageDialogButton.Yes);
            var future = tui.confirm(title, text);
            assertTrue(future.isDone() && future.get());
        }
    }

    @Test
    void confirm_whenNoClicked_completesFutureWithFalse() throws ExecutionException, InterruptedException {
        var title = "Confirm?";
        var text = "Are you sure?";
        try (var mockedMessageDialog = mockStatic(MessageDialog.class)) {
            mockedMessageDialog.when(() -> MessageDialog.showMessageDialog(any(WindowBasedTextGUI.class), eq(title), eq(text), eq(MessageDialogButton.Yes), eq(MessageDialogButton.No)))
                .thenReturn(MessageDialogButton.No);
            var future = tui.confirm(title, text);
            assertTrue(future.isDone() && !future.get());
        }
    }

    // --- requestDiffInteraction tests ---
    @Test
    void diffInteraction_addsWindowAndAcceptReturnsCorrectly() throws ExecutionException, InterruptedException {
        var diff = "--- a/file.txt\n+++ b/file.txt\n@@ -1 +1 @@\n-old\n+new";
        List<Runnable> capturedActions = new ArrayList<>();
        try (var mockedButton = mockConstruction(Button.class, (mock, context) -> {
            if (context.arguments().size() > 1 && context.arguments().get(1) instanceof Runnable) {
                capturedActions.add((Runnable) context.arguments().get(1));
            }
        })) {
            var future = tui.diffInteraction(diff);
            verify(gui).addWindow(windowCaptor.capture());
            assertNotNull(windowCaptor.getValue());
            assertEquals("Apply Diff?", windowCaptor.getValue().getTitle());
            assertFalse(capturedActions.isEmpty());
            capturedActions.getFirst().run(); // Accept action
            assertTrue(future.isDone());
            var result = future.get();
            assertTrue(result.accepted());
            assertFalse(result.edited());
            assertEquals(diff, result.newDiff());
        }
    }

    @Test
    void diffInteraction_rejectReturnsCorrectly() throws ExecutionException, InterruptedException {
        var diff = "diff content";
        List<Runnable> capturedActions = new ArrayList<>();
        try (var mockedButton = mockConstruction(Button.class, (mock, context) -> {
            if (context.arguments().size() > 1 && context.arguments().get(1) instanceof Runnable) {
                capturedActions.add((Runnable) context.arguments().get(1));
            }
        })) {
            var future = tui.diffInteraction(diff);
            assertTrue(capturedActions.size() >= 2);
            capturedActions.get(1).run(); // Reject action
            assertTrue(future.isDone());
            var result = future.get();
            assertFalse(result.accepted());
            assertFalse(result.edited());
            assertNull(result.newDiff());
        }
    }

    @Test
    void requestDiffInteraction_editCallsConfigEditAndCompletesFuture() throws ExecutionException, InterruptedException {
        var originalDiff = "original diff";
        var editedDiff = "edited diff";
        var editFuture = CompletableFuture.completedFuture(editedDiff);
        doReturn(editFuture).when(tui).configEdit(originalDiff);
        List<Runnable> capturedActions = new ArrayList<>();
        try (var mockedButton = mockConstruction(Button.class, (mock, context) -> {
            if (context.arguments().size() > 1 && context.arguments().get(1) instanceof Runnable) {
                capturedActions.add((Runnable) context.arguments().get(1));
            }
        })) {
            var interactionFuture = tui.diffInteraction(originalDiff);
            assertTrue(capturedActions.size() >= 3);
            capturedActions.get(2).run(); // Edit action
            assertTrue(interactionFuture.isDone());
            var result = interactionFuture.get();
            assertTrue(result.accepted());
            assertTrue(result.edited());
            assertEquals(editedDiff, result.newDiff());
            verify(tui).configEdit(originalDiff);
        }
    }

     @Test
     void requestDiffInteraction_editCallsConfigEditAndHandlesNullReturn() throws ExecutionException, InterruptedException {
         var originalDiff = "original diff";
        CompletableFuture<String> editFuture = CompletableFuture.completedFuture(null);
         doReturn(editFuture).when(tui).configEdit(originalDiff);
        List<Runnable> capturedActions = new ArrayList<>();
         try (var mockedButton = mockConstruction(Button.class, (mock, context) -> {
            if (context.arguments().size() > 1 && context.arguments().get(1) instanceof Runnable) {
                capturedActions.add((Runnable) context.arguments().get(1));
            }
        })) {
             var interactionFuture = tui.diffInteraction(originalDiff);
            assertTrue(capturedActions.size() >= 3);
            capturedActions.get(2).run(); // Edit action
            assertTrue(interactionFuture.isDone());
             var result = interactionFuture.get();
            assertFalse(result.accepted());
            assertFalse(result.edited());
            assertNull(result.newDiff());
            verify(tui).configEdit(originalDiff);
        }
    }

    // --- requestConfigEdit tests ---
    @Test
    void config() {
        var currentConfig = "{\"key\": \"value\"}";
        List<TextBox> capturedTextBoxes = new ArrayList<>();

        try (var mockedTextBox = mockConstruction(TextBox.class, (mock, context) -> {
            // Capture TextBox instance
            capturedTextBoxes.add(mock);
            // Verify initial text if possible, or do it after future call
            assertEquals(currentConfig, context.arguments().get(0));
            assertEquals(TextBox.Style.MULTI_LINE, context.arguments().get(1));
        })) {
            tui.configEdit(currentConfig);
            verify(gui).addWindow(windowCaptor.capture());
            var window = (BasicWindow) windowCaptor.getValue();
            assertNotNull(window);
            assertEquals("Editor", window.getTitle());
            assertFalse(capturedTextBoxes.isEmpty());
        }
    }

    @Test
    void configEdit_saveButtonCompletesFutureWithTextBoxContent() throws ExecutionException, InterruptedException {
        var currentConfig = "initial config";
        var newConfig = "new edited config";
        List<Runnable> capturedActions = new ArrayList<>();
        List<TextBox> capturedTextBoxes = new ArrayList<>();

        try (var mockedButton = mockConstruction(Button.class, (mock, context) -> {
            if (context.arguments().size() > 1 && context.arguments().get(1) instanceof Runnable) {
                capturedActions.add((Runnable) context.arguments().get(1));
            }
        });
             var mockedTextBox = mockConstruction(TextBox.class, (mock, context) -> {
            capturedTextBoxes.add(mock);
            when(mock.getText()).thenReturn(newConfig); // Stub getText() on the mocked TextBox
        })) {
            var future = tui.configEdit(currentConfig);

            assertFalse(capturedActions.isEmpty(), "Save button action should have been captured.");
            // Assuming "Save" is the first button
            capturedActions.getFirst().run();

            assertTrue(future.isDone());
            assertEquals(newConfig, future.get());
            assertFalse(capturedTextBoxes.isEmpty());
            verify(capturedTextBoxes.getFirst()).getText(); // Verify getText was called by the Save button's lambda
        }
    }

    @Test
    void configEdit_cancelButtonCompletesFutureWithNull() throws ExecutionException, InterruptedException {
        var currentConfig = "initial config";
        List<Runnable> capturedActions = new ArrayList<>();

        try (var mockedButton = mockConstruction(Button.class, (mock, context) -> {
             if (context.arguments().size() > 1 && context.arguments().get(1) instanceof Runnable) {
                capturedActions.add((Runnable) context.arguments().get(1));
            }
        })) {
            var future = tui.configEdit(currentConfig);

            assertTrue(capturedActions.size() >= 2, "Cancel button action should have been captured.");
            // Assuming "Cancel" is the second button
            capturedActions.get(1).run();

            assertTrue(future.isDone());
            assertNull(future.get());
        }
    }
}
