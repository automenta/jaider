package dumb.jaider.demo.steps.common; // Updated package

import dumb.jaider.core.DemoContext; // Assuming DemoContext is in core
import dumb.jaider.core.DemoStep;    // Assuming DemoStep is in core
import dumb.jaider.core.BuildManagerService; // Assuming BuildManagerService is in core
import dumb.jaider.ui.TUI;
import dumb.jaider.core.workflows.CodeGenerationWorkflow; // Assuming CodeGenerationWorkflow is in core.workflows
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Javadoc and class content from the original CompileProjectStep.java
// will be pasted here by me, with only the package declaration changed.
// (The rest of the file content is identical to what was read in the previous step)

public class CompileProjectStep implements DemoStep {
    private static final Logger logger = LoggerFactory.getLogger(CompileProjectStep.class);
    private final String stepName; // A descriptive name for this compilation instance, e.g., "Initial Project Compilation".

    /**
     * Constructs a CompileProjectStep.
     * This step demonstrates Jaider's ability to integrate with build systems (e.g., Maven)
     * to compile the project, which is a crucial part of the code generation and verification lifecycle.
     *
     * @param stepName A descriptive name for this compilation step, used in logging and TUI messages.
     */
    public CompileProjectStep(String stepName) {
        this.stepName = stepName;
    }

    /**
     * Executes the project compilation step.
     * It retrieves the project path from {@link DemoContext}, invokes the compilation process
     * via the {@link CodeGenerationWorkflow}, and then displays the results to the user.
     * It also allows the user to decide whether to continue the demo if compilation fails.
     *
     * Expects from DemoContext:
     *   - {@link DemoContext#getProjectPath()}: The path to the project to be compiled.
     * Puts into DemoContext:
     *   - "lastCompileResultSuccess_[stepName]" (Boolean): True if compilation succeeded, false otherwise.
     *   - "lastCompileResultOutput_[stepName]" (String): The output (stdout/stderr) from the compilation process.
     *
     * @param tui The TUI instance for user interaction.
     * @param workflow The CodeGenerationWorkflow to trigger project compilation.
     * @param context The DemoContext holding shared data, including the project path.
     * @return True if the demo should continue, false if the user opts to stop after a failure.
     * @throws Exception If any unrecoverable error occurs.
     */
    @Override
    public boolean execute(TUI tui, CodeGenerationWorkflow workflow, DemoContext context) throws Exception {
        logger.info("Executing CompileProjectStep: {}", stepName);

        // Ensure the project path is available in the context.
        if (context.getProjectPath() == null) {
            logger.error("Project path is null in DemoContext. Cannot compile project for step: {}", stepName);
            tui.showModalMessage("Error: Missing Project Path",
                    "Cannot compile the project because the project path is not set in the demo's context. This is needed to locate the pom.xml or build scripts.").join();
            return false; // Stop the demo if project path isn't set.
        }

        // Inform the user about the upcoming compilation.
        tui.showModalMessage("Project Compilation: " + stepName,
                "Jaider will now attempt to compile the project using Maven (or the configured build tool).\n" +
                "This step ('" + stepName + "') validates the generated/modified code.\n\nThis might take a moment...").join();

        // Trigger the compilation using the workflow.
        BuildManagerService.BuildResult compileResult = workflow.compileProject(stepName);

        // Store compilation results in DemoContext for potential later inspection or use by other steps.
        // Using stepName in keys allows results from multiple compile steps to be stored distinctly.
        context.put("lastCompileResultSuccess_" + stepName, compileResult.success());
        context.put("lastCompileResultOutput_" + stepName, compileResult.output());
        logger.info("Compilation for '{}' finished. Success: {}", stepName, compileResult.success());

        // Prepare and display the compilation result message.
        String resultTitle = "Compilation Result: " + stepName;
        String resultMessage = "Compilation " + (compileResult.success() ? "succeeded" : "failed") + " for step: " + stepName +
                               "\n\nBuild Tool Exit Code: " + compileResult.exitCode() +
                               "\n\nOutput Log:\n" +
                               "------------------------------------\n" +
                               compileResult.output() +
                               "\n------------------------------------";
        tui.showScrollableText(resultTitle, resultMessage).join();

        // If compilation failed, ask the user if they want to continue the demo.
        if (!compileResult.success()) {
            logger.warn("Compilation failed for step: {}. Asking user whether to continue.", stepName);
            boolean continueDemo = tui.confirm("Compilation Failed",
                    "Compilation for '" + stepName + "' failed. This may indicate issues with the generated or modified code.\n\n" +
                    "Do you want to continue with the rest of the demo?").join();
            if (!continueDemo) {
                logger.info("User chose to stop the demo after compilation failure for step: {}", stepName);
                return false; // Stop the demo as per user's choice.
            }
            logger.info("User chose to continue the demo despite compilation failure for step: {}", stepName);
        }
        return true; // Continue the demo.
    }
}
