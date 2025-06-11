package dumb.jaider.refactoring;

import java.util.Objects;

public class RenameOperationInput {
    private final String targetName;
    private final String newName;
    private final int targetPosition; // Optional: -1 if not specified, character offset

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

    public String getTargetName() {
        return targetName;
    }

    public String getNewName() {
        return newName;
    }

    public int getTargetPosition() {
        return targetPosition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RenameOperationInput that = (RenameOperationInput) o;
        return targetPosition == that.targetPosition &&
               targetName.equals(that.targetName) &&
               newName.equals(that.newName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetName, newName, targetPosition);
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
