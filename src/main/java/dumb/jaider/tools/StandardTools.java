package dumb.jaider.tools;

import com.github.difflib.patch.Patch;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.utils.Util;
import dumb.jaider.vcs.GitService;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.stream.Collectors;

public class StandardTools {
    private final JaiderModel model;
    private final Config config;
    private final EmbeddingModel embedding;

    public StandardTools(JaiderModel model, Config config, EmbeddingModel embedding) {
        this.model = model;
        this.config = config;
        this.embedding = embedding;
    }

    // Removed diffReader method

    public Set<Object> getReadOnlyTools() {
        // findRelevantCode and searchWeb are considered read-only.
        // Other tools like readFile might also be, but this is a specific list for ArchitectAgent.
        // For now, returning 'this' means all @Tool methods in this class are available.
        // If more fine-grained control is needed, specific tool instances can be returned.
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
            if (results == null || results.results() == null || results.results().isEmpty()) {
                return "No results found for: " + query;
            }
            return results.results().stream()
                    .map(answer -> "Source: " + answer.url() + "\nTitle: " + answer.title() + "\nSnippet: " + answer.snippet())
                    .collect(Collectors.joining("\n\n---\n\n"));
        } catch (Exception e) {
            return "Error performing web search for '" + query + "': " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    @Tool("Applies a code change using the unified diff format.")
    public String applyDiff(String diff) {
        // return "Error: Diff functionality is temporarily disabled due to library issues.";

        try {
            Patch<String> patch = Util.diffReader(diff); // Step 1: Read the diff, now returns Patch<String>

            // Step 2: Parse original and revised filenames from the diff string
            String originalFileName = null;
            String revisedFileName = null;
            for (String line : diff.split("\n")) {
                if (line.startsWith("--- a/")) {
                    originalFileName = line.substring("--- a/".length());
                } else if (line.startsWith("+++ b/")) {
                    revisedFileName = line.substring("+++ b/".length());
                }
                if (originalFileName != null && revisedFileName != null) {
                    break;
                }
            }

            if (originalFileName == null && revisedFileName == null && patch != null && !patch.getDeltas().isEmpty()) {
                // If there are deltas, we expect filenames.
                // However, an empty diff (no deltas) might not have filenames, which is fine.
                // Check if patch is empty. If not, then it's an error.
                boolean isEmptyPatch = patch.getDeltas().stream().allMatch(delta -> delta.getSource().getLines().isEmpty() && delta.getTarget().getLines().isEmpty());
                if (!isEmptyPatch) {
                   return "Error: Could not parse filenames from diff header.";
                }
                // If it's an empty patch with no filenames, treat as no-op / successful no-op.
                // Or let DiffApplier decide based on null filenames if patch is also empty.
            }


            DiffApplier diffApplier = new DiffApplier(); // Step 3: Instantiate DiffApplier
            // Step 4: Apply diff, passing parsed filenames
            String applyResult = diffApplier.apply(this.model, patch, originalFileName, revisedFileName);

            // Step 5: Handle result and set lastAppliedDiff
            // Adjusted condition to check for specific success message from DiffApplier
            if (applyResult.startsWith("Diff applied successfully to file") ||
                (patch != null && patch.getDeltas().isEmpty() && originalFileName == null && revisedFileName == null) ||
                 applyResult.startsWith("File") && applyResult.endsWith("deleted successfully.")) {
                this.model.lastAppliedDiff = diff;
            } else {
                this.model.lastAppliedDiff = null;
            }
            return applyResult;

        } catch (IOException e) { // Catch errors from Util.diffReader
            this.model.lastAppliedDiff = null;
            return "Error processing diff input: " + e.getMessage();
        // PatchFailedException is handled within DiffApplier.apply and returned as a string.
        // Thus, it's not expected to be thrown here.
        } catch (Exception e) { // Catch any other unexpected errors
            this.model.lastAppliedDiff = null;
            return "An unexpected error occurred while applying diff: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }

    }

    @Tool("Reads the complete content of a file.")
    public String readFile(String fileName) {
        return model.readFileContent(model.dir.resolve(fileName));
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
                    .directory(model.dir.toFile())
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
        GitService gitService = new GitService(this.model.dir);
        return gitService.commitChanges(message);
    }

    @Tool("Finds relevant code snippets from the entire indexed codebase.")
    public String findRelevantCode(String query) {
        if (embedding == null) {
            return "Error: Embedding model is not available. Cannot search code. Ensure LLM provider that supports embeddings is configured (e.g. OpenAI).";
        }
        if (model.embeddings == null) {
            return "Project not indexed. Run /index first.";
        }
        try {
            var queryEmbedding = embedding.embed(query).content();
            var r = EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding).build();
            var relevant = model.embeddings.search(r);
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
