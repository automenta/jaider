package dumb.jaider.tools;

import java.io.File;
import java.io.IOException;

@FunctionalInterface
public interface ProcessExecutor {
    Process execute(String[] command, File directory) throws IOException;
}
