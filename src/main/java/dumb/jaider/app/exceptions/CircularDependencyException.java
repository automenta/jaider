package dumb.jaider.app.exceptions;

import java.util.Set;

public class CircularDependencyException extends RuntimeException {
    public CircularDependencyException(String componentId, Set<String> creationPath) {
        super("Circular dependency detected for component: " + componentId +
              ". Creation path: " + String.join(" -> ", creationPath) + " -> [" + componentId + "]");
    }

    public CircularDependencyException(String message) {
        super(message);
    }
}
