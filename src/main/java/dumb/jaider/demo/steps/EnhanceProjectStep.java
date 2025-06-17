package dumb.jaider.demo.steps;

import dumb.jaider.demo.DemoContext;
import dumb.jaider.demo.DemoStep;
import dumb.jaider.ui.TUI;
import dumb.jaider.workflow.CodeGenerationWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnhanceProjectStep implements DemoStep {
    private static final Logger logger = LoggerFactory.getLogger(EnhanceProjectStep.class);

    private final String enhancementDescription;
    private final String packageName;
    private final String className;
    private final String[] verificationKeywords;
    private final String codeContextKey; // Key for the current code, e.g., "initialCode"
                                     // Key for storing enhanced code, e.g., "enhancedCode"
    private final String enhancedCodeContextKey;

    public EnhanceProjectStep(String enhancementDescription, String packageName, String className,
                              String codeContextKey, String enhancedCodeContextKey, String... verificationKeywords) {
        this.enhancementDescription = enhancementDescription;
        this.packageName = packageName;
        this.className = className;
        this.codeContextKey = codeContextKey;
        this.enhancedCodeContextKey = enhancedCodeContextKey;
        this.verificationKeywords = verificationKeywords;
    }

    @Override
    public boolean execute(TUI tui, CodeGenerationWorkflow workflow, DemoContext context) throws Exception {
        logger.info("Executing EnhanceProjectStep: {}", enhancementDescription);

        String currentCode = context.get(codeContextKey);
        if (currentCode == null || currentCode.isEmpty()) {
            logger.error("Current code ('{}') is null or empty in DemoContext. Cannot enhance.", codeContextKey);
            tui.showModalMessage("Error", "Cannot enhance project: Current code ('" + codeContextKey + "') is not set or empty.").join();
            return false; // Stop or handle error
        }

        if (context.getProjectPath() == null) {
            logger.error("Project path is null in DemoContext. Cannot enhance (for saving).");
            tui.showModalMessage("Error", "Cannot enhance project: Project path is not set.").join();
            return false;
        }

        tui.showModalMessage("Code Enhancement", "About to enhance the project with: '" + enhancementDescription + "'.\nThis might take a moment...").join();

        CodeGenerationWorkflow.EnhanceProjectResult result = workflow.enhanceProject(
            currentCode,
            enhancementDescription,
            packageName,
            className,
            verificationKeywords
        );

        // Update context
        context.setCurrentCode(result.enhancedCode()); // Keep this for general "current code" access
        context.setCurrentFilePath(result.javaFilePath()); // Path might not change if overwriting
        context.set(enhancedCodeContextKey, result.enhancedCode()); // Store with specific key
        context.setLastLLMResponse(result.enhancedCode()); // The enhanced code is the LLM's "response" here

        logger.info("Project enhanced. New code length: {}", result.enhancedCode().length());

        // Display the enhanced code
        // For now, we show the full new code. A diff view would be an improvement later.
        tui.showModalMessage("Enhancement Complete", "Code enhancement is complete. Click OK to view the new code.").join();
        tui.showScrollableText("Enhanced Code: " + className, result.enhancedCode()).join();

        tui.showModalMessage("View Complete", "Press OK to proceed to the next step in the demo.").join();
        return true; // Continue the demo
    }
}
