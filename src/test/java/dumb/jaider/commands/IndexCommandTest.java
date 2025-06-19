package dumb.jaider.commands;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dumb.jaider.app.App;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.ui.UI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexCommandTest {

    @Mock
    private AppContext appContext;
    @Spy
    private final JaiderModel model = new JaiderModel(Paths.get("target/test-project-index"));
    @Mock
    private App app;
    @Mock
    private UI ui;
    @Mock
    private EmbeddingModel embeddingModel;
    @Spy
    private EmbeddingStore<TextSegment> embeddingStoreSpy = new EmbeddingStore<>() {
        @Override
        public String add(Embedding embedding) {
            return "id";
        }

        @Override
        public void add(String id, Embedding embedding) {
        }

        @Override
        public String add(Embedding embedding, TextSegment textSegment) {
            return "id";
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            return Collections.nCopies(embeddings.size(), "id");
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
            return Collections.nCopies(embeddings.size(), "id");
        }

        @Override
        public dev.langchain4j.store.embedding.EmbeddingSearchResult<TextSegment> search(dev.langchain4j.store.embedding.EmbeddingSearchRequest request) {
            return new dev.langchain4j.store.embedding.EmbeddingSearchResult<>(Collections.emptyList());
        }
    };

    @InjectMocks
    private IndexCommand indexCommand;

    @BeforeEach
    void setUp() {
        try {
            Files.createDirectories(model.dir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create test project directory", e);
        }

        when(appContext.model()).thenReturn(model);

        lenient().when(embeddingModel.embedAll(anyList())).thenAnswer(invocation -> {
            List<TextSegment> inputSegments = invocation.getArgument(0);
            List<Embedding> dummyEmbeddings = new ArrayList<>();
            if (inputSegments != null) {
                for (var i = 0; i < inputSegments.size(); i++) {
                    dummyEmbeddings.add(Embedding.from(new float[]{0.1f, 0.2f, (float) (i + 3) / 100f}));
                }
            }
            return Response.from(dummyEmbeddings);
        });
    }

    @Test
    void execute_alreadyIndexed_shouldLogMessageAndNotIndex() {
        model.isIndexed = true;
        indexCommand.execute(null, appContext);
        verify(model).addLog(argThat(msg -> ((AiMessage)msg).text().contains("Project is already indexed.")));
        verify(app, never()).setStatePublic(any());
        verify(embeddingModel, never()).embedAll(any());
    }

    @Test
    void execute_noEmbeddingModel_shouldLogMessageAndNotIndex() {
        model.isIndexed = false;
        when(appContext.app()).thenReturn(app);
        when(app.getEmbeddingModel()).thenReturn(null);
        indexCommand.execute(null, appContext);
        verify(model).addLog(argThat(msg -> ((AiMessage)msg).text().contains("Embedding model not available.")));
        verify(app, never()).setStatePublic(any());
    }

    @Test
    void execute_noFilesToLoad_shouldLogMessageAndNotIndex() {
        model.isIndexed = false;
        when(appContext.app()).thenReturn(app);
        when(appContext.ui()).thenReturn(ui);
        when(app.getEmbeddingModel()).thenReturn(embeddingModel);
        model.files.clear(); // This line is irrelevant as IndexCommand scans projectDir

        // To ensure no files are loaded, we'd need to ensure model.projectDir is empty
        // or all files within are filtered by the IndexCommand's pathMatcher.
        // For this test, we assume the tempDir is clean and pathMatcher filters everything else.

        indexCommand.execute(null, appContext);

        verify(app).setStatePublic(App.State.AGENT_THINKING);
        var messageCaptor = ArgumentCaptor.forClass(AiMessage.class);
        verify(app, timeout(1000)).finishTurnPublic(messageCaptor.capture());

        assertNotNull(messageCaptor.getValue());
        // This assertion might still fail if unexpected files are found in target/test-project-index
        assertTrue(messageCaptor.getValue().text().contains("[Jaider] Project successfully indexed with 0 documents. No content found to index."), "Actual message: " + messageCaptor.getValue().text());
        assertTrue(model.isIndexed);
    }

    @Test
    void execute_successfulIndexing_shouldUpdateModelAndFinishTurn() throws IOException {
        when(appContext.app()).thenReturn(app);
        when(appContext.ui()).thenReturn(ui);
        var testProjectDir = model.dir;
        var dummyFile = testProjectDir.resolve("dummy.txt");
        Files.writeString(dummyFile, "Test content for indexing.");
        try {
            model.files.clear(); // Irrelevant for IndexCommand file discovery
            model.isIndexed = false;
            when(app.getEmbeddingModel()).thenReturn(embeddingModel);

            indexCommand.execute(null, appContext);

            verify(app).setStatePublic(App.State.AGENT_THINKING);
            var messageCaptor = ArgumentCaptor.forClass(AiMessage.class);
            verify(app, timeout(1000)).finishTurnPublic(messageCaptor.capture());

            assertNotNull(messageCaptor.getValue());
            // dummy.txt is NOT filtered by IndexCommand's pathMatcher
            assertTrue(messageCaptor.getValue().text().contains("[Jaider] Project successfully indexed with 1 segments."), "Actual message for success: " + messageCaptor.getValue().text());
            assertTrue(model.isIndexed);
            verify(embeddingModel).embedAll(anyList());
        } finally {
            Files.deleteIfExists(dummyFile);
        }
    }

    @Test
    void execute_exceptionDuringEmbedding_shouldLogErrorAndFinishTurn() throws IOException {
        when(appContext.app()).thenReturn(app);
        when(appContext.ui()).thenReturn(ui);
        var testProjectDir = model.dir;
        var dummyFile = testProjectDir.resolve("dummyException.txt");
        Files.writeString(dummyFile, "Content for exception test.");

        try {
            model.files.clear(); // Irrelevant
            model.isIndexed = false;
            when(app.getEmbeddingModel()).thenReturn(embeddingModel);

            // Override lenient stubbing in setUp() for this specific test case
            when(embeddingModel.embedAll(anyList())).thenThrow(new RuntimeException("Embedding failed!"));

            indexCommand.execute(null, appContext);

            verify(app).setStatePublic(App.State.AGENT_THINKING);
            var messageCaptor = ArgumentCaptor.forClass(AiMessage.class);
            verify(app, timeout(1000)).finishTurnPublic(messageCaptor.capture());

            assertNotNull(messageCaptor.getValue());
            // dummyException.txt will be processed, and the exception will be caught
            assertTrue(messageCaptor.getValue().text().contains("[Error] Failed to index project: Embedding failed!"), "Actual message for exception: " + messageCaptor.getValue().text());
            assertFalse(model.isIndexed); // Should not be marked as indexed if embedding fails
            verify(ui).redraw(model);

        } finally {
            Files.deleteIfExists(dummyFile);
        }
    }

    @Test
    void execute_nullProjectDirectory_shouldLogErrorAndFinishTurn() {
        // 1. Set model.isIndexed = false;
        model.isIndexed = false;

        // 2. Simulate model.dir being null by stubbing getDir()
        // model.dir = null; // This is final, cannot be reassigned
        doReturn(null).when(model).getDir();


        // 3. Ensure app.getEmbeddingModel() returns a valid mock embeddingModel (though not strictly necessary for this path, good for consistency)
        when(appContext.app()).thenReturn(app);
        // Remove when(appContext.ui()).thenReturn(ui); as ui specific to indexing status shouldn't be called
        when(app.getEmbeddingModel()).thenReturn(embeddingModel); // Should not be reached, but good to have appContext fully mocked.

        // 4. Call indexCommand.execute(null, appContext);
        indexCommand.execute(null, appContext);

        // 5. Verify that app.setStatePublic(App.State.AGENT_THINKING); is NOT called.
        verify(app, never()).setStatePublic(eq(App.State.AGENT_THINKING));
        verify(app, never()).setStatePublic(any(App.State.class)); // More general check if specific state is too restrictive

        // 6. Verify that app.finishTurnPublic(messageCaptor.capture()); is called
        ArgumentCaptor<AiMessage> messageCaptor = ArgumentCaptor.forClass(AiMessage.class);
        // No timeout needed if it's a direct call now
        verify(app).finishTurnPublic(messageCaptor.capture());

        // 7. Assert that the captured message is the specific error message
        AiMessage capturedMessage = messageCaptor.getValue();
        assertNotNull(capturedMessage, "Captured message should not be null");
        assertEquals("[Error] Project directory is not set. Cannot index.", capturedMessage.text(), "Error message does not match expected.");

        // 8. Assert that model.isIndexed remains false.
        assertFalse(model.isIndexed, "model.isIndexed should remain false after a failure");

        // 9. Verify ui().redraw(model) for status "Indexing project..." is NOT called.
        // If finishTurnPublic causes a redraw, that's separate.
        // This specific redraw is tied to the AGENT_THINKING state and "Indexing project..." status.
        verify(ui, never()).redraw(model);
    }
}
