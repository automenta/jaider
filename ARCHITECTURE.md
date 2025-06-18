# Jaider Architecture Overview

This document provides a high-level overview of the Jaider application's architecture. Jaider is an AI programming assistant that allows users to interact with Large Language Models (LLMs) to perform coding tasks.

## Core Components

Jaider's architecture is modular, with several key components responsible for different aspects of its functionality:

1.  **`Jaider` (Main Class - `dumb.jaider.app.Jaider`)**:
    *   The main entry point of the application.
    *   Initializes and starts the `App` component.

2.  **`App` (`dumb.jaider.app.App`)**:
    *   The central orchestrator of the application.
    *   Manages the application's lifecycle, state (e.g., `IDLE`, `AGENT_THINKING`, `WAITING_USER_PLAN_APPROVAL`), and global services.
    *   Integrates various services like UI, configuration, agent management, and user input handling.
    *   Handles the main interaction loop.

3.  **`UI` (Interface `dumb.jaider.ui.UI`, Implementation e.g., `dumb.jaider.ui.TUI`)**:
    *   Responsible for all user interactions, including displaying information and capturing user input.
    *   The `TUI` (Text-based User Interface) is the primary implementation, providing a command-line interface.
    *   Abstracted to potentially allow for other UI implementations in the future.

4.  **`Config` (`dumb.jaider.config.Config`)**:
    *   Manages application settings, primarily loaded from the `.jaider.json` file.
    *   Provides access to configuration values like LLM provider details, API keys, and the `runCommand`.
    *   Supports dynamic reloading of configuration.

5.  **`JaiderModel` (`dumb.jaider.model.JaiderModel`)**:
    *   The data model for the application, holding state information relevant to the UI and application logic.
    *   Includes chat history, logs, status bar text, active suggestions, etc.
    *   Observed by the UI to trigger redraws when data changes.

6.  **`AgentService` (`dumb.jaider.app.AgentService`)**:
    *   Manages the different AI agent types (Coder, Architect, Ask).
    *   Responsible for creating, switching, and providing access to the currently active agent.
    *   Configures agents with appropriate tools and LLM models based on the application settings.

7.  **`LLM Providers` (e.g., `dumb.jaider.llm.OllamaProvider`, `dumb.jaider.llm.OpenAiProvider`, `dumb.jaider.llm.GeminiProvider`)**:
    *   Implementations for interacting with various LLM services (Ollama, OpenAI, Gemini via Vertex AI, Generic OpenAI-compatible).
    *   Handle the specifics of API communication for each LLM.

8.  **`AgentInteractionService` (`dumb.jaider.app.AgentInteractionService`)**:
    *   Manages the direct interaction flow with the current AI agent.
    *   Sends user messages to the agent and processes the agent's responses.
    *   Handles asynchronous communication with the agent.
    *   Manages the plan approval workflow (extracting plans, prompting user for approval, and proceeding based on user's decision).
    *   Orchestrates tool execution requests from the agent via `ToolLifecycleManager`.

9.  **`UserInputHandler` (`dumb.jaider.app.UserInputHandler`)**:
    *   Parses raw user input from the UI.
    *   Distinguishes between commands (e.g., `/mode`, `/add`) and messages intended for the AI agent.
    *   Routes commands to their respective `Command` implementations.
    *   Forwards messages to the `AgentInteractionService`.
    *   Interacts with `ProactiveSuggestionService` to display and manage suggestions.

10. **`Command` (Interface `dumb.jaider.commands.Command` and implementations)**:
    *   Defines a common interface for all user-executable commands (e.g., `AddFileCommand`, `ModeCommand`).
    *   Each command encapsulates the logic for its specific action.

11. **`ToolManager` and `Tools` (e.g., `dumb.jaider.toolmanager.ToolManager`, `dumb.jaider.tools.*`)**:
    *   `ToolManager`: Manages the lifecycle and availability of tools that can be used by AI agents.
    *   Tools (`applyDiff`, `readFile`, `runValidationCommand`, `searchWeb`, etc.): Represent specific capabilities the AI agent can invoke to interact with the system or external resources. These are often wrappers around LangChain4j `dev.langchain4j.agent.tool.Tool` annotated methods.

12. **`ToolLifecycleManager` (`dumb.jaider.app.ToolLifecycleManager`)**:
    *   Responsible for handling the execution of tools requested by the AI agent.
    *   Takes a `ToolExecutionRequest` from the agent, invokes the appropriate tool method, and returns the result to the `AgentInteractionService`.

13. **`SessionManager` (`dumb.jaider.app.SessionManager`)**:
    *   Handles saving and loading of the application's session, which can include chat history, files in context, and other relevant state to allow users to resume their work.

14. **`SelfUpdateService` (`dumb.jaider.app.SelfUpdateService`)**:
    *   Manages the process of Jaider updating its own codebase. This involves receiving a proposed update (diff), applying it, building, testing, and restarting the application.

15. **`ProactiveSuggestionService` (`dumb.jaider.suggestion.ProactiveSuggestionService`)**:
    *   Generates and manages proactive suggestions for the user based on their input and the current context. These suggestions might be potential commands or actions the user might want to take.

## High-Level Interaction Flow (Example: User sends a message to Coder agent)

1.  User types a message in the **UI** (e.g., "Refactor the `doSomething` method in `MyClass.java` to be more efficient").
2.  The **UI** passes the raw input string to the **`UserInputHandler`**.
3.  **`UserInputHandler`** determines it's not a command (doesn't start with `/`).
4.  (Optional) **`ProactiveSuggestionService`** might generate suggestions based on the input.
5.  **`UserInputHandler`** forwards the message to the **`App`**, which then passes it to **`AgentInteractionService`**.
6.  **`AgentInteractionService`** adds the user message to the chat memory and invokes the current agent (obtained from **`AgentService`**, e.g., CoderAgent).
7.  The agent (e.g., CoderAgent, configured with an **LLM Provider** like **`OpenAiProvider`**) processes the message and its context (including any tools it has, like `readFile` or `applyDiff`).
8.  The agent formulates a response, which might include a plan and/or tool execution requests.
9.  **`AgentInteractionService`** receives the agent's response.
    *   If a plan is expected and present, it extracts the plan and uses the **UI** to ask the user for approval.
    *   If the user approves, or if no plan approval was needed, and there's a tool execution request:
        *   The request is passed to the **`ToolLifecycleManager`**.
        *   **`ToolLifecycleManager`** executes the relevant tool (e.g., `readFileContent` tool reads a file).
        *   The result of the tool execution is returned to the agent via **`AgentInteractionService`**, and the agent continues its turn.
    *   If the agent's response is a textual message without tool calls, it's displayed to the user via the **UI**.
10. The **UI** redraws to show the new state, messages, or prompts.
11. The **`SessionManager`** saves the session state.

This flow illustrates the collaboration between the components to fulfill a user request. The architecture is designed to be extensible, allowing for new agents, tools, LLM providers, and UI components to be added in the future.
