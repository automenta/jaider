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
import java.nio.file.Path;
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
    private JaiderModel model = new JaiderModel(Paths.get("target/test-project-index"));
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
                for (int i = 0; i < inputSegments.size(); i++) {
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
        ArgumentCaptor<AiMessage> messageCaptor = ArgumentCaptor.forClass(AiMessage.class);
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
        Path testProjectDir = model.dir;
        Path dummyFile = testProjectDir.resolve("dummy.txt");
        Files.writeString(dummyFile, "Test content for indexing.");
        try {
            model.files.clear(); // Irrelevant for IndexCommand file discovery
            model.isIndexed = false;
            when(app.getEmbeddingModel()).thenReturn(embeddingModel);

            indexCommand.execute(null, appContext);

            verify(app).setStatePublic(App.State.AGENT_THINKING);
            ArgumentCaptor<AiMessage> messageCaptor = ArgumentCaptor.forClass(AiMessage.class);
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
        Path testProjectDir = model.dir;
        Path dummyFile = testProjectDir.resolve("dummyException.txt");
        Files.writeString(dummyFile, "Content for exception test.");

        try {
            model.files.clear(); // Irrelevant
            model.isIndexed = false;
            when(app.getEmbeddingModel()).thenReturn(embeddingModel);

            // Override lenient stubbing in setUp() for this specific test case
            when(embeddingModel.embedAll(anyList())).thenThrow(new RuntimeException("Embedding failed!"));

            indexCommand.execute(null, appContext);

            verify(app).setStatePublic(App.State.AGENT_THINKING);
            ArgumentCaptor<AiMessage> messageCaptor = ArgumentCaptor.forClass(AiMessage.class);
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
}
