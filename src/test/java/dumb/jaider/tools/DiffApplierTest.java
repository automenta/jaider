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
import java.io.File; // Added import
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
        UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs

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
        System.out.println("testApplyDiffToNewFile - fromFile: " + fileDiff.getFromFile());
        System.out.println("testApplyDiffToNewFile - toFile: " + fileDiff.getToFile());
        UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs

        // Apply the diff
        String result = diffApplier.apply(model, unifiedDiff);
        assertTrue(result.startsWith("Diff applied successfully to all specified files."), "Result was: " + result);

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
        UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs
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
        UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs

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
        UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs


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

    @Test
    void testApplyDiffWithPatchFailedException() throws IOException {
        // 1. Prepare an existing file (or use the one from setUp)
        Path targetFilePath = model.projectDir.resolve("existingFile.txt");
        List<String> actualOriginalLines = Files.readAllLines(targetFilePath); // e.g., ["line1", "line2", "line3"]

        // 2. Create a patch that will fail.
        //    Generate a patch based on a *different* original state.
        List<String> mismatchedOriginalLines = Arrays.asList(" совершенно", " другая", " строка"); // "completely", "different", "line" in Russian
        List<String> changedLinesForMismatched = Arrays.asList("совершенно", "другая", "измененная строка"); // "completely", "different", "changed line"
        Patch<String> failingPatch = DiffUtils.diff(mismatchedOriginalLines, changedLinesForMismatched);

        // 3. Create UnifiedDiff objects
        UnifiedDiffFile fileDiff = UnifiedDiffFile.from("existingFile.txt", "existingFile.txt", failingPatch);
        System.out.println("testApplyDiff_patchFailedException - fromFile: " + fileDiff.getFromFile());
        System.out.println("testApplyDiff_patchFailedException - toFile: " + fileDiff.getToFile());
        UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs

        // 4. Apply the diff and assert the expected error message
        String result = diffApplier.apply(model, unifiedDiff);
        assertTrue(result.contains("Patch application failed"), "Result message should contain 'Patch application failed'. Actual: " + result);
        // We can't easily assert the full pfe.getMessage() as it's internal to java-diff-utils,
        // but checking the prefix is a good indicator.

        // 5. Verify that the original file has not been changed
        List<String> linesAfterAttempt = Files.readAllLines(targetFilePath);
        assertEquals(actualOriginalLines, linesAfterAttempt, "Original file should not be modified if patch application fails.");
    }

    @Test
    void testApplyDiffWithFileReadIOException() throws IOException {
        // 1. Create a new file specifically for this test
        Path unreadableFilePath = tempDir.resolve("unreadableFile.txt");
        List<String> originalLines = Arrays.asList("line1", "line2");
        Files.write(unreadableFilePath, originalLines);
        model.filesInContext.add(unreadableFilePath); // Add to context

        // 2. Make the file unreadable
        java.io.File fileToMakeUnreadable = unreadableFilePath.toFile();
        if (!fileToMakeUnreadable.setReadable(false)) {
            // If setting readable to false fails, skip the test or fail with a message
            // For CI environments, this might sometimes be an issue depending on permissions
            System.err.println("Warning: Could not make file unreadable. Test testApplyDiffWithFileReadIOException might not be effective.");
            // Depending on strictness, could use:
            // fail("Could not make file unreadable, prerequisite for testApplyDiffWithFileReadIOException failed.");
            // For now, we'll let it proceed, but the error won't be triggered as expected.
        }

        // 3. Create a simple, valid diff (it won't be applied anyway)
        List<String> changedLines = Arrays.asList("line1_changed", "line2");
        Patch<String> patch = DiffUtils.diff(originalLines, changedLines); // Patch against original content
        UnifiedDiffFile fileDiff = UnifiedDiffFile.from("unreadableFile.txt", "unreadableFile.txt", patch);
        UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs

        // 4. Apply the diff and assert the expected error message
        String result = diffApplier.apply(model, unifiedDiff);

        // 5. Restore readability (important for cleanup, especially if setReadable(false) succeeded)
        // and assert the result.
        // Run this regardless of whether setReadable(false) succeeded, to be safe.
        fileToMakeUnreadable.setReadable(true);

        assertTrue(result.startsWith("Error reading original file 'unreadableFile.txt' for diff application:"),
                "Result was: " + result); // Provide actual result on failure for better debugging

        // 6. Verify the file content hasn't changed (it shouldn't have been touched if reading failed)
        List<String> linesAfterAttempt = Files.readAllLines(unreadableFilePath);
        assertEquals(originalLines, linesAfterAttempt, "File content should not change if initial read fails.");

        // Clean up the test file
        Files.delete(unreadableFilePath);
        model.filesInContext.remove(unreadableFilePath);
    }

    @Test
    void testApplyDiffWithFileWriteIOException() throws IOException {
        // 1. Define a name for a "file" that will actually be a directory
        String targetName = "targetIsDir.txt";
        Path directoryAsFile = tempDir.resolve(targetName);

        // 2. Create a directory with that name
        Files.createDirectory(directoryAsFile);
        // Note: We don't add this directory to filesInContext as a file.
        // The DiffApplier will attempt to treat it as a new or existing file based on the diff.
        // If it's treated as a new file, it will try to write to it.
        // If it's treated as an existing file (not in context), it would error out earlier,
        // so we'll make the diff indicate this is a new file.

        // 3. Create a diff for a new file that matches the directory's name
        List<String> newFileLines = Arrays.asList("line1", "line2");
        // Patch from empty (new file) to newFileLines
        Patch<String> patch = DiffUtils.diff(Collections.emptyList(), newFileLines);
        // Critical: getFromFile is /dev/null for new file, getToFile is our targetName
        UnifiedDiffFile fileDiff = UnifiedDiffFile.from("/dev/null", targetName, patch);
        UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs

        // 4. Apply the diff
        String result = diffApplier.apply(model, unifiedDiff);

        // 5. Assert the expected error message
        //    The exact message might vary by OS/JVM (e.g., "Is a directory", "Access denied")
        //    So, we check for the generic prefix from DiffApplier.
        assertTrue(result.startsWith("Error writing patched file '" + targetName + "':"),
                   "Result was: " + result); // Provide actual result for debugging

        // 6. Clean up the directory
        Files.delete(directoryAsFile);
    }

    @Test
    void testApplyDiffWithInvalidFileNamesInDiff() throws IOException {
        // 1. Create a patch (content doesn't really matter for this test, but it needs to be valid for UnifiedDiffFile)
        //    Using an empty patch for simplicity as the content of the patch is not relevant to filename validation.
        Patch<String> emptyPatch = DiffUtils.diff(Collections.emptyList(), Collections.emptyList());

        // 2. Create UnifiedDiffFile with problematic fromFile and toFile combinations

        // Scenario 1: Both are /dev/null
        UnifiedDiffFile fileDiff1 = UnifiedDiffFile.from("/dev/null", "/dev/null", emptyPatch);
        UnifiedDiff unifiedDiff1 = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff1}); // Explicit array for varargs
        String result1 = diffApplier.apply(model, unifiedDiff1);
        assertEquals("Error: Could not determine file name from UnifiedDiffFile entry.", result1, "Scenario 1 failed: Both /dev/null");

        // Scenario 2: fromFile is /dev/null, toFile is null
        // UnifiedDiffFile.from() might throw an NPE if toFile is null and patch indicates changes.
        // Let's ensure the patch is truly empty for this case.
        UnifiedDiffFile fileDiff2 = UnifiedDiffFile.from("/dev/null", null, emptyPatch);
        UnifiedDiff unifiedDiff2 = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff2}); // Explicit array for varargs
        String result2 = diffApplier.apply(model, unifiedDiff2);
        assertEquals("Error: Could not determine file name from UnifiedDiffFile entry.", result2, "Scenario 2 failed: fromFile /dev/null, toFile null");

        // Scenario 3: fromFile is /dev/null, toFile is empty
        UnifiedDiffFile fileDiff3 = UnifiedDiffFile.from("/dev/null", "", emptyPatch);
        UnifiedDiff unifiedDiff3 = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff3}); // Explicit array for varargs
        String result3 = diffApplier.apply(model, unifiedDiff3);
        assertEquals("Error: Could not determine file name from UnifiedDiffFile entry.", result3, "Scenario 3 failed: fromFile /dev/null, toFile empty");

        // Scenario 4: fromFile is null, toFile is /dev/null
        UnifiedDiffFile fileDiff4 = UnifiedDiffFile.from(null, "/dev/null", emptyPatch);
        UnifiedDiff unifiedDiff4 = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff4}); // Explicit array for varargs
        String result4 = diffApplier.apply(model, unifiedDiff4);
        assertEquals("Error: Could not determine file name from UnifiedDiffFile entry.", result4, "Scenario 4 failed: fromFile null, toFile /dev/null");

        // Scenario 5: fromFile is empty, toFile is /dev/null
        UnifiedDiffFile fileDiff5 = UnifiedDiffFile.from("", "/dev/null", emptyPatch);
        UnifiedDiff unifiedDiff5 = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff5}); // Explicit array for varargs
        String result5 = diffApplier.apply(model, unifiedDiff5);
        assertEquals("Error: Could not determine file name from UnifiedDiffFile entry.", result5, "Scenario 5 failed: fromFile empty, toFile /dev/null");

        // Scenario 6: Both are null
        UnifiedDiffFile fileDiff6 = UnifiedDiffFile.from(null, null, emptyPatch);
        UnifiedDiff unifiedDiff6 = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff6}); // Explicit array for varargs
        String result6 = diffApplier.apply(model, unifiedDiff6);
        assertEquals("Error: Could not determine file name from UnifiedDiffFile entry.", result6, "Scenario 6 failed: Both null");

        // Scenario 7: Both are empty
        UnifiedDiffFile fileDiff7 = UnifiedDiffFile.from("", "", emptyPatch);
        UnifiedDiff unifiedDiff7 = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff7}); // Explicit array for varargs
        String result7 = diffApplier.apply(model, unifiedDiff7);
        assertEquals("Error: Could not determine file name from UnifiedDiffFile entry.", result7, "Scenario 7 failed: Both empty");
    }
}
