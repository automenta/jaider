package dumb.jaider.app.exceptions;

public class InvalidComponentDefinitionException extends RuntimeException {
    public InvalidComponentDefinitionException(String componentId, String message) {
        super("Invalid definition for component '" + componentId + "': " + message);
    }

    public InvalidComponentDefinitionException(String message) {
        super(message);
    }
}
