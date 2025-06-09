package dumb.jaider.tools;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.unifieddiff.UnifiedDiff;
import com.github.difflib.unifieddiff.UnifiedDiffFile; // Added import
import com.github.difflib.unifieddiff.UnifiedDiffReader;
import dumb.jaider.model.JaiderModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffApplierTest {

    private DiffApplier diffApplier;
    private JaiderModel model;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        diffApplier = new DiffApplier();
        model = new JaiderModel(tempDir);
        // Create a dummy file in context for testing
        Path existingFile = tempDir.resolve("existingFile.txt");
        Files.write(existingFile, Arrays.asList("line1", "line2", "line3"));
        model.filesInContext.add(existingFile);
    }

    @Test
    void testApplyDiffToExistingFile() throws IOException {
        // Create a diff
        List<String> originalLines = Files.readAllLines(model.projectDir.resolve("existingFile.txt"));
        List<String> changedLines = Arrays.asList("line1", "line2_changed", "line3");
        Patch<String> patch = DiffUtils.diff(originalLines, changedLines);
        UnifiedDiffFile fileDiff = UnifiedDiffFile.from("existingFile.txt", "existingFile.txt", patch);
        UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, fileDiff);

        // Apply the diff
        String result = diffApplier.apply(model, unifiedDiff);
        assertEquals("Diff applied successfully to all specified files.", result);

        // Verify file content
        List<String> actualLines = Files.readAllLines(model.projectDir.resolve("existingFile.txt"));
        assertEquals(changedLines, actualLines);
    }

    @Test
    void testApplyDiffToNewFile() throws IOException {
        List<String> newFileLines = Arrays.asList("newline1", "newline2");
        Patch<String> patch = DiffUtils.diff(Collections.emptyList(), newFileLines);
        UnifiedDiffFile fileDiff = UnifiedDiffFile.from("/dev/null", "newFile.txt", patch);
        UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, fileDiff);

        // Apply the diff
        String result = diffApplier.apply(model, unifiedDiff);
        assertEquals("Diff applied successfully to all specified files.", result);

        // Verify file content and context
        Path newFilePath = model.projectDir.resolve("newFile.txt");
        assertTrue(Files.exists(newFilePath));
        List<String> actualLines = Files.readAllLines(newFilePath);
        assertEquals(newFileLines, actualLines);
        assertTrue(model.filesInContext.contains(newFilePath));
    }

    @Test
    void testApplyDiffToDeleteFile() throws IOException {
        // Create a diff for deleting a file (empty target)
        List<String> originalLines = Files.readAllLines(model.projectDir.resolve("existingFile.txt"));
        Patch<String> patch = DiffUtils.diff(originalLines, Collections.emptyList());
        UnifiedDiffFile fileDiff = UnifiedDiffFile.from("existingFile.txt", "existingFile.txt", patch);
        UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, fileDiff);
        // In a real delete scenario, the toFile might be /dev/null or the patch indicates all lines deleted.
        // For this test, we'll simulate by checking if the file becomes empty.

        // Apply the diff
        String result = diffApplier.apply(model, unifiedDiff);
        assertEquals("Diff applied successfully to all specified files.", result);

        // Verify file content (should be empty)
        List<String> actualLines = Files.readAllLines(model.projectDir.resolve("existingFile.txt"));
        assertTrue(actualLines.isEmpty());
    }

    @Test
    void testApplyDiffToNonExistingFileNotInContext() throws IOException {
        // This test is tricky because DiffApplier checks context for existing files.
        // If a file doesn't exist, it's treated as a new file.
        // Let's simulate a case where getFromFile points to a non-existent file,
        // but it's not /dev/null, which might be an edge case.
        List<String> originalLines = Collections.emptyList(); // Pretend it was empty or non-existent
        List<String> changedLines = Arrays.asList("some content");
        Patch<String> patch = DiffUtils.diff(originalLines, changedLines);
        UnifiedDiffFile fileDiff = UnifiedDiffFile.from("/dev/null", "nonExistentFile.txt", patch);
        UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, fileDiff);

        // Apply the diff
        String result = diffApplier.apply(model, unifiedDiff);
        assertEquals("Diff applied successfully to all specified files.", result);

        Path newFilePath = model.projectDir.resolve("nonExistentFile.txt");
        assertTrue(Files.exists(newFilePath));
        assertEquals(changedLines, Files.readAllLines(newFilePath));
        assertTrue(model.filesInContext.contains(newFilePath));
    }

    @Test
    void testApplyDiffToExistingFileNotInContext() throws IOException {
        // Create a file that exists but is not in context
        Path notInContextFile = tempDir.resolve("notInContext.txt");
        Files.write(notInContextFile, Arrays.asList("original line"));

        List<String> originalLines = Files.readAllLines(notInContextFile);
        List<String> changedLines = Arrays.asList("changed line");
        Patch<String> patch = DiffUtils.diff(originalLines, changedLines);
        // Critical: Ensure getFromFile is the actual filename, not /dev/null
        UnifiedDiffFile fileDiff = UnifiedDiffFile.from("notInContext.txt", "notInContext.txt", patch);
        UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, fileDiff);


        // Apply the diff - expect error
        String result = diffApplier.apply(model, unifiedDiff);
        assertEquals("Error: Cannot apply diff to an existing file not in context: notInContext.txt", result);
    }

    @Test
    void testApplyNullUnifiedDiff() {
        String result = diffApplier.apply(model, null);
        assertEquals("Error: UnifiedDiff or its file list is null.", result);
    }

    @Test
    void testApplyUnifiedDiffWithNullFiles() {
        // Simulate a UnifiedDiff with a null getFiles() list - this is hard to create directly
        // For now, this test relies on the first check in DiffApplier.apply()
        // A more robust way would be to mock UnifiedDiff if a mocking framework was in use.
        // We will create a UnifiedDiff with no files in it.
        UnifiedDiff mockDiff = new UnifiedDiff(); // Corrected constructor

        String result = diffApplier.apply(model, mockDiff);
        // If getFiles() is empty (not null), it should return "Diff applied successfully..."
        // If getFiles() itself was null, it would be "Error: UnifiedDiff or its file list is null."
        // Given the constructor, getFiles() will be an empty list.
        assertEquals("Diff applied successfully to all specified files.", result);

        // To truly test the "null files list" we'd need a mock or a custom UnifiedDiff implementation.
        // The current check `unifiedDiff == null || unifiedDiff.getFiles() == null` handles it.
    }
}
