package dumb.jaider.demo;

import dumb.jaider.app.App;
import dumb.jaider.commands.DemoCommand;
import dumb.jaider.mocks.MockTUI;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.integration.OllamaService; // Now used for mock
import dumb.jaider.integration.ProjectManager; // Now used for mock
import dev.langchain4j.model.chat.ChatLanguageModel; // For mocking the model in OllamaService

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;


public class DemoExecutionIntegrationTest {

    private MockTUI mockTUI;

    @Mock
    private App mockApp;

    @Mock
    private OllamaService mockOllamaService;

    @Mock
    private ProjectManager mockProjectManager;

    @Mock
    private ChatLanguageModel mockChatLanguageModel;

    private JaiderModel jaiderModel;
    private DemoCommand demoCommand;

    @TempDir
    Path tempDirUsedByJUnit; // This is for the test class itself, PM will use a sub-path.

    @BeforeEach
    void setUp() throws IOException { // Added IOException for Files.createDirectories
        MockitoAnnotations.openMocks(this);

        mockTUI = new MockTUI();
        jaiderModel = new JaiderModel();

        when(mockApp.getUi()).thenReturn(mockTUI);
        when(mockApp.getModel()).thenReturn(jaiderModel);

        // Configure OllamaService mock
        mockOllamaService.chatModel = mockChatLanguageModel;

        String helloWorldCode = "package com.example.hello;\n" +
                                "public class HelloWorld {\n" +
                                "    public static void main(String[] args) {\n" +
                                "        System.out.println(\"Hello, World!\");\n" +
                                "    }\n" +
                                "}";
        // Configure ProjectManager mock
        Path mockProjectBasePath = tempDirUsedByJUnit.resolve("mockManagedProject");
        Files.createDirectories(mockProjectBasePath); // Ensure base path for getProjectDir exists

        // This mock ensures that when getProjectDir() is called, it returns our defined path.
        when(mockProjectManager.getProjectDir()).thenReturn(mockProjectBasePath);

        // createTemporaryProject is called with a project name.
        // For the mock, we don't need it to actually create a dir on its own,
        // as getProjectDir() is already returning the path it should manage.
        // So, a doNothing or a simple answer is fine.
        Mockito.doAnswer(invocation -> {
            // Optionally log or verify invocation.getArgument(0) which is the project name
            // String projectName = invocation.getArgument(0);
            // System.out.println("MockProjectManager: createTemporaryProject called with " + projectName);
            // No need to update getProjectDir mock here as it's consistently returning mockProjectBasePath
            return null;
        }).when(mockProjectManager).createTemporaryProject(anyString());


        Mockito.doNothing().when(mockProjectManager).writeFile(any(Path.class), anyString());

        when(mockProjectManager.compileProject())
            .thenReturn(CompletableFuture.completedFuture(true)); // Simulate successful compilation

        // Mockito.doNothing().when(mockProjectManager).cleanupProject(); // Default behavior for void methods in mocks

        List<String> tuiMessages = new ArrayList<>(); // Capture TUI messages for assertions
        mockTUI.onShowModalMessageAccept = tuiMessages::add;
        mockTUI.onShowScrollableTextAccept = tuiMessages::add;
        mockTUI.onShowInputDialogAccept = tuiMessages::add;

        // Instantiate DemoCommand with mocks
        demoCommand = new DemoCommand(mockApp, mockOllamaService, mockProjectManager);
    }

    private void prepareModalMessageInteractions(int count) {
        for (int i = 0; i < count; i++) {
            mockTUI.addShowModalMessageFuture(CompletableFuture.completedFuture(null));
        }
    }

