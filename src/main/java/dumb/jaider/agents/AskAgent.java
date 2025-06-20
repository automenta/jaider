package dumb.jaider.agents;

// Removed unused imports: UserMessage, Response, AiMessage (directly)

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;

import java.util.Set;

public class AskAgent extends AbstractAgent {

    // Constructor for production use
    public AskAgent(ChatModel model, ChatMemory memory) { // Changed from ChatModel
        super(model, memory, Set.of(), "You are a helpful assistant. Answer the user's questions clearly and concisely. You do not have access to any tools.");
    }

    // Constructor for testing, allowing JaiderAiService injection
    protected AskAgent(ChatModel model, ChatMemory memory, JaiderAiService aiService) { // Changed from ChatModel
        super(model, memory, Set.of(), aiService, null); // System prompt is not used by this constructor path if AiService is mocked
    }

    @Override
    public String name() {
        return "Ask";
    }

    // This method now correctly overrides Agent.act(String)
    // and uses the JaiderAiService (this.ai) from AbstractAgent.
    @Override
    public String act(String userQuery) {
        // The AiService (this.ai) is already configured with ChatMemory and ChatModel.
        // It will handle adding the user message to memory, calling the model,
        // and adding the AI response to memory.
        return this.ai.chat(userQuery);
    }
}
