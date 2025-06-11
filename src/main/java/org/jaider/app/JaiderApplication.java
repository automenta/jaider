package org.jaider.app;

import dumb.jaider.model.JaiderModel;
import dumb.jaider.tools.JaiderTools;
import org.jaider.service.BasicRestartService;
import org.jaider.service.BuildManagerService;
import org.jaider.service.LocalGitService;
import org.jaider.service.SelfUpdateOrchestratorService;
import org.jaider.ui.CommandLineUserInterfaceService;

import java.io.File;
import java.nio.file.Paths; // Added import
import java.util.Scanner; // For simple command simulation

public class JaiderApplication {

    private static JaiderModel jaiderModel;
    private static CommandLineUserInterfaceService uiService;
    private static LocalGitService gitService;
    private static BuildManagerService buildManagerService;
    private static BasicRestartService restartService;
    private static SelfUpdateOrchestratorService selfUpdateOrchestratorService;
    private static JaiderTools jaiderTools; // The tool the LLM would use

    public static void main(String[] args) {
        System.out.println("Jaider Application Starting...");

        // 1. Initialize Model and Services
        // Initialize JaiderModel with the project directory
        jaiderModel = new JaiderModel(Paths.get(".").toAbsolutePath());
        jaiderModel.setOriginalArgs(args);
        // TODO: Set the project directory correctly. For now, using current directory.
        // This needs to be the root of the Maven project Jaider is working on (which is Jaider itself for self-update)
        System.out.println("Project directory set to: " + jaiderModel.dir.toAbsolutePath().toString());

        System.out.println("SELF-UPDATE TARGET MARKER: This line is a target for modification."); // Marker for diff

        // uiService = new CommandLineUserInterfaceService(); // Commented out
        gitService = new LocalGitService();
        buildManagerService = new BuildManagerService();
        restartService = new BasicRestartService();

        selfUpdateOrchestratorService = new SelfUpdateOrchestratorService(
                jaiderModel,
                null, // uiService, // Commented out
                buildManagerService,
                gitService,
                restartService
        );

        jaiderTools = new JaiderTools(jaiderModel, selfUpdateOrchestratorService);

        // Add shutdown hook to clean up UI service resources
        // Runtime.getRuntime().addShutdownHook(new Thread(() -> { // Commented out
        //     System.out.println("Jaider Application Shutting Down...");
        //     if (uiService != null) {
        //         uiService.shutdown();
        //     }
        // }));

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
            // if (uiService != null) { // Ensure shutdown if exiting normally through loop // Commented out
            //     uiService.shutdown();
            // }
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
        String diffContent = "<<<<<<< SEARCH\n" +
                             "        System.out.println(\"Jaider Application Starting...\");\n" +
                             "======= SEARCH\n" +
                             "        System.out.println(\"Jaider Application Starting...\");\n" +
                             "        // This is a self-update test comment!\n" +
                             "        System.out.println(\"Project directory set to: \" + jaiderModel.getDir().getAbsolutePath());\n" +
                             "======= REPLACE\n" +
                             "        System.out.println(\"Jaider Application Starting...\");\n" +
                             "        // This is a self-update test comment, applied!\n" +
                             "        System.out.println(\"Project directory set to: \" + jaiderModel.getDir().getAbsolutePath());\n" +
                             ">>>>>>> REPLACE";

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
            "<<<<<<< SEARCH\n" +
            "        jaiderModel.setDir(new File(\".\")); \n" +
            "        System.out.println(\"Project directory set to: \" + jaiderModel.getDir().getAbsolutePath());\n" +
            "=======\n" +
            "        jaiderModel.setDir(new File(\".\")); \n" +
            "        System.out.println(\"Jaider Self-Update: A new line added here!\"); // Added by self-update\n" +
            "        System.out.println(\"Project directory set to: \" + jaiderModel.getDir().getAbsolutePath());\n" +
            ">>>>>>> REPLACE";


        System.out.println("Simulating LLM call to jaiderTools.proposeSelfUpdate...");
        String result = jaiderTools.proposeSelfUpdate(targetFilePath, diffContent, "Test self-update commit");
        System.out.println("Result of proposal: " + result);
    }
}
