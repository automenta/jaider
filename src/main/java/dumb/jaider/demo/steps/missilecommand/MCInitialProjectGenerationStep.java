package dumb.jaider.demo.steps.missilecommand; // Updated package

import dumb.jaider.core.DemoContext; // Assuming DemoContext is in core
import dumb.jaider.core.DemoStep;    // Assuming DemoStep is in core
import dumb.jaider.ui.TUI;
import dumb.jaider.core.workflows.CodeGenerationWorkflow; // Assuming CodeGenerationWorkflow is in core.workflows
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// No CompletableFuture needed here apparently, but Path is.
import java.nio.file.Path; // Ensure Path is imported if DemoContext uses it directly

// Renamed class
public class MCInitialProjectGenerationStep implements DemoStep {
    private static final Logger logger = LoggerFactory.getLogger(MCInitialProjectGenerationStep.class); // Updated logger

    private final String description;          // Natural language description of the project.
    private final String packageName;          // Target package name for the main class.
    private final String className;            // Target class name for the main class.
    private final String pomContent;           // Content of the pom.xml file for the project.
    private final String[] verificationKeywords; // Keywords to verify in the generated code.

    /**
     * Constructs an MCInitialProjectGenerationStep.
     * This step demonstrates Jaider's capability to bootstrap a new project from scratch,
     * specifically for the Missile Command demo, including generating a main code file and a pom.xml.
     *
     * @param description A natural language description of the project to be generated.
     * @param packageName The package name for the main class of the project.
     * @param className The name for the main class of the project.
     * @param pomContent The full content of the pom.xml (or other build file) for the project.
     * @param verificationKeywords Optional keywords to verify in the generated code.
     */
    public MCInitialProjectGenerationStep(String description, String packageName, String className, String pomContent, String... verificationKeywords) {
        this.description = description;
        this.packageName = packageName;
        this.className = className;
        this.pomContent = pomContent;
        this.verificationKeywords = verificationKeywords;
    }

    /**
     * Executes the initial project generation step for Missile Command.
     * It informs the user, then invokes the project generation capability of the {@link CodeGenerationWorkflow}.
     * The results, including the generated code and file paths, are stored in {@link DemoContext}.
     * The generated code is then displayed to the user.
     *
     * Puts into DemoContext:
     *   - {@link DemoContext#setCurrentCode(String)}: The generated source code of the main class.
     *   - {@link DemoContext#setProjectPath(Path)}: The path to the root of the generated project.
     *   - {@link DemoContext#setCurrentFilePath(Path)}: The path to the generated main source file.
     *   - "initialCode" (String): The generated source code, also stored under this specific key for clarity.
     *
     * @param tui The TUI instance for user interaction.
     * @param workflow The CodeGenerationWorkflow to perform project generation.
     * @param context The DemoContext to store results.
     * @return True, indicating the demo should continue.
     * @throws Exception If any error occurs during execution.
     */
    @Override
    public boolean execute(TUI tui, CodeGenerationWorkflow workflow, DemoContext context) throws Exception {
        logger.info("Executing MCInitialProjectGenerationStep for project description: \"{}\"", description);

        // Inform the user about the upcoming code generation process.
        tui.showModalMessage("Initial Missile Command Generation",
                "Jaider will now attempt to generate the initial Missile Command game structure and code based on the following description:\n" +
                "\"" + description + "\"\n\nThis includes generating the main game class ('" + className + "') " +
                "in package '" + packageName + "' and a pom.xml file.\n\nThis might take a moment...").join();

        // Execute the project generation task via the workflow.
        CodeGenerationWorkflow.ProjectGenerationResult result = workflow.generateInitialProject(
            description, packageName, className, pomContent, verificationKeywords
        );

        // Store the results of the generation in the DemoContext.
        context.setCurrentCode(result.generatedCode());      // The code of the main class.
        context.setProjectPath(result.projectPath());        // Root directory of the generated project.
        context.setCurrentFilePath(result.javaFilePath());   // Path to the main class file.
        context.put("initialCode", result.generatedCode()); // Store specifically as "initialCode" for this demo.
        logger.info("Initial Missile Command project generated successfully. Main class code length: {}, Project Path: {}",
                result.generatedCode().length(), result.projectPath());

        // Inform the user that code generation is complete and show the generated code.
        tui.showModalMessage("Missile Command Code Generated",
                "The initial code for '" + className + "' has been generated by the Language Model.\n" +
                "Click OK to view the content of the main game file.").join();

        // Display the generated code in a scrollable text view.
        tui.showScrollableText("Generated Code: " + className, result.generatedCode()).join(); // Wait for user to close the viewer

        // Confirm viewing before proceeding.
        tui.showModalMessage("View Complete",
                "You have viewed the initially generated Missile Command code.\nPress OK to proceed to the next step (e.g., compilation).").join();
        return true; // Indicate successful execution and continue the demo.
    }
}
