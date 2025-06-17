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
import dumb.jaider.demo.steps.tutorial.TutorialDemoSteps.TutorialInitializeLLMStep;
import dumb.jaider.demo.steps.tutorial.TutorialDemoSteps.TutorialGenerateProjectStep;
import dumb.jaider.ui.TUI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class JaiderIntegrationTutorialDemo {

    private static final Logger logger = LoggerFactory.getLogger(JaiderIntegrationTutorialDemo.class);

    public JaiderIntegrationTutorialDemo() {
        // Constructor can be kept for future use or removed if not needed.
    }

    public static void main(String[] args) {
        TUI tui = new TUI();
        try {
            logger.info("Jaider Integration Tutorial Demo starting.");

            // Initialize services for CodeGenerationWorkflow
            // As with InteractiveDemo, these steps manage their own LLM interaction (LangChain4j ChatModel).
            // The workflow is provided to the executor but might not be used by these specific tutorial steps.
            LanguageModelService llmService = new DummyOllamaService(); // Placeholder
            ProjectManager projectManager = new ProjectManager(".");
            VerificationService verificationService = new VerificationService(".");

            CodeGenerationWorkflow codeGenerationWorkflow = new CodeGenerationWorkflow(
                llmService,
                projectManager,
                verificationService
            );

            DemoContext demoContext = new DemoContext();
            InteractiveDemoExecutor executor = new InteractiveDemoExecutor(tui, codeGenerationWorkflow, demoContext);

            List<DemoStep> steps = new ArrayList<>();
            steps.add(new DisplayMessageStep("Jaider Tutorial",
                "Welcome to the Jaider Integration Tutorial Demo (Simplified)!\n" +
                "This tutorial demonstrates basic LLM interaction for single file generation using a TUI.\n" +
                "It does NOT showcase most of Jaider's advanced features."));
            steps.add(new TutorialInitializeLLMStep());
            steps.add(new TutorialGenerateProjectStep());
            steps.add(new DisplayMessageStep("Tutorial Finished",
                "This simplified tutorial focused on initializing an LLM and generating a single file.\n" +
                "For a comprehensive look at Jaider, please try the main application or other demos."));

            executor.runDemo(steps);

        } catch (Exception e) {
            logger.error("An error occurred during the Jaider Integration Tutorial Demo: {}", e.getMessage(), e);
            if (tui != null) {
                tui.showModalMessage("Critical Error", "An unexpected error occurred: " + e.getMessage() + "\nPlease check the logs.");
            } else {
                System.err.println("An unexpected error occurred in JaiderIntegrationTutorialDemo: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            if (tui != null) {
                tui.close();
            }
            logger.info("Jaider Integration Tutorial Demo finished.");
            System.out.println("Jaider Integration Tutorial Demo finished. TUI closed.");
        }
    }
}
