package dumb.jaider.app;

import dumb.jaider.ui.TUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
