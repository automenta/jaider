package dumb.jaider.refactoring;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RefactoringService {
    private final ParserRegistry parserRegistry;

    public RefactoringService(ParserRegistry parserRegistry) {
        this.parserRegistry = parserRegistry;
    }

    public String smartRename(Path filePath, String originalName, int position, String newName) {
        try {
            var content = Files.readString(filePath);
            var parserOptional = parserRegistry.getParserForFile(filePath);

            if (parserOptional.isPresent()) {
                // AST-based rename path
                System.out.println("[RefactoringService] AST-based rename path (parser found for " + filePath.getFileName() + ", but AST logic is TODO).");
                // LanguageParser parser = parserOptional.get();
                // LanguageAgnosticASTNode astRoot = parser.parse(filePath, content);
                // RenameOperationInput input = new RenameOperationInput(originalName, newName, position);
                // RenameOperation renameOp = new YourSpecificRenameOperationImpl(); // This would be an implementation
                // List<SourceChange> changes = renameOp.calculateChanges(astRoot, input);
                // ... apply changes and generate diff ...

                // TODO: Implement AST-based node location and rename.
                // Conceptual steps:
                // 1. Traverse the AST (astRoot) to find candidate nodes.
                //    - A visitor pattern or a recursive search function could be used.
                //    - Candidates: nodes where node.getText().equals(originalName).
                //    - Further filtering by node.getType() might be needed if available (e.g., "identifier", "methodName").
                // 2. Handle Ambiguity using `position`:
                //    - If multiple candidates are found:
                //        - If `position` is provided (e.g., >= 0), select the candidate whose startOffset is closest to `position`.
                //        - If `position` is not provided, what to do?
                //            - Option A: Rename all occurrences (like text-based fallback, but AST-aware for each).
                //            - Option B: Return an error/clarification request if ambiguous and position not given.
                //            - Option C: Attempt to infer the most likely candidate based on surrounding context (advanced).
                //    - For initial implementation, Option B (error if ambiguous and no position) is safest for AST path.
                // 3. Once the target node is identified:
                //    - Create RenameOperationInput with the specific node (or its details).
                //    - Instantiate the appropriate language-specific RenameOperation.
                //    - Call renameOperation.calculateChanges(targetNode, renameInput).
                //    - ... (rest of the diff generation logic)
                return "AST rename for '" + originalName + "' to '" + newName + "' in '" + filePath + "' is not fully implemented yet. No changes made.";
            } else {
                // Text-based fallback rename path
                System.out.println("[RefactoringService] Text-based fallback rename path for " + filePath.getFileName() + ".");
                if (originalName.equals(newName)) {
                    return "Original name and new name are the same. No changes made.";
                }

                // Simple whole-word text search and replace
                // Using Pattern.quote on originalName to treat it literally in regex
                // Using \\b for word boundaries
                var pattern = Pattern.compile("\\b" + Pattern.quote(originalName) + "\\b");
                var matcher = pattern.matcher(content);
                var modifiedContent = matcher.replaceAll(Matcher.quoteReplacement(newName));

                if (content.equals(modifiedContent)) {
                    return "Original name '" + originalName + "' not found as a whole word in " + filePath + ". No changes made.";
                }

                return generateDiff(filePath, content, modifiedContent);
            }
        } catch (IOException e) {
            System.err.println("Error in smartRename: " + e.getMessage());
            return "Error processing rename for " + filePath + ": " + e.getMessage();
        }
    }

    private String generateDiff(Path filePath, String originalContent, String modifiedContent) {
        var originalLines = Arrays.asList(originalContent.split("\\r?\\n"));
        var modifiedLines = Arrays.asList(modifiedContent.split("\\r?\\n"));

        try {
            var patch = DiffUtils.diff(originalLines, modifiedLines);
            if (patch.getDeltas().isEmpty()) {
                return "No textual differences found after rename operation (this might be unexpected if a change was intended).";
            }
            // The context size (5 lines) is a common default.
            // The filePath.toString() is used for the "--- a/" and "+++ b/" lines in the diff.
            var unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
                    filePath.toString(), // Original file path for diff header
                    filePath.toString(), // New file path for diff header (same file for rename)
                    originalLines,
                    patch,
                    5 // Context lines
            );
            return String.join("\n", unifiedDiff);
        } catch (Exception e) { // Catching general Exception from DiffUtils, though it's often more specific
            System.err.println("Error generating diff: " + e.getMessage());
            return "Error generating diff: " + e.getMessage();
        }
    }
}
