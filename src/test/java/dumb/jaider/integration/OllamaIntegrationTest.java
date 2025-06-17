package dumb.jaider.integration;

import dumb.jaider.service.BuildManagerService;
import dumb.jaider.workflow.CodeGenerationWorkflow; // Import the new workflow class
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*; // Standard JUnit 5 assertions
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OllamaIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(OllamaIntegrationTest.class);

    private static TestConfig testConfig;
    private static OllamaService ollamaService;
    private static ProjectManager projectManager;
    private static VerificationService verificationService;
    private static CodeGenerationWorkflow codeGenerationWorkflow; // New workflow field

    // These fields will now be populated from the results of workflow methods
    private static String generatedInitialCode;
    private static String generatedEnhancedCode;
    private static Path projectJavaFile;
    private static Path projectDirectory; // To store the project directory path

    @BeforeAll
    static void globalSetup() throws CodeGenerationWorkflow.WorkflowException { // Add WorkflowException
        logger.info("Starting global setup for OllamaIntegrationTest...");

        // Initialize TestConfig to load test configurations (e.g., Ollama base URL)
        testConfig = new TestConfig();
        // Initialize OllamaService with the loaded configuration
        ollamaService = new OllamaService(testConfig);

        // Check if Ollama instance is available and responsive.
        // These checks use JUnit's assumeTrue, which will skip the tests if the conditions are not met.
        try {
            // Assumption: The chatModel inside ollamaService should be initialized by its constructor.
            assumeTrue(ollamaService.chatModel != null, "OllamaChatModel failed to initialize. Check logs from OllamaService constructor. This usually indicates an issue connecting to the Ollama server.");
            // Define a simple test prompt to check basic communication with Ollama.
            String testPrompt = "Respond with just the word 'test'.";
            // Send the prompt to the Ollama model.
            String response = ollamaService.chatModel.chat(testPrompt);
            // Assumption: The response should not be null and should contain "test" (case-insensitive).
            assumeTrue(response != null && response.toLowerCase().contains("test"), "Ollama instance not responding as expected to a basic 'test' prompt at " + testConfig.getOllamaBaseUrl() + ". Response: '" + response + "'");
            logger.info("Ollama instance responded to basic check successfully.");
        } catch (Exception e) {
            // If any exception occurs during the Ollama check (e.g., network issues), log it and skip tests.
            logger.warn("Ollama instance not available or not responding at {}. Skipping all tests in this class. Error: {}", testConfig.getOllamaBaseUrl(), e.getMessage(), e);
            assumeTrue(false, "Ollama instance not available. Skipping tests. Exception: " + e.getMessage());
        }

        // Initialize ProjectManager for handling project file and directory operations.
        projectManager = new ProjectManager();
        // Initialize VerificationService for performing checks on generated code and project state.
        verificationService = new VerificationService();

        // Instantiate the CodeGenerationWorkflow class, injecting its dependencies.
        // This workflow class encapsulates the logic for different Jaider operations like code generation, Q&A, etc.
        codeGenerationWorkflow = new CodeGenerationWorkflow(ollamaService, projectManager, verificationService);

        try {
            // Create a temporary directory that will serve as the root for the test project.
            // This is a prerequisite for many workflow methods that operate on a project structure.
            projectDirectory = projectManager.createTemporaryProject("OllamaMissileCommandTest");
            logger.info("Global setup complete. Project directory created at: {}", projectDirectory);
        } catch (IOException e) {
            // If the temporary project directory cannot be created, fail the setup, as tests cannot proceed.
            logger.error("Failed to create temporary project in globalSetup", e);
            fail("Global setup failed: Could not create temporary project directory. Error: " + e.getMessage(), e);
        }
    }

    @AfterAll
    static void globalTeardown() {
        logger.info("Starting global teardown for OllamaIntegrationTest...");
        if (projectManager != null) {
            projectManager.cleanupProject(); // ProjectManager handles its own cleanup
        }
        logger.info("Global teardown complete.");
    }

    private String getMissileCommandPom() {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
               "    <modelVersion>4.0.0</modelVersion>\n" +
               "    <groupId>com.example.missilecommand</groupId>\n" +
               "    <artifactId>missile-command</artifactId>\n" +
               "    <version>1.0-SNAPSHOT</version>\n" +
               "    <properties>\n" +
               "        <maven.compiler.source>11</maven.compiler.source>\n" +
               "        <maven.compiler.target>11</maven.compiler.target>\n" +
               "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
               "    </properties>\n" +
               "    <dependencies>\n" +
               "        <!-- Swing is part of standard Java SE, no explicit dependency needed unless using a very modular JDK -->\n" +
               "    </dependencies>\n" +
               "    <build>\n" +
               "        <plugins>\n" +
               "            <plugin>\n" +
               "                <groupId>org.apache.maven.plugins</groupId>\n" +
               "                <artifactId>maven-compiler-plugin</artifactId>\n" +
               "                <version>3.8.1</version>\n" +
               "                <configuration>\n" +
               "                    <source>11</source>\n" +
               "                    <target>11</target>\n" +
               "                </configuration>\n" +
               "            </plugin>\n" +
               "           <plugin>\n" +
               "               <artifactId>maven-surefire-plugin</artifactId>\n" +
               "               <version>2.22.2</version> <!-- Or any recent version -->\n" +
               "           </plugin>\n" +
               "        </plugins>\n" +
               "    </build>\n" +
               "</project>";
    }

    @Test
    @Order(1)
    @DisplayName("/code: Generate Missile Command from scratch")
    void step1_generateMissileCommand() throws CodeGenerationWorkflow.WorkflowException {
        // Purpose: Tests the initial code generation capability of Jaider.
        // Feature: Demonstrates Jaider's ability to generate a complete, runnable Java application
        // from a natural language description using the CodeGenerationWorkflow.generateInitialProject method.
        logger.info("STEP 1: Generating Missile Command game using CodeGenerationWorkflow...");
        String gameDescription = "a complete, runnable, single-file Missile Command game in Java using Swing. The main class should be named 'MissileCommandGame' and be in package 'com.example.game'.";

        // Execute the code generation workflow.
        CodeGenerationWorkflow.ProjectGenerationResult result = codeGenerationWorkflow.generateInitialProject(
            gameDescription,
            "com.example.game",
            "MissileCommandGame",
            getMissileCommandPom(), // Provide a POM for the project
            // Verification keywords to check if the generated code is relevant:
            "MissileCommandGame", "JFrame", "Swing", "java.awt"
        );

        // Assertions to verify the result of the code generation:
        assertNotNull(result, "ProjectGenerationResult from generateInitialProject should not be null.");
        generatedInitialCode = result.generatedCode(); // Store for subsequent tests
        projectJavaFile = result.javaFilePath(); // Store for subsequent tests

        // Check that code was generated and the file was created.
        assertNotNull(generatedInitialCode, "Generated code string should not be null.");
        assertFalse(generatedInitialCode.isEmpty(), "Generated code string should not be empty.");
        assertNotNull(projectJavaFile, "Path to the generated Java file should not be null.");
        assertTrue(Files.exists(projectJavaFile), "The generated Java file '" + projectJavaFile + "' should exist on disk.");

        logger.info("STEP 1: Completed. Generated code to file: {}", projectJavaFile);
    }

    @Test
    @Order(2)
    @DisplayName("Verify project structure and compilation (Initial)")
    void step2_verifyAndCompileInitialProject() throws CodeGenerationWorkflow.WorkflowException {
        // Purpose: Tests the project compilation capability after initial code generation.
        // Feature: Demonstrates Jaider's ability to compile the generated project using the
        // CodeGenerationWorkflow.compileProject method, which internally uses ProjectManager and BuildManagerService.
        logger.info("STEP 2: Verifying initial project structure and compilation using CodeGenerationWorkflow...");
        // Assumptions: Ensure that prior steps (code generation) were successful.
        assumeTrue(generatedInitialCode != null && !generatedInitialCode.isEmpty(), "Initial code generation must have succeeded in Step 1 for compilation to proceed.");
        assumeTrue(projectDirectory != null && Files.exists(projectDirectory), "Project directory must exist for compilation. Current: " + projectDirectory);
        assumeTrue(projectManager.getProjectDir() != null && Files.exists(projectManager.getProjectDir()), "ProjectManager must have a valid project directory for compilation.");


        // Note: Verification of file existence (e.g., pom.xml, specific Java files) is
        // implicitly handled by the workflow's compile step or the initial generation step.
        // Explicit checks can be added here using verificationService if needed for more granular testing.
        // e.g., verificationService.verifyJavaFileExists(projectDirectory, "com.example.game", "MissileCommandGame");
        // e.g., verificationService.verifyPomExists(projectDirectory);

        // Execute the compilation workflow.
        BuildManagerService.BuildResult compileResult = codeGenerationWorkflow.compileProject("Initial project");

        // Assertions to verify the compilation outcome:
        assertNotNull(compileResult, "Compilation result (BuildResult) should not be null.");
        assertTrue(compileResult.success(), "Initial project compilation should be successful. Compiler output: " + compileResult.output());
        assertEquals(0, compileResult.exitCode(), "Initial project compilation Maven exit code should be 0 (success). Output: " + compileResult.output());
        logger.info("STEP 2: Initial project compilation completed successfully.");
    }

    @Test
    @Order(3)
    @DisplayName("/ask: Ask questions about the program")
    void step3_askQuestions() throws CodeGenerationWorkflow.WorkflowException {
        // Purpose: Tests the Q&A capability of Jaider regarding previously generated code.
        // Feature: Demonstrates Jaider's ability to answer questions about a given codebase using
        // the CodeGenerationWorkflow.askQuestionAboutCode method.
        logger.info("STEP 3: Asking questions about the program using CodeGenerationWorkflow...");
        // Assumption: Ensure that initial code was generated to ask questions about it.
        assumeTrue(generatedInitialCode != null && !generatedInitialCode.isEmpty(), "Initial code generation must have succeeded in Step 1 to ask questions about it.");

        // Question 1: Regarding the main class.
        String question1 = "What is the main class of this game as specified in the initial prompt?";
        CodeGenerationWorkflow.AskQuestionResult answer1Result = codeGenerationWorkflow.askQuestionAboutCode(
            generatedInitialCode, // Provide the context (the generated code)
            question1,
            "missilecommandgame" // Keywords to verify the answer's relevance
        );
        String answer1 = answer1Result.answer();
        logger.info("Question 1: '{}' - Answer: '{}'", question1, answer1);
        // Assertions for answer 1:
        assertNotNull(answer1, "Answer to question 1 ('main class') should not be null.");
        assertFalse(answer1.isEmpty(), "Answer to question 1 ('main class') should not be empty.");
        // Note: Specific content assertion is implicitly handled by the workflow's keyword verification.

        // Question 2: Regarding JFrame initialization.
        String question2 = "How is the game window (JFrame) initialized?";
        CodeGenerationWorkflow.AskQuestionResult answer2Result = codeGenerationWorkflow.askQuestionAboutCode(
            generatedInitialCode, // Provide the context
            question2
            // No specific keywords for this answer verification in this test, relying on general LLM correctness.
        );
        String answer2 = answer2Result.answer();
        logger.info("Question 2: '{}' - Answer: '{}'", question2, answer2);
        // Assertions for answer 2:
        assertNotNull(answer2, "Answer to question 2 ('JFrame initialization') should not be null.");
        assertFalse(answer2.isEmpty(), "Answer to question 2 ('JFrame initialization') should not be empty.");
        logger.info("STEP 3: Q&A session completed.");
    }

    @Test
    @Order(4)
    @DisplayName("/ask: Ask a contextual follow-up question")
    void step4_askContextualQuestion() throws CodeGenerationWorkflow.WorkflowException {
        // Purpose: Tests Jaider's ability to handle context in a Q&A session.
        // Feature: Demonstrates if the Q&A service can use the context of the code and a previous implicit statement (from prompt)
        // to answer a follow-up question correctly.
        logger.info("STEP 4: Asking a contextual follow-up question...");
        assumeTrue(generatedInitialCode != null && !generatedInitialCode.isEmpty(), "Initial code generation must have succeeded to ask contextual questions.");

        // This test will ask about the UI framework and then a follow-up based on common knowledge of that framework.
        String initialQuestion = "What primary UI framework is used to build the graphical interface in the generated MissileCommandGame?";
        CodeGenerationWorkflow.AskQuestionResult initialAnswerResult = codeGenerationWorkflow.askQuestionAboutCode(
            generatedInitialCode,
            initialQuestion,
            "Swing" // Expect "Swing" in the answer.
        );
        String initialAnswer = initialAnswerResult.answer();
        logger.info("Initial Contextual Question: '{}' - Answer: '{}'", initialQuestion, initialAnswer);
        assertNotNull(initialAnswer, "Answer to initial contextual question ('UI framework') should not be null.");
        assertFalse(initialAnswer.isEmpty(), "Answer to initial contextual question ('UI framework') should not be empty.");
        assertTrue(initialAnswer.toLowerCase().contains("swing"), "Answer to initial contextual question should mention 'Swing'.");

        // Follow-up question that relies on the context that it's a Swing application.
        String followupQuestion = "Given that it uses Swing, what specific Swing class is typically extended to create the main application window or frame?";
        CodeGenerationWorkflow.AskQuestionResult followupAnswerResult = codeGenerationWorkflow.askQuestionAboutCode(
            generatedInitialCode, // Provide the code again as context
            followupQuestion,
            "JFrame" // Expect "JFrame" in the answer.
        );
        String followupAnswer = followupAnswerResult.answer();
        logger.info("Follow-up Contextual Question: '{}' - Answer: '{}'", followupQuestion, followupAnswer);
        assertNotNull(followupAnswer, "Answer to follow-up contextual question ('JFrame') should not be null.");
        assertFalse(followupAnswer.isEmpty(), "Answer to follow-up contextual question ('JFrame') should not be empty.");
        assertTrue(followupAnswer.toLowerCase().contains("jframe"), "Follow-up answer should correctly identify JFrame as the class to extend for a Swing application window.");

        logger.info("STEP 4: Contextual Q&A completed.");
    }

    @Test
    @Order(5)
    @DisplayName("/code: Enhance Missile Command with sound")
    void step5_enhanceWithSound() throws CodeGenerationWorkflow.WorkflowException {
        // Purpose: Tests the code enhancement capability of Jaider.
        // Feature: Demonstrates Jaider's ability to modify existing code based on an enhancement request
        // using the CodeGenerationWorkflow.enhanceProject method.
        logger.info("STEP 5: Enhancing Missile Command with sound using CodeGenerationWorkflow...");
        // Assumptions: Ensure initial code exists and the Java file path is known.
        assumeTrue(generatedInitialCode != null && !generatedInitialCode.isEmpty(), "Initial code generation must have succeeded in Step 1 for enhancement.");
        assumeTrue(projectJavaFile != null && Files.exists(projectJavaFile), "Project Java file '" + projectJavaFile + "' must exist for enhancement.");

        String enhancementDescription = "Add sound effects for two events: when a missile is fired and when an explosion occurs. Use javax.sound.sampled.Clip for playing sounds. Create placeholder methods playFireSound() and playExplosionSound() if actual sound file loading is complex, but include necessary imports for javax.sound.sampled.*.";

        // Execute the enhancement workflow.
        CodeGenerationWorkflow.EnhanceProjectResult enhanceResult = codeGenerationWorkflow.enhanceProject(
            generatedInitialCode, // Provide the original code
            enhancementDescription,
            "com.example.game",
            "MissileCommandGame",
            // Verification keywords for the enhanced code:
            "javax.sound.sampled", "playFireSound", "playExplosionSound"
        );

        generatedEnhancedCode = enhanceResult.enhancedCode(); // Store for subsequent tests
        projectJavaFile = enhanceResult.javaFilePath(); // Path should ideally be the same if overwriting

        // Assertions for the enhanced code:
        assertNotNull(generatedEnhancedCode, "Enhanced code string should not be null.");
        assertFalse(generatedEnhancedCode.isEmpty(), "Enhanced code string should not be empty.");
        assertTrue(Files.exists(projectJavaFile), "Enhanced Java file '" + projectJavaFile + "' should still exist (or be overwritten).");
        // Implicitly, the workflow should have verified the presence of keywords.

        logger.info("STEP 5: Code enhancement completed. Enhanced code written to file: {}", projectJavaFile);
    }

    @Test
    @Order(6)
    @DisplayName("Verify enhanced project and re-compilation")
    void step6_verifyAndCompileEnhancedProject() throws CodeGenerationWorkflow.WorkflowException {
        // Purpose: Tests the project compilation capability after code enhancement.
        // Feature: Demonstrates Jaider's ability to re-compile the project after modifications,
        // ensuring the enhancements are syntactically correct and integrated properly.
        logger.info("STEP 6: Verifying enhanced project and re-compilation using CodeGenerationWorkflow...");
        // Assumptions: Ensure both initial and enhanced code generation were successful.
        assumeTrue(generatedInitialCode != null && !generatedInitialCode.isEmpty(), "Initial code must have been generated (Step 1).");
        assumeTrue(generatedEnhancedCode != null && !generatedEnhancedCode.isEmpty(), "Enhanced code generation must have succeeded in Step 5 for re-compilation.");
        assumeTrue(projectDirectory != null && Files.exists(projectDirectory), "Project directory must exist for re-compilation. Current: " + projectDirectory);
        assumeTrue(projectManager.getProjectDir() != null && Files.exists(projectManager.getProjectDir()), "ProjectManager must have a valid project directory for re-compilation.");

        // Note: Verification of file content (e.g., containing new methods, imports)
        // is implicitly handled by the enhanceProject workflow method's keyword check.
        // Explicit checks can be added here for more rigorous testing if necessary:
        // e.g., verificationService.verifyCodeIsLonger(generatedInitialCode, generatedEnhancedCode, "MissileCommandGame.java");
        // e.g., verificationService.verifyCodeContains(generatedEnhancedCode, "Enhanced Code", "javax.sound.sampled", "playFireSound", "playExplosionSound");

        // Execute the compilation workflow for the enhanced project.
        BuildManagerService.BuildResult compileResult = codeGenerationWorkflow.compileProject("Enhanced project");

        // Assertions for the compilation of the enhanced project:
        assertNotNull(compileResult, "Compilation result (BuildResult) for enhanced project should not be null.");
        assertTrue(compileResult.success(), "Enhanced project compilation should be successful. Compiler output: " + compileResult.output());
        assertEquals(0, compileResult.exitCode(), "Enhanced project compilation Maven exit code should be 0 (success). Output: " + compileResult.output());
        logger.info("STEP 6: Enhanced project compilation completed successfully.");
    }
}
