package dumb.jaider.demo.steps.common; // Updated package

import dumb.jaider.core.DemoContext; // Assuming DemoContext is in core
import dumb.jaider.core.DemoStep;    // Assuming DemoStep is in core
import dumb.jaider.ui.TUI;
import dumb.jaider.core.workflows.CodeGenerationWorkflow; // Assuming CodeGenerationWorkflow is in core.workflows
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Javadoc and class content from the original AskQuestionStep.java
// will be pasted here by me, with only the package declaration changed.
// (The rest of the file content is identical to what was read in the previous step)

public class AskQuestionStep implements DemoStep {
    private static final Logger logger = LoggerFactory.getLogger(AskQuestionStep.class);

    private final String question;
    private final String[] verificationKeywords; // Keywords to check for in the LLM's answer for basic validation.
    private final String codeContextKey; // Key in DemoContext to retrieve the code relevant to the question.

    /**
     * Constructs an AskQuestionStep.
     * This step demonstrates Jaider's ability to use a Language Model to answer questions about a given code context.
     *
     * @param question The question to ask the Language Model.
     * @param codeContextKey The key in {@link DemoContext} where the relevant code for the question is stored (e.g., "initialCode", "enhancedCode").
     * @param verificationKeywords Optional keywords to verify in the Language Model's answer.
     */
    public AskQuestionStep(String question, String codeContextKey, String... verificationKeywords) {
        this.question = question;
        this.codeContextKey = codeContextKey;
        this.verificationKeywords = verificationKeywords;
    }

    /**
     * Executes the ask question step.
     * Retrieves specified code from {@link DemoContext}, asks the configured question to the Language Model via
     * the {@link CodeGenerationWorkflow}, and displays the answer.
     *
     * @param tui The TUI instance for user interaction.
     * @param workflow The CodeGenerationWorkflow to interact with the Language Model.
     * @param context The DemoContext holding shared data.
     * @return True if the step executed successfully, false otherwise.
     * @throws Exception If any error occurs during execution.
     */
    @Override
    public boolean execute(TUI tui, CodeGenerationWorkflow workflow, DemoContext context) throws Exception {
        logger.info("Executing AskQuestionStep for question: \"{}\" against code from context key: {}", question, codeContextKey);

        // Retrieve the code that the question is about from DemoContext.
        String codeContextValue = (String) context.get(codeContextKey); // Cast to String
        if (codeContextValue == null || codeContextValue.isEmpty()) {
            logger.error("Code context '{}' is null or empty in DemoContext. Cannot ask question.", codeContextKey);
            tui.showModalMessage("Error: Missing Code Context",
                    "Cannot ask the question because the relevant code (context key: '" + codeContextKey + "') was not found or is empty in the demo's context.").join();
            return false; // Stop if the necessary code context is missing.
        }

        // Inform the user what is about to happen.
        tui.showModalMessage("Ask Language Model",
                "Jaider will now ask the Language Model the following question about the code previously stored as '" + codeContextKey + "':\n\n" +
                "\"" + question + "\"\n\nThis may take a few moments.").join();

        // Use the workflow to ask the question about the specified code.
        CodeGenerationWorkflow.AskQuestionResult result = workflow.askQuestionAboutCode(codeContextValue, question, verificationKeywords);

        // Store the LLM's raw answer in DemoContext for potential later use or inspection.
        context.setLastLLMResponse(result.answer()); // Assuming DemoContext has this method
        // Store the answer with a more specific, question-related key.
        String answerKey = "answer_to_question_" + question.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("_+$", "");
        context.put(answerKey, result.answer());
        logger.info("Stored answer for question \"{}\" in context with key: {}", question, answerKey);

        // Prepare and display the question and the Language Model's answer.
        String displayMessage = "Question Asked to Language Model:\n" +
                                "---------------------------------\n" +
                                question +
                                "\n\nLanguage Model's Answer:\n" +
                                "--------------------------\n" +
                                result.answer();

        tui.showScrollableText("Language Model Q&A Result", displayMessage).join();

        return true; // Indicate successful execution of this step.
    }
}
