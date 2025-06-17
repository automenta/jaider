package dumb.jaider.commands;

import dumb.jaider.app.App;
import dumb.jaider.demo.DemoStep;
import dumb.jaider.demo.InteractiveDemoExecutor;
import dumb.jaider.demo.steps.*;
import dumb.jaider.integration.OllamaService;
import dumb.jaider.integration.ProjectManager;
import dumb.jaider.integration.TestConfig;
import dumb.jaider.integration.VerificationService;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.ui.TUI; // Assuming TUI is the concrete UI implementation needed
import dumb.jaider.ui.UI; // If App returns UI interface, but InteractiveDemoExecutor expects TUI
import dumb.jaider.workflow.CodeGenerationWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DemoCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(DemoCommand.class);
    private final App app;

    public DemoCommand(App app) {
        this.app = app;
    }

    @Override
    public String Mnemonic() {
        return "/demo";
    }

    @Override
    public String Help() {
        return "Runs an interactive demo. Usage: /demo <demoname>\n" +
               "Available demos: missile_command, hello_world"; // Added hello_world
    }

    @Override
    public CompletableFuture<Void> execute(String... args) {
        JaiderModel model = app.getModel();
        // InteractiveDemoExecutor expects TUI, so cast is appropriate here.
        // Ensure app.getUi() returns an instance that is actually a TUI.
        TUI ui = (TUI) app.getUi();

        if (args == null || args.length == 0) {
            model.addLog(dev.langchain4j.data.message.AiMessage.from("Usage: /demo <demoname>. Try '/demo missile_command' or '/demo hello_world'."));
            ui.redraw(model);
            return CompletableFuture.completedFuture(null);
        }

        String demoName = args[0].toLowerCase();
        logger.info("Attempting to start demo: {}", demoName);

        TestConfig testConfig = new TestConfig();
        OllamaService ollamaService = new OllamaService(testConfig);
        if (ollamaService.chatModel == null) {
            String errorMsg = "Ollama chat model failed to initialize. Cannot run demo. Please check Ollama setup.";
            logger.error(errorMsg);
            model.addLog(dev.langchain4j.data.message.AiMessage.from(errorMsg));
            ui.redraw(model);
            return CompletableFuture.completedFuture(null);
        }

        ProjectManager projectManager = new ProjectManager();
        VerificationService verificationService = new VerificationService();
        CodeGenerationWorkflow codeGenerationWorkflow = new CodeGenerationWorkflow(ollamaService, projectManager, verificationService);
        InteractiveDemoExecutor demoExecutor = new InteractiveDemoExecutor(ui, codeGenerationWorkflow);

        List<DemoStep> steps = new ArrayList<>();
        // tempProjectName removed as project creation is inside if blocks

        try {
            if ("missile_command".equals(demoName)) {
                projectManager.createTemporaryProject("InteractiveMissileCommandDemo");
                logger.info("Temporary project for Missile Command demo created at: {}", projectManager.getProjectDir());
                steps = getMissileCommandSteps();
            } else if ("hello_world".equals(demoName)) {
                projectManager.createTemporaryProject("InteractiveHelloWorldDemo");
                logger.info("Temporary project for Hello World demo created at: {}", projectManager.getProjectDir());
                steps = getHelloWorldSteps();
            } else {
                model.addLog(dev.langchain4j.data.message.AiMessage.from("Unknown demo: " + demoName + ". Available: missile_command, hello_world"));
                ui.redraw(model);
                // No project created yet, so no cleanup needed here for ProjectManager instance
                return CompletableFuture.completedFuture(null);
            }

            if (steps.isEmpty()) { // Should not happen if demo name is valid and methods return steps
                model.addLog(dev.langchain4j.data.message.AiMessage.from("No steps defined for demo: " + demoName + ", though it was recognized. This is an internal error."));
                ui.redraw(model);
                // If project was created (e.g. createTemporaryProject was called but getSteps returned empty)
                if (projectManager.getProjectDir() != null) {
                    projectManager.cleanupProject();
                    logger.info("Cleaned up project directory due to empty steps for demo: {}", demoName);
                }
                return CompletableFuture.completedFuture(null);
            }

            // If we reach here, a project was created and steps were populated.
            // runDemo will execute, and the finally block will handle cleanup.
            demoExecutor.runDemo(steps);

        } catch (IOException e) {
            logger.error("IOException during demo setup or execution for {}: {}", demoName, e.getMessage(), e);
            model.addLog(dev.langchain4j.data.message.AiMessage.from("Error with demo " + demoName + " (IO): " + e.getMessage()));
            ui.redraw(model);
        } catch (Exception e) {
            logger.error("Exception during demo execution for {}: {}", demoName, e.getMessage(), e);
            model.addLog(dev.langchain4j.data.message.AiMessage.from("Error running demo " + demoName + ": " + e.getMessage()));
            ui.redraw(model);
        } finally {
            // This ensures cleanup if a project directory was created by projectManager
            if (projectManager.getProjectDir() != null) {
                projectManager.cleanupProject();
                logger.info("Cleaned up project directory after demo: {}", demoName);
            }
        }

        model.addLog(dev.langchain4j.data.message.AiMessage.from("Demo '" + demoName + "' finished."));
        ui.redraw(model);
        return CompletableFuture.completedFuture(null);
    }

    private List<DemoStep> getMissileCommandSteps() {
        List<DemoStep> steps = new ArrayList<>();
        steps.add(new MessageStep("Welcome", "Welcome to the Interactive Missile Command Generation Demo!\nPress OK to start."));
        steps.add(new InitialProjectGenerationStep(
            "a complete, runnable, single-file Missile Command game in Java using Swing. The main class should be named 'MissileCommandGame' and be in package 'com.example.game'.",
            "com.example.game",
            "MissileCommandGame",
            getMissileCommandPomContent(),
            "MissileCommandGame", "JFrame", "Swing", "java.awt"
        ));
        steps.add(new CompileProjectStep("Initial Missile Command Compilation"));
        steps.add(new MessageStep("Q&A Time", "Now let's ask the LLM some questions about the generated code."));
        steps.add(new AskQuestionStep(
            "What is the main class of this game as specified in the initial prompt?",
            "initialCode",
            "missilecommandgame"
        ));
        steps.add(new AskQuestionStep(
            "How is the game window (JFrame) initialized?",
            "initialCode"
        ));
        steps.add(new MessageStep("Code Enhancement", "Next, we'll ask the LLM to enhance the game by adding sound effects."));
        steps.add(new EnhanceProjectStep(
            "Add sound effects for two events: when a missile is fired and when an explosion occurs. Use javax.sound.sampled.Clip for playing sounds. Create placeholder methods playFireSound() and playExplosionSound() if actual sound file loading is complex, but include necessary imports for javax.sound.sampled.*.",
            "com.example.game",
            "MissileCommandGame",
            "initialCode",
            "enhancedCode",
            "javax.sound.sampled", "playFireSound", "playExplosionSound"
        ));
        steps.add(new CompileProjectStep("Enhanced Missile Command Compilation"));
        steps.add(new MessageStep("Demo Complete", "You've reached the end of the Missile Command Interactive Demo!\nPress OK to exit."));
        return steps;
    }

    private List<DemoStep> getHelloWorldSteps() {
        List<DemoStep> steps = new ArrayList<>();
        steps.add(new MessageStep("Hello, World! Demo", "Welcome to the Interactive Hello, World! Java Application Demo.\nThis demo will generate and compile a simple Hello World program.\nPress OK to start."));

        steps.add(new InitialProjectGenerationStep(
            "a simple Hello World application in Java. The main class should be named 'HelloWorld' in package 'com.example.hello', and it should print 'Hello, World!' to the console.",
            "com.example.hello",
            "HelloWorld",
            getHelloWorldPomContent(),
            "HelloWorld", "System.out.println", "Hello, World!"
        ));

        steps.add(new CompileProjectStep("Hello, World! Compilation"));

        steps.add(new MessageStep("Demo Complete", "You've reached the end of the Hello, World! Interactive Demo!\nPress OK to exit."));
        return steps;
    }

    private String getMissileCommandPomContent() {
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

    private String getHelloWorldPomContent() {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
               "    <modelVersion>4.0.0</modelVersion>\n" +
               "    <groupId>com.example.helloworld</groupId>\n" +
               "    <artifactId>hello-world-app</artifactId>\n" +
               "    <version>1.0-SNAPSHOT</version>\n" +
               "    <properties>\n" +
               "        <maven.compiler.source>11</maven.compiler.source>\n" +
               "        <maven.compiler.target>11</maven.compiler.target>\n" +
               "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
               "    </properties>\n" +
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
}
