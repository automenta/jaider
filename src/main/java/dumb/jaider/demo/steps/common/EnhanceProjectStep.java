package dumb.jaider.demo.steps.common;

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
    private final String codeContextKey;
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
        logger.info("Executing EnhanceProjectStep to apply enhancement: \"{}\" on class {}.{}", enhancementDescription, packageName, className);

        String currentCode = (String) context.get(codeContextKey);
        if (currentCode == null || currentCode.isEmpty()) {
            logger.error("Current code ('{}') is null or empty in DemoContext. Cannot enhance project.", codeContextKey);
            tui.showModalMessage("Error: Missing Source Code",
                    "Cannot enhance the project because the source code (context key: '" + codeContextKey + "') to be enhanced was not found or is empty.").join();
            return false;
        }

        if (context.getProjectPath() == null) {
            logger.error("Project path is null in DemoContext. Cannot enhance project as it's needed for saving the changes.");
            tui.showModalMessage("Error: Missing Project Path",
                    "Cannot enhance the project because the project path is not set. This is required to save the enhanced file.").join();
            return false;
        }

        tui.showModalMessage("Code Enhancement",
                "Jaider will now attempt to enhance the class '" + className + "' based on the following instruction:\n" +
                "\"" + enhancementDescription + "\"\n\nThis may take a few moments...").join();

        CodeGenerationWorkflow.EnhanceProjectResult result = workflow.enhanceProject(
            currentCode,
            enhancementDescription,
            packageName,
            className,
            verificationKeywords
        );

        context.setCurrentCode(result.enhancedCode());
        context.setCurrentFilePath(result.javaFilePath());
        context.put(enhancedCodeContextKey, result.enhancedCode());
        context.setLastLLMResponse(result.enhancedCode());
        logger.info("Project enhancement complete for class {}.{}. New code length: {}", packageName, className, result.enhancedCode().length());

        tui.showModalMessage("Enhancement Complete",
                "The code enhancement task for '" + className + "' is complete. The class has been modified.\n" +
                "Click OK to view the new version of the code.").join();
        tui.showScrollableText("Enhanced Code: " + className, result.enhancedCode()).join();

        tui.showModalMessage("View Complete", "You have viewed the enhanced code.\nPress OK to proceed to the next step.").join();
        return true;
    }
}
