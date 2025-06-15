package dumb.jaider.llm;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification; // Added import
import dev.langchain4j.data.message.ChatMessage;
// import dev.langchain4j.data.message.Content; // Reverted, will use message.text()
// import dev.langchain4j.data.message.TextContent; // Reverted
import dev.langchain4j.model.Tokenizer;

import java.util.Collections;
import java.util.List;

public class NoOpTokenizer implements Tokenizer {

    @Override
    public int estimateTokenCountInText(String text) {
        if (text == null) return 0;
        return (text.length() + 3) / 4; // Rough estimate
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        if (message == null) return 0;
        // Reverting to message.text() to avoid symbol not found error for contents()
        // This will likely re-introduce a deprecation warning, but aims to fix compilation error.
        return estimateTokenCountInText(message.text());
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        int count = 0;
        if (messages == null) return 0;
        for (ChatMessage message : messages) {
            count += estimateTokenCountInMessage(message);
        }
        return count;
    }

    @Override
    public int estimateTokenCountInToolExecutionRequests(Iterable<ToolExecutionRequest> requests) {
        if (requests == null) return 0;
        int count = 0;
        for (ToolExecutionRequest request : requests) {
            if (request != null) {
                count += (request.name() != null ? estimateTokenCountInText(request.name()) : 0);
                count += (request.arguments() != null ? estimateTokenCountInText(request.arguments()) : 0);
            }
        }
        return count;
    }

    @Override
    public int estimateTokenCountInToolSpecifications(Iterable<ToolSpecification> toolSpecifications) {
        if (toolSpecifications == null) return 0;
        int count = 0;
        for (ToolSpecification spec : toolSpecifications) {
            if (spec != null) {
                count += estimateTokenCountInText(spec.name());
                if (spec.description() != null) {
                     count += estimateTokenCountInText(spec.description());
                }
                // A very rough estimate for parameters
                if (spec.parameters() != null && spec.parameters().properties() != null) {
                    count += spec.parameters().properties().size() * 5; // 5 tokens per parameter as a wild guess
                }
            }
        }
        return count;
    }

    // Removing @Override temporarily due to persistent compilation errors
    // and inability to verify exact interface signature.
    // This is a workaround for a NoOp implementation.
    public List<Integer> encode(String text) {
        return Collections.emptyList();
    }

    // Removing @Override temporarily
    public List<Integer> encode(String text, int maxTokens) {
        List<Integer> encoded = encode(text);
        if (encoded.size() > maxTokens) {
            return encoded.subList(0, maxTokens);
        }
        return encoded;
    }

    // Removing @Override temporarily
    public String decode(List<Integer> tokens) {
        return "";
    }
}
