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
        // UnifiedDiffFile fileDiff = UnifiedDiffFile.from("existingFile.txt", "existingFile.txt", patch);
        // UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs

        // Apply the diff
        String result = diffApplier.apply(model, patch, "existingFile.txt", "existingFile.txt");
        assertEquals("Diff applied successfully to file existingFile.txt.", result);

        // Verify file content
        List<String> actualLines = Files.readAllLines(model.projectDir.resolve("existingFile.txt"));
        assertEquals(changedLines, actualLines);
    }

    @Test
    void testApplyDiffToNewFile() throws IOException {
        List<String> newFileLines = Arrays.asList("newline1", "newline2");
        Patch<String> patch = DiffUtils.diff(Collections.emptyList(), newFileLines);
        // UnifiedDiffFile fileDiff = UnifiedDiffFile.from("/dev/null", "newFile.txt", patch);
        // System.out.println("testApplyDiffToNewFile - fromFile: " + fileDiff.getFromFile());
        // System.out.println("testApplyDiffToNewFile - toFile: " + fileDiff.getToFile());
        // UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs

        // Apply the diff
        String result = diffApplier.apply(model, patch, "/dev/null", "newFile.txt");
        assertTrue(result.startsWith("Diff applied successfully to file newFile.txt."), "Result was: " + result);

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
        // UnifiedDiffFile fileDiff = UnifiedDiffFile.from("existingFile.txt", "existingFile.txt", patch);
        // UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs

        // Apply the diff to delete the file
        String result = diffApplier.apply(model, patch, "existingFile.txt", "/dev/null");
        assertEquals("File existingFile.txt deleted successfully.", result);

        // Verify file is deleted
        assertFalse(Files.exists(model.projectDir.resolve("existingFile.txt")));
        assertFalse(model.filesInContext.contains(model.projectDir.resolve("existingFile.txt")));
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
        // UnifiedDiffFile fileDiff = UnifiedDiffFile.from("/dev/null", "nonExistentFile.txt", patch);
        // UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs

        // Apply the diff
        String result = diffApplier.apply(model, patch, "/dev/null", "nonExistentFile.txt");
        assertEquals("Diff applied successfully to file nonExistentFile.txt.", result);

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
        // UnifiedDiffFile fileDiff = UnifiedDiffFile.from("notInContext.txt", "notInContext.txt", patch);
        // UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs

        // Apply the diff - expect error
        String result = diffApplier.apply(model, patch, "notInContext.txt", "notInContext.txt");
        assertEquals("Error: Cannot apply diff to an existing file not in context: notInContext.txt", result);
    }

    @Test
    void testApplyNullPatch() { // Renamed test and changed to pass null patch
        String result = diffApplier.apply(model, null, "a.txt", "b.txt");
        assertEquals("Error: Patch object is null.", result);
    }

    // This test is removed as it's no longer relevant.
    // The concept of a UnifiedDiff object with a null getFiles() list doesn't directly translate
    // to the new DiffApplier.apply(model, Patch<String>, String, String) signature.
    // Testing for null filenames is covered in testApplyDiffWithInvalidFileNamesInDiff.
    // @Test
    // void testApplyUnifiedDiffWithNullFiles() { ... }

    @Test
    void testApplyDiffWithEmptyPatch() throws IOException {
        Patch<String> emptyPatch = new Patch<>(); // Create an empty patch
        String originalFileName = "existingFile.txt";
        String revisedFileName = "existingFile.txt";
        List<String> originalContent = Files.readAllLines(model.projectDir.resolve(originalFileName));

        String result = diffApplier.apply(model, emptyPatch, originalFileName, revisedFileName);
        assertEquals("Diff applied successfully to file " + revisedFileName + ".", result);

        // Verify file content remains unchanged
        List<String> contentAfterApply = Files.readAllLines(model.projectDir.resolve(revisedFileName));
        assertEquals(originalContent, contentAfterApply);
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

        // 3. Create UnifiedDiff objects - No longer needed directly for apply method
        // UnifiedDiffFile fileDiff = UnifiedDiffFile.from("existingFile.txt", "existingFile.txt", failingPatch);
        // System.out.println("testApplyDiff_patchFailedException - fromFile: " + fileDiff.getFromFile());
        // System.out.println("testApplyDiff_patchFailedException - toFile: " + fileDiff.getToFile());
        // UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs

        // 4. Apply the diff and assert the expected error message
        String result = diffApplier.apply(model, failingPatch, "existingFile.txt", "existingFile.txt");
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
        // UnifiedDiffFile fileDiff = UnifiedDiffFile.from("unreadableFile.txt", "unreadableFile.txt", patch);
        // UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs

        // 4. Apply the diff and assert the expected error message
        String result = diffApplier.apply(model, patch, "unreadableFile.txt", "unreadableFile.txt");

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
        // UnifiedDiffFile fileDiff = UnifiedDiffFile.from("/dev/null", targetName, patch);
        // UnifiedDiff unifiedDiff = UnifiedDiff.from(null, null, new UnifiedDiffFile[]{fileDiff}); // Explicit array for varargs

        // 4. Apply the diff
        String result = diffApplier.apply(model, patch, "/dev/null", targetName);

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
        Patch<String> emptyPatch = DiffUtils.diff(Collections.emptyList(), Collections.emptyList());

        // Test cases for invalid filename combinations
        String errorMsg = "Error: Could not determine file name for applying patch.";

        // Scenario 1: Both are /dev/null
        String result1 = diffApplier.apply(model, emptyPatch, "/dev/null", "/dev/null");
        assertEquals(errorMsg, result1, "Scenario 1 failed: Both /dev/null");

        // Scenario 2: fromFile is /dev/null, toFile is null
        String result2 = diffApplier.apply(model, emptyPatch, "/dev/null", null);
        assertEquals(errorMsg, result2, "Scenario 2 failed: fromFile /dev/null, toFile null");

        // Scenario 3: fromFile is /dev/null, toFile is empty
        String result3 = diffApplier.apply(model, emptyPatch, "/dev/null", "");
        assertEquals(errorMsg, result3, "Scenario 3 failed: fromFile /dev/null, toFile empty");

        // Scenario 4: fromFile is null, toFile is /dev/null
        // This is a valid deletion scenario if the patch is not empty.
        // If the patch IS empty, it's ambiguous, let's test DiffApplier's behavior.
        // Assuming DiffApplier treats null originalFileName with /dev/null revised as deletion only if patch is non-empty.
        // For an empty patch, it might fall into "could not determine filename" if it tries to use originalFileName first.
        // Based on current DiffApplier logic: revisedFileName is /dev/null, so it's deletion.
        // originalFileName (null) is used for filePath. This will likely cause an NPE or error in model.projectDir.resolve(null).
        // Let's adjust DiffApplier to handle null originalFileName more gracefully in deletion.
        // For now, expecting an error. If DiffApplier is robust, it might be a successful no-op or specific error.
        // String result4 = diffApplier.apply(model, emptyPatch, null, "/dev/null");
        // assertEquals(errorMsg, result4, "Scenario 4 failed: fromFile null, toFile /dev/null");
        // After DiffApplier change to handle null originalFileName in deletion:
        // This test case might change. If originalFileName is null for deletion, it might try to delete a file named "null" or similar.
        // For now, let's stick to the current expectation that it fails to determine a valid name if original is also unusable.
         String result4 = diffApplier.apply(model, emptyPatch, null, "/dev/null");
         assertEquals(errorMsg, result4, "Scenario 4 failed: fromFile null, toFile /dev/null");


        // Scenario 5: fromFile is empty, toFile is /dev/null
        String result5 = diffApplier.apply(model, emptyPatch, "", "/dev/null");
        assertEquals(errorMsg, result5, "Scenario 5 failed: fromFile empty, toFile /dev/null");

        // Scenario 6: Both are null
        String result6 = diffApplier.apply(model, emptyPatch, null, null);
        assertEquals(errorMsg, result6, "Scenario 6 failed: Both null");

        // Scenario 7: Both are empty
        String result7 = diffApplier.apply(model, emptyPatch, "", "");
        assertEquals(errorMsg, result7, "Scenario 7 failed: Both empty");
    }
}
