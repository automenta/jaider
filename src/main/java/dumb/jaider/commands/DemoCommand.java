package dumb.jaider.commands;

import dumb.jaider.app.App;
import dumb.jaider.demo.DemoProvider;
import dumb.jaider.demo.DemoStep;
import dumb.jaider.demo.InteractiveDemoExecutor;
import dumb.jaider.demo.demos.ContextualQADemo; // Import for the new demo
import dumb.jaider.demo.demos.HelloWorldDemo; // For explicit registration
import dumb.jaider.demo.demos.MissileCommandDemo; // For explicit registration
import dumb.jaider.integration.OllamaService;
import dumb.jaider.integration.ProjectManager;
import dumb.jaider.integration.TestConfig;
import dumb.jaider.integration.VerificationService;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.ui.TUI;
import dumb.jaider.workflow.CodeGenerationWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DemoCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(DemoCommand.class);
    private final App app;
    private final Map<String, DemoProvider> availableDemos = new HashMap<>();

    private final OllamaService ollamaService;
    private final ProjectManager projectManager;
    // VerificationService and CodeGenerationWorkflow are created using these, so they don't need to be injected separately for now unless CGW becomes more complex to init.

    // Primary constructor for application use
    public DemoCommand(App app) {
        this(app, new OllamaService(new TestConfig()), new ProjectManager());
        // Note: TestConfig() might need to be configurable if it affects OllamaService significantly for tests.
    }

    // Constructor for testing with dependency injection
    public DemoCommand(App app, OllamaService ollamaService, ProjectManager projectManager) {
        this.app = app;
        this.ollamaService = ollamaService;
        this.projectManager = projectManager; // Use injected ProjectManager
        discoverDemos();
    }

    private void discoverDemos() {
        DemoProvider missileDemo = new MissileCommandDemo();
        availableDemos.put(missileDemo.getName(), missileDemo);
        DemoProvider helloDemo = new HelloWorldDemo();
        availableDemos.put(helloDemo.getName(), helloDemo);
        DemoProvider contextualQADemo = new ContextualQADemo(); // Instantiate the new demo
        availableDemos.put(contextualQADemo.getName(), contextualQADemo); // Register the new demo
        logger.info("Discovered demos: {}", availableDemos.keySet());
    }

    @Override
    public String Mnemonic() {
        return "/demo";
    }

    @Override
    public String Help() {
        StringBuilder helpText = new StringBuilder("Runs an interactive demo. Usage: /demo <demoname>\nAvailable demos:\n");
        if (availableDemos.isEmpty()) {
            helpText.append("  No demos currently available.");
        } else {
            for (DemoProvider demo : availableDemos.values()) {
                helpText.append(String.format("  %s: %s\n", demo.getName(), demo.getDescription()));
            }
        }
        return helpText.toString();
    }

    @Override
    public CompletableFuture<Void> execute(String... args) {
        JaiderModel model = app.getModel();
        TUI ui = (TUI) app.getUi();

        if (args == null || args.length == 0) {
            model.addLog(dev.langchain4j.data.message.AiMessage.from("Usage: /demo <demoname>. Use /help demo to see available demos."));
            ui.redraw(model);
            return CompletableFuture.completedFuture(null);
        }

        String demoName = args[0].toLowerCase();
        logger.info("Attempting to start demo: {}", demoName);

        DemoProvider selectedDemo = availableDemos.get(demoName);

        if (selectedDemo == null) {
            model.addLog(dev.langchain4j.data.message.AiMessage.from("Unknown demo: " + demoName + ". Use /help demo to see available demos."));
            ui.redraw(model);
            return CompletableFuture.completedFuture(null);
        }

        // Use injected OllamaService
        if (this.ollamaService.chatModel == null) { // Check the injected service
            String errorMsg = "Ollama chat model failed to initialize. Cannot run demo. Please check Ollama setup.";
            logger.error(errorMsg);
            model.addLog(dev.langchain4j.data.message.AiMessage.from(errorMsg));
            ui.redraw(model);
            return CompletableFuture.completedFuture(null);
        }

        // VerificationService is stateless and simple for now
        VerificationService verificationService = new VerificationService();
        // CodeGenerationWorkflow now uses the injected ollamaService and projectManager
        CodeGenerationWorkflow codeGenerationWorkflow = new CodeGenerationWorkflow(this.ollamaService, this.projectManager, verificationService);
        InteractiveDemoExecutor demoExecutor = new InteractiveDemoExecutor(ui, codeGenerationWorkflow);

        List<DemoStep> steps;

        try {
            // Use the injected projectManager for creating the temporary project directory
            // The POM content is handled by InitialProjectGenerationStep via the workflow
            this.projectManager.createTemporaryProject("JaiderDemo_" + selectedDemo.getName());
            logger.info("Temporary project for {} demo created at: {}", selectedDemo.getName(), this.projectManager.getProjectDir());

            steps = selectedDemo.getSteps();

            if (steps == null || steps.isEmpty()) { // Added null check for steps
                model.addLog(dev.langchain4j.data.message.AiMessage.from("No steps defined for demo: " + demoName + ". This is an internal error."));
                ui.redraw(model);
                if (this.projectManager.getProjectDir() != null) {
                    this.projectManager.cleanupProject();
                    logger.info("Cleaned up project directory due to empty steps for demo: {}", demoName);
                }
                return CompletableFuture.completedFuture(null);
            }

            demoExecutor.runDemo(steps);

        } catch (IOException e) {
            // Log with selectedDemo.getName() for better context, which was there but good to confirm
            logger.error("IOException during demo setup or execution for {}: {}", selectedDemo.getName(), e.getMessage(), e);
            model.addLog(dev.langchain4j.data.message.AiMessage.from("Error with demo " + selectedDemo.getName() + " (IO): " + e.getMessage()));
            ui.redraw(model);
        } catch (Exception e) {
            logger.error("Exception during demo execution for {}: {}", selectedDemo.getName(), e.getMessage(), e);
            model.addLog(dev.langchain4j.data.message.AiMessage.from("Error running demo " + selectedDemo.getName() + ": " + e.getMessage()));
            ui.redraw(model);
        } finally {
            if (this.projectManager.getProjectDir() != null) {
                this.projectManager.cleanupProject();
                logger.info("Cleaned up project directory after demo: {}", selectedDemo.getName()); // Use selectedDemo.getName()
            }
        }

        model.addLog(dev.langchain4j.data.message.AiMessage.from("Demo '" + selectedDemo.getName() + "' finished.")); // Use selectedDemo.getName()
        ui.redraw(model);
        return CompletableFuture.completedFuture(null);
    }
}
