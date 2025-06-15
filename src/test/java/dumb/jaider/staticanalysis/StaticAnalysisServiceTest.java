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
import org.mockito.ArgumentCaptor; // Added import
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays; // Added import
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaticAnalysisServiceTest {

    @Mock
    private ToolManager toolManager;

    // Removed class-level @Mock for ToolDescriptor; will be created locally in tests
    // @Mock
    // private ToolDescriptor toolDescriptor;

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
        lenient().when(analyzer1.getCategory()).thenReturn("static-analyzer");
        lenient().when(analyzer1.getToolName()).thenReturn("Analyzer1");

        ToolDescriptor nonAnalyzer = mock(ToolDescriptor.class);
        lenient().when(nonAnalyzer.getCategory()).thenReturn("formatter");
        lenient().when(nonAnalyzer.getToolName()).thenReturn("Formatter1");

        ToolDescriptor analyzer2 = mock(ToolDescriptor.class);
        lenient().when(analyzer2.getCategory()).thenReturn("STATIC-ANALYZER"); // Test case-insensitivity
        lenient().when(analyzer2.getToolName()).thenReturn("Analyzer2");

        Map<String, ToolDescriptor> descriptors = Map.of(
                "Analyzer1", analyzer1,
                "Formatter1", nonAnalyzer,
                "Analyzer2", analyzer2
        );
        lenient().when(toolManager.getToolDescriptors()).thenReturn(descriptors); // toolManager is @Mock

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

        ToolDescriptor localToolDescriptor = mock(ToolDescriptor.class); // Local mock
        when(toolManager.getToolDescriptor(toolName)).thenReturn(localToolDescriptor);
        when(localToolDescriptor.getCategory()).thenReturn("static-analyzer");
        when(toolManager.provisionTool(toolName)).thenReturn(true);
        when(localToolDescriptor.getAnalysisCommandPattern()).thenReturn(commandPattern);
        when(localToolDescriptor.getDefaultConfig()).thenReturn(defaultConfig);
        when(localToolDescriptor.getResultsParserClass()).thenReturn(parserClass);

        // Mock ProcessBuilder and Process
        try (MockedConstruction<ProcessBuilder> mockedPbConstruction = Mockito.mockConstruction(
                ProcessBuilder.class,
                (mock, context) -> {
                    Object rawCommandArg = context.arguments().get(0);
                    List<String> actualCommand;
                    if (rawCommandArg instanceof List) {
                        actualCommand = (List<String>) rawCommandArg;
                    } else if (rawCommandArg instanceof String[]) {
                        actualCommand = Arrays.asList((String[]) rawCommandArg);
                    } else {
                        fail("Unexpected command argument type: " + rawCommandArg.getClass().getName());
                        return;
                    }
                    // Expected command for this test
                    List<String> expectedCommand = List.of("semgrep", "scan", "--config", "auto", "--json", targetPath.toAbsolutePath().toString());
                    assertEquals(expectedCommand, actualCommand, "ProcessBuilder command mismatch");

                    when(mock.directory(any(File.class))).thenReturn(mock); // Chain call
                    when(mock.start()).thenReturn(process);
                })) {

            when(process.getInputStream()).thenReturn(new ByteArrayInputStream(sampleOutput.getBytes(StandardCharsets.UTF_8)));
            when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
            when(process.waitFor()).thenReturn(0);

            List<StaticAnalysisIssue> expectedIssues = Collections.emptyList();
            List<StaticAnalysisIssue> actualIssues = staticAnalysisService.runAnalysis(toolName, targetPath, Collections.emptyMap());
            assertEquals(expectedIssues, actualIssues);

            // Verification of command is now done inside the mockConstruction context
            // ProcessBuilder constructedPb = mockedPbConstruction.constructed().getFirst();
            // No need to verify constructedPb.command() separately if verifying constructor args.
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

        ToolDescriptor localToolDescriptor = mock(ToolDescriptor.class); // Local mock
        when(toolManager.getToolDescriptor(toolName)).thenReturn(localToolDescriptor);
        when(localToolDescriptor.getCategory()).thenReturn("static-analyzer");
        when(toolManager.provisionTool(toolName)).thenReturn(true);
        when(localToolDescriptor.getAnalysisCommandPattern()).thenReturn(commandPattern);
        when(localToolDescriptor.getDefaultConfig()).thenReturn(defaultConfig); // Default config
        when(localToolDescriptor.getResultsParserClass()).thenReturn(parserClass);


        try (MockedConstruction<ProcessBuilder> mockedPbConstruction = Mockito.mockConstruction(
                ProcessBuilder.class,
                (mock, context) -> {
                    Object rawCommandArg = context.arguments().get(0);
                    List<String> actualCommand;
                    if (rawCommandArg instanceof List) {
                        actualCommand = (List<String>) rawCommandArg;
                    } else if (rawCommandArg instanceof String[]) {
                        actualCommand = Arrays.asList((String[]) rawCommandArg);
                    } else {
                        fail("Unexpected command argument type: " + rawCommandArg.getClass().getName());
                        return;
                    }
                    List<String> expectedCommand = List.of("semgrep", "scan", "--config", "custom_rules", "--json", targetPath.toAbsolutePath().toString());
                    assertEquals(expectedCommand, actualCommand, "ProcessBuilder command mismatch for runtime options");

                    when(mock.directory(any(File.class))).thenReturn(mock);
                    when(mock.start()).thenReturn(process);
                })) {

            when(process.getInputStream()).thenReturn(new ByteArrayInputStream(sampleOutput.getBytes(StandardCharsets.UTF_8)));
            when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
            when(process.waitFor()).thenReturn(0);

            staticAnalysisService.runAnalysis(toolName, targetPath, runtimeOptions);

            // Verification of command is now done inside the mockConstruction context
        }
    }


    @Test
    void runAnalysis_toolNotFound() {
        String toolName = "UnknownTool";
        when(toolManager.getToolDescriptor(toolName)).thenReturn(null); // This uses the class-level toolManager mock

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> staticAnalysisService.runAnalysis(toolName, targetPath, Collections.emptyMap()));
        assertEquals("No descriptor found for tool: " + toolName, exception.getMessage());
    }

    @Test
    void runAnalysis_toolNotStaticAnalyzer() {
        String toolName = "NotAnAnalyzer";
        ToolDescriptor localToolDescriptor = mock(ToolDescriptor.class); // Local mock
        when(toolManager.getToolDescriptor(toolName)).thenReturn(localToolDescriptor);
        when(localToolDescriptor.getCategory()).thenReturn("formatter"); // Not a static-analyzer

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> staticAnalysisService.runAnalysis(toolName, targetPath, Collections.emptyMap()));
        assertEquals("Tool " + toolName + " is not categorized as a static-analyzer.", exception.getMessage());
    }

    @Test
    void runAnalysis_provisioningFails() {
        String toolName = "Semgrep";
        ToolDescriptor localToolDescriptor = mock(ToolDescriptor.class); // Local mock
        when(toolManager.getToolDescriptor(toolName)).thenReturn(localToolDescriptor);
        when(localToolDescriptor.getCategory()).thenReturn("static-analyzer");
        when(toolManager.provisionTool(toolName)).thenReturn(false); // Provisioning fails

        RuntimeException exception = assertThrows(RuntimeException.class, () -> staticAnalysisService.runAnalysis(toolName, targetPath, Collections.emptyMap()));
        assertEquals("Tool " + toolName + " is not available or could not be installed.", exception.getMessage());
    }

    @Test
    void runAnalysis_commandPatternMissing() {
        String toolName = "ToolWithNoCommand";
        ToolDescriptor localToolDescriptor = mock(ToolDescriptor.class); // Local mock
        when(toolManager.getToolDescriptor(toolName)).thenReturn(localToolDescriptor);
        when(localToolDescriptor.getCategory()).thenReturn("static-analyzer");
        when(toolManager.provisionTool(toolName)).thenReturn(true);
        when(localToolDescriptor.getAnalysisCommandPattern()).thenReturn(null); // Command pattern is missing

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> staticAnalysisService.runAnalysis(toolName, targetPath, Collections.emptyMap()));
        assertEquals("Analysis command pattern is not defined for tool: " + toolName, exception.getMessage());
    }


    @Test
    void runAnalysis_parserClassNotFound() throws InterruptedException {
        String toolName = "Semgrep";
        String invalidParserClass = "com.example.NonExistentParser";
        ToolDescriptor localToolDescriptor = mock(ToolDescriptor.class); // Local mock
        when(toolManager.getToolDescriptor(toolName)).thenReturn(localToolDescriptor);
        when(localToolDescriptor.getCategory()).thenReturn("static-analyzer");
        when(toolManager.provisionTool(toolName)).thenReturn(true);
        when(localToolDescriptor.getAnalysisCommandPattern()).thenReturn("semgrep scan {targetPath}");
        when(localToolDescriptor.getResultsParserClass()).thenReturn(invalidParserClass);
        when(localToolDescriptor.getDefaultConfig()).thenReturn(Collections.emptyMap());


        try (MockedConstruction<ProcessBuilder> mockedPbConstruction = Mockito.mockConstruction(
                ProcessBuilder.class,
                (mock, context) -> {
                    when(mock.directory(any(File.class))).thenReturn(mock); // Chain call
                    when(mock.start()).thenReturn(process);
                })) {
            when(process.getInputStream()).thenReturn(new ByteArrayInputStream("output".getBytes(StandardCharsets.UTF_8)));
            when(process.getErrorStream()).thenReturn(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
            when(process.waitFor()).thenReturn(0);

            Exception exception = assertThrows(Exception.class, () -> staticAnalysisService.runAnalysis(toolName, targetPath, Collections.emptyMap()));
            assertTrue(exception.getMessage().contains("Error with results parser " + invalidParserClass));
            assertInstanceOf(ClassNotFoundException.class, exception.getCause());
        }
    }

    @Test
    void runAnalysis_processExecutionError() {
        String toolName = "Semgrep";
        ToolDescriptor localToolDescriptor = mock(ToolDescriptor.class); // Local mock
        when(toolManager.getToolDescriptor(toolName)).thenReturn(localToolDescriptor);
        lenient().when(localToolDescriptor.getCategory()).thenReturn("static-analyzer");
        when(toolManager.provisionTool(toolName)).thenReturn(true);
        lenient().when(localToolDescriptor.getAnalysisCommandPattern()).thenReturn("semgrep scan {targetPath}");
        lenient().when(localToolDescriptor.getResultsParserClass()).thenReturn("dumb.jaider.staticanalysis.SemgrepResultsParser");
        lenient().when(localToolDescriptor.getDefaultConfig()).thenReturn(Map.of("semgrepConfig", "auto"));


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

        ToolDescriptor localToolDescriptor = mock(ToolDescriptor.class); // Local mock
        when(toolManager.getToolDescriptor(toolName)).thenReturn(localToolDescriptor);
        when(localToolDescriptor.getCategory()).thenReturn("static-analyzer");
        when(toolManager.provisionTool(toolName)).thenReturn(true);
        when(localToolDescriptor.getAnalysisCommandPattern()).thenReturn(commandPattern);
        when(localToolDescriptor.getResultsParserClass()).thenReturn(null); // No parser defined
        when(localToolDescriptor.getDefaultConfig()).thenReturn(Collections.emptyMap());


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
            StaticAnalysisIssue issue = issues.getFirst();
            assertEquals(targetPath.toString(), issue.filePath());
            assertEquals("RAW_OUTPUT", issue.ruleId());
            assertTrue(issue.message().contains(rawToolOutput));
        }
    }
}
