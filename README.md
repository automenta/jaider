# Jaider - The AI Programming Assistant

## Overview

Jaider is an interactive, command-line AI programming assistant designed to help developers with coding tasks by leveraging Large Language Models (LLMs). It operates within your project directory, allowing AI agents to read your code, propose changes via diffs, run validation commands, and even search the web for information. Jaider is inspired by tools like Aider and aims to provide a flexible and configurable environment for AI-assisted development.

## Features

*   **Multiple Agent Modes:**
    *   `Coder`: For writing, modifying, and verifying code changes.
    *   `Architect`: For high-level codebase analysis, design discussions (read-only access to tools).
    *   `Ask`: For general questions without file system access.
*   **Multiple LLM Integrations:**
    *   **Ollama:** Connect to local LLMs running via Ollama.
    *   **Generic OpenAI-compatible:** Connect to any API endpoint that follows the OpenAI REST API specification.
    *   **Google Gemini (via Vertex AI):** Utilize Google's Gemini models. (Note: Direct OpenAI integration is currently commented out in the code but can be re-enabled).
*   **Interactive Diff Application:** Proposed code changes are presented as diffs, which you can accept, reject, or even edit before applying.
*   **Configurable Validation Command:** Define a custom command (`runCommand` in `.jaider.json`) to run tests, linters, or build processes after changes are applied. The agent receives structured JSON feedback (exit code, success status, output) from this command.
*   **Web Search Capability:** Agents can use the `searchWeb` tool (powered by Tavily) to find information online.
*   **Project Indexing & Code Search:**
    *   `/index`: Create a searchable semantic index of your project's codebase.
    *   `findRelevantCode` tool: Allows agents to find code snippets relevant to their current task.
*   **Undo Functionality:** The `/undo` command attempts to revert the last applied diff. It uses `git checkout` for modified files and deletes newly created files.
*   **Session Management:** Jaider can save and restore your session, including files in context and chat history.
*   **Configuration via `.jaider.json`:** Most settings are externalized for easy customization.

## Configuration (`.jaider.json`)

Jaider uses a `.jaider.json` file in the root of your project directory for configuration. If this file doesn't exist, Jaider will create one with default values upon first run.

Key configurable fields include:

*   `llmProvider`: Specifies the LLM provider to use.
    *   Values: `"ollama"`, `"genericOpenai"`, `"gemini"`, `"openai"` (currently inactive).
    *   Default: `"ollama"`
*   `ollamaBaseUrl`: (for `ollama` provider) The base URL for your Ollama instance (e.g., `"http://localhost:11434"`).
*   `ollamaModelName`: (for `ollama` provider) The name of the Ollama model to use (e.g., `"llamablit"`).
*   `genericOpenaiBaseUrl`: (for `genericOpenai` provider) The base URL of the OpenAI-compatible API (e.g., `"http://localhost:8080/v1"`).
*   `genericOpenaiModelName`: (for `genericOpenai` provider) The model name for the generic API.
*   `genericOpenaiApiKey`: (for `genericOpenai` provider) The API key, if required by your generic endpoint. (Current integration is basic and may not fully support complex auth headers).
*   `geminiModelName`: (for `gemini` provider) The Gemini model name (e.g., `"gemini-1.5-flash-latest"`).
*   `geminiApiKey`: (for `gemini` provider, if using direct Gemini API - Vertex AI typically uses ADC).
*   `tavilyApiKey`: API key for Tavily web search.
*   `runCommand`: The command to execute for validation (e.g., tests, linter, build). Example: `"mvn test"`, `"npm run lint"`.
*   `apiKeys`: A JSON object to store API keys for specific services (though some, like Gemini and Tavily, have their own top-level keys for simplicity).
    *   `"openai": "YOUR_OPENAI_API_KEY"`
    *   `"anthropic": "YOUR_ANTHROPIC_API_KEY"`
    *   `"google": "YOUR_GOOGLE_API_KEY"` (Note: This is a generic Google key; Gemini via Vertex AI uses ADC or its specific key).

**Example `.jaider.json`:**

```json
{
  "llmProvider": "ollama",
  "ollamaBaseUrl": "http://localhost:11434",
  "ollamaModelName": "llama3",
  "genericOpenaiBaseUrl": "http://localhost:8080/v1",
  "genericOpenaiModelName": "local-gpt",
  "genericOpenaiApiKey": "sk-yourkey",
  "geminiApiKey": "YOUR_GEMINI_API_KEY",
  "geminiModelName": "gemini-1.5-flash-latest",
  "tavilyApiKey": "YOUR_TAVILY_API_KEY",
  "runCommand": "mvn clean test",
  "apiKeys": {
    "openai": "YOUR_OPENAI_API_KEY",
    "anthropic": "YOUR_ANTHROPIC_API_KEY",
    "google": "YOUR_GOOGLE_API_KEY"
  }
}
```

