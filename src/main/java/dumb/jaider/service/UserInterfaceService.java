package dumb.jaider.service;

// Placeholder for User Interface Service
public interface UserInterfaceService {
    void showMessage(String message);
    void showError(String message);
    // Returns true for "yes", false for "no"
    // The second parameter is a callback that accepts the boolean response.
    void askYesNoQuestion(String message, java.util.function.Consumer<Boolean> callback);
}
