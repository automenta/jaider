package dumb.jaider.app.exceptions;

public class ComponentInstantiationException extends RuntimeException {
    public ComponentInstantiationException(String componentId, Throwable cause) {
        super("Error instantiating component: " + componentId, cause);
    }

    public ComponentInstantiationException(String componentId, String message, Throwable cause) {
        super("Error instantiating component: " + componentId + ". " + message, cause);
    }

     public ComponentInstantiationException(String componentId, String message) {
        super("Error instantiating component: " + componentId + ". " + message);
    }
}
