package dumb.jaider.refactoring;

import java.io.IOException;
import java.nio.file.Path;

public interface LanguageParser {
    /**
     * Parses the given source file content into a language-agnostic AST.
     * @param filePath The path to the source file (used for context, e.g., diagnostics).
     * @param content The content of the source file.
     * @return The root node of the LanguageAgnosticAST.
     * @throws IOException If there's an error reading the file or during parsing.
     * @throws UnsupportedOperationException If parsing this language or specific constructs is not supported.
     */
    LanguageAgnosticASTNode parse(Path filePath, String content) throws IOException;

    /**
     * Gets a unique identifier for the language supported by this parser.
     * (e.g., "java", "python", "javascript"). This ID should match keys used in ParserRegistry.
     */
    String getLanguageId();
}
