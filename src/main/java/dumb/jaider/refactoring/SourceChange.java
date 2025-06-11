package dumb.jaider.refactoring;

import java.nio.file.Path;
import java.util.Objects;

public class SourceChange {
    private final Path filePath;
    private final int offset;
    private final int length;
    private final String newText;

    public SourceChange(Path filePath, int offset, int length, String newText) {
        this.filePath = Objects.requireNonNull(filePath, "filePath cannot be null");
        this.newText = Objects.requireNonNull(newText, "newText cannot be null");
        if (offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative");
        }
        if (length < 0) {
            throw new IllegalArgumentException("length cannot be negative");
        }
        this.offset = offset;
        this.length = length;
    }

    public Path getFilePath() {
        return filePath;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public String getNewText() {
        return newText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SourceChange that = (SourceChange) o;
        return offset == that.offset &&
               length == that.length &&
               filePath.equals(that.filePath) &&
               newText.equals(that.newText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, offset, length, newText);
    }

    @Override
    public String toString() {
        return "SourceChange{" +
                "filePath=" + filePath +
                ", offset=" + offset +
                ", length=" + length +
                ", newText='" + newText + '\'' +
                '}';
    }
}
