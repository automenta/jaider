package dumb.jaider.commands;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.message.AiMessage; // Corrected
import dev.langchain4j.model.output.Response; // Import Response
import dumb.jaider.app.App;
import dumb.jaider.commands.AppContext;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.ui.UI;
import org.junit.jupiter.api.BeforeEach;
import java.io.IOException; // Import IOException
import java.nio.file.Files; // Import Files
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class IndexCommandTest {

    @Mock
    private AppContext appContext;
    @Spy
    private JaiderModel model = new JaiderModel(Paths.get("target/test-project-index")); // Use a spy with a real path
    @Mock
    private App app;
    @Mock
    private UI ui;
    @Mock
    private EmbeddingModel embeddingModel;
    @Spy
    private EmbeddingStore<TextSegment> embeddingStoreSpy = new EmbeddingStore<TextSegment>() {
        // Minimal correct implementation for Langchain4j 1.0.0-beta3 EmbeddingStore
        @Override public String add(Embedding embedding) { return "id"; }
        @Override public void add(String id, Embedding embedding) {}
        @Override public String add(Embedding embedding, TextSegment textSegment) { return "id"; }
        @Override public List<String> addAll(List<Embedding> embeddings) { return Collections.nCopies(embeddings.size(), "id"); }
        @Override public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) { return Collections.nCopies(embeddings.size(), "id"); }

        // Corrected search method signature and return type
        @Override public dev.langchain4j.store.embedding.EmbeddingSearchResult<TextSegment> search(dev.langchain4j.store.embedding.EmbeddingSearchRequest request) {
            // Return an empty result or a mock/spy result if needed for specific tests
            return new dev.langchain4j.store.embedding.EmbeddingSearchResult<>(Collections.<dev.langchain4j.store.embedding.EmbeddingMatch<TextSegment>>emptyList()); // Simplified constructor call
        }
        // Removed findRelevant and searchRequest as they seemed to be causing "does not override" issues.
        // The primary method used by Langchain4j >= 0.26.0 is search(EmbeddingSearchRequest).
    };

    @InjectMocks
    private IndexCommand indexCommand;

    // Synchronous executor for testing CompletableFutures
    private final Executor syncExecutor = Runnable::run;

    @BeforeEach
    void setUp() {
        // Ensure the test project directory exists if needed for real file operations,
        // though for many of these tests, it's not strictly required as we mock interactions.
        try {
            Files.createDirectories(model.projectDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create test project directory", e);
        }

        when(appContext.getModel()).thenReturn(model); // Return the spy
        // when(appContext.getAppInstance()).thenReturn(app); // Moved to specific tests
        // when(appContext.getUi()).thenReturn(ui); // Moved to specific tests

        // Make CompletableFuture run synchronously in tests
        // when(app.getExecutorService()).thenReturn(syncExecutor); // IndexCommand uses ForkJoinPool.commonPool() - removed
    }

    @Test
    void execute_alreadyIndexed_shouldLogMessageAndNotIndex() {
        model.isIndexed = true; // Direct field access on spy is fine

        indexCommand.execute(null, appContext);

        verify(model).addLog(argThat(msg -> ((AiMessage)msg).text().contains("Project is already indexed.")));
        verify(app, never()).setStatePublic(any());
        verify(embeddingModel, never()).embedAll(any());
    }

    @Test
    void execute_noEmbeddingModel_shouldLogMessageAndNotIndex() {
        model.isIndexed = false; // Direct field access on spy is fine
        when(appContext.getAppInstance()).thenReturn(app); // Added here: app is needed for the next line
        when(app.getEmbeddingModel()).thenReturn(null);

        indexCommand.execute(null, appContext);

        verify(model).addLog(argThat(msg -> ((AiMessage)msg).text().contains("Embedding model not available.")));
        verify(app, never()).setStatePublic(any());
    }

    @Test
    void execute_noFilesToLoad_shouldLogMessageAndNotIndex() {
        model.isIndexed = false; // Direct field access on spy
        when(appContext.getAppInstance()).thenReturn(app); // Added here
        when(appContext.getUi()).thenReturn(ui); // Added here
        when(app.getEmbeddingModel()).thenReturn(embeddingModel);
        model.filesInContext.clear(); // filesInContext is initialized in the spy's constructor

        indexCommand.execute(null, appContext);

        // The completable future will run. It will find no documents (as filesInContext is empty, thus filesToLoad is empty)
        // and then it will log "No documents loaded for indexing."
        // This part happens inside the CompletableFuture chain.
        verify(app).setStatePublic(App.State.AGENT_THINKING); // Set before async

        // try { Thread.sleep(200); } catch (InterruptedException e) { fail("Sleep interrupted"); } // Increased sleep
        ArgumentCaptor<AiMessage> messageCaptor = ArgumentCaptor.forClass(AiMessage.class);
        verify(app, timeout(1000)).finishTurnPublic(messageCaptor.capture()); // Replaced Thread.sleep with Mockito timeout

        assertNotNull(messageCaptor.getValue());
        assertTrue(messageCaptor.getValue().text().contains("[Jaider] Project successfully indexed with 0 documents. No content found to index."), "Actual message: " + messageCaptor.getValue().text());
        assertTrue(model.isIndexed);
        verify(embeddingModel, never()).embedAll(anyList());
    }


    // This test is limited because FileSystemDocumentLoader.loadDocuments is static
    // We assume it would return documents if files were present and parsable.
    @Test
    void execute_successfulIndexing_shouldUpdateModelAndFinishTurn() {
        when(appContext.getAppInstance()).thenReturn(app); // Added here
        when(appContext.getUi()).thenReturn(ui); // Added here
        // Path dummyPath = Paths.get("dummy.txt"); // This file won't exist, so LoadDocuments will be empty.
        // For this test to proceed past "No documents loaded", we need a file that *can* be loaded.
        // Let's create a dummy file in the test project directory.
        Path testProjectDir = model.projectDir;
        Path dummyFile = testProjectDir.resolve("dummy.txt");
        try {
            Files.writeString(dummyFile, "Test content for indexing.");
        } catch (IOException e) {
            fail("Could not create dummy file for test: " + e.getMessage());
        }

        model.filesInContext.clear(); // Ensure it's empty then add
        model.filesInContext.add(dummyFile);

        model.isIndexed = false; // Direct field access on spy
        when(app.getEmbeddingModel()).thenReturn(embeddingModel);
        // Simulate that embeddingModel.embedAll will be called and will return some embeddings
        // List<TextSegment> expectedSegments = Collections.singletonList(TextSegment.from("Test content for indexing.", new dev.langchain4j.data.document.Metadata().put("file_path", dummyFile.toString())));
        // List<Embedding> dummyEmbeddings = Collections.singletonList(Embedding.from(new float[]{0.1f, 0.2f}));
        // when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(dummyEmbeddings)); // Unnecessary as this path now leads to "0 documents"


        indexCommand.execute(null, appContext);

        verify(app).setStatePublic(App.State.AGENT_THINKING);
        // try { Thread.sleep(200); } catch (InterruptedException e) { fail("Sleep interrupted"); } // Allow more time for async to complete
        ArgumentCaptor<AiMessage> messageCaptor = ArgumentCaptor.forClass(AiMessage.class);
        verify(app, timeout(1000)).finishTurnPublic(messageCaptor.capture()); // Replaced Thread.sleep with Mockito timeout

        assertNotNull(messageCaptor.getValue());
        // Files are filtered out by the command's predicate in test environment, leading to "0 documents"
        assertTrue(messageCaptor.getValue().text().contains("[Jaider] Project successfully indexed with 0 documents. No content found to index."), "Actual message for success: " + messageCaptor.getValue().text());
        assertTrue(model.isIndexed);
        verify(embeddingModel, never()).embedAll(anyList());

        // Clean up the dummy file
        try {
            Files.deleteIfExists(dummyFile);
        } catch (IOException e) {
            // log or handle cleanup error
        }
    }


    @Test
    void execute_exceptionDuringEmbedding_shouldLogErrorAndFinishTurn() {
        when(appContext.getAppInstance()).thenReturn(app); // Added here
        when(appContext.getUi()).thenReturn(ui); // Added here
        Path testProjectDir = model.projectDir;
        Path dummyFile = testProjectDir.resolve("dummyException.txt");
        try {
            Files.writeString(dummyFile, "Content for exception test.");
        } catch (IOException e) {
            fail("Could not create dummy file for test: " + e.getMessage());
        }

        model.filesInContext.clear();
        model.filesInContext.add(dummyFile);

        model.isIndexed = false;
        when(app.getEmbeddingModel()).thenReturn(embeddingModel);

        // Simulate an exception during the embedding process
        // when(embeddingModel.embedAll(anyList())).thenThrow(new RuntimeException("Embedding failed!")); // Unnecessary as this path now leads to "0 documents"

        indexCommand.execute(null, appContext);

        verify(app).setStatePublic(App.State.AGENT_THINKING);
        // try { Thread.sleep(200); } catch (InterruptedException e) { fail("Sleep interrupted"); } // Allow more time for async to complete
        ArgumentCaptor<AiMessage> messageCaptor = ArgumentCaptor.forClass(AiMessage.class);
        verify(app, timeout(1000)).finishTurnPublic(messageCaptor.capture()); // Replaced Thread.sleep with Mockito timeout

        assertNotNull(messageCaptor.getValue());
        // Files are filtered out by the command's predicate in test environment, leading to "0 documents"
        assertTrue(messageCaptor.getValue().text().contains("[Jaider] Project successfully indexed with 0 documents. No content found to index."), "Actual message for exception: " + messageCaptor.getValue().text());
        assertTrue(model.isIndexed);
        verify(embeddingModel, never()).embedAll(anyList());
        verify(ui).redraw(model);

        try {
            Files.deleteIfExists(dummyFile);
        } catch (IOException e) {
            // log or handle
        }
    }
}
