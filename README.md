# Jaider - The AI Programming Assistant

## Overview

Jaider is an interactive, command-line AI programming assistant designed to help developers with coding tasks by leveraging Large Language Models (LLMs). It operates within your project directory, allowing AI agents to read your code, propose changes via diffs, run validation commands, and even search the web for information. Jaider is inspired by tools like Aider and aims to provide a flexible and configurable environment for AI-assisted development.

For those interested in the internal design, a detailed overview of Jaider's components and their interactions can be found in [ARCHITECTURE.md](ARCHITECTURE.md).

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
*   **Self-Development Capability:** Jaider can modify its own codebase using the /self-develop command, allowing for AI-driven improvements and feature additions to Jaider itself.

## Configuration (`.jaider.json`)

Jaider uses a `.jaider.json` file in the root of your project directory for configuration. If this file doesn't exist, Jaider will create one with default values upon first run.

**API Key Configuration Precedence:** For services like Tavily, Gemini, and OpenAI (including Generic OpenAI), API keys are resolved in the following order:
1. Environment Variable (e.g., `TAVILY_API_KEY`, `GEMINI_API_KEY`, `GENERIC_OPENAI_API_KEY`, `OPENAI_API_KEY`)
2. Key within the `apiKeys` map in `.jaider.json` (e.g., `"tavily": "your_key"`, `"google": "your_key"` for Gemini, `"genericOpenai": "your_key"`, `"openai": "your_key"`)

Key configurable fields include:

*   `llmProvider`: Specifies the LLM provider to use.
    *   Values: `"ollama"`, `"genericOpenai"`, `"openai"`, `"gemini"`.
    *   Default: `"ollama"`
