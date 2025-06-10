package dumb.jaider.refactoring;

import java.util.List;
import java.util.Optional;

public interface LanguageAgnosticASTNode {
    /**
     * Gets the type of this node (e.g., "MethodDeclaration", "VariableDeclarator", "Identifier").
     * The type names should be generic where possible, or language-specific if unavoidable.
     */
    String getType();

    /**
     * Gets the text content of this node from the source code.
     */
    String getText();

    /**
     * Gets the starting offset of this node in the source file.
     */
    int getStartOffset();

    /**
     * Gets the ending offset of this node in the source file.
     */
    int getEndOffset();

    /**
     * Gets the parent of this node.
     * @return An Optional containing the parent, or an empty Optional if this is the root node.
     */
    Optional<LanguageAgnosticASTNode> getParent();

    /**
     * Gets the children of this node.
     * @return A list of child nodes. The list is empty if this is a leaf node.
     */
    List<LanguageAgnosticASTNode> getChildren();

    /**
     * Finds all descendant nodes of a specific type.
     * @param type The type of the nodes to find.
     * @return A list of descendant nodes matching the type.
     */
    List<LanguageAgnosticASTNode> findDescendantsOfType(String type);

    /**
     * Gets a language-specific property of the node.
     * This allows access to details not covered by the generic interface.
     * @param key The key for the property.
     * @return An Optional containing the property value, or empty if not present.
     */
    Optional<Object> getProperty(String key);
}
