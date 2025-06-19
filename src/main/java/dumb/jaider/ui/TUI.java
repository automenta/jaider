package dumb.jaider.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
import com.googlecode.lanterna.TerminalPosition; // Required for WindowListener
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dumb.jaider.app.App;
import dumb.jaider.model.JaiderModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class TUI implements UI {
    MultiWindowTextGUI gui;
    ActionListBox contextListBox;
    Panel logListBoxPanel; // Panel to hold log messages
    TextBox inputBox;
    Label statusBar;
    Label suggestionsLabel;

    private final List<String> commandHistory = new ArrayList<>();
    private int commandHistoryIndex = -1;

    @Override
    public void init(App app) throws IOException {
        var terminal = new DefaultTerminalFactory().createTerminal();
        var screen = new TerminalScreen(terminal);
        screen.startScreen();
        var window = new BasicWindow("Jaider - The AI Programming Assistant");
        window.setHints(Arrays.asList(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));

        Panel rootPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        // Per problem: rootPanel.setLayoutData(LinearLayout.createHorizontallyFilled()); // May not be needed or available - Omitted

        contextListBox = new ActionListBox(new TerminalSize(35, 0));
        Panel leftPanel = new Panel();
        leftPanel.addComponent(contextListBox.withBorder(Borders.singleLine("Context")));
        leftPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning));
        rootPanel.addComponent(leftPanel);

        Panel rightPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        rightPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));

        logListBoxPanel = Panels.vertical(); // This will hold the labels for logs
        Panel mainContentViewPanel = new Panel(new BorderLayout()); // Use BorderLayout to make logListBoxPanel fill the center
        mainContentViewPanel.addComponent(logListBoxPanel.withBorder(Borders.singleLine("Log / Output")), BorderLayout.Location.CENTER);
        // Let mainContentViewPanel fill the available space in rightPanel
        mainContentViewPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        rightPanel.addComponent(mainContentViewPanel);

        inputBox = new TextBox(new TerminalSize(100,1));
        inputBox.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Enter) {
                String text = inputBox.getText();
                if (!text.isBlank()) {
                    app.handleUserInput(text);
                    if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(text)) {
                        commandHistory.add(text);
                    }
                    commandHistoryIndex = commandHistory.size();
                }
                inputBox.setText("");
                return false;
            } else if (keyStroke.getKeyType() == KeyType.ArrowUp) {
                if (!commandHistory.isEmpty()) {
                    commandHistoryIndex = Math.max(0, commandHistoryIndex - 1);
                    inputBox.setText(commandHistory.get(commandHistoryIndex));
                    inputBox.setCaretPosition(inputBox.getText().length());
                }
                return false;
            } else if (keyStroke.getKeyType() == KeyType.ArrowDown) {
                if (commandHistoryIndex < commandHistory.size() - 1) {
                    commandHistoryIndex++;
                    inputBox.setText(commandHistory.get(commandHistoryIndex));
                    inputBox.setCaretPosition(inputBox.getText().length());
                } else if (commandHistoryIndex == commandHistory.size() - 1) {
                    commandHistoryIndex++;
                    inputBox.setText("");
                }
                return false;
            }
            return true;
        });
        // Panel for the input text box, to allow it to have its own border and be managed by rightPanel's LinearLayout
        Panel inputAreaPanel = new Panel(new LinearLayout(Direction.HORIZONTAL)); // Or just Panel() and let TextBox define size
        inputAreaPanel.addComponent(inputBox.withBorder(Borders.singleLine("Input")).setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill)));
        // inputAreaPanel itself will take the minimum space it needs vertically due to rightPanel's LinearLayout.
        rightPanel.addComponent(inputAreaPanel);
        rootPanel.addComponent(rightPanel);

        Panel statusBarPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        suggestionsLabel = new Label("");
        suggestionsLabel.setForegroundColor(TextColor.ANSI.YELLOW);
        statusBar = new Label("");

        statusBarPanel.addComponent(suggestionsLabel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning)));
        // Adding an expanding empty space to push status bar text to the right
        statusBarPanel.addComponent(new EmptySpace(TerminalSize.ZERO).setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill)));
        statusBarPanel.addComponent(statusBar.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.End)));

        Panel contentPane = new Panel(new BorderLayout());
        contentPane.addComponent(rootPanel, BorderLayout.Location.CENTER);
        contentPane.addComponent(statusBarPanel, BorderLayout.Location.BOTTOM);
        window.setComponent(contentPane);
        window.setFocusedInteractable(inputBox);

        /*
        // Commenting out problematic WindowListener due to onFocusChanged @Override issues
        window.addWindowListener(new WindowListener() {
            @Override
            public void onResized(Window w, TerminalSize oldSize, TerminalSize newSize) {}

            @Override
            public void onMoved(Window w, TerminalPosition oldPosition, TerminalPosition newPosition) {}

            @Override
            public void onInput(Window w, KeyStroke keyStroke, AtomicBoolean hasBeenHandled) {
                if (keyStroke.isCtrlDown() && keyStroke.getKeyType() == KeyType.Character) {
                    Interactable toFocus = null;
                    switch (keyStroke.getCharacter()) {
                        case '1': toFocus = contextListBox; break;
                        case '2': toFocus = inputBox; break;
                        case '3': toFocus = inputBox; break;
                    }
                    if (toFocus != null) {
                        final Interactable finalToFocus = toFocus;
                        gui.getGUIThread().invokeLater(() -> {
                            if (finalToFocus.isVisible()) {
                               w.setFocusedInteractable(finalToFocus);
                            }
                        });
                        hasBeenHandled.set(true);
                    }
                }
            }

            @Override
            public void onUnhandledInput(Window w, KeyStroke keyStroke, AtomicBoolean hasBeenHandled) {}

            @Override
            public void onFocusChanged(Window w, Interactable oldFocus, Interactable newFocus) {}
        });
        */

        gui = new MultiWindowTextGUI(new SeparateTextGUIThread.Factory(), screen);
        gui.addWindowAndWait(window);
    }

    @Override
    public void redraw(JaiderModel model) {
        if (gui == null) return;
        gui.getGUIThread().invokeLater(() -> {
            contextListBox.clearItems();
            if (model.files != null) {
                model.files.forEach(p -> contextListBox.addItem(model.dir.relativize(p).toString(), null));
            }

            logListBoxPanel.removeAllComponents();
            if (model.log != null) {
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
                    logListBoxPanel.addComponent(l); // Add labels directly to logListBoxPanel
                }
            }

            statusBar.setText(String.format("| Mode: %s | %s | Tokens: %d", model.mode, model.statusBarText, model.currentTokenCount));

            if (model.getActiveSuggestions() != null && !model.getActiveSuggestions().isEmpty()) {
                var suggestionTexts = model.getActiveSuggestions().stream()
                        .map(s -> String.format("[%d] %s?", s.displayNumber(), s.originalSuggestion().suggestedToolName()))
                        .collect(Collectors.joining("  "));
                suggestionsLabel.setText("Suggestions: " + suggestionTexts + " (use /accept <num>)");
            } else {
                suggestionsLabel.setText("");
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> confirm(String title, String text) {
        var future = new CompletableFuture<Boolean>();
        gui.getGUIThread().invokeLater(() -> {
            MessageDialog dialog = new MessageDialogBuilder()
                .setTitle(title)
                .setText(text)
                .addButton(MessageDialogButton.Yes)
                .addButton(MessageDialogButton.No)
                .build();
            dialog.setCloseWindowWithEscape(true);
            future.complete(dialog.showDialog(gui) == MessageDialogButton.Yes);
        });
        return future;
    }

    @Override
    public CompletableFuture<DiffInteractionResult> diffInteraction(String diff) {
        var future = new CompletableFuture<DiffInteractionResult>();
        gui.getGUIThread().invokeLater(() -> {
            var dialog = new BasicWindow("Apply Diff?");
            dialog.setHints(List.of(Window.Hint.CENTERED));
            dialog.setCloseWindowWithEscape(true);
            var content = new Panel(new LinearLayout(Direction.VERTICAL));
            content.addComponent(new Label("Agent wants to apply these changes:"));
            var diffTextBox = new TextBox(diff, TextBox.Style.MULTI_LINE);
            diffTextBox.setReadOnly(true);
            diffTextBox.setPreferredSize(new TerminalSize(80, 15));
            content.addComponent(diffTextBox.withBorder(Borders.singleLine("Diff")));
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
            editorDialog.setHints(List.of(Window.Hint.CENTERED));
            editorDialog.setCloseWindowWithEscape(true);
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
        if (gui != null && gui.getScreen() != null) {
            gui.getScreen().stopScreen();
        }
    }

    @Override
    public CompletableFuture<Boolean> confirmPlan(String title, String planText, AiMessage agentMessage) {
        var future = new CompletableFuture<Boolean>();
        gui.getGUIThread().invokeLater(() -> {
            var dialog = new BasicWindow(title);
            dialog.setHints(List.of(Window.Hint.CENTERED));
            dialog.setCloseWindowWithEscape(true);
            var contentPanel = new Panel(new LinearLayout(Direction.VERTICAL));
            contentPanel.addComponent(new Label("Agent's Proposed Plan:"));
            var planTextBox = new TextBox(planText, TextBox.Style.MULTI_LINE);
            planTextBox.setReadOnly(true);
            planTextBox.setPreferredSize(new TerminalSize(80, 15));
            contentPanel.addComponent(planTextBox.withBorder(Borders.singleLine("Plan")));
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
            });
        }
    }

    @Override
    public CompletableFuture<String> switchProjectDirectory(String currentDirectory) {
        var future = new CompletableFuture<String>();
        gui.getGUIThread().invokeLater(() -> {
            var dialog = new BasicWindow("Switch Project Directory");
            dialog.setHints(List.of(Window.Hint.CENTERED));
            dialog.setCloseWindowWithEscape(true);
            var contentPanel = new Panel(new LinearLayout(Direction.VERTICAL));
            contentPanel.addComponent(new Label("Enter new project directory:"));
            var directoryInput = new TextBox(currentDirectory);
            directoryInput.setPreferredSize(new TerminalSize(80, 1));
            contentPanel.addComponent(directoryInput);
            var buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
            buttonPanel.addComponent(new Button("Switch", () -> {
                future.complete(directoryInput.getText());
                dialog.close();
            }));
            buttonPanel.addComponent(new Button("Cancel", () -> {
                future.complete(null);
                dialog.close();
            }));
            contentPanel.addComponent(buttonPanel);
            dialog.setComponent(contentPanel);
            gui.addWindow(dialog);
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> showGlobalConfiguration() {
        var future = new CompletableFuture<Void>();
        gui.getGUIThread().invokeLater(() -> {
            var dialog = new BasicWindow("Global Configuration");
            dialog.setHints(List.of(Window.Hint.CENTERED));
            dialog.setCloseWindowWithEscape(true);
            var contentPanel = new Panel(new LinearLayout(Direction.VERTICAL));
            contentPanel.addComponent(new Label("Global configuration settings will be displayed here."));
            var buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
            buttonPanel.addComponent(new Button("OK", () -> {
                future.complete(null);
                dialog.close();
            }));
            contentPanel.addComponent(buttonPanel);
            dialog.setComponent(contentPanel);
            gui.addWindow(dialog);
        });
        return future;
    }
}
