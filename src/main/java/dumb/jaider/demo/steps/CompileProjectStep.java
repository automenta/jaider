package dumb.jaider.demo.steps;

import dumb.jaider.demo.DemoContext;
import dumb.jaider.demo.DemoStep;
import dumb.jaider.service.BuildManagerService;
import dumb.jaider.ui.TUI;
import dumb.jaider.workflow.CodeGenerationWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompileProjectStep implements DemoStep {
    private static final Logger logger = LoggerFactory.getLogger(CompileProjectStep.class);
    private final String stepName; // e.g., "Initial Project Compilation"

    public CompileProjectStep(String stepName) {
        this.stepName = stepName;
    }

    @Override
    public boolean execute(TUI tui, CodeGenerationWorkflow workflow, DemoContext context) throws Exception {
        logger.info("Executing CompileProjectStep: {}", stepName);

        if (context.getProjectPath() == null) {
            logger.error("Project path is null in DemoContext. Cannot compile.");
            tui.showModalMessage("Error", "Cannot compile: Project path is not set in the context.").join();
            return false; // Stop the demo if project path isn't set
        }

        tui.showModalMessage("Compilation", "About to compile the project for step: '" + stepName + "'.\nThis might take a moment...").join();

        BuildManagerService.BuildResult compileResult = workflow.compileProject(stepName);
        context.set("lastCompileResultSuccess_" + stepName, compileResult.success());
        context.set("lastCompileResultOutput_" + stepName, compileResult.output());

        String resultMessage = "Compilation " + (compileResult.success() ? "succeeded" : "failed") + " for: " + stepName +
                               "\nExit Code: " + compileResult.exitCode() +
                               "\n\nOutput:\n" + compileResult.output();

        tui.showScrollableText("Compilation Result: " + stepName, resultMessage).join();

        if (!compileResult.success()) {
            boolean continueDemo = tui.confirm("Compilation Failed", "Compilation failed. Do you want to continue the demo?").join();
            if (!continueDemo) {
                logger.warn("User chose to stop demo after compilation failure for step: {}", stepName);
                return false; // Stop the demo
            }
        }
        return true; // Continue the demo
    }
}
