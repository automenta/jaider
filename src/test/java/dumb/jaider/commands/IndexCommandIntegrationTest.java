package dumb.jaider.commands;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dumb.jaider.app.App;
import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.ui.UI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexCommandIntegrationTest {

    @TempDir
    Path tempProjectDir;

    JaiderModel testJaiderModel;
    App testApp;
    Config testConfig;
    DummyEmbeddingModel dummyEmbeddingModel;
    IndexCommand indexCommand;

    @Mock
    UI mockUi;

    // Inner class for testing
    static class DummyEmbeddingModel implements EmbeddingModel {
        public final List<TextSegment> segmentsPassedToEmbedAll = new ArrayList<>(); // Made public for easier access in tests

        @Override
        public Response<Embedding> embed(String text) {
            // For simplicity, IndexCommand uses embedAll. This method can be basic.
            return Response.from(Embedding.from(List.of(0.1f, 0.2f, 0.3f)));
        }

        @Override
        public Response<Embedding> embed(TextSegment textSegment) {
            return embed(textSegment.text());
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            this.segmentsPassedToEmbedAll.addAll(segments);
            List<Embedding> dummyEmbeddings = segments.stream()
                    .map(segment -> Embedding.from(List.of(0.1f, 0.2f, 0.3f, (float) segment.text().length()))) // Add some variation
                    .collect(Collectors.toList());
            return Response.from(dummyEmbeddings);
        }

        public List<TextSegment> getSegmentsPassedToEmbedAll() {
            return segmentsPassedToEmbedAll;
        }
    }

    // setUp and test methods will be implemented in the next steps.

    @BeforeEach
    void setUp() throws IOException {
        testJaiderModel = new JaiderModel(tempProjectDir);

        // Create dummy files
        Files.writeString(tempProjectDir.resolve("file1.txt"), "This is content of file1.");
        Files.writeString(tempProjectDir.resolve("file2.txt"), "Content for file2 is here.");
        // Create an empty file to ensure it's skipped
        Files.writeString(tempProjectDir.resolve("emptyfile.txt"), "");
        // Create a .git directory to ensure it's skipped
        Files.createDirectories(tempProjectDir.resolve(".git"));
        Files.writeString(tempProjectDir.resolve(".git").resolve("config"), "some git config");

        // Debug file listing
        System.out.println("--- Test setUp: Listing files in " + tempProjectDir + " ---");
        try (java.util.stream.Stream<Path> stream = Files.walk(tempProjectDir)) {
            stream.forEach(System.out::println);
        }
        System.out.println("--- End of file listing ---");

        // mockUi is initialized by Mockito via @Mock annotation
        dummyEmbeddingModel = new DummyEmbeddingModel();

        // Spy on a real App instance.
        // App's constructor calls update(), which initializes its own JaiderModel, Config, and EmbeddingModel.
        // We need to ensure our testJaiderModel is used by the command, and our dummyEmbeddingModel by the App.
        testApp = spy(new App(mockUi));

        // After App's constructor and its internal update() (which sets up real embedding model),
        // stub getEmbeddingModel() to return our dummy.
        // This ensures IndexCommand, when it calls context.getAppInstance().getEmbeddingModel(), gets our dummy.
        doReturn(dummyEmbeddingModel).when(testApp).getEmbeddingModel();

        // Also, IndexCommand uses context.getAppInstance().finishTurnPublic()
        // and context.getAppInstance().setStatePublic(). These should work on the spy.

        // The IndexCommand will operate on the JaiderModel passed via AppContext.
        // The App instance itself has its own JaiderModel. We'll pass our testJaiderModel
        // to the AppContext for the command.

        testConfig = testApp.getConfig(); // Get the config from the spied App.
        indexCommand = new IndexCommand();
    }

    @Test
    void execute_shouldIndexFilesInModel() {
        // Debugging blocks removed as the main test now passes, indicating the fix in IndexCommand.java was successful.

        // Create AppContext with the test model and spied App
        AppContext appContext = new AppContext(testJaiderModel, testConfig, mockUi, testApp);

        // Execute the command
        indexCommand.execute(null, appContext);

        // Wait for the asynchronous indexing to complete by verifying finishTurnPublic is called
        // Adjust timeout if necessary (e.g., for slower CI environments)
        ArgumentCaptor<AiMessage> messageCaptor = ArgumentCaptor.forClass(AiMessage.class);
        verify(testApp, timeout(3000)).finishTurnPublic(messageCaptor.capture()); // Increased timeout slightly

        // Assertions
        assertTrue(testJaiderModel.isIndexed, "Model should be marked as indexed.");
        assertNotNull(testJaiderModel.embeddings, "Embedding store should be initialized in the model.");

        // Verify the message logged by finishTurnPublic
        AiMessage loggedMessage = messageCaptor.getValue();
        assertNotNull(loggedMessage);
        assertTrue(loggedMessage.text().contains("Project successfully indexed with 2 segments."),
                   "Logged message should indicate 2 segments were indexed. Actual: " + loggedMessage.text());


        // Check segments passed to the dummy embedding model
        List<TextSegment> embeddedSegments = dummyEmbeddingModel.getSegmentsPassedToEmbedAll();
        assertEquals(2, embeddedSegments.size(), "Should have embedded 2 segments (one for each non-empty file).");

        // Verify content of the embedded segments
        String contentFile1 = "This is content of file1.";
        String contentFile2 = "Content for file2 is here.";

        boolean foundFile1Content = embeddedSegments.stream().anyMatch(s -> s.text().equals(contentFile1));
        boolean foundFile2Content = embeddedSegments.stream().anyMatch(s -> s.text().equals(contentFile2));

        assertTrue(foundFile1Content, "Content of file1.txt should be among the embedded segments.");
        assertTrue(foundFile2Content, "Content of file2.txt should be among the embedded segments.");

        // Optional: Check if the embedding store actually contains items.
        // InMemoryEmbeddingStore doesn't have a simple size() method.
        // We could try adding a known embedding and segment then searching, but verifying
        // the segments passed to embedAll is a strong indicator.
        // For now, let's rely on the segments passed to the dummy model and the success message.
    }
}
