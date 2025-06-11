package dumb.jaider.refactoring;

import java.util.List;

public interface RefactoringOperation<T_Input> {
    /**
     * Calculates the list of source changes required to perform the refactoring.
     *
     * @param astRoot The root of the language-agnostic AST for the file.
     * @param input Specific parameters for the refactoring operation (e.g., {@link RenameOperationInput}).
     * @return A list of {@link SourceChange} objects representing the modifications.
     *         Returns an empty list if no changes are needed or if the operation cannot be performed.
     * @throws IllegalArgumentException if the input is invalid for the operation.
     */
    List<SourceChange> calculateChanges(LanguageAgnosticASTNode astRoot, T_Input input);
}
