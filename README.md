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
    *   **OpenAI:** Connect directly to OpenAI's API.
    *   **Google Gemini (via Vertex AI):** Utilize Google's Gemini models.
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

**API Key Configuration Precedence:** For services like Tavily, Gemini, and OpenAI (including Generic OpenAI), API keys are resolved in the following order:
1. Environment Variable (e.g., `TAVILY_API_KEY`, `GEMINI_API_KEY`, `GENERIC_OPENAI_API_KEY`, `OPENAI_API_KEY`)
2. Specific top-level key in `.jaider.json` (e.g., `tavilyApiKey`, `geminiApiKey`, `genericOpenaiApiKey`, `openaiApiKey`)
3. Key within the `apiKeys` map in `.jaider.json` (e.g., `"tavily": "your_key"`, `"google": "your_key"` for Gemini, `"genericOpenai": "your_key"`, `"openai": "your_key"`)

Key configurable fields include:

*   `llmProvider`: Specifies the LLM provider to use.
    *   Values: `"ollama"`, `"genericOpenai"`, `"openai"`, `"gemini"`.
    *   Default: `"ollama"`
*   `ollamaBaseUrl`: (for `ollama` provider) The base URL for your Ollama instance (e.g., `"http://localhost:11434"`).
*   `ollamaModelName`: (for `ollama` provider) The name of the Ollama model to use (e.g., `"llamablit"`).
*   `genericOpenaiBaseUrl`: (for `genericOpenai` provider) The base URL of the OpenAI-compatible API (e.g., `"http://localhost:8080/v1"`).
*   `genericOpenaiModelName`: (for `genericOpenai` provider) The chat model name for the generic API.
*   `genericOpenaiEmbeddingModelName`: (for `genericOpenai` provider) The embedding model name to use with your generic OpenAI-compatible endpoint (e.g., `"text-embedding-ada-002"`). Default: `"text-embedding-ada-002"`
*   `genericOpenaiApiKey`: (Legacy, in `.jaider.json`) (for `genericOpenai` provider) The API key, if required. Preferred: `GENERIC_OPENAI_API_KEY` env var.
*   `openaiApiKey`: (Legacy, in `.jaider.json`) API key for OpenAI. Preferred: `OPENAI_API_KEY` env var.
*   `openaiModelName`: (for `openai` provider) The OpenAI model name to use (e.g., `"gpt-4o-mini"`, `"gpt-4-turbo"`, `"gpt-3.5-turbo"`). Default: `"gpt-4o-mini"`
*   `geminiModelName`: (for `gemini` provider) The Gemini chat model name (e.g., `"gemini-1.5-flash-latest"`).
*   `geminiEmbeddingModelName`: (for `gemini` provider) The specific Vertex AI Gemini embedding model name to use (e.g., `"textembedding-gecko"`, `"textembedding-gecko-multilingual"`). Default: `"textembedding-gecko"`
*   `geminiApiKey`: (Legacy, in `.jaider.json`) (for `gemini` provider, if using direct Gemini API - Vertex AI typically uses ADC). Preferred: `GEMINI_API_KEY` env var.
*   `tavilyApiKey`: (Legacy, in `.jaider.json`) API key for Tavily web search. Preferred: `TAVILY_API_KEY` env var.
*   `runCommand`: The command to execute for validation (e.g., tests, linter, build). Example: `"mvn test"`, `"npm run lint"`.
*   `apiKeys`: A JSON object to store API keys for specific services. This is a fallback if specific keys or environment variables are not set.
    *   `"openai": "YOUR_OPENAI_API_KEY"`
    *   `"anthropic": "YOUR_ANTHROPIC_API_KEY"`
    *   `"google": "YOUR_GOOGLE_API_KEY"` (Note: This is a generic Google key; Gemini via Vertex AI uses ADC or its specific key).
    *   `"tavily": "YOUR_TAVILY_KEY_VIA_MAP"`
    *   `"genericOpenai": "YOUR_GENERIC_OPENAI_KEY_VIA_MAP"`

**Example `.jaider.json`:**

