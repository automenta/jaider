package dumb.jaider.app;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dumb.jaider.agents.Agent;
import dumb.jaider.config.Config;
import dumb.jaider.llm.LlmProviderFactory;
import dumb.jaider.model.JaiderModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AgentService {
    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);

    private final Map<String, Agent> agents = new HashMap<>();
    private Agent currentAgent;
    private final Config config;
    private final JaiderModel jaiderModel;
    private final ChatMemory chatMemory;
    private final LlmProviderFactory llmProviderFactory; // To create models for agents

    public AgentService(Config config, JaiderModel jaiderModel, ChatMemory chatMemory, LlmProviderFactory llmProviderFactory) {
        this.config = config;
        this.jaiderModel = jaiderModel;
        this.chatMemory = chatMemory;
        this.llmProviderFactory = llmProviderFactory;
        // Agent initialization will be handled by a separate method, called from App.update()
    }

    public Agent getAgent(String name) {
        return agents.get(name);
    }

    public Agent getCurrentAgent() {
        return currentAgent;
    }

    public Set<String> getAvailableAgentNames() {
        return agents.keySet();
    }

    public void switchAgent(String mode) {
        var newAgent = agents.values().stream()
                               .filter(a -> a.name().equalsIgnoreCase(mode))
                               .findFirst()
                               .orElse(null);

        if (newAgent != null) {
            currentAgent = newAgent;
            jaiderModel.mode = currentAgent.name();
            jaiderModel.addLog(AiMessage.from("[Jaider] Switched to " + jaiderModel.mode + " mode."));
        } else {
            var availableModes = String.join(", ", agents.keySet());
            jaiderModel.addLog(AiMessage.from("[Jaider] Unknown mode. Available modes: " + availableModes));
        }
    }

    public void updateAgents() {
        // This method will contain the agent initialization logic previously in App.update()
        agents.clear();
        var injector = config.getInjector();

        if (injector == null) {
            logger.error("DI not initialized in AgentService.updateAgents(). Cannot fetch components. Reverting to manual agent creation (limited functionality).");
            // Fallback to minimal functionality or throw error
            var localChatModelManual = llmProviderFactory.createChatModel();
            var standardToolsManual = new dumb.jaider.tools.StandardTools(jaiderModel, config, llmProviderFactory.createEmbeddingModel());

            Set<Object> fallbackCoderTools = new HashSet<>();
            fallbackCoderTools.add(standardToolsManual);
            // Assuming CoderAgent, ArchitectAgent, AskAgent are in dumb.jaider.agents package
            agents.put("Coder", new dumb.jaider.agents.CoderAgent(localChatModelManual, chatMemory, fallbackCoderTools, null));
            agents.put("Architect", new dumb.jaider.agents.ArchitectAgent(localChatModelManual, chatMemory, standardToolsManual));
            agents.put("Ask", new dumb.jaider.agents.AskAgent(localChatModelManual, chatMemory));
        } else {
            try {
                agents.put("Coder", (Agent) injector.getComponent("coderAgent"));
                agents.put("Architect", (Agent) injector.getComponent("architectAgent"));
                agents.put("Ask", (Agent) injector.getComponent("askAgent"));
            } catch (Exception e) {
                logger.error("Error updating agents from DI.", e);
                jaiderModel.addLog(AiMessage.from("[Jaider] CRITICAL ERROR: Failed to update agents using DI. Application might be unstable. Check config. " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }

        // Set default agent if current one is not valid anymore or null
        if (currentAgent == null || !agents.containsKey(currentAgent.name())) {
            currentAgent = agents.get("Coder"); // Default to Coder
            if (currentAgent != null) {
                jaiderModel.mode = currentAgent.name();
            } else {
                 // This case should ideally not happen if Coder is always creatable
                logger.error("Default agent 'Coder' could not be initialized.");
                jaiderModel.addLog(AiMessage.from("[Jaider] CRITICAL ERROR: Default agent 'Coder' could not be initialized."));
            }
        }
    }
}
