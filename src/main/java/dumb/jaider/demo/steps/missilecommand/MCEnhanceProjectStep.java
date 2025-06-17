package dumb.jaider.demo.steps.missilecommand; // Updated package

import dumb.jaider.core.DemoContext; // Assuming DemoContext is in core
import dumb.jaider.core.DemoStep;    // Assuming DemoStep is in core
import dumb.jaider.ui.TUI;
import dumb.jaider.core.workflows.CodeGenerationWorkflow; // Assuming CodeGenerationWorkflow is in core.workflows
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path; // Ensure Path is imported if DemoContext uses it

// Renamed class
public class MCEnhanceProjectStep implements DemoStep {
    private static final Logger logger = LoggerFactory.getLogger(MCEnhanceProjectStep.class); // Updated logger

    private final String enhancementDescription; // Describes what changes to make.
    private final String packageName;            // The package of the class to be enhanced.
    private final String className;              // The name of the class to be enhanced.
    private final String[] verificationKeywords; // Keywords to check for in the enhanced code.
    private final String codeContextKey;         // Key in DemoContext to get the current code to be enhanced (e.g., "initialCode").
    private final String enhancedCodeContextKey; // Key in DemoContext to store the resulting enhanced code (e.g., "enhancedCode").

    /**
     * Constructs an MCEnhanceProjectStep.
     * This step demonstrates Jaider's capability to modify or add to existing code
     * based on a natural language description of the desired enhancements, specifically for the Missile Command demo.
     *
     * @param enhancementDescription A natural language description of the changes to make.
     * @param packageName The package name of the class to enhance.
     * @param className The name of the class to enhance.
     * @param codeContextKey The key in {@link DemoContext} for retrieving the current source code.
     * @param enhancedCodeContextKey The key in {@link DemoContext} for storing the enhanced source code.
     * @param verificationKeywords Optional keywords to verify in the enhanced code.
     */
    public MCEnhanceProjectStep(String enhancementDescription, String packageName, String className,
                              String codeContextKey, String enhancedCodeContextKey, String... verificationKeywords) {
        this.enhancementDescription = enhancementDescription;
        this.packageName = packageName;
        this.className = className;
        this.codeContextKey = codeContextKey;
        this.enhancedCodeContextKey = enhancedCodeContextKey;
        this.verificationKeywords = verificationKeywords;
    }

    /**
     * Executes the project enhancement step for Missile Command.
     * Retrieves the current code from {@link DemoContext}, sends it along with the enhancement description
     * to the Language Model via the {@link CodeGenerationWorkflow}, and then updates the context with the
     * enhanced code. The enhanced code is also displayed to the user.
     *
     * @param tui The TUI instance for user interaction.
     * @param workflow The CodeGenerationWorkflow to perform the code enhancement.
     * @param context The DemoContext holding shared data.
     * @return True if the step executed successfully, false otherwise.
     * @throws Exception If any error occurs during execution.
     */
    @Override
    public boolean execute(TUI tui, CodeGenerationWorkflow workflow, DemoContext context) throws Exception {
        logger.info("Executing MCEnhanceProjectStep to apply enhancement: \"{}\" on class {}.{}", enhancementDescription, packageName, className);

        // Retrieve the current code to be enhanced from DemoContext.
        String currentCode = (String) context.get(codeContextKey); // Cast to String
        if (currentCode == null || currentCode.isEmpty()) {
            logger.error("Current code ('{}') is null or empty in DemoContext. Cannot enhance project.", codeContextKey);
            tui.showModalMessage("Error: Missing Source Code",
                    "Cannot enhance the project because the source code (context key: '" + codeContextKey + "') to be enhanced was not found or is empty.").join();
            return false; // Stop if the base code is missing.
        }

        // Ensure project path is set, as enhancement implies saving the modified file eventually.
        if (context.getProjectPath() == null) {
            logger.error("Project path is null in DemoContext. Cannot enhance project as it's needed for saving the changes.");
            tui.showModalMessage("Error: Missing Project Path",
                    "Cannot enhance the project because the project path is not set. This is required to save the enhanced file.").join();
            return false;
        }

        // Inform the user about the enhancement process.
        tui.showModalMessage("Missile Command Code Enhancement",
                "Jaider will now attempt to enhance the class '" + className + "' based on the following instruction:\n" +
                "\"" + enhancementDescription + "\"\n\nThis may take a few moments...").join();

        // Perform the enhancement using the workflow.
        CodeGenerationWorkflow.EnhanceProjectResult result = workflow.enhanceProject(
            currentCode,
            enhancementDescription,
            packageName,
            className,
            verificationKeywords
        );

        // Update DemoContext with the results of the enhancement.
        context.setCurrentCode(result.enhancedCode());          // Set as the overall "current" code.
        context.setCurrentFilePath(result.javaFilePath());      // Update current file path, though it might be the same if overwriting.
        context.put(enhancedCodeContextKey, result.enhancedCode()); // Store specifically under the "enhancedCode" key.
        context.setLastLLMResponse(result.enhancedCode());      // The enhanced code is considered the LLM's direct response for this step.
        logger.info("Missile Command project enhancement complete for class {}.{}. New code length: {}", packageName, className, result.enhancedCode().length());

        // Display the enhanced code to the user.
        tui.showModalMessage("Enhancement Complete",
                "The code enhancement task for Missile Command is complete. The class '" + className + "' has been modified.\n" +
                "Click OK to view the new version of the code.").join();
        tui.showScrollableText("Enhanced Code: " + className, result.enhancedCode()).join();

        // Confirm viewing before proceeding.
        tui.showModalMessage("View Complete", "You have viewed the enhanced Missile Command code.\nPress OK to proceed to the next step in the demo.").join();
        return true; // Indicate successful execution.
    }
}
