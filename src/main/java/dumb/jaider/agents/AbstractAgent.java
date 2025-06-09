package dumb.jaider.agents;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import java.util.List;
import java.util.Set;

public abstract class AbstractAgent implements Agent {
    protected final JaiderAiService ai;
    protected final Set<Object> tools;

    public AbstractAgent(ChatLanguageModel model, ChatMemory memory, Set<Object> tools, String systemPrompt) {
        this.tools = tools;
        this.ai = AiServices.builder(JaiderAiService.class)
            .chatLanguageModel(model)
            .chatMemory(memory)
            .tools(tools.toArray())
            .systemMessageProvider(vars -> systemPrompt)
            .build();
    }

    @Override
    public Response<AiMessage> act(List<ChatMessage> messages) {
        return ai.act(messages);
    }

    @Override
    public Set<Object> getTools() {
        return this.tools;
    }
}
