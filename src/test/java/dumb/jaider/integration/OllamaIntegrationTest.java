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
        testConfig = new TestConfig();
        ollamaService = new OllamaService(testConfig);

        try {
            assumeTrue(ollamaService.chatModel != null, "OllamaChatModel failed to initialize. Check logs from OllamaService constructor.");
            String testPrompt = "Respond with just the word 'test'.";
            String response = ollamaService.chatModel.chat(testPrompt);
            assumeTrue(response != null && response.toLowerCase().contains("test"), "Ollama instance not responding as expected at " + testConfig.getOllamaBaseUrl());
            logger.info("Ollama instance responded to basic check.");
        } catch (Exception e) {
            logger.warn("Ollama instance not available or not responding at {}. Skipping tests. Error: {}", testConfig.getOllamaBaseUrl(), e.getMessage(), e);
            assumeTrue(false, "Ollama instance not available. Skipping tests. " + e.getMessage());
        }

        projectManager = new ProjectManager();
        verificationService = new VerificationService();

        // Instantiate the workflow class
        codeGenerationWorkflow = new CodeGenerationWorkflow(ollamaService, projectManager, verificationService);

        try {
            // Create the temporary project directory. This is a prerequisite for the workflow methods.
            projectDirectory = projectManager.createTemporaryProject("OllamaMissileCommandTest");
            logger.info("Global setup complete. Project directory: {}", projectDirectory);
        } catch (IOException e) {
            logger.error("Failed to create temporary project in globalSetup", e);
            fail("Global setup failed: Could not create temporary project directory.", e);
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
        logger.info("STEP 1: Generating Missile Command game using CodeGenerationWorkflow...");
        String gameDescription = "a complete, runnable, single-file Missile Command game in Java using Swing. The main class should be named 'MissileCommandGame' and be in package 'com.example.game'.";

        CodeGenerationWorkflow.ProjectGenerationResult result = codeGenerationWorkflow.generateInitialProject(
            gameDescription,
            "com.example.game",
            "MissileCommandGame",
            getMissileCommandPom(),
            // Verification keywords for the generated code:
            "MissileCommandGame", "JFrame", "Swing", "java.awt"
        );

        assertNotNull(result, "ProjectGenerationResult should not be null");
        generatedInitialCode = result.generatedCode();
        projectJavaFile = result.javaFilePath();
        // projectDirectory is already set in globalSetup and available to projectManager

        assertNotNull(generatedInitialCode, "Generated code should not be null");
        assertFalse(generatedInitialCode.isEmpty(), "Generated code should not be empty");
        assertNotNull(projectJavaFile, "Project Java file path should not be null");
        assertTrue(Files.exists(projectJavaFile), "Project Java file should exist");

        logger.info("STEP 1: Completed.");
    }

    @Test
    @Order(2)
    @DisplayName("Verify project structure and compilation (Initial)")
    void step2_verifyAndCompileInitialProject() throws CodeGenerationWorkflow.WorkflowException {
        logger.info("STEP 2: Verifying initial project structure and compilation using CodeGenerationWorkflow...");
        assumeTrue(generatedInitialCode != null && !generatedInitialCode.isEmpty(), "Initial code generation must have succeeded in Step 1.");
        assumeTrue(projectDirectory != null && Files.exists(projectDirectory), "Project directory must exist.");
        assumeTrue(projectManager.getProjectDir() != null && Files.exists(projectManager.getProjectDir()), "ProjectManager must have a valid project directory.");


        // Verification of file existence is implicitly handled by the workflow's compile step (and generateInitialProject)
        // but can be explicitly called if needed:
        // verificationService.verifyJavaFileExists(projectDirectory, "com.example.game", "MissileCommandGame");
        // verificationService.verifyPomExists(projectDirectory);

        BuildManagerService.BuildResult compileResult = codeGenerationWorkflow.compileProject("Initial project");

        assertNotNull(compileResult, "Compilation result should not be null");
        assertTrue(compileResult.success(), "Initial project compilation should be successful. Output: " + compileResult.output());
        assertEquals(0, compileResult.exitCode(), "Initial project compilation exit code should be 0.");
        logger.info("STEP 2: Completed.");
    }

    @Test
    @Order(3)
    @DisplayName("/ask: Ask questions about the program")
    void step3_askQuestions() throws CodeGenerationWorkflow.WorkflowException {
        logger.info("STEP 3: Asking questions about the program using CodeGenerationWorkflow...");
        assumeTrue(generatedInitialCode != null && !generatedInitialCode.isEmpty(), "Initial code generation must have succeeded.");

        String question1 = "What is the main class of this game as specified in the initial prompt?";
        CodeGenerationWorkflow.AskQuestionResult answer1Result = codeGenerationWorkflow.askQuestionAboutCode(
            generatedInitialCode,
            question1,
            "missilecommandgame" // Keywords for answer verification
        );
        String answer1 = answer1Result.answer();
        logger.info("Question 1: '{}' - Answer: '{}'", question1, answer1);
        assertNotNull(answer1, "Answer to question 1 should not be null");
        assertFalse(answer1.isEmpty(), "Answer to question 1 should not be empty");
        // Specific assertion on content already handled by workflow if keywords are provided

        String question2 = "How is the game window (JFrame) initialized?";
        CodeGenerationWorkflow.AskQuestionResult answer2Result = codeGenerationWorkflow.askQuestionAboutCode(
            generatedInitialCode,
            question2
            // No specific keywords for this answer verification in this test
        );
        String answer2 = answer2Result.answer();
        logger.info("Question 2: '{}' - Answer: '{}'", question2, answer2);
        assertNotNull(answer2, "Answer to question 2 should not be null");
        assertFalse(answer2.isEmpty(), "Answer to question 2 should not be empty");
        logger.info("STEP 3: Completed.");
    }

    @Test
    @Order(4)
    @DisplayName("/code: Enhance Missile Command with sound")
    void step4_enhanceWithSound() throws CodeGenerationWorkflow.WorkflowException {
        logger.info("STEP 4: Enhancing Missile Command with sound using CodeGenerationWorkflow...");
        assumeTrue(generatedInitialCode != null && !generatedInitialCode.isEmpty(), "Initial code generation must have succeeded.");
        assumeTrue(projectJavaFile != null && Files.exists(projectJavaFile), "Project Java file must exist for enhancement.");

        String enhancementDescription = "Add sound effects for two events: when a missile is fired and when an explosion occurs. Use javax.sound.sampled.Clip for playing sounds. Create placeholder methods playFireSound() and playExplosionSound() if actual sound file loading is complex, but include necessary imports for javax.sound.sampled.*.";

        CodeGenerationWorkflow.EnhanceProjectResult enhanceResult = codeGenerationWorkflow.enhanceProject(
            generatedInitialCode,
            enhancementDescription,
            "com.example.game",
            "MissileCommandGame",
            // Verification keywords for the enhanced code:
            "javax.sound.sampled", "playFireSound", "playExplosionSound"
        );

        generatedEnhancedCode = enhanceResult.enhancedCode();
        projectJavaFile = enhanceResult.javaFilePath(); // Update projectJavaFile path if it changed (it shouldn't for overwrite)

        assertNotNull(generatedEnhancedCode, "Enhanced code should not be null");
        assertFalse(generatedEnhancedCode.isEmpty(), "Enhanced code should not be empty");
        assertTrue(Files.exists(projectJavaFile), "Enhanced Java file should exist");

        logger.info("STEP 4: Completed.");
    }

    @Test
    @Order(5)
    @DisplayName("Verify enhanced project and re-compilation")
    void step5_verifyAndCompileEnhancedProject() throws CodeGenerationWorkflow.WorkflowException {
        logger.info("STEP 5: Verifying enhanced project and re-compilation using CodeGenerationWorkflow...");
        assumeTrue(generatedInitialCode != null && !generatedInitialCode.isEmpty(), "Initial code must have been generated.");
        assumeTrue(generatedEnhancedCode != null && !generatedEnhancedCode.isEmpty(), "Enhanced code generation must have succeeded in Step 4.");
        assumeTrue(projectDirectory != null && Files.exists(projectDirectory), "Project directory must exist.");
        assumeTrue(projectManager.getProjectDir() != null && Files.exists(projectManager.getProjectDir()), "ProjectManager must have a valid project directory.");

        // Verification of file existence and content (longer, keywords) are handled by enhanceProject workflow method.
        // verificationService.verifyJavaFileExists(projectDirectory, "com.example.game", "MissileCommandGame");
        // verificationService.verifyCodeIsLonger(generatedInitialCode, generatedEnhancedCode, "MissileCommandGame.java");
        // verificationService.verifyCodeContains(generatedEnhancedCode, "Enhanced Code", "javax.sound.sampled", "playFireSound", "playExplosionSound");

        BuildManagerService.BuildResult compileResult = codeGenerationWorkflow.compileProject("Enhanced project");

        assertNotNull(compileResult, "Compilation result for enhanced project should not be null");
        assertTrue(compileResult.success(), "Enhanced project compilation should be successful. Output: " + compileResult.output());
        assertEquals(0, compileResult.exitCode(), "Enhanced project compilation exit code should be 0.");
        logger.info("STEP 5: Completed.");
    }
}
