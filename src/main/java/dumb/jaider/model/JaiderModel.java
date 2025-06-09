package dumb.jaider.model;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage; // Added import
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class JaiderModel {
    public static final int LOG_CAPACITY = 200;

    public final Path projectDir = Paths.get("").toAbsolutePath();
    public final Set<Path> filesInContext = new HashSet<>();
    public final List<ChatMessage> logMessages = new ArrayList<>();
    public String statusBarText = "Jaider initialized. /help for commands.";
    public int currentTokenCount = 0;
    public EmbeddingStore<TextSegment> embeddingStore;
    public boolean isIndexed = false;
    public String lastAppliedDiff = null;
    public String agentMode = "Coder";

    public void addLog(ChatMessage message) {
        // Ensure message is an AiMessage before trying to get text.
        // UserMessage text is handled directly by its own text() method if needed.
        // This check avoids ClassCastException if a non-AiMessage type doesn't have a text() method.
        // However, the original code directly cast to AiMessage for text extraction.
        // Replicating original logic:
        if (message instanceof AiMessage) {
            var t = ((AiMessage) message).text();
            if (t == null || t.isBlank()) return;
        } else if (message instanceof dev.langchain4j.data.message.UserMessage) {
             var t = ((dev.langchain4j.data.message.UserMessage) message).text();
             if (t == null || t.isBlank()) return;
        }
        // If it's a different ChatMessage type without specific text extraction,
        // it might be added without this check, or a more generic text access method should be used.
        // For now, only add if text is non-blank, primarily for AiMessage as in original.
        // The original code had: var t = ((AiMessage) message).text();
        // This implies it only expected AiMessages or similar that have a .text() that could be blank.
        // Let's refine to handle UserMessages as well, as they are also logged.
        String textContent = "";
        if (message instanceof AiMessage) {
            textContent = ((AiMessage) message).text();
        } else if (message instanceof dev.langchain4j.data.message.UserMessage) {
            textContent = ((dev.langchain4j.data.message.UserMessage) message).text();
        }
        // Add any message that has text, or any message if textContent remains blank (e.g. special system messages without text)
        // The original code's `if (t == null || t.isBlank()) return;` was inside addLog(ChatMessage message)
        // and `t` was derived from `((AiMessage) message).text()`.
        // This means UserMessages were added unconditionally by `model.addLog(UserMessage.from(input));`
        // but AiMessages were filtered.
        // To keep original behavior: UserMessages are added directly by App.handleUserInput.
        // AiMessages are added by App.processAgentTurn via model.addLog.
        // So, this method should primarily filter AiMessages.
        if (message instanceof AiMessage) {
             if (textContent == null || textContent.isBlank()) return;
        }

        logMessages.add(message);
        if (logMessages.size() > LOG_CAPACITY) logMessages.removeFirst();
    }

    public String getFileContext() {
        return filesInContext.isEmpty() ?
            "No files are in context. Use /add or the `findRelevantCode` tool." :
            filesInContext.stream().map(this::readFileContent).collect(Collectors.joining("\n\n"));
    }

    public String readFileContent(Path path) {
        try {
            return String.format("--- %s ---\n%s", projectDir.relativize(path), Files.readString(path));
        } catch (IOException e) {
            return String.format("Error reading file %s: %s", path, e.getMessage());
        }
    }
}
