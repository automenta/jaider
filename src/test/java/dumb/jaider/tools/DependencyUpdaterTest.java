package dumb.jaider.tools;

import dumb.jaider.tooling.ToolContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DependencyUpdaterTest {

    private DependencyUpdater dependencyUpdater;
    private ToolContext mockToolContext;
    private Path projectRoot;

    @TempDir
    Path tempDir; // JUnit 5 temporary directory

    @BeforeEach
    void setUp() throws IOException {
        dependencyUpdater = new DependencyUpdater();
        projectRoot = tempDir.resolve("test-project");
        Files.createDirectories(projectRoot);
        mockToolContext = new ToolContext(projectRoot);
    }

    @Test
    void testGetName() {
        assertEquals("DependencyUpdater", dependencyUpdater.getName());
    }

    @Test
    void testGetDescription() {
        assertTrue(dependencyUpdater.getDescription().contains("Identifies outdated Maven dependencies"));
    }

    @Test
    void testIsAvailable_mavenInstalled() {
        // This test assumes 'mvn --version' runs successfully and returns 0
        // In a real CI environment, Maven should be installed.
        // For local tests, this might be flaky if Maven isn't in PATH or configured.
        // Consider mocking ProcessBuilder if more control is needed, but that's more complex.
        assertTrue(dependencyUpdater.isAvailable(), "Maven should be available for this test to pass");
    }

    @Test
    void testParseOutput_validJsonUpdates() {
        var jsonInput = new JSONArray()
            .put(new JSONObject()
                .put("groupId", "group1")
                .put("artifactId", "artifact1")
                .put("currentVersion", "1.0")
                .put("newVersion", "1.1")
                .put("diff", "--- a/pom.xml\n+++ b/pom.xml\n@@ -1,1 +1,1 @@\n-version 1.0\n+version 1.1"))
            .toString();

        var result = dependencyUpdater.parseOutput(jsonInput);
        assertNotNull(result);
        assertInstanceOf(List.class, result);
        var updates = (List<Map<String, String>>) result;
        assertEquals(1, updates.size());
        assertEquals("group1", updates.getFirst().get("groupId"));
        assertEquals("1.1", updates.getFirst().get("newVersion"));
        assertTrue(updates.getFirst().get("diff").contains("+version 1.1"));
    }

    @Test
    void testParseOutput_mavenCommandFailedError() {
        var errorInput = "Maven command failed with exit code 1...";
        var result = dependencyUpdater.parseOutput(errorInput);
        assertInstanceOf(List.class, result);
        var updates = (List<Map<String, String>>) result;
        assertEquals(1, updates.size());
        assertTrue(updates.getFirst().containsKey("error"));
        assertTrue(updates.getFirst().get("details").contains("Maven command failed"));
    }

    @Test
    void testParseOutput_noUpdatesFoundRaw() {
        var rawInput = "[INFO] --- versions-maven-plugin:2.8.1:display-dependency-updates (default-cli) @ jaider ---" + System.lineSeparator() +
                          "[INFO] No dependencies found that satisfy the filters";
        var result = dependencyUpdater.parseOutput(rawInput);
        assertInstanceOf(List.class, result);
        var updates = (List<Map<String, String>>) result;
        assertTrue(updates.isEmpty(), "Expected empty list when no updates are found in raw output");
    }

    @Test
    void testParseOutput_emptyJsonArray() { // For when execute returns "[]"
        var jsonInput = "[]";
        var result = dependencyUpdater.parseOutput(jsonInput);
        assertInstanceOf(List.class, result);
        var updates = (List<Map<String, String>>) result;
        assertTrue(updates.isEmpty());
    }

    // More detailed tests for the execute method would be complex due to ProcessBuilder.
    // They would ideally involve:
    // 1. Mocking ProcessBuilder and Process to control Maven command output.
    // 2. Writing a sample pom.xml to tempDir.
    // 3. Verifying that the execute method reads the pom.xml, processes mocked Maven output,
    //    and generates the correct JSON string with diffs.

    // Example of a more involved execute test (conceptual):
    /*
    @Test
    void testExecute_withUpdatesAndDiffGeneration() throws Exception {
        // 1. Setup: Write a sample pom.xml
        String pomContent = """
        <project>
            <modelVersion>4.0.0</modelVersion>
            <groupId>com.example</groupId>
            <artifactId>test-pom</artifactId>
            <version>1.0.0</version>
            <dependencies>
                <dependency>
                    <groupId>group1</groupId>
                    <artifactId>artifact1</artifactId>
                    <version>1.0</version>
                </dependency>
            </dependencies>
        </project>
        """;
        Path pomFile = projectRoot.resolve("pom.xml");
        Files.writeString(pomFile, pomContent);

        // 2. Mock ProcessBuilder and Process
        Process mockProcess = Mockito.mock(Process.class);
        ProcessBuilder mockPb = Mockito.mock(ProcessBuilder.class);
        // ... somehow intercept new ProcessBuilder("mvn", ...) and return mockPb ...
        // This usually requires PowerMockito or a similar framework for static/constructor mocking,
        // or refactoring DependencyUpdater to accept a ProcessBuilderFactory.

        String mavenOutput = """
        [INFO] --- versions-maven-plugin:2.8.1:display-dependency-updates (default-cli) @ jaider ---
        [INFO] The following dependencies in Dependencies have newer versions:
        [INFO]   group1:artifact1 ........................................ 1.0 -> 1.1
        """;
        InputStream inputStream = new ByteArrayInputStream(mavenOutput.getBytes());
        InputStream errorStream = new ByteArrayInputStream("".getBytes());

        when(mockProcess.getInputStream()).thenReturn(inputStream);
        when(mockProcess.getErrorStream()).thenReturn(errorStream);
        when(mockProcess.waitFor()).thenReturn(0);
        // when(mockPb.start()).thenReturn(mockProcess); // If ProcessBuilder could be mocked

        // 3. Execute (this is the hard part to test without heavy mocking or refactoring)
        // String jsonResult = dependencyUpdater.execute(mockToolContext);

        // 4. Assertions
        // JSONArray updates = new JSONArray(jsonResult);
        // assertEquals(1, updates.length());
        // JSONObject update = updates.getJSONObject(0);
        // assertEquals("group1", update.getString("groupId"));
        // assertEquals("artifact1", update.getString("artifactId"));
        // assertEquals("1.0", update.getString("currentVersion"));
        // assertEquals("1.1", update.getString("newVersion"));
        // String diff = update.getString("diff");
        // assertTrue(diff.contains("- <version>1.0</version>"));
        // assertTrue(diff.contains("+ <version>1.1</version>"));
    }
    */
}
