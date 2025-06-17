package dumb.jaider.commands;

import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dumb.jaider.app.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class IndexCommand implements Command {
    private static final Logger logger = LoggerFactory.getLogger(IndexCommand.class);

    @Override
    public void execute(String args, AppContext context) {
        var m = context.model();
        if (m.isIndexed) {
            m.addLog(AiMessage.from("[Jaider] Project is already indexed."));
            return;
        }
        if (context.app().getEmbeddingModel() == null) {
            m.addLog(AiMessage.from("[Jaider] Embedding model not available. Cannot index. Please configure a provider that supports embeddings (e.g. OpenAI)."));
            return;
        }

        context.app().setStatePublic(App.State.AGENT_THINKING); // Needs to be public or called via a public method in App
        m.statusBarText = "Indexing project...";
        context.ui().redraw(m);

        CompletableFuture.runAsync(() -> {
            try {
                var rootDir = m.dir; // Store rootDir
                var documents = FileSystemDocumentLoader.loadDocuments(
                    rootDir, // Use rootDir here
                    (Path relativePath) -> {
                        // Path objects from FileSystemDocumentLoader's pathMatcher might be relative to the rootDir.
                        // Resolve them to ensure Files.isRegularFile and Files.size work correctly.
                        var absolutePath = rootDir.resolve(relativePath);
                        try {
                            // It's good practice to log which path is being evaluated by the matcher if issues persist.
                            // System.out.println("Predicate evaluating: " + absolutePath);
                            return !absolutePath.toString().contains(".git") &&
                                   Files.isRegularFile(absolutePath) &&
                                   Files.size(absolutePath) > 0;
                        } catch (IOException e) {
                            // System.err.println("Predicate IOException for " + absolutePath + ": " + e.getMessage());
                            return false; // Exclude path if attributes can't be read or path is problematic
                        }
                    }
                );

                if (documents.isEmpty()) {
                    m.isIndexed = true;
                    context.app().finishTurnPublic(AiMessage.from("[Jaider] Project successfully indexed with 0 documents. No content found to index."));
                    return;
                }

                var segments = DocumentSplitters.recursive(500, 100).splitAll(documents);

                if (segments.isEmpty()) { // Should ideally not be reached if documents were loaded, but as a safeguard
                    m.isIndexed = true;
                    context.app().finishTurnPublic(AiMessage.from("[Jaider] Project successfully indexed with 0 segments (from non-empty documents). No textual content found to index."));
                    return;
                }

                m.embeddings = new InMemoryEmbeddingStore<>();
                var embeddingResponse = context.app().getEmbeddingModel().embedAll(segments);
                if (embeddingResponse == null) {
                    context.app().finishTurnPublic(AiMessage.from("[Error] Failed to index project: EmbeddingModel returned null or empty embeddings."));
                    return;
                }
                var embeddings = embeddingResponse.content();
                m.embeddings.addAll(embeddings, segments);
                m.isIndexed = true;
                context.app().finishTurnPublic(AiMessage.from("[Jaider] Project successfully indexed with " + segments.size() + " segments."));
            } catch (Exception e) {
                logger.error("Error during indexing for path '{}': {}", args, e.getMessage(), e); // Added SLF4J logging
                // Log the full stack trace for better debugging on the server/log file
                // Consider also sending a more user-friendly part of the message to the UI
                e.printStackTrace(); // Good for server logs
                var userFriendlyMessage = e.getMessage();
                if (userFriendlyMessage == null || userFriendlyMessage.isBlank()) {
                    userFriendlyMessage = e.getClass().getSimpleName();
                }
                context.app().finishTurnPublic(AiMessage.from("[Error] Failed to index project: " + userFriendlyMessage));
            }
        });
    }
}