```json
{
  "llmProvider": "ollama",
  "ollamaBaseUrl": "http://localhost:11434",
  "ollamaModelName": "llama3",
  "genericOpenaiBaseUrl": "http://localhost:8080/v1",
  "genericOpenaiModelName": "local-gpt",
  "genericOpenaiEmbeddingModelName": "text-embedding-ada-002", // Specific embedding model for Generic OpenAI
  "genericOpenaiApiKey": "", // Can be set here, but GENERIC_OPENAI_API_KEY env var is preferred
  "openaiApiKey": "", // Can be set here, but OPENAI_API_KEY env var is preferred
  "openaiModelName": "gpt-4o-mini", // Specific model for OpenAI
  "geminiApiKey": "", // Can be set here, but GEMINI_API_KEY env var is preferred
  "geminiModelName": "gemini-1.5-flash-latest",
  "geminiEmbeddingModelName": "textembedding-gecko", // Specific embedding model for Gemini
  "tavilyApiKey": "", // Can be set here, but TAVILY_API_KEY env var is preferred
  "runCommand": "mvn clean test",
  "apiKeys": { // Fallback map
    "openai": "YOUR_OPENAI_API_KEY_VIA_MAP",
    "anthropic": "YOUR_ANTHROPIC_API_KEY_VIA_MAP",
    "google": "YOUR_GOOGLE_API_KEY_FOR_GEMINI_VIA_MAP",
    "tavily": "YOUR_TAVILY_KEY_VIA_MAP",
    "genericOpenai": "YOUR_GENERIC_OPENAI_KEY_VIA_MAP"
  }
}
```

## LLM Setup Details

*   **Ollama:**
    *   Set `llmProvider: "ollama"`.
    *   Configure `ollamaBaseUrl` (default: `"http://localhost:11434"`) and `ollamaModelName`.
*   **Generic OpenAI-compatible API:**
    *   Set `llmProvider: "genericOpenai"`.
    *   Configure `genericOpenaiBaseUrl` for the API endpoint.
    *   Set `genericOpenaiModelName` for the chat model.
    *   Optionally, set `genericOpenaiEmbeddingModelName` if your endpoint supports a specific embedding model (defaults to `"text-embedding-ada-002"`).
    *   If your endpoint requires an API key (Bearer token), set it via `genericOpenaiApiKey` in the JSON or preferably the `GENERIC_OPENAI_API_KEY` environment variable. Jaider uses Langchain4j's `OpenAiChatModel` and `OpenAiEmbeddingModel` clients, which are designed to handle standard OpenAI API authentication.
*   **OpenAI:**
    *   Set `llmProvider: "openai"`.
    *   Configure `openaiModelName` (e.g., `"gpt-4o-mini"`, `"gpt-4-turbo"`).
    *   Ensure your OpenAI API key is configured via one of the methods described in "API Key Configuration Precedence" (typically `OPENAI_API_KEY` environment variable).
*   **Google Gemini (via Vertex AI):**
    *   Set `llmProvider: "gemini"`.
    *   Configure `geminiModelName` for chat capabilities (e.g., `"gemini-pro"`, `"gemini-1.5-flash-latest"`).
    *   For embedding capabilities, Jaider now also supports `VertexAiEmbeddingModel`. You can specify `geminiEmbeddingModelName` in your `.jaider.json` (e.g., `"textembedding-gecko"`). If not specified, it defaults to `"textembedding-gecko"`.
    *   **Authentication:** The primary method for Vertex AI (both chat and embedding) is Application Default Credentials (ADC). Ensure your environment is set up (e.g., by running `gcloud auth application-default login`).
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
    *   Java 21 (or the version specified in `pom.xml`).
    *   Apache Maven.
    *   Git (Jaider works best within a Git repository).
    *   (Optional) An Ollama instance running if you intend to use local models.
    *   (Optional) Google Cloud SDK configured for Application Default Credentials if using Gemini via Vertex AI.
2.  **Build:**
    ```bash
    mvn clean package
    ```
3.  **Run:**
    After packaging, the application can be run using the executable JAR created in the `target` directory:
    ```bash
    java -jar target/jaider-1.0-SNAPSHOT.jar
    ```
    (If you change the project version, update `1.0-SNAPSHOT` accordingly).

    Alternatively, for development purposes, you can run the application directly using Maven:
    ```bash
    mvn exec:java -Dexec.mainClass="dumb.jaider.Jaider"
    ```
4.  **First Run:** Jaider will create a default `.jaider.json` if one isn't found. You should edit this file to configure your preferred LLM provider and API keys. Use `/edit-config` or edit it manually.

---
Inspired by [Aider](https://github.com/Aider-AI/aider).
