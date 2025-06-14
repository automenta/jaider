package dumb.jaider.app.exceptions;

public class TokenizerInitializationException extends RuntimeException {
    public TokenizerInitializationException(String message) {
        super(message);
    }

    public TokenizerInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
