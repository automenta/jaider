package org.jaider.service;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicRestartService implements RestartService {

    private static final Logger logger = LoggerFactory.getLogger(BasicRestartService.class);

    @Override
    public boolean restartApplication(String[] originalArgs) {
        logger.info("Attempting to restart application with arguments: {}", Arrays.toString(originalArgs));

        try {
            // Get the path to the currently running Java executable
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
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
                String mainClass = System.getProperty("sun.java.command"); // This can be "MainClass arg1 arg2" or just "MainClass" or "path/to/jar.jar"
                if (mainClass == null || mainClass.trim().isEmpty()) {
                     logger.error("Cannot determine main class (sun.java.command is empty). Cannot restart from compiled classes.");
                     return false;
                }
                // Attempt to parse the main class from sun.java.command
                String[] commandParts = mainClass.split("\\s+");
                if (commandParts[0].toLowerCase().endsWith(".jar")) { // It was actually a jar
                     logger.info("sun.java.command indicates JAR: {}. Switching to JAR restart.", commandParts[0]);
                     command.set(command.size() -1, "-jar"); // remove javaBin, add -jar
                     command.add(commandParts[0]); // jar path
                     // Add other args from sun.java.command if any, before originalArgs
                     for(int i=1; i<commandParts.length; i++) {
                        command.add(commandParts[i]);
                     }
                } else {
                    // Assuming commandParts[0] is the main class
                    logger.info("Attempting restart with main class: {} (requires classpath to be set correctly).", commandParts[0]);
                    command.add("-cp");
                    command.add(System.getProperty("java.class.path"));
                    command.add(commandParts[0]); // Main class
                    // Add other args from sun.java.command if any, before originalArgs
                     for(int i=1; i<commandParts.length; i++) {
                        command.add(commandParts[i]);
                     }
                }
            }

            if (originalArgs != null) {
                command.addAll(Arrays.asList(originalArgs));
            }

            logger.info("Restart command: {}", String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
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
