package dumb.jaider.refactoring;

import java.util.Objects;

/**
 * @param targetPosition  Optional: -1 if not specified, character offset */
public record RenameOperationInput(String targetName, String newName, int targetPosition) {
    public RenameOperationInput(String targetName, String newName, int targetPosition) {
        this.targetName = Objects.requireNonNull(targetName, "targetName cannot be null");
        this.newName = Objects.requireNonNull(newName, "newName cannot be null");
        if (targetName.isBlank()) {
            throw new IllegalArgumentException("targetName cannot be blank");
        }
        if (newName.isBlank()) {
            throw new IllegalArgumentException("newName cannot be blank");
        }
        this.targetPosition = targetPosition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (RenameOperationInput) o;
        return targetPosition == that.targetPosition &&
                targetName.equals(that.targetName) &&
                newName.equals(that.newName);
    }

    @Override
    public String toString() {
        return "RenameOperationInput{" +
                "targetName='" + targetName + '\'' +
                ", newName='" + newName + '\'' +
                ", targetPosition=" + targetPosition +
                '}';
    }
}
