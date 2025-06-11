package dumb.jaider.refactoring;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ParserRegistry {
    private final Map<String, LanguageParser> parsers = new HashMap<>();

    public void registerParser(LanguageParser parser) {
        if (parser != null && parser.getLanguageId() != null && !parser.getLanguageId().isBlank()) {
            parsers.put(parser.getLanguageId().toLowerCase(), parser);
        } else {
            // Log or throw an exception for invalid parser registration
            System.err.println("ParserRegistry: Attempted to register an invalid parser (null, or null/blank language ID).");
        }
    }

    public Optional<LanguageParser> getParserForFile(Path filePath) {
        if (filePath == null) {
            return Optional.empty();
        }
        String fileName = filePath.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return Optional.empty(); // No extension or empty extension
        }
        String extension = fileName.substring(lastDot + 1).toLowerCase();
        // Simple extension to language ID mapping. This could be more sophisticated.
        // For example, .js -> javascript, .py -> python, .java -> java
        // This mapping might need to be configurable or more robust.
        // For now, assuming languageId from parser IS the extension.
        return Optional.ofNullable(parsers.get(extension));
    }

    // Potentially add a method to get parser by explicit language ID string
    public Optional<LanguageParser> getParser(String languageId) {
        if (languageId == null || languageId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(parsers.get(languageId.toLowerCase()));
    }
}
