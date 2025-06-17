package dumb.jaider.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dumb.jaider.app.App;
import dumb.jaider.model.JaiderModel;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class TUI implements UI {
    /*private*/ MultiWindowTextGUI gui; // Changed to package-private for testing
    /*private*/ ActionListBox contextListBox; // Changed to package-private for testing
    /*private*/ Panel logListBox; // Changed to package-private for testing
    /*private*/ Label statusBar; // Changed to package-private for testing
    /*private*/ Label suggestionsLabel; // Added for displaying suggestions
    /*private*/ TextBox inputBox; // Added inputBox field

    @Override
    public void init(App app) throws IOException {
        var terminal = new DefaultTerminalFactory().createTerminal();
        var screen = new TerminalScreen(terminal);
        screen.startScreen();
        var window = new BasicWindow("Jaider - The AI Programming Assistant");
        window.setHints(Arrays.asList(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));

        var mainPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        contextListBox = new ActionListBox(new TerminalSize(35, 0));
        mainPanel.addComponent(contextListBox.withBorder(Borders.singleLine("Context")));
        logListBox = Panels.vertical();
        mainPanel.addComponent(logListBox.withBorder(Borders.singleLine("Log")));

        this.inputBox = new TextBox(new TerminalSize(100, 1)); // Assign to field
        this.inputBox.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Enter) {
                app.handleUserInput(this.inputBox.getText()); // Use field
                this.inputBox.setText(""); // Use field
            }
            return true;
        });
        statusBar = new Label("");
        suggestionsLabel = new Label(""); // Initialize suggestionsLabel
        suggestionsLabel.setForegroundColor(TextColor.ANSI.YELLOW); // Make suggestions stand out a bit

        // Panel for input and status/suggestions
        var bottomStrip = new Panel(new LinearLayout(Direction.VERTICAL));
        bottomStrip.addComponent(suggestionsLabel);
        // Panel for input box and status bar side-by-side
        var inputStatusPanel = new Panel(new LinearLayout(Direction.HORIZONTAL))
                .addComponent(inputBox)
                .addComponent(statusBar);
        bottomStrip.addComponent(inputStatusPanel);


        var contentPane = new Panel(new BorderLayout())
                .addComponent(mainPanel, BorderLayout.Location.CENTER)
                .addComponent(bottomStrip, BorderLayout.Location.BOTTOM); // Add bottomStrip here
        window.setComponent(contentPane);
        window.setFocusedInteractable(inputBox);

        gui = new MultiWindowTextGUI(new SeparateTextGUIThread.Factory(), screen);
        gui.addWindowAndWait(window);
    }

    @Override
    public void redraw(JaiderModel model) {
        if (gui == null) return;
        gui.getGUIThread().invokeLater(() -> {
            contextListBox.clearItems();
            model.files.forEach(p -> contextListBox.addItem(model.dir.relativize(p).toString(), null));
            logListBox.removeAllComponents();
            for (var msg : model.log) {
                var text = dumb.jaider.utils.Util.chatMessageToText(msg);
                if (text == null || text.isBlank()) continue;

                var l = new Label(String.format("[%s] %s", msg.type().name(), text));
                if (msg instanceof UserMessage)
                    l.setForegroundColor(TextColor.ANSI.CYAN);
                else if (msg instanceof AiMessage aim) {
                    if (aim.hasToolExecutionRequests()) {
                        l.setText(String.format("[Agent] Wants to use tool: %s", aim.toolExecutionRequests().getFirst().name()));
                        l.setForegroundColor(TextColor.ANSI.YELLOW);
                    } else
                        l.setForegroundColor(TextColor.ANSI.GREEN);
                }
                logListBox.addComponent(l);
            }
            statusBar.setText(String.format(" | Mode: %s | %s | Tokens: %d", model.mode, model.statusBarText, model.currentTokenCount));

            // Display suggestions
            if (model.getActiveSuggestions() != null && !model.getActiveSuggestions().isEmpty()) {
                var suggestionTexts = model.getActiveSuggestions().stream()
                        .map(s -> String.format("[%d] %s?", s.displayNumber(), s.originalSuggestion().suggestedToolName())) // Use ActiveSuggestion
                        .collect(java.util.stream.Collectors.joining("  ")); // Use more space for readability
                suggestionsLabel.setText("Suggestions: " + suggestionTexts + " (use /accept <num>)");
            } else {
                suggestionsLabel.setText(""); // Clear suggestions if none
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> confirm(String title, String text) {
        var future = new CompletableFuture<Boolean>();
        gui.getGUIThread().invokeLater(() -> future.complete(
                MessageDialog.showMessageDialog(gui, title, text, MessageDialogButton.Yes, MessageDialogButton.No) == MessageDialogButton.Yes));
        return future;
    }

    @Override
    public CompletableFuture<DiffInteractionResult> diffInteraction(String diff) {
        var future = new CompletableFuture<DiffInteractionResult>();
        gui.getGUIThread().invokeLater(() -> {
            var dialog = new BasicWindow("Apply Diff?");
            var content = new Panel(new LinearLayout(Direction.VERTICAL));
            content.addComponent(new Label("Agent wants to apply these changes:"));

            var diffPanel = new Panel();
            diff.lines().forEach(line -> {
                var lineLabel = new Label(line);
                if (line.startsWith("+")) lineLabel.setForegroundColor(TextColor.ANSI.GREEN);
                else if (line.startsWith("-")) lineLabel.setForegroundColor(TextColor.ANSI.RED);
                else if (line.startsWith("@")) lineLabel.setForegroundColor(TextColor.ANSI.CYAN);
                diffPanel.addComponent(lineLabel);
            });
            content.addComponent(diffPanel.withBorder(Borders.singleLine()));

            var buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
            buttonPanel.addComponent(new Button("Accept", () -> {
                future.complete(new DiffInteractionResult(true, false, diff));
                dialog.close();
            }));
            buttonPanel.addComponent(new Button("Reject", () -> {
                future.complete(new DiffInteractionResult(false, false, null));
                dialog.close();
            }));
            buttonPanel.addComponent(new Button("Edit", () -> {
                dialog.close();
                configEdit(diff).thenAccept(editedDiff -> {
                    if (editedDiff != null) future.complete(new DiffInteractionResult(true, true, editedDiff));
                    else future.complete(new DiffInteractionResult(false, false, null));
                });
            }));
            content.addComponent(buttonPanel);
            dialog.setComponent(content);
            gui.addWindow(dialog);
        });
        return future;
    }

    @Override
    public CompletableFuture<String> configEdit(String currentConfig) {
        var future = new CompletableFuture<String>();
        gui.getGUIThread().invokeLater(() -> {
            var editorDialog = new BasicWindow("Editor");
            var editorPanel = new Panel(new BorderLayout());
            var configTextBox = new TextBox(currentConfig, TextBox.Style.MULTI_LINE);
            configTextBox.setPreferredSize(new TerminalSize(80, 25));
            editorPanel.addComponent(configTextBox, BorderLayout.Location.CENTER);

            var buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
            buttonPanel.addComponent(new Button("Save", () -> {
                future.complete(configTextBox.getText());
                editorDialog.close();
            }));
            buttonPanel.addComponent(new Button("Cancel", () -> {
                future.complete(null);
                editorDialog.close();
            }));
            editorPanel.addComponent(buttonPanel, BorderLayout.Location.BOTTOM);
            editorDialog.setComponent(editorPanel);
            gui.addWindow(editorDialog);
        });
        return future;
    }

    @Override
    public void close() throws IOException {
        if (gui != null) gui.getScreen().stopScreen();
    }

    @Override
    public CompletableFuture<Boolean> confirmPlan(String title, String planText, AiMessage agentMessage) {
        var future = new CompletableFuture<Boolean>();
        gui.getGUIThread().invokeLater(() -> {
            var dialog = new BasicWindow(title);
            dialog.setHints(Arrays.asList(Window.Hint.CENTERED)); // Center the dialog

            var contentPanel = new Panel(new LinearLayout(Direction.VERTICAL));
            contentPanel.addComponent(new Label("Agent's Proposed Plan:")); // General instruction or use title if more specific

            // Use a TextBox for the plan text to make it scrollable
            var planTextBox = new TextBox(planText, TextBox.Style.MULTI_LINE);
            planTextBox.setReadOnly(true);
            // Make the TextBox scrollable and reasonably sized.
            // Adjust preferred size as needed, e.g., based on typical plan length or screen size.
            planTextBox.setPreferredSize(new TerminalSize(80, 15));
            contentPanel.addComponent(planTextBox);

            var buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
            buttonPanel.addComponent(new Button("Approve", () -> {
                future.complete(true);
                dialog.close();
            }));
            buttonPanel.addComponent(new Button("Reject", () -> {
                future.complete(false);
                dialog.close();
            }));
            contentPanel.addComponent(buttonPanel);

            dialog.setComponent(contentPanel);
            gui.addWindow(dialog);
        });
        return future;
    }

    @Override
    public void setInputText(String text) {
        if (this.inputBox != null && gui != null) {
            gui.getGUIThread().invokeLater(() -> {
                this.inputBox.setText(text);
                this.inputBox.setCaretPosition(text.length());
                // Ensure the input box gets focus if it doesn't have it,
                // though it should generally be focused.
                // This might require getting the window and setting focused interactable if not.
                // For now, assume it's focused or will be by user action.
            });
        }
    }
}
