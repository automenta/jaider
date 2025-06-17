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

    public final Path dir;

    /** active files in context */
    public final Set<Path> files = new HashSet<>();

    public final List<ChatMessage> log = new ArrayList<>();

    public String statusBarText = "Jaider initialized. /help for commands.";
    public int currentTokenCount = 0;

    public EmbeddingStore<TextSegment> embeddings;

    public boolean isIndexed = false;
    public String lastAppliedDiff = null;
    public String mode = "Coder";

    private String[] originalArgs;
    private List<dumb.jaider.suggestion.ActiveSuggestion> activeSuggestions = new ArrayList<>(); // Changed for actionable suggestions

    public JaiderModel() {
        this.dir = Paths.get("").toAbsolutePath();
    }

    public JaiderModel(Path dir) {
        this.dir = dir;
    }

    public void addLog(ChatMessage message) {
        if (message instanceof AiMessage) {
            var t = ((AiMessage) message).text();
            if (t == null || t.isBlank()) return;
        } else if (message instanceof UserMessage) {
             var t = ((UserMessage) message).singleText();
             if (t == null || t.isBlank()) return;
        }

        var textContent = "";
        if (message instanceof AiMessage) {
            textContent = ((AiMessage) message).text();
        } else if (message instanceof UserMessage) {
            textContent = ((UserMessage) message).singleText();
        }

        if (message instanceof AiMessage) {
             if (textContent == null || textContent.isBlank()) return;
        }

        log.add(message);
        if (log.size() > LOG_CAPACITY) log.removeFirst();
    }

    public String getFileContext() {
        return files.isEmpty() ?
            "No files are in context. Use /add or the `findRelevantCode` tool." :
                files.stream().map(this::readFileContent).collect(Collectors.joining("\n\n"));
    }

    public String readFileContent(Path path) {
        try {
            return String.format("--- %s ---\n%s", dir.relativize(path), Files.readString(path));
        } catch (IOException e) {
            return String.format("Error reading file %s: %s", path, e.getMessage());
        }
    }

    public String[] getOriginalArgs() {
        return originalArgs;
    }

    public void setOriginalArgs(String[] originalArgs) {
        this.originalArgs = originalArgs;
    }

    public List<dumb.jaider.suggestion.ActiveSuggestion> getActiveSuggestions() {
        return activeSuggestions;
    }

    public void setActiveSuggestions(List<dumb.jaider.suggestion.ActiveSuggestion> activeSuggestions) {
        this.activeSuggestions = activeSuggestions;
    }

    public void clearActiveSuggestions() {
        if (this.activeSuggestions != null) {
            this.activeSuggestions.clear();
        }
    }

    public com.google.common.collect.ImmutableList<String> getContextFilePaths() {
        if (files.isEmpty()) {
            return com.google.common.collect.ImmutableList.of();
        }
        return files.stream()
                .map(path -> dir.relativize(path).toString())
                .collect(com.google.common.collect.ImmutableList.toImmutableList());
    }

    public Path getDir() { // Changed return type to Path
        return this.dir;
    }

    // Removed getFiles() method as AddCommand will use the public field model.files directly
}
