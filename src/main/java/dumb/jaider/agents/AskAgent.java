package dumb.jaider.agents;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import java.util.Set;

public class AskAgent extends AbstractAgent {
    public AskAgent(ChatLanguageModel model, ChatMemory memory) {
        super(model, memory, Set.of(), "You are a helpful assistant. Answer the user's questions clearly and concisely. You do not have access to any tools.");
    }

    @Override
    public String name() {
        return "Ask";
    }
}
