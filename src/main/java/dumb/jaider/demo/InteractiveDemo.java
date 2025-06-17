package dumb.jaider.demo;

import dumb.jaider.core.DemoContext;
import dumb.jaider.core.DemoStep;
import dumb.jaider.core.InteractiveDemoExecutor;
import dumb.jaider.core.llms.DummyOllamaService;
import dumb.jaider.core.llms.LanguageModelService;
import dumb.jaider.core.ProjectManager;
import dumb.jaider.core.VerificationService;
import dumb.jaider.core.workflows.CodeGenerationWorkflow;
import dumb.jaider.demo.steps.common.DisplayMessageStep; // Updated import
import dumb.jaider.demo.steps.interactive.InteractiveDemoSteps.SelectLLMStep;
import dumb.jaider.demo.steps.interactive.InteractiveDemoSteps.EnterApiKeyStep;
import dumb.jaider.demo.steps.interactive.InteractiveDemoSteps.SelectProjectDescriptionStep;
import dumb.jaider.demo.steps.interactive.InteractiveDemoSteps.GenerateAndVerifyStep;
import dumb.jaider.ui.TUI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


public class InteractiveDemo {

    private static final Logger logger = LoggerFactory.getLogger(InteractiveDemo.class);

    public InteractiveDemo() {
        // Constructor remains, can be empty or used for pre-TUI setup if any.
    }

    public static void main(String[] args) {
        TUI tui = new TUI();
        try {
            System.out.println("Initializing Interactive Demo..."); // Can be removed if TUI starts fast
            logger.info("Interactive Demo starting.");

            // Initialize services for CodeGenerationWorkflow
            // For this demo, GenerateAndVerifyStep handles LLM interaction directly,
            // so the workflow's LLM service might not be strictly used by that step.
            // We use DummyOllamaService as a placeholder if no specific LLM is chosen for the workflow itself.
            LanguageModelService llmService = new DummyOllamaService(); // Placeholder
            ProjectManager projectManager = new ProjectManager("."); // Current directory
            VerificationService verificationService = new VerificationService("."); // Current directory

            CodeGenerationWorkflow codeGenerationWorkflow = new CodeGenerationWorkflow(
                llmService,
                projectManager,
                verificationService
            );

            DemoContext demoContext = new DemoContext();
            InteractiveDemoExecutor executor = new InteractiveDemoExecutor(tui, codeGenerationWorkflow, demoContext);

            List<DemoStep> steps = new ArrayList<>();
            steps.add(new DisplayMessageStep("Welcome",
                "This is a simplified interactive demo showcasing the basic capability of generating a single file using a Large Language Model (LLM).\n" +
                "The full Jaider application provides a much richer and more powerful AI-assisted development experience.\n" +
                "For the complete feature set, please run the main Jaider application."));
            steps.add(new SelectLLMStep());
            steps.add(new EnterApiKeyStep());
            steps.add(new SelectProjectDescriptionStep());
            steps.add(new GenerateAndVerifyStep());
            steps.add(new DisplayMessageStep("Demo Finished",
                "This simplified demo focused on single-file generation.\n" +
                "To explore Jaider's full capabilities for AI-assisted coding, please try the main application."));

            executor.runDemo(steps);

        } catch (Exception e) {
            logger.error("An error occurred during the interactive demo: {}", e.getMessage(), e);
            if (tui != null) {
                tui.showModalMessage("Critical Error", "An unexpected error occurred: " + e.getMessage() + "\nPlease check the logs.");
            } else {
                System.err.println("An unexpected error occurred: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            if (tui != null) {
                tui.close();
            }
            logger.info("Interactive Demo finished.");
            System.out.println("Interactive demo finished. TUI closed."); // Ensures console output after TUI closes
        }
    }
}
