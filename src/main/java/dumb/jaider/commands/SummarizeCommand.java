package dumb.jaider.commands;

import dumb.jaider.app.AppContext;
import dumb.jaider.model.JaiderModel; // For AiMessage
import dev.langchain4j.data.message.AiMessage; // For AiMessage
import dev.langchain4j.model.chat.ChatLanguageModel; // To interact with the LLM

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SummarizeCommand implements Command {

    @Override
    public void execute(String args, AppContext appContext) {
        if (args == null || args.isBlank()) {
            appContext.getModel().addLog(AiMessage.from("[SummarizeCommand] Error: No file path or code snippet provided."));
            return;
        }

        String contentToSummarize;
        // Try to read as a file path first
        try {
            // Check if the argument might be a file path
            if (args.length() < 260 && Files.exists(Paths.get(args))) { // Basic check for path-like string
                contentToSummarize = new String(Files.readAllBytes(Paths.get(args)));
                appContext.getModel().addLog(AiMessage.from("[SummarizeCommand] Summarizing file: " + args));
            } else {
                // Treat as a direct code snippet
                contentToSummarize = args;
                appContext.getModel().addLog(AiMessage.from("[SummarizeCommand] Summarizing provided code snippet."));
            }
        } catch (IOException e) {
            appContext.getModel().addLog(AiMessage.from("[SummarizeCommand] Error reading file " + args + ": " + e.getMessage()));
            return;
        } catch (Exception e) {
            appContext.getModel().addLog(AiMessage.from("[SummarizeCommand] Error: " + e.getMessage()));
            return;
        }

        if (contentToSummarize.isBlank()) {
            appContext.getModel().addLog(AiMessage.from("[SummarizeCommand] Error: Content to summarize is empty."));
            return;
        }

        try {
            ChatLanguageModel chatModel = appContext.getApp().getConfig().getComponent("appChatLanguageModel", ChatLanguageModel.class);
            if (chatModel == null) {
                appContext.getModel().addLog(AiMessage.from("[SummarizeCommand] Error: ChatLanguageModel is not available."));
                return;
            }

            String prompt = "Summarize the following Java code snippet:

```java
" + contentToSummarize + "
```";
            String summary = chatModel.generate(prompt);

            appContext.getModel().addLog(AiMessage.from("[Summary]
" + summary));

        } catch (Exception e) {
            appContext.getModel().addLog(AiMessage.from("[SummarizeCommand] Error generating summary: " + e.getMessage()));
            e.printStackTrace(); // For more detailed error logging to console
        }
    }
}
