package dumb.jaider.coordinator;

import dumb.jaider.tooling.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleToolCoordinator implements ToolCoordinator {
    private static final Logger LOGGER = Logger.getLogger(SimpleToolCoordinator.class.getName());

    @Override
    public List<Object> executePlan(List<ToolExecutionRequest> requests, Map<String, Tool> availableTools) {
        List<Object> results = new ArrayList<>();
        LOGGER.log(Level.INFO, "Starting execution of tool plan with " + requests.size() + " requests.");

        for (ToolExecutionRequest request : requests) {
            Tool tool = availableTools.get(request.toolName());
            if (tool == null) {
                String errorMsg = "Tool not found: " + request.toolName();
                LOGGER.log(Level.SEVERE, errorMsg);
                results.add(new RuntimeException(errorMsg)); // Store exception as result
                if (!request.continueOnError()) {
                    LOGGER.log(Level.SEVERE, "Halting execution due to missing tool and continueOnError=false.");
                    break;
                }
                continue;
            }

            if (!tool.isAvailable()) {
                String errorMsg = "Tool not available: " + request.toolName();
                LOGGER.log(Level.WARNING, errorMsg);
                results.add(new RuntimeException(errorMsg)); // Store exception as result
                if (!request.continueOnError()) {
                    LOGGER.log(Level.WARNING, "Halting execution due to unavailable tool and continueOnError=false.");
                    break;
                }
                continue;
            }

            LOGGER.log(Level.INFO, "Executing tool: " + tool.getName() + " with context: " + request.context().getAllParameters());
            try {
                String rawOutput = tool.execute(request.context());
                Object parsedOutput = tool.parseOutput(rawOutput);
                results.add(parsedOutput != null ? parsedOutput : rawOutput); // Prefer parsed output
                LOGGER.log(Level.INFO, "Tool " + tool.getName() + " executed successfully.");
            } catch (Exception e) {
                String errorMsg = "Error executing tool " + request.toolName() + ": " + e.getMessage();
                LOGGER.log(Level.SEVERE, errorMsg, e);
                results.add(e); // Store exception as result
                if (!request.continueOnError()) {
                    LOGGER.log(Level.SEVERE, "Halting execution due to tool error and continueOnError=false.");
                    break;
                }
            }
        }
        LOGGER.log(Level.INFO, "Finished execution of tool plan. Processed " + results.size() + " tools.");
        return results;
    }
}
