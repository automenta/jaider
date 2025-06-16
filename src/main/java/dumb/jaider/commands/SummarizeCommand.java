package dumb.jaider.commands;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel; // Changed from ChatLanguageModel

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SummarizeCommand implements Command {

    @Override
    public void execute(String args, AppContext appContext) {
        if (args == null || args.isBlank()) {
            appContext.model().addLog(AiMessage.from("[SummarizeCommand] Error: No file path or code snippet provided.")); // Corrected
            return;
        }

        String contentToSummarize;
        // Try to read as a file path first
        try {
            // Check if the argument might be a file path
            if (args.length() < 260 && Files.exists(Paths.get(args))) { // Basic check for path-like string
                contentToSummarize = new String(Files.readAllBytes(Paths.get(args)));
                appContext.model().addLog(AiMessage.from("[SummarizeCommand] Summarizing file: " + args)); // Corrected
            } else {
                // Treat as a direct code snippet
                contentToSummarize = args;
                appContext.model().addLog(AiMessage.from("[SummarizeCommand] Summarizing provided code snippet.")); // Corrected
            }
        } catch (IOException e) {
            appContext.model().addLog(AiMessage.from("[SummarizeCommand] Error reading file " + args + ": " + e.getMessage())); // Corrected
            return;
        } catch (Exception e) {
            appContext.model().addLog(AiMessage.from("[SummarizeCommand] Error: " + e.getMessage())); // Corrected
            return;
        }

        if (contentToSummarize.isBlank()) {
            appContext.model().addLog(AiMessage.from("[SummarizeCommand] Error: Content to summarize is empty.")); // Corrected
            return;
        }

        try {
            ChatModel chatModel = appContext.app().getConfig().getComponent("appChatLanguageModel", ChatModel.class); // Changed from ChatLanguageModel
            if (chatModel == null) {
                appContext.model().addLog(AiMessage.from("[SummarizeCommand] Error: ChatLanguageModel is not available.")); // Corrected
                return;
            }

            String prompt = "Summarize the following Java code snippet:\n\n```java\n" + contentToSummarize + "\n```";
            // String summary = chatModel.generate(prompt); // Commented out

            // appContext.model().addLog(AiMessage.from("[Summary]\n" + summary)); // Commented out
            appContext.model().addLog(AiMessage.from("[SummarizeCommand] Summarization is temporarily disabled."));


        } catch (Exception e) {
            appContext.model().addLog(AiMessage.from("[SummarizeCommand] Error generating summary: " + e.getMessage())); // Corrected
            e.printStackTrace(); // For more detailed error logging to console
        }
    }
}
