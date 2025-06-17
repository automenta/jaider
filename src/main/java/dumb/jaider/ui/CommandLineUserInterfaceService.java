package dumb.jaider.ui;

import dumb.jaider.app.App; // Added import
import dumb.jaider.model.JaiderModel; // Added import
import dev.langchain4j.data.message.AiMessage; // Added import
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture; // Added import
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
// Removed Consumer import as askYesNoQuestion is being adapted
// import java.util.function.Consumer;

public class CommandLineUserInterfaceService implements UI { // Changed interface

    private static final Logger logger = LoggerFactory.getLogger(CommandLineUserInterfaceService.class);
    private final PrintStream out;
    private final BufferedReader inReader;
    private final ExecutorService executorService; // For handling asynchronous input for askYesNoQuestion

    public CommandLineUserInterfaceService() {
        this.out = System.out;
        // Using System.in directly with Scanner/BufferedReader can be problematic if System.in is closed elsewhere.
        // For a robust CLI, especially if other parts of the app might read from System.in,
        // a more sophisticated input handling mechanism might be needed.
        // For now, direct use is acceptable for this basic implementation.
        this.inReader = new BufferedReader(new InputStreamReader(System.in));
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "cli-input-thread");
            t.setDaemon(true); // So it doesn't prevent JVM shutdown
            return t;
        });
    }

    // Constructor for testing with specific streams
    public CommandLineUserInterfaceService(PrintStream out, InputStreamReader inReader) {
        this.out = out;
        this.inReader = new BufferedReader(inReader); // Wrap in BufferedReader
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "cli-input-thread-test");
            t.setDaemon(true);
            return t;
        });
    }

    // --- Methods from old UserInterfaceService (can be kept as helper or removed if not used elsewhere) ---
    // For now, keeping them and marking as potentially unused by UI interface
    // public void showMessage(String message) {
    //     logger.info("Showing message to user: {}", message);
    //     out.println("[INFO] " + message);
    // }

    // public void showError(String message) {
    //     logger.error("Showing error to user: {}", message);
    //     System.err.println("[ERROR] " + message);
    // }

    // --- Implementation of UI interface ---

    @Override
    public void init(App app) throws IOException {
        logger.info("CommandLineUserInterfaceService.init called (no-op for CLI).");
        // For a real CLI app, this might set up console, etc.
    }

    @Override
    public void redraw(JaiderModel model) {
        // CLI doesn't have a persistent "redraw" concept like a GUI.
        // Could print status or log.
        // logger.info("CommandLineUserInterfaceService.redraw called (no-op for CLI). Model: {}", model);
    }

    @Override
    public CompletableFuture<Boolean> confirm(String title, String text) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        out.println("\n[CONFIRMATION] " + title);
        out.println(text);
        out.print(" (yes/no): ");

        executorService.submit(() -> {
            try {
                String line = inReader.readLine();
                if (line != null) {
                    line = line.trim().toLowerCase();
                    boolean response = line.equals("yes") || line.equals("y");
                    future.complete(response);
                } else {
                    logger.warn("No input received for confirm (null line). Defaulting to 'false'.");
                    future.complete(false);
                }
            } catch (IOException e) {
                logger.error("IOException reading user input for confirm. Defaulting to 'false'.", e);
                future.complete(false);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<DiffInteractionResult> diffInteraction(String diff) {
        logger.info("CommandLineUserInterfaceService.diffInteraction called. Auto-accepting for now.");
        out.println("\n[DIFF INTERACTION]");
        out.println("Proposed diff:\n" + diff);
        // For CLI, this would need a more complex interaction (view, edit, accept, reject)
        // Or simply use confirm for accept/reject.
        // Returning auto-accept for now to fulfill the interface.
        return confirm("Apply this diff?", diff)
                .thenApply(approved -> new DiffInteractionResult(approved, false, approved ? diff : null));
    }

    @Override
    public CompletableFuture<String> configEdit(String currentConfig) {
        logger.info("CommandLineUserInterfaceService.configEdit called.");
        out.println("\n[CONFIG EDIT]");
        out.println("Current config:\n" + currentConfig);
        out.println("Enter new config (or press Enter to keep current, type 'cancel' to abort):");
        CompletableFuture<String> future = new CompletableFuture<>();
        executorService.submit(() -> {
            try {
                // This simple implementation reads a single line.
                // A real editor would be multi-line.
                String line = inReader.readLine();
                if (line == null || line.equalsIgnoreCase("cancel")) {
                    future.complete(null); // Abort
                } else if (line.isEmpty()) {
                    future.complete(currentConfig); // Keep current
                } else {
                    future.complete(line); // Use new single line config
                }
            } catch (IOException e) {
                logger.error("IOException during config edit.", e);
                future.complete(null); // Abort on error
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Boolean> confirmPlan(String title, String planText, AiMessage agentMessage) {
        logger.info("CommandLineUserInterfaceService.confirmPlan called.");
        return confirm(title, planText); // Re-use the confirm logic
    }

    @Override
    public void setInputText(String text) {
        logger.info("CommandLineUserInterfaceService.setInputText called with: '{}' (no-op for basic CLI).", text);
        // In a more advanced CLI, this might pre-fill an input line.
    }

    @Override
    public void close() throws IOException {
        logger.info("Shutting down CommandLineUserInterfaceService.");
        if (inReader != null) {
            // Only close if not System.in, but here it is System.in.
            // Closing System.in is generally not recommended.
            // For this app, it's part of main so let main handle System.in lifecycle.
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) { // Shorter timeout
                executorService.shutdownNow();
                if (!executorService.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    logger.error("Executor did not terminate.");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("CommandLineUserInterfaceService shutdown complete.");
    }
}