    @Test
    void testHelloWorldDemo_RunsSuccessfully_WithMocks() {
        // Configure mockOllamaService for HelloWorld specific response
        String helloWorldCode = "package com.example.hello;\n" +
                                "public class HelloWorld {\n" +
                                "    public static void main(String[] args) {\n" +
                                "        System.out.println(\"Hello, World!\");\n" +
                                "    }\n" +
                                "}";
        when(mockOllamaService.generate(anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(helloWorldCode));

        // HelloWorldDemo has 9 modal/scrollable interactions
        prepareModalMessageInteractions(9);

        CompletableFuture<Void> result = demoCommand.execute("hello_world");

        assertNotNull(result, "Demo command should return a CompletableFuture.");

        assertDoesNotThrow(() -> result.get(10, TimeUnit.SECONDS),
            "Demo command execution failed with mocks.");
        assertTrue(result.isDone() && !result.isCompletedExceptionally(),
            "Demo command should complete successfully with mocks.");

        boolean ollamaInitFailedLogged = jaiderModel.getLogs().stream()
            .anyMatch(log -> log.getContents().contains("Ollama chat model failed to initialize"));
        assertFalse(ollamaInitFailedLogged, "Ollama initialization failure should NOT be logged when using mocks.");

        boolean demoFinishedLogged = jaiderModel.getLogs().stream()
            .anyMatch(log -> log.getContents().contains("Demo 'hello_world' finished."));
        assertTrue(demoFinishedLogged, "Expected demo finished message in logs.");

        // Verify mock interactions
        Mockito.verify(mockOllamaService, Mockito.atLeastOnce()).generate(anyString(), any());
        Mockito.verify(mockProjectManager, Mockito.times(1)).createTemporaryProject(startsWith("JaiderDemo_hello_world"));
        Mockito.verify(mockProjectManager, Mockito.times(1)).compileProject();
        Mockito.verify(mockProjectManager, Mockito.times(1)).cleanupProject();

        // Example: Verify a TUI message was shown (using the captured tuiMessages list)
        // List<String> capturedMessages = ((List<String>) mockTUI.onShowModalMessageAccept);
        // assertTrue(tuiMessages.stream().anyMatch(msg -> msg.contains("Welcome to the Interactive Hello, World!")), "Welcome message not shown.");
        // assertTrue(tuiMessages.stream().anyMatch(msg -> msg.contains("Compilation Successful!")), "Compilation successful message not shown.");
    }

    @Test
    void testMissileCommandDemo_RunsSuccessfully_WithMocks() {
        // 1. Prepare TUI interactions
        // Recalculated Modals for Missile Command:
        // 1 (Welcome)
        // + 3 (Initial Gen: generating, view code, view complete)
        // + 1 (Gen Explained)
        // + 1 (Compile Time)
        // + 1 (Compile Success)
        // + 1 (Q&A Time intro)
        // + 1 (Understanding Code intro)
        // + 2 (AskQuestionStep 1: show Q modal, show A modal)
        // + 2 (AskQuestionStep 2: show Q modal, show A modal)
        // + 1 (Code Enhance intro)
        // + 1 (Modifying Code intro)
        // + 3 (EnhanceProjectStep: enhancing, view code, view complete)
        // + 1 (Enhancement Explained)
        // + 1 (Compile Enhanced)
        // + 1 (Enhanced Compiled)
        // + 1 (Demo Complete)
        // Total = 23 modals.
        prepareModalMessageInteractions(23);
        // No user input needed for AskQuestionSteps as questions are hardcoded in the demo.

        // 2. Configure mockOllamaService responses for Missile Command
        String initialMissileCode = "package com.example.game;\n" +
                "import javax.swing.*;\n" +
                "public class MissileCommandGame extends JFrame {\n" +
                "    public MissileCommandGame() { setTitle(\"Missile Command\"); setSize(800, 600); setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); setVisible(true); }\n" +
                "    public static void main(String[] args) { new MissileCommandGame(); }\n" +
                "}";
        String qaAnswerMainClass = "The main class is specified as com.example.game.MissileCommandGame in the prompt.";
        String qaAnswerJFrame = "The JFrame is initialized by extending JFrame and calling setTitle, setSize, etc., in the constructor.";
        String enhancedMissileCode = "package com.example.game;\n" +
                "import javax.swing.*;\n" +
                "import javax.sound.sampled.*;\n" + // Added import
                "public class MissileCommandGame extends JFrame {\n" +
                "    public MissileCommandGame() { setTitle(\"Missile Command\"); setSize(800, 600); setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); setVisible(true); }\n" +
                "    public void playFireSound() { /* Placeholder */ }\n" + // Added method
                "    public void playExplosionSound() { /* Placeholder */ }\n" + // Added method
                "    public static void main(String[] args) { new MissileCommandGame(); }\n" +
                "}";

        when(mockOllamaService.generate(anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(initialMissileCode))      // For InitialProjectGenerationStep
            .thenReturn(CompletableFuture.completedFuture(qaAnswerMainClass))       // For first AskQuestionStep
            .thenReturn(CompletableFuture.completedFuture(qaAnswerJFrame))          // For second AskQuestionStep
            .thenReturn(CompletableFuture.completedFuture(enhancedMissileCode));     // For EnhanceProjectStep

        // mockProjectManager.compileProject() is already set up in @BeforeEach to return true.
        // This single mock behavior will apply for both compilation calls.

        // 3. Execute command
        CompletableFuture<Void> result = demoCommand.execute("missile_command");

        // 4. Assertions
        assertNotNull(result, "Demo command should return a CompletableFuture.");
        assertDoesNotThrow(() -> result.get(20, TimeUnit.SECONDS), // Longer timeout for more steps
            "Missile Command demo execution failed with mocks.");
        assertTrue(result.isDone() && !result.isCompletedExceptionally(),
            "Missile Command demo command should complete successfully with mocks.");

        boolean ollamaInitFailedLogged = jaiderModel.getLogs().stream()
            .anyMatch(log -> log.getContents().contains("Ollama chat model failed to initialize"));
        assertFalse(ollamaInitFailedLogged, "Ollama initialization failure should NOT be logged for Missile Command when using mocks.");

        boolean demoFinishedLogged = jaiderModel.getLogs().stream()
            .anyMatch(log -> log.getContents().contains("Demo 'missile_command' finished."));
        assertTrue(demoFinishedLogged, "Expected missile_command demo finished message in logs.");

        // Verify mock interactions
        Mockito.verify(mockOllamaService, Mockito.times(4)).generate(anyString(), any());
        Mockito.verify(mockProjectManager, Mockito.times(1)).createTemporaryProject(startsWith("JaiderDemo_missile_command"));
        Mockito.verify(mockProjectManager, Mockito.times(2)).compileProject();
        Mockito.verify(mockProjectManager, Mockito.times(1)).cleanupProject();
    }
}
