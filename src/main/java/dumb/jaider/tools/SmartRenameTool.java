package dumb.jaider.tools;

import dev.langchain4j.agent.tool.Tool;
import dumb.jaider.refactoring.RefactoringService;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SmartRenameTool {

    private final RefactoringService refactoringService;

    public SmartRenameTool(RefactoringService refactoringService) {
        this.refactoringService = refactoringService;
    }

    @Tool("Renames a variable, method, or class. If possible, it uses AST-based renaming for accuracy. " +
          "Otherwise, it falls back to text-based renaming. " +
          "Provide the file path, the original name, the new name, and optionally the character position " +
          "of one instance of the original name to help disambiguate (if not provided, position is -1).")
    public String smartRename(String filePath, String originalName, String newName, Integer position) {
        if (filePath == null || filePath.isBlank()) {
            return "Error: filePath cannot be null or blank for smartRename.";
        }
        if (originalName == null || originalName.isBlank()) {
            return "Error: originalName cannot be null or blank for smartRename.";
        }
        if (newName == null || newName.isBlank()) {
            return "Error: newName cannot be null or blank for smartRename.";
        }

        Path path = Paths.get(filePath);
        int actualPosition = (position == null) ? -1 : position;

        try {
            return refactoringService.smartRename(path, originalName, actualPosition, newName);
        } catch (Exception e) {
            // Log the exception properly in a real scenario
            System.err.println("Error during smartRename execution: " + e.getMessage());
            e.printStackTrace();
            return "Error: An unexpected error occurred during the smart rename operation: " + e.getMessage();
        }
    }
}