## LLM Setup Details

*   **Ollama:**
    *   Set `llmProvider: "ollama"`.
    *   Configure `ollamaBaseUrl` (default: `"http://localhost:11434"`) and `ollamaModelName`.
*   **Generic OpenAI-compatible API:**
    *   Set `llmProvider: "genericOpenai"`.
    *   Configure `genericOpenaiBaseUrl` and `genericOpenaiModelName`.
    *   Set `genericOpenaiApiKey` if your endpoint requires a Bearer token. Note: The current implementation uses the Ollama client which may not robustly support arbitrary headers for all OpenAI-compatible APIs.
*   **Google Gemini (via Vertex AI):**
    *   Set `llmProvider: "gemini"`.
    *   Configure `geminiModelName` (e.g., `"gemini-pro"`, `"gemini-1.5-flash-latest"`).
    *   **Authentication:** The primary method for Vertex AI is Application Default Credentials (ADC). Ensure your environment is set up (e.g., by running `gcloud auth application-default login`).
    *   You also need to set the following environment variables:
        *   `GOOGLE_CLOUD_PROJECT`: Your Google Cloud Project ID.
        *   `GOOGLE_CLOUD_LOCATION`: The region for your Vertex AI resources (e.g., "us-central1").
    *   The `geminiApiKey` field in `.jaider.json` is provided but might be more relevant for future direct Gemini API integrations if they differ from Vertex AI's ADC.

## Key Commands

*   `/mode <Coder|Architect|Ask>`: Switch between agent modes.
*   `/add <file(s)...>`: Add one or more files (space-separated) to the agent's context.
*   `/run [args...]`: Executes the command specified in `runCommand` from `.jaider.json`. (Note: argument passing to the command is a planned enhancement).
*   `/index`: Creates/updates a semantic index of your project's codebase for the `findRelevantCode` tool.
*   `/undo`: Attempts to revert the last applied diff.
    *   For files modified by the patch, it uses `git checkout <file>` to revert to the last committed state.
    *   For files newly created by the patch, it deletes them.
    *   Use with caution, as it may not perfectly reverse all complex changes.
*   `/edit-config`: Opens the `.jaider.json` file in a simple text editor within the UI, allowing you to modify and save settings. Jaider will reload the configuration upon saving.
*   `/help`: Displays help information.
*   `/exit`: Exits Jaider.

## Tools Available to Agents

Agents (primarily the CoderAgent) can use the following tools:

*   `applyDiff(diff_string)`: Applies a code change provided in the unified diff format.
*   `readFile(file_path)`: Reads the content of a specified file.
*   `runValidationCommand(optional_args)`: Runs the configured validation command. Returns a JSON string with `exitCode` (int), `success` (boolean), and `output` (string).
*   `commitChanges(commit_message)`: Stages all current changes and commits them with the given message.
*   `findRelevantCode(query)`: Searches the indexed codebase for snippets relevant to the query.
*   `searchWeb(query)`: Performs a web search using Tavily to find information online.

## Getting Started

1.  **Prerequisites:**
    *   Java 25 (or the version specified in `pom.xml`).
    *   Apache Maven.
    *   Git (Jaider works best within a Git repository).
    *   (Optional) An Ollama instance running if you intend to use local models.
    *   (Optional) Google Cloud SDK configured for Application Default Credentials if using Gemini via Vertex AI.
2.  **Build:**
    ```bash
    mvn clean package
    ```
3.  **Run:**
    (The exact command might depend on your `pom.xml`'s exec plugin configuration if any. A typical way to run a Main class with Maven, assuming dependencies are shaded or classpath is managed):
    ```bash
    mvn exec:java -Dexec.mainClass="dumb.jaider.Jaider"
    ```
    Or, after packaging, run the JAR (details depend on packaging):
    ```bash
    java -jar target/jaider-1.0-SNAPSHOT.jar
    ```
    (Adjust JAR name and command based on your build output).
4.  **First Run:** Jaider will create a default `.jaider.json` if one isn't found. You should edit this file to configure your preferred LLM provider and API keys. Use `/edit-config` or edit it manually.

---
Inspired by [Aider](https://github.com/Aider-AI/aider).
