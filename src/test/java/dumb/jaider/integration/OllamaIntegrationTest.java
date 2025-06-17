package dumb.jaider.integration;

import dumb.jaider.service.BuildManagerService;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files; // Added for Files.exists

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OllamaIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(OllamaIntegrationTest.class);

    private static TestConfig testConfig;
    private static OllamaService ollamaService;
    private static ProjectManager projectManager;
    private static VerificationService verificationService;

    private static String generatedInitialCode;
    private static String generatedEnhancedCode;
    private static Path projectJavaFile;


    @BeforeAll
    static void globalSetup() {
        logger.info("Starting global setup for OllamaIntegrationTest...");
        testConfig = new TestConfig(); // Uses defaults, can be configured via environment or properties if needed

        ollamaService = new OllamaService(testConfig); // Initialize service first

        // Basic check to see if Ollama might be running at the default location.
        // This doesn't guarantee the model is available or works, but is a basic sanity check.
        try {
            assumeTrue(ollamaService.chatModel != null, "OllamaChatModel failed to initialize. Check previous logs from OllamaService constructor.");
            // Try a very simple ping-like interaction
            String testPrompt = "Respond with just the word 'test'.";
            String response = ollamaService.chatModel.chat(testPrompt); // Direct access for this simple check
            assumeTrue(response != null && response.toLowerCase().contains("test"), "Ollama instance not responding as expected at " + testConfig.getOllamaBaseUrl());
            logger.info("Ollama instance responded to basic check.");
        } catch (Exception e) {
            logger.warn("Ollama instance not available or not responding at {}. Skipping tests. Error: {}", testConfig.getOllamaBaseUrl(), e.getMessage(), e);
            assumeTrue(false, "Ollama instance not available. Skipping tests. " + e.getMessage());
        }

        projectManager = new ProjectManager();
        verificationService = new VerificationService();

        try {
            projectManager.createTemporaryProject("OllamaMissileCommandTest");
            logger.info("Global setup complete. Project directory: {}", projectManager.getProjectDir());
        } catch (IOException e) {
            logger.error("Failed to create temporary project in globalSetup", e);
            Assertions.fail("Global setup failed: Could not create temporary project directory.", e);
        }
    }

    @AfterAll
    static void globalTeardown() {
        logger.info("Starting global teardown for OllamaIntegrationTest...");
        if (projectManager != null) {
            projectManager.cleanupProject();
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
    void step1_generateMissileCommand() throws IOException {
        logger.info("STEP 1: Generating Missile Command game...");
        String gameDescription = "a complete, runnable, single-file Missile Command game in Java using Swing. The main class should be named 'MissileCommandGame' and be in package 'com.example.game'.";
        generatedInitialCode = ollamaService.generateCode(gameDescription);
        assertNotNull(generatedInitialCode, "Generated code should not be null");
        Assertions.assertFalse(generatedInitialCode.isEmpty(), "Generated code should not be empty");

        logger.info("Initial code generated by Ollama. Length: {}", generatedInitialCode.length());
        verificationService.verifyCodeContains(generatedInitialCode, "Initial Code", "MissileCommandGame", "JFrame", "Swing", "java.awt");


        projectJavaFile = projectManager.saveJavaFile("com.example.game", "MissileCommandGame", generatedInitialCode);
        projectManager.createPomFile(getMissileCommandPom());
        logger.info("STEP 1: Completed.");
    }

    @Test
    @Order(2)
    @DisplayName("Verify project structure and compilation (Initial)")
    void step2_verifyAndCompileInitialProject() {
        logger.info("STEP 2: Verifying initial project structure and compilation...");
        Assumptions.assumeTrue(generatedInitialCode != null && !generatedInitialCode.isEmpty(), "Initial code generation must have succeeded.");
        Assumptions.assumeTrue(projectManager.getProjectDir() != null && Files.exists(projectManager.getProjectDir()), "Project directory must exist.");

        verificationService.verifyJavaFileExists(projectManager.getProjectDir(), "com.example.game", "MissileCommandGame");
        verificationService.verifyPomExists(projectManager.getProjectDir());

        BuildManagerService.BuildResult compileResult = projectManager.compile();
        verificationService.verifyCompilationSucceeded(compileResult, "Initial project");
        logger.info("STEP 2: Completed.");
    }

    @Test
    @Order(3)
    @DisplayName("/ask: Ask questions about the program")
    void step3_askQuestions() {
        logger.info("STEP 3: Asking questions about the program...");
        Assumptions.assumeTrue(generatedInitialCode != null && !generatedInitialCode.isEmpty(), "Initial code generation must have succeeded.");

        String question1 = "What is the main class of this game as specified in the initial prompt?";
        String answer1 = ollamaService.askQuestion(generatedInitialCode, question1);
        logger.info("Question 1: '{}' - Answer: '{}'", question1, answer1);
        assertNotNull(answer1, "Answer to question 1 should not be null");
        Assertions.assertFalse(answer1.isEmpty(), "Answer to question 1 should not be empty");
        verificationService.verifyCodeContains(answer1.toLowerCase(), "Answer 1", "missilecommandgame");


        String question2 = "How is the game window (JFrame) initialized?";
        String answer2 = ollamaService.askQuestion(generatedInitialCode, question2);
        logger.info("Question 2: '{}' - Answer: '{}'", question2, answer2);
        assertNotNull(answer2, "Answer to question 2 should not be null");
        Assertions.assertFalse(answer2.isEmpty(), "Answer to question 2 should not be empty");
        logger.info("STEP 3: Completed.");
    }

    @Test
    @Order(4)
    @DisplayName("/code: Enhance Missile Command with sound")
    void step4_enhanceWithSound() throws IOException {
        logger.info("STEP 4: Enhancing Missile Command with sound...");
        Assumptions.assumeTrue(generatedInitialCode != null && !generatedInitialCode.isEmpty(), "Initial code generation must have succeeded.");
        Assumptions.assumeTrue(projectJavaFile != null && Files.exists(projectJavaFile), "Project Java file must exist.");

        String enhancementDescription = "Add sound effects for two events: when a missile is fired and when an explosion occurs. Use javax.sound.sampled.Clip for playing sounds. Create placeholder methods playFireSound() and playExplosionSound() if actual sound file loading is complex, but include necessary imports for javax.sound.sampled.*.";
        generatedEnhancedCode = ollamaService.enhanceCode(generatedInitialCode, enhancementDescription);
        assertNotNull(generatedEnhancedCode, "Enhanced code should not be null");
        Assertions.assertFalse(generatedEnhancedCode.isEmpty(), "Enhanced code should not be empty");

        logger.info("Enhanced code generated by Ollama. Length: {}", generatedEnhancedCode.length());

        projectManager.saveJavaFile("com.example.game", "MissileCommandGame", generatedEnhancedCode); // Overwrite existing file
        logger.info("STEP 4: Completed.");
    }

    @Test
    @Order(5)
    @DisplayName("Verify enhanced project and re-compilation")
    void step5_verifyAndCompileEnhancedProject() {
        logger.info("STEP 5: Verifying enhanced project and re-compilation...");
        Assumptions.assumeTrue(generatedInitialCode != null && !generatedInitialCode.isEmpty(), "Initial code must have been generated.");
        Assumptions.assumeTrue(generatedEnhancedCode != null && !generatedEnhancedCode.isEmpty(), "Enhanced code generation must have succeeded.");
        Assumptions.assumeTrue(projectManager.getProjectDir() != null && Files.exists(projectManager.getProjectDir()), "Project directory must exist.");

        verificationService.verifyJavaFileExists(projectManager.getProjectDir(), "com.example.game", "MissileCommandGame"); // File should still exist
        verificationService.verifyCodeIsLonger(generatedInitialCode, generatedEnhancedCode, "MissileCommandGame.java");
        verificationService.verifyCodeContains(generatedEnhancedCode, "Enhanced Code", "javax.sound.sampled", "playFireSound", "playExplosionSound");

        BuildManagerService.BuildResult compileResult = projectManager.compile();
        verificationService.verifyCompilationSucceeded(compileResult, "Enhanced project");
        logger.info("STEP 5: Completed.");
    }
}
