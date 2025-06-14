package dumb.jaider.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// Assuming TUI is the concrete UI implementation for now.
// If UI interface is used more broadly, then UI import would be needed.
import dumb.jaider.ui.TUI;

public class Jaider {

    private static final Logger logger = LoggerFactory.getLogger(Jaider.class);

    public static void main(String[] args) {
        logger.info("Jaider self-development task initiated.");
        try {
            // App is now in the same package
            new App(new TUI(), args).run();
        } catch (Exception e) {
            System.err.println("Jaider failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
