package dumb.jaider.demo;

import dumb.jaider.ui.TUI;
import dumb.jaider.workflow.CodeGenerationWorkflow;

@FunctionalInterface
public interface DemoStep {
    /**
     * Executes the demo step.
     * @param tui The TUI for user interaction.
     * @param workflow The CodeGenerationWorkflow for backend operations.
     * @param context The DemoContext for sharing state between steps.
     * @return true if the demo should continue, false to stop.
     * @throws Exception if an error occurs during step execution.
     */
    boolean execute(TUI tui, CodeGenerationWorkflow workflow, DemoContext context) throws Exception;
}
