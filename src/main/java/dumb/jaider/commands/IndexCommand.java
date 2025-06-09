package dumb.jaider.commands;

import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.util.concurrent.CompletableFuture;
import dumb.jaider.app.App; // For App.State
import java.nio.file.Path;
import java.nio.file.Files; // Added import for Files
import java.io.IOException; // Added import for IOException

public class IndexCommand implements Command {
    @Override
    public void execute(String args, AppContext context) {
        if (context.getModel().isIndexed) {
            context.getModel().addLog(AiMessage.from("[Jaider] Project is already indexed."));
            return;
        }
        if (context.getAppInstance().getEmbeddingModel() == null) {
            context.getModel().addLog(AiMessage.from("[Jaider] Embedding model not available. Cannot index. Please configure a provider that supports embeddings (e.g. OpenAI)."));
            return;
        }

        context.getAppInstance().setStatePublic(App.State.AGENT_THINKING); // Needs to be public or called via a public method in App
        context.getModel().statusBarText = "Indexing project...";
        context.getUi().redraw(context.getModel());

        CompletableFuture.runAsync(() -> {
            try {
                Path rootDir = context.getModel().projectDir; // Store rootDir
                var documents = FileSystemDocumentLoader.loadDocuments(
                    rootDir, // Use rootDir here
                    (Path relativePath) -> {
                        // Path objects from FileSystemDocumentLoader's pathMatcher might be relative to the rootDir.
                        // Resolve them to ensure Files.isRegularFile and Files.size work correctly.
                        Path absolutePath = rootDir.resolve(relativePath);
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
                    context.getModel().isIndexed = true;
                    context.getAppInstance().finishTurnPublic(AiMessage.from("[Jaider] Project successfully indexed with 0 documents. No content found to index."));
                    return;
                }

                var splitter = DocumentSplitters.recursive(500, 100);
                var segments = splitter.splitAll(documents);

                if (segments.isEmpty()) { // Should ideally not be reached if documents were loaded, but as a safeguard
                    context.getModel().isIndexed = true;
                    context.getAppInstance().finishTurnPublic(AiMessage.from("[Jaider] Project successfully indexed with 0 segments (from non-empty documents). No textual content found to index."));
                    return;
                }

                context.getModel().embeddingStore = new InMemoryEmbeddingStore<>();
                var embeddingResponse = context.getAppInstance().getEmbeddingModel().embedAll(segments);
                if (embeddingResponse == null || embeddingResponse.content() == null) {
                    context.getAppInstance().finishTurnPublic(AiMessage.from("[Error] Failed to index project: EmbeddingModel returned null or empty embeddings."));
                    return;
                }
                var embeddings = embeddingResponse.content();
                context.getModel().embeddingStore.addAll(embeddings, segments);
                context.getModel().isIndexed = true;
                context.getAppInstance().finishTurnPublic(AiMessage.from("[Jaider] Project successfully indexed with " + segments.size() + " segments."));
            } catch (Exception e) {
                // Log the full stack trace for better debugging on the server/log file
                // Consider also sending a more user-friendly part of the message to the UI
                e.printStackTrace(); // Good for server logs
                String userFriendlyMessage = e.getMessage();
                if (userFriendlyMessage == null || userFriendlyMessage.isBlank()) {
                    userFriendlyMessage = e.getClass().getSimpleName();
                }
                context.getAppInstance().finishTurnPublic(AiMessage.from("[Error] Failed to index project: " + userFriendlyMessage));
            }
        });
    }
}
