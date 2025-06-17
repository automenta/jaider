package dumb.jaider.integration;

import dumb.jaider.service.BuildManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*; // Using JUnit 5 assertions

public class VerificationService {
    private static final Logger logger = LoggerFactory.getLogger(VerificationService.class);

    public void verifyFileExists(Path filePath, String fileDescription) {
        logger.info("Verifying existence of {}: {}", fileDescription, filePath);
        assertTrue(Files.exists(filePath), fileDescription + " should exist at " + filePath);
    }

    public void verifyFileNotEmpty(Path filePath, String fileDescription) {
        logger.info("Verifying {} is not empty: {}", fileDescription, filePath);
        verifyFileExists(filePath, fileDescription); // Prerequisite
        try {
            assertTrue(Files.size(filePath) > 0, fileDescription + " should not be empty. Path: " + filePath);
            logger.info("{} at {} is not empty (size: {} bytes).", fileDescription, filePath, Files.size(filePath));
        } catch (IOException e) {
            fail("IOException while checking file size for " + filePath + ": " + e.getMessage());
        }
    }

    public void verifyJavaFileExists(Path projectDir, String packageName, String className) {
        Path javaFilePath = projectDir.resolve("src").resolve("main").resolve("java")
                                     .resolve(packageName.replace('.', '/'))
                                     .resolve(className + ".java");
        logger.info("Verifying existence of Java file: {}", javaFilePath);
        verifyFileExists(javaFilePath, "Java file " + className + ".java");
        verifyFileNotEmpty(javaFilePath, "Java file " + className + ".java");
    }

    public void verifyPomExists(Path projectDir) {
        Path pomPath = projectDir.resolve("pom.xml");
        logger.info("Verifying existence of pom.xml: {}", pomPath);
        verifyFileExists(pomPath, "pom.xml file");
        verifyFileNotEmpty(pomPath, "pom.xml file");
    }

    public void verifyCompilationSucceeded(BuildManagerService.BuildResult result, String compileStepName) {
        logger.info("Verifying {} compilation succeeded.", compileStepName);
        assertNotNull(result, compileStepName + " compilation result should not be null.");
        if (!result.success()) {
            logger.error("{} compilation failed. Output:\n{}", compileStepName, result.output());
        }
        assertTrue(result.success(), compileStepName + " compilation should be successful. Output: " + result.output());
        assertEquals(0, result.exitCode(), compileStepName + " compilation exit code should be 0.");
        logger.info("{} compilation successful.", compileStepName);
    }

    public void verifyCodeContains(String code, String fileDescription, String... keywords) {
        logger.info("Verifying {} code contains keywords: {}", fileDescription, String.join(", ", keywords));
        assertNotNull(code, fileDescription + " code should not be null.");
        for (String keyword : keywords) {
            assertTrue(code.contains(keyword), fileDescription + " code should contain keyword: '" + keyword + "'");
        }
        logger.info("{} code contains all specified keywords.", fileDescription);
    }

    public void verifyCodeIsLonger(String oldCode, String newCode, String fileDescription) {
        logger.info("Verifying {} code is longer after enhancement.", fileDescription);
        assertNotNull(oldCode, "Old " + fileDescription + " code should not be null.");
        assertNotNull(newCode, "New " + fileDescription + " code should not be null.");
        assertTrue(newCode.length() > oldCode.length(), fileDescription + " code should be longer. Old length: " + oldCode.length() + ", New length: " + newCode.length());
        logger.info("{} code is longer (Old: {}, New: {}).", fileDescription, oldCode.length(), newCode.length());
    }
}
