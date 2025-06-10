package dumb.jaider.app;

// Assuming TUI is the concrete UI implementation for now.
// If UI interface is used more broadly, then UI import would be needed.
import dumb.jaider.ui.TUI;

public class Jaider {

    public static void main(String[] args) {
        try {
            // App is now in the same package
            new App(new TUI(), args).run();
        } catch (Exception e) {
            System.err.println("Jaider failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }
}