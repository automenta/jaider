package dumb.jaider.coordinator;

import dumb.jaider.tooling.Tool;
import java.util.List;
import java.util.Map;

/**
 * Responsible for coordinating the execution of tools.
 */
public interface ToolCoordinator {
    /**
     * Executes a list of tool requests sequentially.
     * @param requests The list of tool execution requests.
     * @param availableTools A map of available tool names to Tool instances.
     * @return A list of results from each tool execution (e.g., raw output or parsed output).
     *         The size of the list should match the number of requests processed.
     */
    List<Object> executePlan(List<ToolExecutionRequest> requests, Map<String, Tool> availableTools);

    // Later, this interface could be expanded to support more complex planning,
    // such as conditional execution, parallel execution, or dynamic plan generation.
}
