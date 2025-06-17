package dumb.jaider.mocks;

import dumb.jaider.model.JaiderModel;
import dumb.jaider.ui.TUI; // Ensure this is the correct UI interface
import dumb.jaider.ui.UIExecutionContext;


import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MockTUI implements TUI {
    private final Queue<String> userInputs = new LinkedList<>();
    private final Queue<CompletableFuture<Void>> messageFutures = new LinkedList<>();
    private final Queue<CompletableFuture<Boolean>> confirmFutures = new LinkedList<>();

    public Consumer<String> onShowModalMessageAccept; // Renamed for clarity
    public Consumer<String> onShowScrollableTextAccept; // Renamed for clarity
    public Consumer<JaiderModel> onRedrawAccept; // Renamed for clarity
    public Consumer<String> onShowInputDialogAccept; // For observing calls

    public void addUserInput(String input) {
        userInputs.add(input);
    }

    public void addShowModalMessageFuture(CompletableFuture<Void> future) {
        messageFutures.add(future);
    }

    public void addConfirmFuture(CompletableFuture<Boolean> future) {
        confirmFutures.add(future);
    }

    public void addShowScrollableTextFuture(CompletableFuture<Void> future) {
        // Can reuse messageFutures or have a dedicated one if behavior differs
        messageFutures.add(future);
    }


    @Override
    public CompletableFuture<Void> showModalMessage(String title, String message) {
        if (onShowModalMessageAccept != null) {
            onShowModalMessageAccept.accept(title + ": " + message);
        }
        if (!messageFutures.isEmpty()) {
            return messageFutures.poll();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<String> showInputDialog(String title, String prompt, String initialValue) {
        if (onShowInputDialogAccept != null) {
            onShowInputDialogAccept.accept(title + ": " + prompt);
        }
        if (userInputs.isEmpty()) {
            // Return a default or fail if no input is queued for an expected dialog
            return CompletableFuture.failedFuture(new IllegalStateException("MockTUI: No user input provided for dialog: " + title));
        }
        return CompletableFuture.completedFuture(userInputs.poll());
    }

    @Override
    public CompletableFuture<Boolean> confirm(String title, String message) {
        if (onShowModalMessageAccept != null) {
            onShowModalMessageAccept.accept("CONFIRM? " + title + ": " + message);
        }
        if (!confirmFutures.isEmpty()) {
            return confirmFutures.poll();
        }
        // Default to true for confirmations if not specified to allow tests to proceed
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Void> showScrollableText(String title, String text) {
        if (onShowScrollableTextAccept != null) {
            onShowScrollableTextAccept.accept(title + ": " + text);
        }
        if (!messageFutures.isEmpty()) {
            return messageFutures.poll();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void redraw(JaiderModel model) {
        if (onRedrawAccept != null) {
            onRedrawAccept.accept(model);
        }
    }

    @Override
    public void start(Consumer<UIExecutionContext> commandProcessor) {
        // Not implemented for this mock
    }

    @Override
    public void stop() {
        // Not implemented for this mock
    }
}
