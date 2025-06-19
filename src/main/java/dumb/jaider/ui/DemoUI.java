package dumb.jaider.ui;

import dev.langchain4j.data.message.AiMessage;
import dumb.jaider.app.App;
import dumb.jaider.model.JaiderModel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DemoUI implements UI {

    private App app;
    private final Scanner directInputScanner = new Scanner(System.in);
    private Queue<String> commandQueue = new LinkedList<>();
    private CompletableFuture<Void> demoCompletionFuture = new CompletableFuture<>();
    private volatile boolean scriptRunning = false;
    private static final ExecutorService uiInteractionExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DemoUI-Interaction-Thread");
        t.setDaemon(true);
        return t;
    });

    public void loadScript(List<String> commands) {
        this.commandQueue.addAll(commands);
    }

    public CompletableFuture<Void> startProcessingScript(App app) {
        this.app = app;
        this.scriptRunning = true;
        uiInteractionExecutor.submit(this::processNextCommand);
        return demoCompletionFuture;
    }

    private void processNextCommand() {
        if (!scriptRunning) {
            if (demoCompletionFuture != null && !demoCompletionFuture.isDone()) {
                demoCompletionFuture.complete(null);
            }
            return;
        }

        if (commandQueue.isEmpty()) {
            System.out.println("[DemoUI] Script finished.");
            scriptRunning = false;
            if (this.app != null) {
                 try {
                    System.out.println("[DemoUI] Script ended. App would normally continue or be closed by user.");
                 } catch (Exception e) {
                     System.err.println("[DemoUI] Error during simulated app post-script actions: " + e.getMessage());
                 }
            }
            if (demoCompletionFuture != null && !demoCompletionFuture.isDone()) {
                demoCompletionFuture.complete(null);
            }
            return;
        }

        String command = commandQueue.poll();
        if (command == null) {
            uiInteractionExecutor.submit(this::processNextCommand);
            return;
        }

        try {
            if (command.startsWith("DEMO_OPERATOR_INPUT:")) {
                String prompt = command.substring("DEMO_OPERATOR_INPUT:".length()).trim();
                System.out.println("[DemoUI] PROMPT: " + prompt);
                System.out.print("[DemoUI] Your input > ");
                String userInput = directInputScanner.nextLine();
                if (this.app != null) {
                    this.app.handleUserInput(userInput);
                }
            } else if (command.startsWith("DEMO_COMMENT:")) {
                System.out.println("[DemoUI] INFO: " + command.substring("DEMO_COMMENT:".length()).trim());
                uiInteractionExecutor.submit(this::processNextCommand);
            } else if (command.startsWith("DEMO_PAUSE:")) {
                 String message = command.substring("DEMO_PAUSE:".length()).trim();
                System.out.println("[DemoUI] PAUSED: " + message);
                 System.out.println("Press Enter to continue...");
                 directInputScanner.nextLine();
                 uiInteractionExecutor.submit(this::processNextCommand);
            } else {
                System.out.println("[DemoUI] Executing scripted command: " + command);
                if (this.app != null) {
                    this.app.handleUserInput(command);
                }
            }
        } catch (Exception e) {
            System.err.println("[DemoUI] Error processing command '" + command + "': " + e.getMessage());
            e.printStackTrace();
            uiInteractionExecutor.submit(this::processNextCommand);
        }
    }


    @Override
    public void init(App app) {
        this.app = app;
        System.out.println("[DemoUI] Initialized by App. Waiting for script to start via startProcessingScript().");
    }

    @Override
    public void redraw(JaiderModel model) {
        System.out.flush();

        System.out.println("[DemoUI] --- REDRAW ---");
        System.out.println("[DemoUI] Mode: " + model.mode);
        System.out.println("[DemoUI] Status: " + model.statusBarText);
        System.out.println("[DemoUI] Current Tokens: " + model.currentTokenCount);
        System.out.println("[DemoUI] Files in Context:");
        if (model.files.isEmpty()) {
            System.out.println("[DemoUI]   (No files in context)");
        } else {
            model.files.forEach(p -> System.out.println("[DemoUI]   - " + (model.dir != null ? model.dir.relativize(p).toString() : p.toString())));
        }
        System.out.println("[DemoUI] Log Messages:");
        if (model.log.isEmpty()) {
             System.out.println("[DemoUI]   (Log is empty)");
        } else {
            new LinkedList<>(model.log).forEach(msg -> {
                String prefix = "[DemoUI]   [" + msg.type().name() + "] ";
                String text = dumb.jaider.utils.Util.chatMessageToText(msg);
                if (msg instanceof AiMessage && ((AiMessage) msg).hasToolExecutionRequests()) {
                    text = "Wants to use tool: " + ((AiMessage) msg).toolExecutionRequests().getFirst().name();
                }
                System.out.println(prefix + text);
            });
        }
        if (model.getActiveSuggestions() != null && !model.getActiveSuggestions().isEmpty()) {
            System.out.println("[DemoUI] Active Suggestions:");
            new LinkedList<>(model.getActiveSuggestions()).forEach(s -> System.out.println("[DemoUI]   [" + s.displayNumber() + "] " + s.originalSuggestion().suggestedToolName()));
        }
        System.out.println("[DemoUI] --- END REDRAW ---");
        System.out.flush();

        if (scriptRunning && this.app != null && this.app.getState() == App.State.IDLE) {
             System.out.println("[DemoUI] App is IDLE. Triggering next command processing from redraw.");
             uiInteractionExecutor.submit(this::processNextCommand);
        } else if (scriptRunning) {
             System.out.println("[DemoUI] App state is " + (this.app != null ? this.app.getState() : "UNKNOWN (app is null)") + ". Waiting for IDLE to process next command.");
        }
    }

    private <T> void handleBlockingUiInteraction(CompletableFuture<T> future, String promptMessage, BlockingTask<T> task) {
        uiInteractionExecutor.submit(() -> {
            if (promptMessage != null && !promptMessage.isEmpty()) {
                 System.out.print(promptMessage);
                 System.out.flush();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                T result = task.execute(reader);
                future.complete(result);
            } catch (Exception e) {
                System.err.println("[DemoUI] Error during blocking UI interaction: " + e.getMessage());
                e.printStackTrace();
                future.completeExceptionally(e);
            } finally {
                 if (scriptRunning && this.app != null && this.app.getState() == App.State.IDLE) {
                     System.out.println("[DemoUI] App is IDLE after UI interaction. Triggering next command processing.");
                     uiInteractionExecutor.submit(this::processNextCommand);
                 }
            }
        });
    }

    @FunctionalInterface
    interface BlockingTask<T> {
        T execute(BufferedReader reader) throws Exception;
    }

    @Override
    public CompletableFuture<Boolean> confirm(String title, String text) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        System.out.println("[DemoUI] CONFIRMATION REQUEST:");
        System.out.println("[DemoUI] Title: " + title);
        System.out.println("[DemoUI] Text: " + text);
        handleBlockingUiInteraction(future, "[DemoUI] Approve? (yes/no) > ", reader -> "yes".equalsIgnoreCase(reader.readLine().trim()));
        return future;
    }

    @Override
    public CompletableFuture<DiffInteractionResult> diffInteraction(String diff) {
        CompletableFuture<DiffInteractionResult> future = new CompletableFuture<>();
        System.out.println("[DemoUI] DIFF INTERACTION REQUEST:");
        System.out.println("[DemoUI] Agent wants to apply these changes:");
        System.out.println("-------------------- DIFF START --------------------");
        System.out.println(diff);
        System.out.println("--------------------- DIFF END ---------------------");

        handleBlockingUiInteraction(future, "[DemoUI] Action? (accept/reject/edit) > ", reader -> {
            String action = reader.readLine().trim().toLowerCase();
            switch (action) {
                case "accept" -> {
                    return new DiffInteractionResult(true, false, diff);
                }
                case "reject" -> {
                    return new DiffInteractionResult(false, false, null);
                }
                case "edit" -> {
                    System.out.println("[DemoUI] Enter the new diff content. Type 'END_OF_DIFF' on a new line to finish:");
                    StringBuilder editedDiffBuilder = new StringBuilder();
                    String line;
                    while (!(line = reader.readLine()).equals("END_OF_DIFF")) {
                        editedDiffBuilder.append(line).append(System.lineSeparator());
                    }
                    return new DiffInteractionResult(true, true, editedDiffBuilder.toString().trim());
                }
                default -> {
                    System.out.println("[DemoUI] Invalid action. Rejecting diff.");
                    return new DiffInteractionResult(false, false, null);
                }
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<String> configEdit(String currentConfig) {
        CompletableFuture<String> future = new CompletableFuture<>();
        System.out.println("[DemoUI] CONFIGURATION EDIT REQUEST:");
        System.out.println("[DemoUI] Current configuration:");
        System.out.println("-------------------- CONFIG START --------------------");
        System.out.println(currentConfig);
        System.out.println("--------------------- CONFIG END ---------------------");
        System.out.println("[DemoUI] Enter the new configuration. Type 'END_OF_CONFIG' on a new line to finish, or 'CANCEL_CONFIG' to abort:");

        handleBlockingUiInteraction(future, null, reader -> {
            StringBuilder newConfigBuilder = new StringBuilder();
            String line;
            boolean cancelled = false;
            while (!(line = reader.readLine()).equals("END_OF_CONFIG")) {
                if ("CANCEL_CONFIG".equals(line)) {
                    cancelled = true;
                    break;
                }
                newConfigBuilder.append(line).append(System.lineSeparator());
            }
            return cancelled ? null : newConfigBuilder.toString().trim();
        });
        return future;
    }

    @Override
    public CompletableFuture<Boolean> confirmPlan(String title, String planText, AiMessage agentMessage) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        System.out.println("[DemoUI] PLAN CONFIRMATION REQUEST:");
        System.out.println("[DemoUI] Title: " + title);
        System.out.println("[DemoUI] Agent's Proposed Plan:");
        System.out.println("-------------------- PLAN START --------------------");
        System.out.println(planText);
        System.out.println("--------------------- PLAN END ---------------------");
        handleBlockingUiInteraction(future, "[DemoUI] Approve plan? (yes/no) > ", reader -> "yes".equalsIgnoreCase(reader.readLine().trim()));
        return future;
    }

    @Override
    public void setInputText(String text) {
        System.out.println("[DemoUI] INFO: Application suggests setting input text to: " + text);
    }

    @Override
    public CompletableFuture<String> switchProjectDirectory(String currentDirectory) {
        return null;
    }

    @Override
    public CompletableFuture<Void> showGlobalConfiguration() {
        return null;
    }

    @Override
    public void close() {
        System.out.println("[DemoUI] Close called. Initiating shutdown of DemoUI resources.");
        scriptRunning = false;

        if (demoCompletionFuture != null && !demoCompletionFuture.isDone()) {
            demoCompletionFuture.complete(null);
        }

        if (!uiInteractionExecutor.isShutdown()) {
            System.out.println("[DemoUI] Shutting down UI interaction executor service...");
            uiInteractionExecutor.shutdown();
            try {
                if (!uiInteractionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("[DemoUI] UI Executor did not terminate in 5s. Forcing shutdown...");
                    List<Runnable> droppedTasks = uiInteractionExecutor.shutdownNow();
                    System.err.println("[DemoUI] " + droppedTasks.size() + " tasks were dropped.");
                    if (!uiInteractionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        System.err.println("[DemoUI] UI Executor did not terminate even after shutdownNow().");
                    }
                }
            } catch (InterruptedException ie) {
                uiInteractionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("[DemoUI] DemoUI resources released.");
    }
}
