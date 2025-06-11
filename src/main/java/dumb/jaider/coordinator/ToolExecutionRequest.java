package dumb.jaider.coordinator;

import dumb.jaider.tooling.ToolContext;
import java.util.Objects;

/**
 * Represents a request to execute a specific tool with a given context.
 */
public class ToolExecutionRequest {
    private final String toolName;
    private final ToolContext context;
    private final boolean continueOnError; // Whether to continue with next tools if this one fails

    public ToolExecutionRequest(String toolName, ToolContext context, boolean continueOnError) {
        Objects.requireNonNull(toolName, "toolName cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        this.toolName = toolName;
        this.context = context;
        this.continueOnError = continueOnError;
    }

    public String getToolName() {
        return toolName;
    }

    public ToolContext getContext() {
        return context;
    }

    public boolean isContinueOnError() {
        return continueOnError;
    }

    @Override
    public String toString() {
        return "ToolExecutionRequest{" +
               "toolName='" + toolName + '\'' +
               ", context=" + context + // Consider a more concise toString for context
               ", continueOnError=" + continueOnError +
               '}';
    }
}
