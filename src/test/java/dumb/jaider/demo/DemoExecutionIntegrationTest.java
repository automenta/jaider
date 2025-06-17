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
        // Initialize mocks created with the @Mock annotation
        MockitoAnnotations.openMocks(this);

        // Instantiate a controllable TUI and a real JaiderModel
        mockTUI = new MockTUI();
        jaiderModel = new JaiderModel(); // Using a real model to observe its state (e.g., logs)

        // Mock the App class to return our mock TUI and real model
        // This allows the DemoCommand to interact with controlled components
        when(mockApp.getUi()).thenReturn(mockTUI);
        when(mockApp.getModel()).thenReturn(jaiderModel);

        // Configure OllamaService mock
        // The DemoCommand uses OllamaService to generate code. We mock this to provide predefined code
        // and avoid actual calls to an Ollama instance.
        // We assign a mock ChatLanguageModel to the chatModel field of the mockOllamaService.
        // This is necessary because the generate() method in OllamaService internally uses this chatModel.
        mockOllamaService.chatModel = mockChatLanguageModel; // Directly assign the mock model

        // Configure ProjectManager mock
        // The DemoCommand uses ProjectManager to handle file system operations like creating projects,
        // writing files, and compiling. We mock these to avoid actual file system changes and compilation.
        Path mockProjectBasePath = tempDirUsedByJUnit.resolve("mockManagedProject");
        Files.createDirectories(mockProjectBasePath); // Ensure base path for getProjectDir exists for the mock

        // Mock getProjectDir() to return a temporary directory controlled by this test.
        // This ensures that any operations supposed to happen within the project directory
        // are directed to a known, temporary location.
        when(mockProjectManager.getProjectDir()).thenReturn(mockProjectBasePath);

        // Mock createTemporaryProject() to do nothing but allow the call.
        // In a real scenario, this would create a new project directory. Here, we simulate its successful execution.
        // We use doAnswer to allow for potential future inspection of arguments if needed.
        Mockito.doAnswer(invocation -> {
            // String projectName = invocation.getArgument(0); // Example of how to get argument
            // System.out.println("MockProjectManager: createTemporaryProject called with " + projectName);
            return null; // Indicates void method successfully executed
        }).when(mockProjectManager).createTemporaryProject(anyString());

        // Mock writeFile() to do nothing.
        // This prevents actual file writing during the test, isolating the test from file system state.
        Mockito.doNothing().when(mockProjectManager).writeFile(any(Path.class), anyString());

        // Mock compileProject() to return a completed CompletableFuture indicating success.
        // This simulates a successful project compilation without actually running a compiler.
        when(mockProjectManager.compileProject())
            .thenReturn(CompletableFuture.completedFuture(true)); // Simulate successful compilation

        // Mockito.doNothing().when(mockProjectManager).cleanupProject(); // Default behavior for void methods in mocks is to do nothing. Explicit mocking is not strictly needed but can be for clarity.

        // Setup TUI message capturing for assertions.
        // This allows us to verify that certain messages were displayed to the user via the TUI.
        List<String> tuiMessages = new ArrayList<>();
        mockTUI.onShowModalMessageAccept = tuiMessages::add; // Capture messages shown via showModalMessage
        mockTUI.onShowScrollableTextAccept = tuiMessages::add; // Capture messages shown via showScrollableText
        mockTUI.onShowInputDialogAccept = tuiMessages::add; // Capture messages shown via showInputDialog (though not used in these demos for input)

        // Instantiate DemoCommand with all the mocked dependencies
        // This is the class under test, now configured to run in a controlled environment.
        demoCommand = new DemoCommand(mockApp, mockOllamaService, mockProjectManager);
    }

    private void prepareModalMessageInteractions(int count) {
        for (int i = 0; i < count; i++) {
            mockTUI.addShowModalMessageFuture(CompletableFuture.completedFuture(null));
        }
    }

    @Test
    void testHelloWorldDemo_RunsSuccessfully_WithMocks() {
        // Section 1: Configure Mocks
        // --------------------------
        // Define the expected Java code to be "generated" by the mock OllamaService for the HelloWorld demo.
        String helloWorldCode = "package com.example.hello;\n" +
                                "public class HelloWorld {\n" +
                                "    public static void main(String[] args) {\n" +
                                "        System.out.println(\"Hello, World!\");\n" +
                                "    }\n" +
                                "}";
        // When mockOllamaService.generate() is called (with any string prompt and any history),
        // then return a CompletableFuture containing the predefined helloWorldCode.
        when(mockOllamaService.generate(anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(helloWorldCode));

        // Prepare the MockTUI for the expected number of modal/scrollable message interactions.
        // The HelloWorld demo sequence involves 9 such interactions.
        prepareModalMessageInteractions(9);

        // Section 2: Execute Command
        // --------------------------
        // Execute the "hello_world" demo. This will trigger the mocked interactions.
        CompletableFuture<Void> result = demoCommand.execute("hello_world");

        // Section 3: Verify Results
        // -------------------------
        // Assert that the command execution returns a CompletableFuture.
        assertNotNull(result, "Demo command should return a CompletableFuture, but was null.");

        // Assert that the CompletableFuture completes within a reasonable timeframe (10 seconds) without throwing an exception.
        assertDoesNotThrow(() -> result.get(10, TimeUnit.SECONDS),
            "Demo command execution for 'hello_world' threw an exception or timed out with mocks.");
        // Assert that the command completed successfully (not exceptionally).
        assertTrue(result.isDone() && !result.isCompletedExceptionally(),
            "Demo command for 'hello_world' should complete successfully (not exceptionally) with mocks.");

        // Verify that no "Ollama chat model failed to initialize" message was logged,
        // as we are using a mock service.
        boolean ollamaInitFailedLogged = jaiderModel.getLogs().stream()
            .anyMatch(log -> log.getContents().contains("Ollama chat model failed to initialize"));
        assertFalse(ollamaInitFailedLogged, "Ollama initialization failure should NOT be logged when using mocks for 'hello_world'.");

        // Verify that the "demo finished" message was logged.
        boolean demoFinishedLogged = jaiderModel.getLogs().stream()
            .anyMatch(log -> log.getContents().contains("Demo 'hello_world' finished."));
        assertTrue(demoFinishedLogged, "Expected 'Demo 'hello_world' finished.' message in logs, but it was not found.");

        // Verify key interactions with mocked services:
        // - OllamaService.generate was called at least once.
        Mockito.verify(mockOllamaService, Mockito.atLeastOnce()).generate(anyString(), any());
        // - ProjectManager.createTemporaryProject was called exactly once with a project name starting with "JaiderDemo_hello_world".
        Mockito.verify(mockProjectManager, Mockito.times(1)).createTemporaryProject(startsWith("JaiderDemo_hello_world"));
        // - ProjectManager.compileProject was called exactly once.
        Mockito.verify(mockProjectManager, Mockito.times(1)).compileProject();
        // - ProjectManager.cleanupProject was called exactly once.
        Mockito.verify(mockProjectManager, Mockito.times(1)).cleanupProject();

        // Example: Verify a TUI message was shown (using the captured tuiMessages list from setUp)
        // This can be useful for ensuring specific user feedback points were reached.
        // List<String> capturedMessages = ((List<String>) mockTUI.onShowModalMessageAccept); // Access through instance variable if needed
        // assertTrue(tuiMessages.stream().anyMatch(msg -> msg.contains("Welcome to the Interactive Hello, World!")), "Welcome message not shown for HelloWorld.");
        // assertTrue(tuiMessages.stream().anyMatch(msg -> msg.contains("Compilation Successful!")), "Compilation successful message not shown for HelloWorld.");
    }

    @Test
    void testMissileCommandDemo_RunsSuccessfully_WithMocks() {
        // Section 1: Configure Mocks
        // --------------------------
        // Prepare the MockTUI for the expected number of modal/scrollable message interactions.
        // The Missile Command demo sequence involves 23 such interactions.
        // Detailed breakdown of interactions:
        // 1 (Welcome)
        // + 3 (Initial Gen: generating, view code, view complete)
        // + 1 (Gen Explained)
        // + 1 (Compile Time)
        // + 1 (Compile Success)
        // + 1 (Q&A Time intro)
        // + 1 (Understanding Code intro)
        // + 2 (AskQuestionStep 1: show Q modal, show A modal) - for "main class" question
        // + 2 (AskQuestionStep 2: show Q modal, show A modal) - for "JFrame" question
        // + 1 (Code Enhance intro)
        // + 1 (Modifying Code intro)
        // + 3 (EnhanceProjectStep: enhancing, view code, view complete)
        // + 1 (Enhancement Explained)
        // + 1 (Compile Enhanced)
        // + 1 (Enhanced Compiled)
        // + 1 (Demo Complete)
        // Total = 23 modals.
        prepareModalMessageInteractions(23);
        // Note: User input for AskQuestionSteps is not mocked here as the demo uses hardcoded questions
        // and does not require actual user input through input dialogs in this mocked setup.

        // Define the sequence of "generated" responses from mockOllamaService for the Missile Command demo.
        // This demo involves multiple calls to generate(): initial code, Q&A answers, and enhanced code.
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
                "import javax.sound.sampled.*;\n" + // Example enhancement: added import
                "public class MissileCommandGame extends JFrame {\n" +
                "    public MissileCommandGame() { setTitle(\"Missile Command\"); setSize(800, 600); setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); setVisible(true); }\n" +
                "    public void playFireSound() { /* Placeholder for sound */ }\n" + // Example enhancement: added method
                "    public void playExplosionSound() { /* Placeholder for sound */ }\n" + // Example enhancement: added method
                "    public static void main(String[] args) { new MissileCommandGame(); }\n" +
                "}";

        // Configure mockOllamaService.generate() to return the predefined responses in sequence.
        // Each call to generate() will return the next CompletableFuture in this chain.
        when(mockOllamaService.generate(anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(initialMissileCode))      // 1st call: For InitialProjectGenerationStep
            .thenReturn(CompletableFuture.completedFuture(qaAnswerMainClass))       // 2nd call: For first AskQuestionStep (main class)
            .thenReturn(CompletableFuture.completedFuture(qaAnswerJFrame))          // 3rd call: For second AskQuestionStep (JFrame)
            .thenReturn(CompletableFuture.completedFuture(enhancedMissileCode));     // 4th call: For EnhanceProjectStep

        // Note: mockProjectManager.compileProject() is already set up in @BeforeEach to return a successful future.
        // This single mock behavior will apply for both compilation calls (initial and enhanced) in this demo.

        // Section 2: Execute Command
        // --------------------------
        // Execute the "missile_command" demo.
        CompletableFuture<Void> result = demoCommand.execute("missile_command");

        // Section 3: Verify Results
        // -------------------------
        // Assert that the command execution returns a CompletableFuture.
        assertNotNull(result, "Demo command should return a CompletableFuture, but was null for 'missile_command'.");

        // Assert that the CompletableFuture completes within a longer timeframe (20 seconds) due to more steps.
        assertDoesNotThrow(() -> result.get(20, TimeUnit.SECONDS),
            "Missile Command demo execution threw an exception or timed out with mocks.");
        // Assert that the command completed successfully.
        assertTrue(result.isDone() && !result.isCompletedExceptionally(),
            "Missile Command demo command should complete successfully (not exceptionally) with mocks.");

        // Verify that no "Ollama chat model failed to initialize" message was logged.
        boolean ollamaInitFailedLogged = jaiderModel.getLogs().stream()
            .anyMatch(log -> log.getContents().contains("Ollama chat model failed to initialize"));
        assertFalse(ollamaInitFailedLogged, "Ollama initialization failure should NOT be logged for 'missile_command' when using mocks.");

        // Verify that the "demo finished" message was logged for missile_command.
        boolean demoFinishedLogged = jaiderModel.getLogs().stream()
            .anyMatch(log -> log.getContents().contains("Demo 'missile_command' finished."));
        assertTrue(demoFinishedLogged, "Expected 'Demo 'missile_command' finished.' message in logs, but it was not found.");

        // Verify key interactions with mocked services:
        // - OllamaService.generate was called exactly 4 times (initial, 2x Q&A, enhanced).
        Mockito.verify(mockOllamaService, Mockito.times(4)).generate(anyString(), any());
        // - ProjectManager.createTemporaryProject was called once with a project name starting with "JaiderDemo_missile_command".
        Mockito.verify(mockProjectManager, Mockito.times(1)).createTemporaryProject(startsWith("JaiderDemo_missile_command"));
        // - ProjectManager.compileProject was called twice (once for initial code, once for enhanced code).
        Mockito.verify(mockProjectManager, Mockito.times(2)).compileProject();
        // - ProjectManager.cleanupProject was called once.
        Mockito.verify(mockProjectManager, Mockito.times(1)).cleanupProject();
    }

    @Test
    void testContextualQADemo_RunsSuccessfully_WithMocks() {
        // Section 1: Configure Mocks
        // --------------------------
        // Define the expected Java code for Calculator class
        String calculatorCode = "package com.example.math;\n" +
                                "public class Calculator {\n" +
                                "    // No public constructor\n" +
                                "    private Calculator() {}\n" +
                                "    public static int add(int a, int b) {\n" +
                                "        return a + b;\n" +
                                "    }\n" +
                                "    public static int subtract(int a, int b) {\n" +
                                "        return a - b;\n" +
                                "    }\n" +
                                "}";

        // Define answers for the Q&A steps
        String q1Answer = "The Calculator class provides 'add' and 'subtract' operations.";
        String q2Answer = "The 'add' operation is implemented as: public static int add(int a, int b) { return a + b; }";

        // Configure mockOllamaService.generate() for sequential calls:
        // 1. Code generation for Calculator
        // 2. Answer for Q1
        // 3. Answer for Q2
        when(mockOllamaService.generate(anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(calculatorCode))   // For InitialProjectGenerationStep
            .thenReturn(CompletableFuture.completedFuture(q1Answer))        // For first AskQuestionStep
            .thenReturn(CompletableFuture.completedFuture(q2Answer));       // For second AskQuestionStep

        // Prepare MockTUI for modal interactions.
        // Calculation:
        // 1 (Welcome) + 1 (Intro) + 3 (Initial Gen) + 1 (Explain Code) + 2 (Q1) + 2 (Q2) + 1 (Completion) = 11 modals
        prepareModalMessageInteractions(11);

        // Section 2: Execute Command
        // --------------------------
        CompletableFuture<Void> result = demoCommand.execute("contextual_qa_demo");

        // Section 3: Verify Results
        // -------------------------
        assertNotNull(result, "Contextual Q&A demo command should return a CompletableFuture.");
        // Timeout might need adjustment based on complexity, but 10s should be fine for these steps.
        assertDoesNotThrow(() -> result.get(15, TimeUnit.SECONDS),
            "Contextual Q&A demo execution failed with mocks or timed out.");
        assertTrue(result.isDone() && !result.isCompletedExceptionally(),
            "Contextual Q&A demo command should complete successfully with mocks.");

        // Verify logs
        boolean ollamaInitFailedLogged = jaiderModel.getLogs().stream()
            .anyMatch(log -> log.getContents().contains("Ollama chat model failed to initialize"));
        assertFalse(ollamaInitFailedLogged, "Ollama initialization failure should NOT be logged for Contextual Q&A demo when using mocks.");

        boolean demoFinishedLogged = jaiderModel.getLogs().stream()
            .anyMatch(log -> log.getContents().contains("Demo 'contextual_qa_demo' finished."));
        assertTrue(demoFinishedLogged, "Expected 'contextual_qa_demo' finished message in logs.");

        // Verify mock interactions
        // OllamaService.generate should be called 3 times (1 for code gen, 2 for Q&A)
        Mockito.verify(mockOllamaService, Mockito.times(3)).generate(anyString(), any());
        // ProjectManager interactions
        Mockito.verify(mockProjectManager, Mockito.times(1)).createTemporaryProject(startsWith("JaiderDemo_contextual_qa_demo"));
        // compileProject is called by InitialProjectGenerationStep and ExplainCodeStep (if it compiles to verify)
        // However, the current structure of InitialProjectGenerationStep internally compiles.
        // ExplainCodeStep might not compile again if not explicitly designed to.
        // Let's assume compile is called once by InitialProjectGenerationStep.
        Mockito.verify(mockProjectManager, Mockito.times(1)).compileProject();
        Mockito.verify(mockProjectManager, Mockito.times(1)).cleanupProject();
    }
}
