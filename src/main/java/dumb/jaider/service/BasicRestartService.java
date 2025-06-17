package dumb.jaider.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BasicRestartService implements RestartService {

    private static final Logger logger = LoggerFactory.getLogger(BasicRestartService.class);

    @Override
    public boolean restartApplication(String[] originalArgs) {
        logger.info("Attempting to restart application with arguments: {}", Arrays.toString(originalArgs));

        try {
            // Get the path to the currently running Java executable
            var javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            logger.debug("Java executable found at: {}", javaBin);

            // Get the path to the currently running JAR file or main class
            // This is the tricky part and can be unreliable.
            File currentExecutable;
            try {
                currentExecutable = new File(BasicRestartService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            } catch (URISyntaxException e) {
                logger.error("Failed to determine current executable path due to URISyntaxException: {}", e.getMessage(), e);
                return false;
            }

            List<String> command = new ArrayList<>();
            command.add(javaBin);

            // Check if running from a JAR or from compiled classes (e.g., in an IDE)
            if (currentExecutable.isFile() && currentExecutable.getName().toLowerCase().endsWith(".jar")) {
                logger.info("Application appears to be running from JAR: {}", currentExecutable.getPath());
                command.add("-jar");
                command.add(currentExecutable.getPath());
            } else {
                logger.info("Application appears to be running from compiled classes (e.g., IDE). Attempting main class restart.");
                // Attempting to use classpath and main class - this is more fragile.
                var mainClass = System.getProperty("sun.java.command"); // This can be "MainClass arg1 arg2" or just "MainClass" or "path/to/jar.jar"
                if (mainClass == null || mainClass.trim().isEmpty()) {
                     logger.error("Cannot determine main class (sun.java.command is empty). Cannot restart from compiled classes.");
                     return false;
                }
                // Attempt to parse the main class from sun.java.command
                var commandParts = mainClass.split("\\s+");
                // Main class
                if (commandParts[0].toLowerCase().endsWith(".jar")) { // It was actually a jar
                     logger.info("sun.java.command indicates JAR: {}. Switching to JAR restart.", commandParts[0]);
                    command.set(0, "-jar"); // remove javaBin, add -jar
                    // Add other args from sun.java.command if any, before originalArgs
                } else {
                    // Assuming commandParts[0] is the main class
                    logger.info("Attempting restart with main class: {} (requires classpath to be set correctly).", commandParts[0]);
                    command.add("-cp");
                    command.add(System.getProperty("java.class.path"));
                    // Add other args from sun.java.command if any, before originalArgs
                }
                command.add(commandParts[0]); // jar path
                command.addAll(Arrays.asList(commandParts).subList(1, commandParts.length));
            }

            if (originalArgs != null) {
                command.addAll(Arrays.asList(originalArgs));
            }

            logger.info("Restart command: {}", String.join(" ", command));

            var processBuilder = new ProcessBuilder(command);
            // Optionally, redirect output/error streams of the new process
            // processBuilder.inheritIO(); // Or redirect to files

            processBuilder.start(); // Start the new process

            logger.info("New process started. Exiting current process.");
            System.exit(0); // Exit the current application
            return true; // Should not be reached if System.exit(0) works

        } catch (IOException e) {
            logger.error("IOException during application restart attempt.", e);
            return false;
        }
    }
}
