package dumb.jaider.diff;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import com.github.difflib.unifieddiff.UnifiedDiff;
import com.github.difflib.unifieddiff.UnifiedDiffFile;
import dumb.jaider.JaiderModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class) // For potential Mockito annotations if needed later, good practice.
public class DiffApplierTest {

    @TempDir
    Path tempDir;

    private JaiderModel model;
    private DiffApplier diffApplier;
    private Path projectDir;

    @BeforeEach
    void setUp() throws IOException {
        projectDir = tempDir.resolve("testProject");
        Files.createDirectories(projectDir);
        model = new JaiderModel(projectDir.toString()); // Initialize model with projectDir
        diffApplier = new DiffApplier(model);
    }

    private UnifiedDiff createUnifiedDiff(List<UnifiedDiffFile> files) {
        UnifiedDiff unifiedDiff = new UnifiedDiff();
        unifiedDiff.setSchema("jaider_test_schema"); // Optional: set schema if needed by your code
        for (UnifiedDiffFile file : files) {
            unifiedDiff.addFile(file);
        }
        return unifiedDiff;
    }

    private UnifiedDiffFile createUnifiedDiffFile(String fromFile, String toFile, List<String> patchLines) {
        UnifiedDiffFile fileDiff = new UnifiedDiffFile();
        fileDiff.setFromFile(fromFile);
        fileDiff.setToFile(toFile);
        // Construct a minimal Patch object. In real scenarios, this would come from UnifiedDiffReader.
        // For testing DiffApplier, we primarily care about fromFile/toFile and the patch lines.
        // The actual diff content needs to be parseable by DiffUtils.parseUnifiedDiff
        // For simplicity, we'll create a patch that adds lines.
        Patch<String> patch = UnifiedDiffUtils.parseUnifiedDiff(patchLines).getFiles().get(0).getPatch();
        fileDiff.setPatch(patch);
        return fileDiff;
    }


    @Test
    void testApplyUnifiedDiffWithEmptyFileList() {
        UnifiedDiff unifiedDiff = createUnifiedDiff(Collections.emptyList());
        String result = diffApplier.apply(unifiedDiff);
        assertEquals("Diff applied successfully to all specified files.", result);
    }

    @Test
    void testApplyDiffToNewFileWithExplicitDevNullFromFile() throws IOException {
        String newFileName = "newFile.txt";
        Path newFilePath = projectDir.resolve(newFileName);
        List<String> patchLines = Arrays.asList(
                "--- /dev/null",
                "+++ " + newFileName, // Or a/newFile.txt, b/newFile.txt depending on diff source
                "@@ -0,0 +1,2 @@",
                "+Hello",
                "+World"
        );
        // Create a diff string and parse it to get UnifiedDiffFile
        String diffStr = String.join("\n", patchLines);
        UnifiedDiff parsedUnifiedDiff = UnifiedDiffUtils.parseUnifiedDiff(Collections.singletonList(diffStr));
        UnifiedDiffFile fileDiff = parsedUnifiedDiff.getFiles().get(0);
        // Ensure getFromFile is /dev/null as per test name
        fileDiff.setFromFile("/dev/null");
        fileDiff.setToFile(newFileName);


        UnifiedDiff unifiedDiff = createUnifiedDiff(Collections.singletonList(fileDiff));
        model.filesInContext.add(newFilePath); // Add to context so DiffApplier knows about it

        String result = diffApplier.apply(unifiedDiff);

        assertEquals("Diff applied successfully to all specified files.", result);
        assertTrue(Files.exists(newFilePath));
        List<String> actualLines = Files.readAllLines(newFilePath);
        assertEquals(Arrays.asList("Hello", "World"), actualLines);
    }

    @Test
    void testApplyDiff_invalidFileNameInDiffEntry() {
        // Create a UnifiedDiffFile where getFromFile() is "/dev/null" and getToFile() is also "/dev/null"
        UnifiedDiffFile invalidFileDiff = new UnifiedDiffFile();
        invalidFileDiff.setFromFile("/dev/null");
        invalidFileDiff.setToFile("/dev/null"); // Invalid: should be a real file name for creation
        // A patch is needed for it to be processed
        Patch<String> emptyPatch = new Patch<>();
        invalidFileDiff.setPatch(emptyPatch);


        UnifiedDiff unifiedDiff = createUnifiedDiff(Collections.singletonList(invalidFileDiff));
        String result = diffApplier.apply(unifiedDiff);
        assertEquals("Error: Could not determine file name from UnifiedDiffFile entry.", result);
    }

    @Test
    void testApplyDiff_patchFailedException() throws IOException, PatchFailedException {
        String existingFileName = "existingFile.txt";
        Path existingFilePath = projectDir.resolve(existingFileName);
        List<String> originalLines = Arrays.asList("line1", "line2", "line3");
        Files.write(existingFilePath, originalLines, StandardCharsets.UTF_8);
        model.filesInContext.add(existingFilePath);

        // Create a patch that would normally apply but we'll make DiffUtils.patch throw an exception
        List<String> patchDefinitionLines = Arrays.asList(
            "--- a/" + existingFileName,
            "+++ b/" + existingFileName,
            "@@ -1,3 +1,3 @@",
            " line1",
            "-line2",
            "+line2_modified",
            " line3"
        );
        String diffStr = String.join("\n", patchDefinitionLines);
        UnifiedDiff parsedUnifiedDiff = UnifiedDiffUtils.parseUnifiedDiff(Collections.singletonList(diffStr));
        UnifiedDiffFile fileDiff = parsedUnifiedDiff.getFiles().get(0);


        UnifiedDiff unifiedDiff = createUnifiedDiff(Collections.singletonList(fileDiff));

        // Use try-with-resources for MockedStatic
        try (MockedStatic<DiffUtils> mockedDiffUtils = Mockito.mockStatic(DiffUtils.class)) {
            mockedDiffUtils.when(() -> DiffUtils.patch(anyList(), any(Patch.class)))
                           .thenThrow(new PatchFailedException("Simulated patch failure"));

            String result = diffApplier.apply(unifiedDiff);
            assertTrue(result.startsWith("Error applying diff to file '" + existingFileName + "': Patch application failed: Simulated patch failure"),
                       "Result was: " + result);
        }

        // Verify original file is unchanged
        List<String> actualLines = Files.readAllLines(existingFilePath);
        assertEquals(originalLines, actualLines, "File content should not change if patch failed.");
    }
}
