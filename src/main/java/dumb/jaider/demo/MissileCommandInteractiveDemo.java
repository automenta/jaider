package dumb.jaider.demo;

import dumb.jaider.demo.steps.*;
import dumb.jaider.integration.OllamaService;
import dumb.jaider.integration.ProjectManager;
import dumb.jaider.integration.TestConfig;
import dumb.jaider.integration.VerificationService;
import dumb.jaider.ui.TUI;
import dumb.jaider.workflow.CodeGenerationWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MissileCommandInteractiveDemo {
    private static final Logger logger = LoggerFactory.getLogger(MissileCommandInteractiveDemo.class);

    // POM content for Missile Command - similar to OllamaIntegrationTest
    private static String getMissileCommandPom() {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
               "    <modelVersion>4.0.0</modelVersion>\n" +
               "    <groupId>com.example.missilecommand</groupId>\n" +
               "    <artifactId>missile-command</artifactId>\n" +
               "    <version>1.0-SNAPSHOT</version>\n" +
               "    <properties>\n" +
               "        <maven.compiler.source>11</maven.compiler.source>\n" +
               "        <maven.compiler.target>11</maven.compiler.target>\n" +
               "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
               "    </properties>\n" +
               "    <dependencies>\n" +
               "        <!-- Swing is part of standard Java SE -->\n" +
               "    </dependencies>\n" +
               "    <build>\n" +
               "        <plugins>\n" +
               "            <plugin>\n" +
               "                <groupId>org.apache.maven.plugins</groupId>\n" +
               "                <artifactId>maven-compiler-plugin</artifactId>\n" +
               "                <version>3.8.1</version>\n" +
               "            </plugin>\n" +
               "        </plugins>\n" +
               "    </build>\n" +
               "</project>";
    }

    public static void main(String[] args) {
        TUI tui = new TUI();
        ProjectManager projectManager = new ProjectManager(); // Manages project files
        // InteractiveDemoExecutor should not be responsible for global setup/teardown of projectManager

        try {
            // Initialize core services
            TestConfig testConfig = new TestConfig(); // Uses defaults
            OllamaService ollamaService = new OllamaService(testConfig);
            // Basic Ollama check (optional here, but good for early failure)
            if (ollamaService.chatModel == null) {
                System.err.println("Ollama chat model failed to initialize. Please check Ollama setup and logs.");
                logger.error("Ollama chat model failed to initialize. Exiting demo.");
                // Show message in TUI if TUI is already partially initialized or fallback to console
                // For now, exiting as TUI init is later.
                return;
            }
             logger.info("Ollama instance seems available.");


            VerificationService verificationService = new VerificationService();
            CodeGenerationWorkflow codeGenerationWorkflow = new CodeGenerationWorkflow(ollamaService, projectManager, verificationService);
            InteractiveDemoExecutor demoExecutor = new InteractiveDemoExecutor(tui, codeGenerationWorkflow);

            // --- Demo Setup ---
            // Create a temporary project directory for this demo run
            try {
                projectManager.createTemporaryProject("InteractiveMissileCommand");
                logger.info("Temporary project directory created: {}", projectManager.getProjectDir());
            } catch (IOException e) {
                logger.error("Failed to create temporary project for demo", e);
                // If TUI were running, show error. For now, console.
                System.err.println("Failed to create temporary project: " + e.getMessage());
                return;
            }

            // Define the demo steps
            List<DemoStep> steps = new ArrayList<>();

            steps.add(new MessageStep("Welcome", "Welcome to the Interactive Missile Command Generation Demo!\nPress OK to start."));

            // Step 1: Generate Missile Command
            steps.add(new InitialProjectGenerationStep(
                "a complete, runnable, single-file Missile Command game in Java using Swing. The main class should be named 'MissileCommandGame' and be in package 'com.example.game'.",
                "com.example.game",
                "MissileCommandGame",
                getMissileCommandPom(),
                "MissileCommandGame", "JFrame", "Swing", "java.awt"
            ));

            // Step 2: Compile Initial Project
            steps.add(new CompileProjectStep("Initial Missile Command Compilation"));

            // Step 3: Ask Questions
            steps.add(new MessageStep("Q&A Time", "Now let's ask the LLM some questions about the generated code."));
            steps.add(new AskQuestionStep(
                "What is the main class of this game as specified in the initial prompt?",
                "initialCode", // Key in DemoContext where initial code is stored
                "missilecommandgame"
            ));
            steps.add(new AskQuestionStep(
                "How is the game window (JFrame) initialized?",
                "initialCode"
                // No specific verification keywords for this answer
            ));

            // Step 4: Enhance Missile Command with Sound
            steps.add(new MessageStep("Code Enhancement", "Next, we'll ask the LLM to enhance the game by adding sound effects."));
            steps.add(new EnhanceProjectStep(
                "Add sound effects for two events: when a missile is fired and when an explosion occurs. Use javax.sound.sampled.Clip for playing sounds. Create placeholder methods playFireSound() and playExplosionSound() if actual sound file loading is complex, but include necessary imports for javax.sound.sampled.*.",
                "com.example.game",
                "MissileCommandGame",
                "initialCode", // Use the initial code as the base for enhancement
                "enhancedCode", // Store the result in DemoContext under this key
                "javax.sound.sampled", "playFireSound", "playExplosionSound"
            ));

            // Step 5: Compile Enhanced Project
            steps.add(new CompileProjectStep("Enhanced Missile Command Compilation"));

            steps.add(new MessageStep("Demo Complete", "You've reached the end of the Missile Command Interactive Demo!\nPress OK to exit."));

            // --- Run the Demo through TUI ---
            // TUI init needs an App instance. For a standalone demo, we might need a lightweight App mock or a way to run TUI more directly.
            // For now, let's assume a simplified TUI setup for demo purposes if App is complex.
            // This part needs to align with how TUI.init expects to be called.
            // If TUI.init blocks, then demoExecutor.runDemo must be called before or by it.

            // Simplified approach: Initialize TUI and then run the demo.
            // The TUI's main loop (addWindowAndWait) will block. We need a way for the demo to run *within* that.
            // This suggests InteractiveDemoExecutor might need to be invoked by a TUI action,
            // or TUI needs a way to run a non-blocking main window and allow other logic.

            // For now, let's launch the TUI and assume the demo steps will correctly use its modal dialogs.
            // The `InteractiveDemoExecutor` uses `tui.confirm`, `tui.showScrollableText`, etc., which create their own windows.

            // A proper App class might be needed if TUI is tightly coupled.
            // Let's defer full TUI init for a moment and focus on the demo logic assembly.
            // The actual execution would be:
            // app.init() which calls tui.init();
            // Then, from some TUI action (e.g. a "Start Demo" button): demoExecutor.runDemo(steps);

            // To make this runnable directly for now, we can print a message.
            // The real TUI integration will be part of the "mechanism to launch demos" step.
            System.out.println("Demo steps configured. TUI integration needed to run.");
            logger.info("Missile Command Interactive Demo configured with {} steps.", steps.size());

            // Placeholder for actual TUI-driven execution:
            // This is where you'd typically initialize the TUI and have an entry point
            // in the TUI to trigger `demoExecutor.runDemo(steps);`
            // For example, if TUI had a "start demo" button:
            // myTUI.on("startDemoButton", () -> demoExecutor.runDemo(steps));
            // myTUI.init(new DummyApp()); // Dummy App for TUI

            // For now, to test the flow of steps without full TUI interaction in this specific subtask:
            // We can't directly call demoExecutor.runDemo(steps) here if TUI methods (like confirm)
            // require an initialized TUI thread and screen, which usually happens in tui.init().

            // The following is a conceptual way to run it if TUI was already up
            // and we got the `demoExecutor` instance.
            // This will likely fail if `tui.gui` is null within those TUI methods.
            // **This execution part will be refined in the TUI integration step.**

            // For this step, the primary goal is to define the sequence of DemoSteps.
            // The actual running mechanism will be handled in step 4 of the main plan.

            // Let's simulate a direct run for now, assuming TUI methods can be called
            // IF a GUI thread is somehow active or they are robust to null `gui`.
            // This is a temporary measure for this subtask.
            // In reality, TUI().init() would be called, and then from within TUI, the demo runs.

            // For now, let's just log that it's ready.
            logger.info("To run this demo, integrate with TUI.init() and trigger demoExecutor.runDemo(steps).");
            // A simple way to proceed for *this step* is to assume that TUI is initialized by a separate mechanism
            // and then `runDemo` is called. The `InteractiveDemoExecutor` itself is TUI-aware.


        } finally {
            if (projectManager != null) {
                projectManager.cleanupProject(); // Ensure cleanup
                logger.info("Temporary project directory cleaned up.");
            }
        }
    }
}
