package dumb.jaider.tools;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.PatchFailedException;
import com.github.difflib.unifieddiff.UnifiedDiff;
import com.github.difflib.unifieddiff.UnifiedDiffFile;
import dumb.jaider.model.JaiderModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.github.difflib.patch.Patch; // Added import

public class DiffApplier {

    public String apply(JaiderModel model, Patch<String> patch, String originalFileName, String revisedFileName) {
        if (patch == null) {
            return "Error: Patch object is null.";
        }

        String fileName = revisedFileName; // Prefer the target file name for operations
        if (fileName == null || fileName.isEmpty() || "/dev/null".equals(fileName)) {
            // If revisedFileName is invalid (e.g. /dev/null for a deletion), try originalFileName
            fileName = originalFileName;
            if (fileName == null || fileName.isEmpty() || "/dev/null".equals(fileName)) {
                return "Error: Could not determine file name for applying patch.";
            }
        }

        Path filePath = model.projectDir.resolve(fileName);
        boolean isNewFile = "/dev/null".equals(originalFileName) || !Files.exists(filePath.getParent().resolve(originalFileName));


        // Context check: For existing files that are being modified (not new, not deleted)
        if (!isNewFile && !"/dev/null".equals(originalFileName) && Files.exists(filePath) && !model.filesInContext.contains(filePath)) {
            return "Error: Cannot apply diff to an existing file not in context: " + fileName;
        }

        // Deletion scenario
        if ("/dev/null".equals(revisedFileName)) {
            try {
                Files.deleteIfExists(filePath);
                model.filesInContext.remove(filePath);
                return "File " + originalFileName + " deleted successfully.";
            } catch (IOException e) {
                return "Error deleting file '" + originalFileName + "': " + e.getMessage();
            }
        }

        List<String> originalLines;
        try {
            // If originalFileName is /dev/null or points to a non-existent file (new file scenario), start with empty lines.
            if ("/dev/null".equals(originalFileName) || !Files.exists(model.projectDir.resolve(originalFileName))) {
                originalLines = new ArrayList<>();
            } else {
                originalLines = Files.readAllLines(model.projectDir.resolve(originalFileName));
            }
        } catch (IOException e) {
            return "Error reading original file '" + originalFileName + "' for diff application: " + e.getMessage();
        }

        try {
            List<String> patchedLines = patch.applyTo(originalLines);
            Files.createDirectories(filePath.getParent()); // Ensure parent directory exists
            Files.write(filePath, patchedLines);

            if (!model.filesInContext.contains(filePath)) { // Add if new or was previously removed
                 model.filesInContext.add(filePath);
            }
        } catch (PatchFailedException pfe) {
            return "Error applying diff to file '" + fileName + "': Patch application failed. Details: " + pfe.getMessage();
        } catch (IOException e) {
            return "Error writing patched file '" + fileName + "': " + e.getMessage();
        }

        return "Diff applied successfully to file " + fileName + ".";
    }
}
