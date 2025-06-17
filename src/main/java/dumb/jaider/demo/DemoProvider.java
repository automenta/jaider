package dumb.jaider.demo;

import java.util.List;

public interface DemoProvider {
    String getName();
    String getDescription(); // A short description for help text
    List<DemoStep> getSteps();
    String getInitialPomContent(); // Moved from DemoCommand
    // Add any other common methods needed by demos, like specific POM content
}
