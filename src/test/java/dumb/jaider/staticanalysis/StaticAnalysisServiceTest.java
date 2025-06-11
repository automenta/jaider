package dumb.jaider.staticanalysis;

import dumb.jaider.toolmanager.ToolDescriptor;
import dumb.jaider.toolmanager.ToolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaticAnalysisServiceTest {

    @Mock
    private ToolManager toolManager;

    @Mock
    private ToolDescriptor toolDescriptor; // Reusable mock for some tests

    @Mock
    private Process process; // Reusable mock for process

    @InjectMocks
    private StaticAnalysisService staticAnalysisService;

    private Path targetPath;

    @BeforeEach
    void setUp() {
        targetPath = Paths.get("/path/to/target");
        // Reset mocks if necessary, though MockitoExtension does some of this.
        // Mockito.reset(toolManager, toolDescriptor, process);
    }

    @Test
    void getAvailableAnalyzers_shouldReturnFilteredAnalyzers() {
        ToolDescriptor analyzer1 = mock(ToolDescriptor.class);
        when(analyzer1.getCategory()).thenReturn("static-analyzer");
        when(analyzer1.getToolName()).thenReturn("Analyzer1");

        ToolDescriptor nonAnalyzer = mock(ToolDescriptor.class);
        when(nonAnalyzer.getCategory()).thenReturn("formatter");
        when(nonAnalyzer.getToolName()).thenReturn("Formatter1");

        ToolDescriptor analyzer2 = mock(ToolDescriptor.class);
        when(analyzer2.getCategory()).thenReturn("STATIC-ANALYZER"); // Test case-insensitivity
        when(analyzer2.getToolName()).thenReturn("Analyzer2");

        Map<String, ToolDescriptor> descriptors = Map.of(
                "Analyzer1", analyzer1,
                "Formatter1", nonAnalyzer,
                "Analyzer2", analyzer2
        );
        when(toolManager.getToolDescriptors()).thenReturn(descriptors);

        List<ToolDescriptor> result = staticAnalysisService.getAvailableAnalyzers();

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(td -> td.getToolName().equals("Analyzer1")));
        assertTrue(result.stream().anyMatch(td -> td.getToolName().equals("Analyzer2")));
        assertFalse(result.stream().anyMatch(td -> td.getToolName().equals("Formatter1")));
    }

    @Test
    void runAnalysis_successfulSemgrepCase() throws Exception {
        String toolName = "Semgrep";
        String commandPattern = "semgrep scan --config {semgrepConfig} --json {targetPath}";
        String parserClass = "dumb.jaider.staticanalysis.SemgrepResultsParser"; // Actual class for this test
        Map<String, Object> defaultConfig = Map.of("semgrepConfig", "auto");
        String sampleOutput = "{\"results\": []}"; // Minimal valid Semgrep JSON

        when(toolManager.getToolDescriptor(toolName)).thenReturn(toolDescriptor);
        when(toolDescriptor.getCategory()).thenReturn("static-analyzer");
        when(toolManager.provisionTool(toolName)).thenReturn(true);
        when(toolDescriptor.getAnalysisCommandPattern()).thenReturn(commandPattern);
        when(toolDescriptor.getDefaultConfig()).thenReturn(defaultConfig);
        when(toolDescriptor.getResultsParserClass()).thenReturn(parserClass);

        // Mock ProcessBuilder and Process
        try (MockedConstruction<ProcessBuilder> mockedPbConstruction = Mockito.mockConstruction(
                ProcessBuilder.class,
                (mock, context) -> {
                    when(mock.directory(any(File.class))).thenReturn(mock); // Chain call
                    when(mock.start()).thenReturn(process);
                })) {

            when(process.getInputStream()).thenReturn(new ByteArrayInputStream(sampleOutput.getBytes(StandardCharsets.UTF_8)));
            when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
            when(process.waitFor()).thenReturn(0);

            // Mock the SemgrepResultsParser if it's too complex, or use the real one
            // For this test, we'll use the real one and expect it to parse correctly.
            // If SemgrepResultsParser had external dependencies or complex logic, mocking it would be better.
            // For now, assume SemgrepResultsParser is simple enough.
            List<StaticAnalysisIssue> expectedIssues = Collections.emptyList(); // Based on "{\"results\": []}"

            List<StaticAnalysisIssue> actualIssues = staticAnalysisService.runAnalysis(toolName, targetPath, Collections.emptyMap());

            assertEquals(expectedIssues, actualIssues);

            // Verify ProcessBuilder was called correctly
            ProcessBuilder constructedPb = mockedPbConstruction.constructed().get(0);
            verify(constructedPb).command(eq(List.of("semgrep", "scan", "--config", "auto", "--json", "/path/to/target")));

            // Verify that the parser was instantiated and used (implicitly tested by getting results)
            // To explicitly verify, you might need to mock the parser if it were passed in or if this was a more integrated test.
        }
    }

    @Test
    void runAnalysis_successfulSemgrepCaseWithRuntimeOption() throws Exception {
        String toolName = "Semgrep";
        String commandPattern = "semgrep scan --config {semgrepConfig} --json {targetPath}";
        String parserClass = "dumb.jaider.staticanalysis.SemgrepResultsParser";
        Map<String, Object> defaultConfig = Map.of("semgrepConfig", "auto");
        String sampleOutput = "{\"results\": []}";
        Map<String, String> runtimeOptions = Map.of("semgrepConfig", "custom_rules");


        when(toolManager.getToolDescriptor(toolName)).thenReturn(toolDescriptor);
        when(toolDescriptor.getCategory()).thenReturn("static-analyzer");
        when(toolManager.provisionTool(toolName)).thenReturn(true);
        when(toolDescriptor.getAnalysisCommandPattern()).thenReturn(commandPattern);
        when(toolDescriptor.getDefaultConfig()).thenReturn(defaultConfig); // Default config
        when(toolDescriptor.getResultsParserClass()).thenReturn(parserClass);


        try (MockedConstruction<ProcessBuilder> mockedPbConstruction = Mockito.mockConstruction(
                ProcessBuilder.class,
                (mock, context) -> {
                    when(mock.directory(any(File.class))).thenReturn(mock);
                    when(mock.start()).thenReturn(process);
                })) {

            when(process.getInputStream()).thenReturn(new ByteArrayInputStream(sampleOutput.getBytes(StandardCharsets.UTF_8)));
            when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
            when(process.waitFor()).thenReturn(0);

            staticAnalysisService.runAnalysis(toolName, targetPath, runtimeOptions);

            ProcessBuilder constructedPb = mockedPbConstruction.constructed().get(0);
            verify(constructedPb).command(eq(List.of("semgrep", "scan", "--config", "custom_rules", "--json", "/path/to/target")));
        }
    }


    @Test
    void runAnalysis_toolNotFound() {
        String toolName = "UnknownTool";
        when(toolManager.getToolDescriptor(toolName)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            staticAnalysisService.runAnalysis(toolName, targetPath, Collections.emptyMap());
        });
        assertEquals("No descriptor found for tool: " + toolName, exception.getMessage());
    }

    @Test
    void runAnalysis_toolNotStaticAnalyzer() {
        String toolName = "NotAnAnalyzer";
        when(toolManager.getToolDescriptor(toolName)).thenReturn(toolDescriptor);
        when(toolDescriptor.getCategory()).thenReturn("formatter"); // Not a static-analyzer

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            staticAnalysisService.runAnalysis(toolName, targetPath, Collections.emptyMap());
        });
        assertEquals("Tool " + toolName + " is not categorized as a static-analyzer.", exception.getMessage());
    }

    @Test
    void runAnalysis_provisioningFails() {
        String toolName = "Semgrep";
        when(toolManager.getToolDescriptor(toolName)).thenReturn(toolDescriptor);
        when(toolDescriptor.getCategory()).thenReturn("static-analyzer");
        when(toolManager.provisionTool(toolName)).thenReturn(false); // Provisioning fails

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            staticAnalysisService.runAnalysis(toolName, targetPath, Collections.emptyMap());
        });
        assertEquals("Tool " + toolName + " is not available or could not be installed.", exception.getMessage());
    }

    @Test
    void runAnalysis_commandPatternMissing() {
        String toolName = "ToolWithNoCommand";
        when(toolManager.getToolDescriptor(toolName)).thenReturn(toolDescriptor);
        when(toolDescriptor.getCategory()).thenReturn("static-analyzer");
        when(toolManager.provisionTool(toolName)).thenReturn(true);
        when(toolDescriptor.getAnalysisCommandPattern()).thenReturn(null); // Command pattern is missing

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            staticAnalysisService.runAnalysis(toolName, targetPath, Collections.emptyMap());
        });
        assertEquals("Analysis command pattern is not defined for tool: " + toolName, exception.getMessage());
    }


    @Test
    void runAnalysis_parserClassNotFound() throws InterruptedException {
        String toolName = "Semgrep";
        String invalidParserClass = "com.example.NonExistentParser";
        when(toolManager.getToolDescriptor(toolName)).thenReturn(toolDescriptor);
        when(toolDescriptor.getCategory()).thenReturn("static-analyzer");
        when(toolManager.provisionTool(toolName)).thenReturn(true);
        when(toolDescriptor.getAnalysisCommandPattern()).thenReturn("semgrep scan {targetPath}");
        when(toolDescriptor.getResultsParserClass()).thenReturn(invalidParserClass);
        when(toolDescriptor.getDefaultConfig()).thenReturn(Collections.emptyMap());


        try (MockedConstruction<ProcessBuilder> mockedPbConstruction = Mockito.mockConstruction(
                ProcessBuilder.class,
                (mock, context) -> {
                    when(mock.directory(any(File.class))).thenReturn(mock); // Chain call
                    when(mock.start()).thenReturn(process);
                })) {
            when(process.getInputStream()).thenReturn(new ByteArrayInputStream("output".getBytes(StandardCharsets.UTF_8)));
            when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
            when(process.waitFor()).thenReturn(0);

            Exception exception = assertThrows(Exception.class, () -> {
                staticAnalysisService.runAnalysis(toolName, targetPath, Collections.emptyMap());
            });
            assertTrue(exception.getMessage().contains("Error with results parser " + invalidParserClass));
            assertTrue(exception.getCause() instanceof ClassNotFoundException);
        }
    }

    @Test
    void runAnalysis_processExecutionError() throws Exception {
        String toolName = "Semgrep";
        when(toolManager.getToolDescriptor(toolName)).thenReturn(toolDescriptor);
        when(toolDescriptor.getCategory()).thenReturn("static-analyzer");
        when(toolManager.provisionTool(toolName)).thenReturn(true);
        when(toolDescriptor.getAnalysisCommandPattern()).thenReturn("semgrep scan {targetPath}");
        when(toolDescriptor.getResultsParserClass()).thenReturn("dumb.jaider.staticanalysis.SemgrepResultsParser");
         when(toolDescriptor.getDefaultConfig()).thenReturn(Map.of("semgrepConfig", "auto"));


        try (MockedConstruction<ProcessBuilder> mockedPbConstruction = Mockito.mockConstruction(
                ProcessBuilder.class,
                (mock, context) -> {
                    when(mock.directory(any(File.class))).thenReturn(mock);
                    // Simulate ProcessBuilder.start() throwing an IOException
                    when(mock.start()).thenThrow(new IOException("Failed to start process"));
                })) {

            Exception exception = assertThrows(IOException.class, () -> { // Expecting IOException directly from pb.start()
                staticAnalysisService.runAnalysis(toolName, targetPath, Collections.emptyMap());
            });
            assertEquals("Failed to start process", exception.getMessage());
        }
    }

    @Test
    void runAnalysis_noParserDefinedReturnsRawOutput() throws Exception {
        String toolName = "RawTool";
        String commandPattern = "rawtool {targetPath}";
        String rawToolOutput = "This is some raw output from the tool.";

        when(toolManager.getToolDescriptor(toolName)).thenReturn(toolDescriptor);
        when(toolDescriptor.getCategory()).thenReturn("static-analyzer");
        when(toolManager.provisionTool(toolName)).thenReturn(true);
        when(toolDescriptor.getAnalysisCommandPattern()).thenReturn(commandPattern);
        when(toolDescriptor.getResultsParserClass()).thenReturn(null); // No parser defined
        when(toolDescriptor.getDefaultConfig()).thenReturn(Collections.emptyMap());


        try (MockedConstruction<ProcessBuilder> mockedPbConstruction = Mockito.mockConstruction(
                ProcessBuilder.class,
                (mock, context) -> {
                    when(mock.directory(any(File.class))).thenReturn(mock);
                    when(mock.start()).thenReturn(process);
                })) {

            when(process.getInputStream()).thenReturn(new ByteArrayInputStream(rawToolOutput.getBytes(StandardCharsets.UTF_8)));
            when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
            when(process.waitFor()).thenReturn(0);

            List<StaticAnalysisIssue> issues = staticAnalysisService.runAnalysis(toolName, targetPath, Collections.emptyMap());

            assertEquals(1, issues.size());
            StaticAnalysisIssue issue = issues.get(0);
            assertEquals(targetPath.toString(), issue.getFilePath());
            assertEquals("RAW_OUTPUT", issue.getRuleId());
            assertTrue(issue.getMessage().contains(rawToolOutput));
        }
    }
}
