package dumb.jaider.agents;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.Set;

public interface Agent {
    String name();
    Response<AiMessage> act(List<ChatMessage> messages); // Used by some agents that handle full message history
    String act(String userQuery); // For simpler agents interacting with a single query
    Set<Object> getTools();
}
