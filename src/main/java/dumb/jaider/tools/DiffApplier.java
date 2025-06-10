package dumb.jaider.tools;

import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import dumb.jaider.model.JaiderModel;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class DiffApplier {

    public String apply(JaiderModel model, Patch<String> patch, String originalFileName, String revisedFileName) {
        if (patch == null) {
            return "Error: Patch object is null.";
        }

        var fileName = revisedFileName; // Prefer the target file name for operations
        if (emptyFileName(fileName)) {
            // If revisedFileName is invalid (e.g. /dev/null for a deletion), try originalFileName
            fileName = originalFileName;
            if (emptyFileName(fileName))
                return "Error: Could not determine file name for applying patch.";
        }

        var filePath = model.dir.resolve(fileName);
        var isNewFile = "/dev/null".equals(originalFileName) || !Files.exists(filePath.getParent().resolve(originalFileName));


        // Context check: For existing files that are being modified (not new, not deleted)
        if (!isNewFile && Files.exists(filePath) && !model.files.contains(filePath)) {
            return "Error: Cannot apply diff to an existing file not in context: " + fileName;
        }

        // Deletion scenario
        if ("/dev/null".equals(revisedFileName)) {
            try {
                Files.deleteIfExists(filePath);
                model.files.remove(filePath);
                return "File " + originalFileName + " deleted successfully.";
            } catch (IOException e) {
                return "Error deleting file '" + originalFileName + "': " + e.getMessage();
            }
        }

        List<String> originalLines;
        try {
            // If originalFileName is /dev/null or points to a non-existent file (new file scenario), start with empty lines.
            if ("/dev/null".equals(originalFileName) || !Files.exists(model.dir.resolve(originalFileName))) {
                originalLines = new ArrayList<>();
            } else {
                originalLines = Files.readAllLines(model.dir.resolve(originalFileName));
            }
        } catch (IOException e) {
            return "Error reading original file '" + originalFileName + "' for diff application: " + e.getMessage();
        }

        try {
            var patchedLines = patch.applyTo(originalLines);
            Files.createDirectories(filePath.getParent()); // Ensure parent directory exists
            Files.write(filePath, patchedLines);

            // Add if new or was previously removed
            model.files.add(filePath);
        } catch (PatchFailedException pfe) {
            return "Error applying diff to file '" + fileName + "': Patch application failed. Details: " + pfe.getMessage();
        } catch (IOException e) {
            return "Error writing patched file '" + fileName + "': " + e.getMessage();
        }

        return "Diff applied successfully to file " + fileName + ".";
    }

    private boolean emptyFileName(String fileName) {
        return fileName == null || fileName.isEmpty() || "/dev/null".equals(fileName);
    }
}
