package dumb.jaider.tooling;

import java.util.Map;

/**
 * Represents an abstract tool that can be executed by Jaider.
 */
public interface Tool {

    /**
     * Gets the name of the tool.
     * @return The unique name of the tool.
     */
    String getName();

    /**
     * Checks if the tool is available and ready to be used.
     * This could involve checking for installed CLIs, accessible APIs, etc.
     * @return true if the tool is available, false otherwise.
     */
    boolean isAvailable();

    /**
     * Executes the tool with the given context.
     * @param context The context for the tool execution, containing necessary data like file paths, project root, etc.
     * @return A string representing the raw output from the tool (e.g., stdout of a CLI).
     * @throws Exception if an error occurs during execution.
     */
    String execute(ToolContext context) throws Exception;

    /**
     * Parses the raw output from the tool into a structured format.
     * The specific structure will depend on the tool.
     * @param rawOutput The raw output string from the execute() method.
     * @return A structured representation of the tool's output (e.g., a Map, a custom POJO).
     *         Returns null if parsing is not applicable or fails.
     */
    Object parseOutput(String rawOutput);

    /**
     * Provides a description of what the tool does.
     * @return A human-readable description.
     */
    String getDescription();
}
