package org.jaider.ui;

import org.jaider.service.UserInterfaceService; // Assuming UserInterfaceService is in org.jaider.service
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

// import org.slf4j.Logger; // Future: Add logging
// import org.slf4j.LoggerFactory; // Future: Add logging

public class CommandLineUserInterfaceService implements UserInterfaceService {

    // private static final Logger logger = LoggerFactory.getLogger(CommandLineUserInterfaceService.class); // Future
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
        out.println("[INFO] " + message);
    }

    @Override
    public void showError(String message) {
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
                    // logger.warn("No input received for yes/no question (null line). Defaulting to 'no'."); // Future
                    System.err.println("CommandLineUserInterfaceService: No input received (null line). Defaulting to 'no'.");
                    callback.accept(false); // Default to no on EOF or error
                }
            } catch (IOException e) {
                // logger.error("IOException reading user input for yes/no question. Defaulting to 'no'.", e); // Future
                System.err.println("CommandLineUserInterfaceService: IOException reading user input. Defaulting to 'no'. Error: " + e.getMessage());
                callback.accept(false); // Default to no on exception
            }
        });
    }

    // Call this method when the application is shutting down to clean up the executor.
    public void shutdown() {
        // logger.info("Shutting down CommandLineUserInterfaceService executor."); // Future
        System.out.println("CommandLineUserInterfaceService: Shutting down input executor.");
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
