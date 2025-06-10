package dumb.jaider.agents;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;

import java.util.List;
import java.util.Set;

public abstract class AbstractAgent implements Agent {
    protected final ChatLanguageModel model;
    protected final ChatMemory memory;
    protected final JaiderAiService ai;
    protected final Set<Object> tools;

    // Constructor for actual use, creates the AiService
    public AbstractAgent(ChatLanguageModel model, ChatMemory memory, Set<Object> tools, String systemPrompt) {
        this.model = model;
        this.memory = memory;
        this.tools = tools;
        this.ai = AiServices.builder(JaiderAiService.class)
                .chatLanguageModel(model)
                .chatMemory(memory)
                .tools(tools.toArray())
                .systemMessageProvider(prompVars -> systemPrompt) // Ensure prompVars is used or removed if not needed by lambda
                .build();
    }

    // Constructor for testing, allows injecting a mock AiService
    protected AbstractAgent(ChatLanguageModel model, ChatMemory memory, Set<Object> tools, JaiderAiService aiService, String systemPromptWontBeUsedByAskAgent) {
        this.model = model;
        this.memory = memory;
        this.tools = tools;
        this.ai = aiService; // Use the injected AiService
    }

    @Override
    public Response<AiMessage> act(List<ChatMessage> messages) {
        return ai.act(messages);
    }

    @Override
    public Set<Object> tools() {
        return this.tools;
    }
}
