package dumb.jaider.app;

import dumb.jaider.model.JaiderModel;
import dumb.jaider.service.BasicRestartService;
import dumb.jaider.service.BuildManagerService;
import dumb.jaider.service.LocalGitService;
import dumb.jaider.service.SelfUpdateOrchestratorService;
import dumb.jaider.tools.JaiderTools;
import dumb.jaider.ui.CommandLineUserInterfaceService;
import org.slf4j.Logger; // Added import
import org.slf4j.LoggerFactory; // Added import
import java.io.IOException; // Added import for IOException in shutdown hook

import java.nio.file.Paths;
import java.util.Scanner;

public class JaiderApplication {

    private static final Logger logger = LoggerFactory.getLogger(JaiderApplication.class); // Added logger
    private static CommandLineUserInterfaceService uiService;
    private static JaiderTools jaiderTools; // The tool the LLM would use

    public static void main(String[] args) {
        System.out.println("Jaider Application Starting...");

        // 1. Initialize Model and Services
        // Initialize JaiderModel with the project directory
        JaiderModel jaiderModel = new JaiderModel(Paths.get(".").toAbsolutePath());
        jaiderModel.setOriginalArgs(args);
        // The project directory is the current directory from which Jaider is run.
        // This is typically the root of the Maven project Jaider is working on (which includes Jaider itself for self-update).
        System.out.println("Project directory set to: " + jaiderModel.dir.toAbsolutePath());

        System.out.println("SELF-UPDATE TARGET MARKER: This line is a target for modification."); // Marker for diff

        uiService = new CommandLineUserInterfaceService();
        LocalGitService gitService = new LocalGitService();
        BuildManagerService buildManagerService = new BuildManagerService();
        BasicRestartService restartService = new BasicRestartService();

        SelfUpdateOrchestratorService selfUpdateOrchestratorService = new SelfUpdateOrchestratorService(
                jaiderModel,
                uiService,
                buildManagerService,
                gitService,
                restartService
        );

        jaiderTools = new JaiderTools(jaiderModel, selfUpdateOrchestratorService);

        // Add shutdown hook to clean up UI service resources
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Jaider Application Shutting Down...");
            if (uiService != null) {
                try {
                    uiService.close();
                } catch (IOException e) {
                    logger.error("Error closing UI service on shutdown: {}", e.getMessage(), e);
                }
            }
        }));

        // 2. Application Main Loop (simplified)
        // In a real Langchain4j app, the agent would call jaiderTools.proposeSelfUpdate.
        // Here, we simulate this and periodically check for pending updates.
        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;
            while (running) {
                // Check for pending updates (the UI service's input runs on a separate thread)
                if (selfUpdateOrchestratorService.getPendingUpdate() != null &&
                    !selfUpdateOrchestratorService.isUpdateInProgress()) { // Add isUpdateInProgress to orchestrator
                    selfUpdateOrchestratorService.triggerUserConfirmationProcess();
                }

                System.out.println("\nJaider CLI Simulator: Enter 'propose' to simulate a self-update proposal, 'exit' to quit.");
                System.out.print("> ");
                String input = scanner.nextLine();

                switch (input.toLowerCase()) {
                    case "propose":
                        simulateSelfUpdateProposal();
                        break;
                    case "exit":
                        running = false;
                        break;
                    default:
                        System.out.println("Unknown command.");
                        break;
                }
                Thread.sleep(100); // Small delay to prevent busy-waiting if automated
            }
        } catch (InterruptedException e) {
            System.err.println("Main loop interrupted.");
            Thread.currentThread().interrupt();
        } finally {
            if (uiService != null) { // Ensure shutdown if exiting normally through loop
                try {
                    uiService.close();
                } catch (IOException e) {
                    // In JaiderApplication, direct logging might be okay, or use its own logger if it had one.
                    System.err.println("Error closing UI service in finally block: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        System.out.println("Jaider Application Exited.");
    }

    private static void simulateSelfUpdateProposal() {
        // Simulate a proposal to update JaiderApplication itself or a dummy file.
        // For a real test, target a file like a utility class or even this main class.
        String targetFilePath = "src/main/java/dumb/jaider/JaiderApplication.java"; // Example target
        // A simple diff: add a comment.
        // IMPORTANT: The line numbers in the diff must match the target file's current state.
        // This diff is just an example and likely WON'T apply cleanly without adjustment.
        String diffContent = """
                <<<<<<< SEARCH
                        System.out.println("Jaider Application Starting...");
                ======= SEARCH
                        System.out.println("Jaider Application Starting...");
                        // This is a self-update test comment!
                        System.out.println("Project directory set to: " + jaiderModel.getDir().getAbsolutePath());
                ======= REPLACE
                        System.out.println("Jaider Application Starting...");
                        // This is a self-update test comment, applied!
                        System.out.println("Project directory set to: " + jaiderModel.getDir().getAbsolutePath());
                >>>>>>> REPLACE""";

        // A more robust diff for testing that adds a new line:
        // Assuming `jaiderModel.setDir(new File("."));` is line 30 (example)
        // And `System.out.println("Project directory set to: " + jaiderModel.getDir().getAbsolutePath());` is line 31
        // The diff should be relative to the content of `targetFilePath`
        // For this example, let's assume we want to add a line after `jaiderModel.setDir(new File("."));`
        // Content of JaiderApplication.java around that area:
        //  jaiderModel = new JaiderModel();
        //  jaiderModel.setOriginalArgs(args);
        //  jaiderModel.setDir(new File("."));
        //  System.out.println("Project directory set to: " + jaiderModel.getDir().getAbsolutePath());

        // A diff to add a new print statement:
        diffContent =
                """
                        <<<<<<< SEARCH
                                jaiderModel.setDir(new File("."));\s
                                System.out.println("Project directory set to: " + jaiderModel.getDir().getAbsolutePath());
                        =======
                                jaiderModel.setDir(new File("."));\s
                                System.out.println("Jaider Self-Update: A new line added here!"); // Added by self-update
                                System.out.println("Project directory set to: " + jaiderModel.getDir().getAbsolutePath());
                        >>>>>>> REPLACE""";


        System.out.println("Simulating LLM call to jaiderTools.proposeSelfUpdate...");
        String result = jaiderTools.proposeSelfUpdate(targetFilePath, diffContent, "Test self-update commit");
        System.out.println("Result of proposal: " + result);
    }
}
