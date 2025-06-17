package dumb.jaider.demo.steps.common;

import dumb.jaider.core.DemoContext;
import dumb.jaider.core.DemoStep;
import dumb.jaider.core.workflows.CodeGenerationWorkflow;
import dumb.jaider.ui.TUI;

/**
 * A generic DemoStep implementation that displays a message to the user in a modal dialog.
 * This step is useful for providing information, welcomes, or farewells within a demo sequence.
 * It can be reused across different demos for consistent message presentation.
 */
public class DisplayMessageStep implements DemoStep {
    private final String title;
    private final String message;

    /**
     * Constructs a DisplayMessageStep.
     * @param title The title of the modal dialog.
     * @param message The message content to display.
     */
    public DisplayMessageStep(String title, String message) {
        this.title = title;
        this.message = message;
    }

    /**
     * Executes the step by showing a modal message to the user via the TUI.
     * This step always returns true, allowing the demo sequence to continue.
     *
     * @param tui The TUI instance for displaying the message.
     * @param workflow The CodeGenerationWorkflow (not actively used by this step but part of the interface).
     * @param context The DemoContext (not actively used by this step but part of the interface).
     * @return Always true.
     * @throws Exception If any error occurs during TUI interaction (though unlikely for showModalMessage).
     */
    @Override
    public boolean execute(TUI tui, CodeGenerationWorkflow workflow, DemoContext context) throws Exception {
        // Display the message to the user using the TUI.
        tui.showModalMessage(title, message);
        // This step always succeeds and allows the demo to continue.
        return true;
    }
}
