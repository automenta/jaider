package dumb.jaider.coordinator;

import dumb.jaider.tooling.ToolContext;

import java.util.Objects;

/**
 * Represents a request to execute a specific tool with a given context.
 * @param continueOnError  Whether to continue with next tools if this one fails
 */
public record ToolExecutionRequest(String toolName, ToolContext context, boolean continueOnError) {
    public ToolExecutionRequest {
        Objects.requireNonNull(toolName, "toolName cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
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
