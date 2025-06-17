package dumb.jaider.demo.steps;

import dumb.jaider.demo.DemoContext;
import dumb.jaider.demo.DemoStep;
import dumb.jaider.ui.TUI;
import dumb.jaider.workflow.CodeGenerationWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AskQuestionStep implements DemoStep {
    private static final Logger logger = LoggerFactory.getLogger(AskQuestionStep.class);

    private final String question;
    private final String[] verificationKeywords;
    private final String codeContextKey; // Key to get code from DemoContext, e.g., "initialCode" or "enhancedCode"

    public AskQuestionStep(String question, String codeContextKey, String... verificationKeywords) {
        this.question = question;
        this.codeContextKey = codeContextKey;
        this.verificationKeywords = verificationKeywords;
    }

    @Override
    public boolean execute(TUI tui, CodeGenerationWorkflow workflow, DemoContext context) throws Exception {
        logger.info("Executing AskQuestionStep: {}", question);

        String codeContextValue = context.get(codeContextKey);
        if (codeContextValue == null || codeContextValue.isEmpty()) {
            logger.error("Code context '{}' is null or empty in DemoContext.", codeContextKey);
            tui.showModalMessage("Error", "Cannot ask question: Code context ('" + codeContextKey + "') is not set or empty.").join();
            return false; // Stop or handle error
        }

        // It's also possible to use context.getCurrentCode() if that's always the desired target.
        // Using a key provides more flexibility.

        tui.showModalMessage("Ask Question", "About to ask the LLM the following question:\n'" + question + "'\nThis might take a moment...").join();

        CodeGenerationWorkflow.AskQuestionResult result = workflow.askQuestionAboutCode(codeContextValue, question, verificationKeywords);
        context.setLastLLMResponse(result.answer());
        // Optionally store with a more specific key as well
        context.set("answer_to_" + question.replaceAll("[^a-zA-Z0-9]", "_"), result.answer());


        String displayMessage = "Question Asked:\n" + question +
                                "\n\nLLM's Answer:\n" + result.answer();

        tui.showScrollableText("LLM Q&A", displayMessage).join();

        return true; // Continue the demo
    }
}