*   `ollamaBaseUrl`: (for `ollama` provider) The base URL for your Ollama instance (e.g., `"http://localhost:11434"`).
*   `ollamaModelName`: (for `ollama` provider) The name of the Ollama model to use (e.g., `"llamablit"`).
*   `genericOpenaiBaseUrl`: (for `genericOpenai` provider) The base URL of the OpenAI-compatible API (e.g., `"http://localhost:8080/v1"`).
*   `genericOpenaiModelName`: (for `genericOpenai` provider) The chat model name for the generic API.
*   `genericOpenaiEmbeddingModelName`: (for `genericOpenai` provider) The embedding model name to use with your generic OpenAI-compatible endpoint (e.g., `"text-embedding-ada-002"`). Default: `"text-embedding-ada-002"`
*   `openaiModelName`: (for `openai` provider) The OpenAI model name to use (e.g., `"gpt-4o-mini"`, `"gpt-4-turbo"`, `"gpt-3.5-turbo"`). Default: `"gpt-4o-mini"`
*   `geminiModelName`: (for `gemini` provider) The Gemini chat model name (e.g., `"gemini-1.5-flash-latest"`).
*   `geminiEmbeddingModelName`: (for `gemini` provider) The specific Vertex AI Gemini embedding model name to use (e.g., `"textembedding-gecko"`, `"textembedding-gecko-multilingual"`). Default: `"textembedding-gecko"`
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
  "genericOpenaiEmbeddingModelName": "text-embedding-ada-002",
  "openaiModelName": "gpt-4o-mini",
  "geminiModelName": "gemini-1.5-flash-latest",
  "geminiEmbeddingModelName": "textembedding-gecko",
  "runCommand": "mvn clean test",
  "apiKeys": {
    "openai": "YOUR_OPENAI_API_KEY",
    "anthropic": "YOUR_ANTHROPIC_API_KEY",
    "google": "YOUR_GEMINI_API_KEY", // For Gemini
    "tavily": "YOUR_TAVILY_API_KEY",
    "genericOpenai": "YOUR_GENERIC_OPENAI_API_KEY"
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
    *   If your endpoint requires an API key (Bearer token), set it via the `apiKeys` map (e.g., `"genericOpenai": "YOUR_KEY"`) in the JSON or preferably the `GENERIC_OPENAI_API_KEY` environment variable.
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
    *   API keys for Gemini (if not using ADC) should be configured via the `apiKeys` map (e.g. `"google": "YOUR_KEY"`) or the `GEMINI_API_KEY` environment variable.

## Key Commands

*   `/mode <Coder|Architect|Ask>`: Switch between agent modes.
*   `/add <file(s)...>`: Add one or more files (space-separated) to the agent's context.
*   `/run [args...]`: Executes the command specified in `runCommand` from `.jaider.json`. (Note: argument passing to the command is a planned enhancement).
*   `/self-develop <task_description>`: Instructs Jaider's CoderAgent to attempt to modify Jaider's own source code to achieve the given task. The agent will propose changes via a diff, which requires user approval before being applied, built, tested, and committed. This triggers a full restart of Jaider upon success. Example: `/self-develop Add a new configuration option to .jaider.json.`
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
*   `proposeSelfUpdate(filePath, diffContent, commitMessage)`: (Used by CoderAgent during self-development) Proposes an update to Jaider's own codebase. This stages the update for user review and triggers the self-update orchestration process (apply, build, test, commit, restart).

## Getting Started

1.  **Prerequisites:**
    *   Java 21 (or the version specified in `pom.xml`).
    *   Java 21 (or the version specified in `pom.xml`).
    *   Apache Maven (though using the included Maven Wrapper `mvnw` is recommended).
    *   Git (Jaider works best within a Git repository).
    *   (Optional) An Ollama instance running if you intend to use local models.
    *   (Optional) Google Cloud SDK configured for Application Default Credentials if using Gemini via Vertex AI.
2.  **Build:**
    It is recommended to use the Maven Wrapper (`mvnw`) included with the project. This ensures you use the project's specified Maven version without needing a separate Maven installation.
    ```bash
    # For Linux/macOS
    ./mvnw clean package

    # For Windows
    mvnw.cmd clean package
    ```
3.  **Run:**
    After packaging, the application can be run using the executable JAR created in the `target` directory:
    ```bash
    java -jar target/jaider-*.jar
    ```
    (Replace `*` with the correct version from the `pom.xml`, e.g., `1.0-SNAPSHOT`).

    Alternatively, for development purposes, you can run the application directly using the Maven Wrapper from the project's root directory:
    ```bash
    # For Linux/macOS
    ./mvnw exec:java -Dexec.mainClass="dumb.jaider.app.Jaider"

    # For Windows
    mvnw.cmd exec:java -Dexec.mainClass="dumb.jaider.app.Jaider"
    ```
    The main class for running the Jaider application is `dumb.jaider.app.Jaider`. Specific main classes for available demos are listed in the "Demonstrations and Examples" section.

4.  **First Run:** When you first run Jaider in a project directory, it will create a default `.jaider.json` file if one isn't already present. You should edit this file to configure your preferred LLM provider, model names, and any necessary API keys. You can use the `/edit-config` command within Jaider or edit the file manually with a text editor.

## Architecture

For a detailed understanding of Jaider's internal structure, components, and high-level interaction flows, please see the dedicated [ARCHITECTURE.md](ARCHITECTURE.md) document.

## Demonstrations and Examples

This project includes ways to see Jaider in action and understand its capabilities.

### 1. Comprehensive Interactive Demo

This is the **primary way to experience Jaider's full range of features**. It runs Jaider with a special UI that executes a predefined script of commands, while still allowing you (the operator) to interact with key decision points like applying diffs, confirming plans, or editing configurations.

**Features Showcased:**
*   Setting up Jaider and adding files to context.
*   Using different agent modes (Ask, Coder, Architect).
*   The core Coder workflow: generating code changes, reviewing diffs, running validation commands.
*   Advanced tools: Web search (requires Tavily API key), project indexing, and code search.
*   Configuration editing via `/edit-config` (allowing on-the-fly LLM provider changes if desired).
*   Undo functionality.
*   And more, covering most of Jaider's commands and agent capabilities.

**Running the Comprehensive Demo:**
1.  **Build the Project:** Ensure the entire project is compiled:
    ```bash
    # For Linux/macOS
    ./mvnw clean package

    # For Windows
    mvnw.cmd clean package
    ```
2.  **Run the Demo:**
    ```bash
    # For Linux/macOS
    ./mvnw exec:java -Dexec.mainClass="dumb.integrationhandler.demo.ComprehensiveInteractiveDemo"

    # For Windows
    mvnw.cmd exec:java -Dexec.mainClass="dumb.integrationhandler.demo.ComprehensiveInteractiveDemo"
    ```
3.  **Follow On-Screen Prompts:** The demo will guide you through various phases. You will be prompted to provide input (e.g., for AI prompts, or decisions like accepting a diff) or press Enter to continue.
    *   **LLM Configuration:** The demo starts with a default `.jaider.json` (using Ollama) created in a temporary directory. The script includes steps where you can use `/edit-config` to change LLM providers (e.g., to Gemini or OpenAI).
    *   **API Keys:** For features like web search (Tavily) or using cloud-based LLMs (Gemini, OpenAI), ensure you have the respective API keys set as environment variables (e.g., `TAVILY_API_KEY`, `GEMINI_API_KEY`, `OPENAI_API_KEY`) or be prepared to add them to the `.jaider.json` configuration when prompted during the `/edit-config` step of the demo.

This demo provides a thorough walkthrough and helps verify that Jaider's components are integrating and functioning correctly.

### 2. Simplified Integration Tutorial

Class: `dumb.integrationhandler.demo.JaiderIntegrationTutorialDemo`

This is a very basic, stripped-down tutorial primarily intended for developers looking to understand the most fundamental LLM interaction for single file generation within Jaider's ecosystem. It performs a single code generation task using a configured LLM (e.g., Gemini, requiring an API key).

**Key Characteristics:**
*   Focuses on programmatic LLM interaction, not the full Jaider application loop.
*   Does **not** showcase most of Jaider's interactive commands or advanced features.
*   It now primarily serves as a minimal example and explicitly directs users to the `ComprehensiveInteractiveDemo` for a complete Jaider experience.

**Running the Simplified Tutorial:**
1.  **Build the Project:**
    ```bash
    # For Linux/macOS
    ./mvnw clean package

    # For Windows
    mvnw.cmd clean package
    ```
2.  **Run the Tutorial:**
    ```bash
    # For Linux/macOS
    ./mvnw exec:java -Dexec.mainClass="dumb.integrationhandler.demo.JaiderIntegrationTutorialDemo"

    # For Windows
    mvnw.cmd exec:java -Dexec.mainClass="dumb.integrationhandler.demo.JaiderIntegrationTutorialDemo"
    ```
    You will be prompted for a Gemini API key if you choose to use Gemini.

### 3. Original Basic Interactive Demo (Legacy)

Class: `dumb.integrationhandler.demo.InteractiveDemo`

This was the original simple demo. It showcases a very simplified workflow for generating a single file using a selected Language Model (Gemini or a placeholder for Ollama) and saves it to a temporary directory. It has been largely superseded by the `ComprehensiveInteractiveDemo` for feature exploration and the `JaiderIntegrationTutorialDemo` for a focused look at basic generation.

**Running the Original Basic Demo:**
```bash
# For Linux/macOS
./mvnw exec:java -Dexec.mainClass="dumb.integrationhandler.demo.InteractiveDemo"

# For Windows
mvnw.cmd exec:java -Dexec.mainClass="dumb.integrationhandler.demo.InteractiveDemo"
```

It is recommended to use the **Comprehensive Interactive Demo** for the best understanding of Jaider's capabilities.

---
Inspired by [Aider](https://github.com/Aider-AI/aider).
