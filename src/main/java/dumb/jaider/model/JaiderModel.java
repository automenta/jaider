package dumb.jaider.model;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
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

    public final Path projectDir;
    public final Set<Path> filesInContext = new HashSet<>();
    public final List<ChatMessage> logMessages = new ArrayList<>();
    public String statusBarText = "Jaider initialized. /help for commands.";
    public int currentTokenCount = 0;
    public EmbeddingStore<TextSegment> embeddingStore;
    public boolean isIndexed = false;
    public String lastAppliedDiff = null;
    public String agentMode = "Coder";

    public JaiderModel() {
        this.projectDir = Paths.get("").toAbsolutePath();
    }

    public JaiderModel(Path projectDir) {
        this.projectDir = projectDir;
    }

    public void addLog(ChatMessage message) {
        if (message instanceof AiMessage) {
            var t = ((AiMessage) message).text();
            if (t == null || t.isBlank()) return;
        } else if (message instanceof UserMessage) {
             var t = ((UserMessage) message).singleText();
             if (t == null || t.isBlank()) return;
        }

        String textContent = "";
        if (message instanceof AiMessage) {
            textContent = ((AiMessage) message).text();
        } else if (message instanceof UserMessage) {
            textContent = ((UserMessage) message).singleText();
        }

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
