package dumb.jaider.demo;

import dumb.jaider.ui.TUI;
import dumb.jaider.workflow.CodeGenerationWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class InteractiveDemoExecutor {
    private static final Logger logger = LoggerFactory.getLogger(InteractiveDemoExecutor.class);

    private final TUI tui;
    private final CodeGenerationWorkflow codeGenerationWorkflow;
    private final DemoContext demoContext;

    public InteractiveDemoExecutor(TUI tui, CodeGenerationWorkflow codeGenerationWorkflow) {
        this.tui = tui;
        this.codeGenerationWorkflow = codeGenerationWorkflow;
        this.demoContext = new DemoContext(); // Initialize a new context for each executor instance
    }

    public void runDemo(List<DemoStep> steps) {
        logger.info("Starting interactive demo with {} steps.", steps.size());
        // Potentially, set up the TUI for demo mode here
        // tui.showModalMessage("Starting Demo", "Welcome to the interactive demo!");

        for (int i = 0; i < steps.size(); i++) {
            DemoStep step = steps.get(i);
            logger.info("Executing demo step {} of {}: {}", i + 1, steps.size(), step.getClass().getSimpleName());
            try {
                // Inform TUI about the current step (optional, TUI could have a dedicated area for this)
                // tui.setDemoStatus("Running step: " + step.getClass().getSimpleName());

                boolean shouldContinue = step.execute(tui, codeGenerationWorkflow, demoContext);

                if (!shouldContinue) {
                    logger.info("Demo step {} indicated to stop the demo.", step.getClass().getSimpleName());
                    tui.showModalMessage("Demo Ended", "The demo was ended by the current step.");
                    break;
                }
            } catch (Exception e) {
                logger.error("Error executing demo step {}: {}", step.getClass().getSimpleName(), e.getMessage(), e);
                // Use TUI to show the error to the user
                boolean continueAfterError = tui.confirm("Error Encountered",
                                             "An error occurred: " + e.getMessage() + "\nDo you want to try to continue to the next step? (Not Recommended)")
                                             .join(); // Using .join() for simplicity in this context, consider async handling
                if (!continueAfterError) {
                    tui.showModalMessage("Demo Aborted", "Demo aborted due to error.");
                    break;
                }
            }
        }
        logger.info("Interactive demo finished.");
        // tui.showModalMessage("Demo Finished", "You have completed the interactive demo!");
        // Clean up TUI from demo mode if needed
        // tui.setDemoStatus("");
    }
}
