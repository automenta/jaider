package dumb.jaider.demo.steps;

import dumb.jaider.demo.DemoContext;
import dumb.jaider.demo.DemoStep;
import dumb.jaider.ui.TUI;
import dumb.jaider.workflow.CodeGenerationWorkflow;

public class MessageStep implements DemoStep {
    private final String title;
    private final String message;

    public MessageStep(String title, String message) {
        this.title = title;
        this.message = message;
    }

    @Override
    public boolean execute(TUI tui, CodeGenerationWorkflow workflow, DemoContext context) throws Exception {
        // Use TUI's confirm dialog as a way to show a message and wait for user to continue.
        // The return value of confirm is used here, but for a pure message,
        // we might just care that the user acknowledged it.
        // If "No" is pressed, it effectively pauses and then continues the demo.
        tui.confirm(title, message).join(); // .join() to wait for user interaction
        return true; // Continue the demo
    }
}
