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

public class DiffApplier {

    public String apply(JaiderModel model, UnifiedDiff unifiedDiff) {
        if (unifiedDiff == null || unifiedDiff.getFiles() == null) {
            return "Error: UnifiedDiff or its file list is null.";
        }

        for (UnifiedDiffFile fileDiff : unifiedDiff.getFiles()) {
            String fileName = fileDiff.getFromFile(); // Or getToFileName, depending on context. getFromFile is typical for patches.
            if (fileName == null || fileName.isEmpty() || "/dev/null".equals(fileName)) {
                 // For new files, getFromFile might be /dev/null. Use getToFileName.
                 fileName = fileDiff.getToFileName();
                 if (fileName == null || fileName.isEmpty() || "/dev/null".equals(fileName)) {
                     // If both are /dev/null or invalid, this diff file is problematic.
                     return "Error: Could not determine file name from UnifiedDiffFile entry.";
                 }
            }

            Path filePath = model.projectDir.resolve(fileName);
            boolean isNewFile = !Files.exists(filePath);

            // For new files, getFromFile is /dev/null. We need to ensure it's not treated as a path.
            if (!isNewFile && !"/dev/null".equals(fileDiff.getFromFile()) && !model.filesInContext.contains(filePath)) {
                 // Check context only for existing files.
                 // If getFromFile is /dev/null, it's a new file, so context check is not applicable here.
                return "Error: Cannot apply diff to an existing file not in context: " + fileName;
            }


            List<String> originalLines;
            try {
                originalLines = isNewFile ? new ArrayList<>() : Files.readAllLines(filePath);
            } catch (IOException e) {
                return "Error reading original file '" + fileName + "' for diff application: " + e.getMessage();
            }

            try {
                List<String> patchedLines = DiffUtils.patch(originalLines, fileDiff.getPatch());
                Files.write(filePath, patchedLines);

                if (isNewFile) {
                    model.filesInContext.add(filePath);
                }
            } catch (PatchFailedException pfe) {
                return "Error applying diff to file '" + fileName + "': Patch application failed. Details: " + pfe.getMessage();
            } catch (IOException e) {
                return "Error writing patched file '" + fileName + "': " + e.getMessage();
            }
        }
        return "Diff applied successfully to all specified files.";
    }
}
