package dumb.jaider.commands;

import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.util.concurrent.CompletableFuture;
import dumb.jaider.app.App; // For App.State
import java.nio.file.Path; // Added for clarity, though might be implicitly available

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
                var documents = FileSystemDocumentLoader.loadDocuments(
                    context.getModel().projectDir,
                    (Path path) -> !path.toString().contains(".git") // Basic gitignore
                );
                var splitter = DocumentSplitters.recursive(500, 100);
                var segments = splitter.splitAll(documents);
                context.getModel().embeddingStore = new InMemoryEmbeddingStore<>(); // Ensure this is thread-safe if needed, or handle access carefully
                var embeddings = context.getAppInstance().getEmbeddingModel().embedAll(segments).content();
                context.getModel().embeddingStore.addAll(embeddings, segments);
                context.getModel().isIndexed = true;
                context.getAppInstance().finishTurnPublic(AiMessage.from("[Jaider] Project successfully indexed with " + segments.size() + " segments.")); // Needs to be public
            } catch (Exception e) {
                context.getAppInstance().finishTurnPublic(AiMessage.from("[Error] Failed to index project: " + e.getMessage())); // Needs to be public
            }
        });
    }
}
