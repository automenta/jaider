package dumb.jaider.ui;

import dumb.jaider.service.UserInterfaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class CommandLineUserInterfaceService implements UserInterfaceService {

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


    @Override
    public void showMessage(String message) {
        logger.info("Showing message to user: {}", message);
        out.println("[INFO] " + message);
    }

    @Override
    public void showError(String message) {
        logger.error("Showing error to user: {}", message);
        // Using System.err for errors is common practice
        System.err.println("[ERROR] " + message);
    }

    @Override
    public void askYesNoQuestion(String message, Consumer<Boolean> callback) {
        out.println("[QUESTION] " + message);
        out.print(" (yes/no): ");

        executorService.submit(() -> {
            try {
                String line = inReader.readLine();
                if (line != null) {
                    line = line.trim().toLowerCase();
                    boolean response = line.equals("yes") || line.equals("y");
                    callback.accept(response);
                } else {
                    // End of stream or error
                    logger.warn("No input received for yes/no question (null line). Defaulting to 'no'.");
                    callback.accept(false); // Default to no on EOF or error
                }
            } catch (IOException e) {
                logger.error("IOException reading user input for yes/no question. Defaulting to 'no'.", e);
                callback.accept(false); // Default to no on exception
            }
        });
    }

    // Call this method when the application is shutting down to clean up the executor.
    public void shutdown() {
        logger.info("Shutting down input executor.");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
