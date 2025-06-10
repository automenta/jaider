package dumb.jaider.agents;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.Response;

import java.util.List;

public interface JaiderAiService {
    Response<AiMessage> act(List<ChatMessage> messages);
    String chat(String userMessage); // Added for direct String interaction
}
