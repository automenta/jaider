package dumb.jaider.app.exceptions;

public class ComponentNotFoundException extends RuntimeException {
    public ComponentNotFoundException(String componentId) {
        super("Component definition not found for id: " + componentId);
    }

    public ComponentNotFoundException(String componentId, String additionalMessage) {
        super("Component definition not found for id: " + componentId + ". " + additionalMessage);
    }
}
