package dumb.jaider.tools;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.PatchFailedException;
import com.github.difflib.unifieddiff.UnifiedDiff;
import com.github.difflib.unifieddiff.UnifiedDiffReader;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.tools.web.search.WebSearchEngine;
import dev.langchain4j.tools.web.search.WebSearchResults;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.utils.Util;
import dumb.jaider.vcs.GitService; // Added import
// org.eclipse.jgit.api.Git is no longer directly used here
import org.json.JSONObject;
import dumb.jaider.tools.DiffApplier; // Added import

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class StandardTools {
    private final JaiderModel model;
    private final Config config;
    private final EmbeddingModel embeddingModel;

    public StandardTools(JaiderModel model, Config config, EmbeddingModel embeddingModel) {
        this.model = model;
        this.config = config;
        this.embeddingModel = embeddingModel;
    }

    // Removed diffReader method

    public Set<Object> getReadOnlyTools() {
        // findRelevantCode and searchWeb are considered read-only.
        // Other tools like readFile might also be, but this is a specific list for ArchitectAgent.
        // For now, returning 'this' means all @Tool methods in this class are available.
        // If more fine-grained control is needed, specific tool instances can be returned.
        // TODO: This might need to be more granular in the future.
        return Set.of(this);
    }

    @Tool("Searches the web for the given query using Tavily.")
    public String searchWeb(String query) {
        if (config.tavilyApiKey == null || config.tavilyApiKey.isBlank() || config.tavilyApiKey.contains("YOUR_")) {
            return "Error: Tavily API key not configured. Please set tavilyApiKey in .jaider.json.";
        }
        try {
            WebSearchEngine tavilySearchEngine = TavilyWebSearchEngine.builder()
                    .apiKey(config.tavilyApiKey)
                    .build();
            WebSearchResults results = tavilySearchEngine.search(query);
            if (results == null || results.answers() == null || results.answers().isEmpty()) {
                return "No results found for: " + query;
            }
            return results.answers().stream()
                    .map(answer -> "Source: " + answer.url() + "\nTitle: " + answer.title() + "\nSnippet: " + answer.snippet())
                    .collect(Collectors.joining("\n\n---\n\n"));
        } catch (Exception e) {
            return "Error performing web search for '" + query + "': " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    @Tool("Applies a code change using the unified diff format.")
    public String applyDiff(String diff) {
        try {
            UnifiedDiff unifiedDiff = Util.diffReader(diff); // Step 1: Read the diff

            DiffApplier diffApplier = new DiffApplier(); // Step 2: Instantiate DiffApplier
            String applyResult = diffApplier.apply(this.model, unifiedDiff); // Step 3: Apply diff

            // Step 4: Handle result and set lastAppliedDiff
            if (applyResult.startsWith("Diff applied successfully")) {
                this.model.lastAppliedDiff = diff;
            } else {
                this.model.lastAppliedDiff = null;
            }
            return applyResult;

        } catch (IOException e) { // Catch errors from Util.diffReader
            this.model.lastAppliedDiff = null;
            return "Error processing diff input: " + e.getMessage();
        } catch (Exception e) { // Catch any other unexpected errors (e.g., from DiffApplier instantiation)
            this.model.lastAppliedDiff = null;
            return "An unexpected error occurred while applying diff: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    @Tool("Reads the complete content of a file.")
    public String readFile(String fileName) {
        return model.readFileContent(model.projectDir.resolve(fileName));
    }

    @Tool("Runs the project's configured validation command (e.g., tests, linter, build). Usage: runValidationCommand <optional_arguments_for_command>")
    public String runValidationCommand(String commandArgs) {
        JSONObject resultJson = new JSONObject();
        if (config.runCommand == null || config.runCommand.isBlank()) {
            resultJson.put("error", "No validation command configured in .jaider.json (key: runCommand).");
            resultJson.put("success", false);
            resultJson.put("exitCode", -1);
            return resultJson.toString();
        }

        String commandToExecute = config.runCommand;

        try {
            ProcessBuilder pb = new ProcessBuilder(commandToExecute.split("\\s+"))
                    .directory(model.projectDir.toFile())
                    .redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            resultJson.put("exitCode", exitCode);
            resultJson.put("success", exitCode == 0);
            resultJson.put("output", output.toString().trim());

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            resultJson.put("error", "Failed to run command '" + commandToExecute + "': " + e.getClass().getSimpleName() + " - " + e.getMessage());
            resultJson.put("success", false);
            resultJson.put("exitCode", -1);
        } catch (Exception e) {
             resultJson.put("error", "An unexpected error occurred while running command '" + commandToExecute + "': " + e.getClass().getSimpleName() + " - " + e.getMessage());
             resultJson.put("success", false);
             resultJson.put("exitCode", -1);
        }
        return resultJson.toString();
    }

    @Tool("Commits all staged changes with a given message.")
    public String commitChanges(String message) {
        GitService gitService = new GitService(this.model.projectDir);
        return gitService.commitChanges(message);
    }

    @Tool("Finds relevant code snippets from the entire indexed codebase.")
    public String findRelevantCode(String query) {
        if (embeddingModel == null) {
            return "Error: Embedding model is not available. Cannot search code. Ensure LLM provider that supports embeddings is configured (e.g. OpenAI).";
        }
        if (model.embeddingStore == null) {
            return "Project not indexed. Run /index first.";
        }
        try {
            var queryEmbedding = embeddingModel.embed(query).content();
            var r = EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding).build();
            var relevant = model.embeddingStore.search(r);
            if (relevant == null || relevant.matches().isEmpty()) {
                return "No relevant code found in the index for: " + query;
            }
            return relevant.matches().stream()
                    .map(match -> String.format("--- From %s (Score: %.4f) ---\n%s",
                            match.embedded().metadata().getString("file_path"),
                            match.score(),
                            match.embedded().text()))
                    .collect(Collectors.joining("\n\n---\n\n"));
        } catch (Exception e) {
            return "Error searching for relevant code: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }
}
