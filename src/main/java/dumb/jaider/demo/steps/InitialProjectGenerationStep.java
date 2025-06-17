package dumb.jaider.demo.steps;

import dumb.jaider.demo.DemoContext;
import dumb.jaider.demo.DemoStep;
import dumb.jaider.ui.TUI;
import dumb.jaider.workflow.CodeGenerationWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture; // Added import

public class InitialProjectGenerationStep implements DemoStep {
    private static final Logger logger = LoggerFactory.getLogger(InitialProjectGenerationStep.class);

    private final String description;
    private final String packageName;
    private final String className;
    private final String pomContent;
    private final String[] verificationKeywords;

    public InitialProjectGenerationStep(String description, String packageName, String className, String pomContent, String... verificationKeywords) {
        this.description = description;
        this.packageName = packageName;
        this.className = className;
        this.pomContent = pomContent;
        this.verificationKeywords = verificationKeywords;
    }

    @Override
    public boolean execute(TUI tui, CodeGenerationWorkflow workflow, DemoContext context) throws Exception {
        logger.info("Executing InitialProjectGenerationStep: {}", description);

        // Inform the user via TUI
        tui.showModalMessage("Code Generation", "About to generate initial project code for: '" + description + "'.\nThis might take a moment...");

        // Execute the workflow
        CodeGenerationWorkflow.ProjectGenerationResult result = workflow.generateInitialProject(
            description, packageName, className, pomContent, verificationKeywords
        );

        // Store results in context
        context.setCurrentCode(result.generatedCode());
        context.setProjectPath(result.projectPath());
        context.setCurrentFilePath(result.javaFilePath());
        context.set("initialCode", result.generatedCode()); // Also store with a specific key

        logger.info("Initial project generated. Code length: {}, Path: {}", result.generatedCode().length(), result.javaFilePath());

        // Display generated code (using a repurposed configEdit or a new TUI method if available)
        // For now, let's assume TUI.configEdit can be used for display by having "Save" act as "Continue"
        // and we tell the user to just "Save" (or "Close") to continue.
        // A more specialized TUI method would be better here.
        tui.showModalMessage("Code Generated", "Initial code has been generated. Click OK to view the code.");

        // Simulate showing code using configEdit - this is a placeholder for better TUI display
        // In a real scenario, the "Save" button in configEdit would need to be "Continue" or "OK"
        // and the text box read-only for display purposes.
        // CompletableFuture<String> viewFuture = tui.configEdit(result.generatedCode());
        // viewFuture.join(); // Wait for user to close the "editor" window
        // tui.showModalMessage("Next Step", "Code generation complete. Press OK to proceed to the next step.");

        tui.showModalMessage("Code Generated", "Initial code has been generated. Click OK to view the code.");
        tui.showScrollableText("Generated Code", result.generatedCode()).join(); // Wait for user to close the viewer

        // The "Next Step" message might be redundant if showScrollableText implies continuation,
        // or it can be kept if explicit pacing is desired.
        // For now, let's keep it to ensure the user acknowledges viewing.
        tui.showModalMessage("View Complete", "Press OK to proceed to the next step in the demo.");
        return true; // Continue the demo
    }
}
