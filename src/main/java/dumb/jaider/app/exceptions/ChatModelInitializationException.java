package dumb.jaider.app.exceptions;

public class ChatModelInitializationException extends RuntimeException {
    public ChatModelInitializationException(String message) {
        super(message);
    }

    public ChatModelInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
