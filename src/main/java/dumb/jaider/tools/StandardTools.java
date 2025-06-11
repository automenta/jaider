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
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        String tavilyApiKey = config.getTavilyApiKey();
        if (tavilyApiKey == null || tavilyApiKey.isBlank() || tavilyApiKey.contains("YOUR_")) {
            return "Error: Tavily API key not configured. Please set TAVILY_API_KEY environment variable or tavilyApiKey in .jaider.json.";
        }
        try {
            WebSearchEngine tavilySearchEngine = TavilyWebSearchEngine.builder()
                    .apiKey(tavilyApiKey)
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
            String outputString = output.toString().trim();
            resultJson.put("output", outputString);

            // --- Jaider AI Agent: Added test report generation ---
            if (config.runCommand != null && config.runCommand.contains("mvn test")) {
                List<Map<String, String>> testReportList = new ArrayList<>();
                String[] lines = outputString.split("\n");
                final String[] currentTestClassHolder = new String[1]; // Holder for effectively final variable
                final String[] currentTestMethodHolder = new String[1]; // Holder for effectively final variable

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();

                    // Try to capture class and method from Surefire/Failsafe error lines
                    if (line.startsWith("[ERROR]") && line.contains("<<< FAILURE!") && line.contains(".")) {
                        // Example: [ERROR]   TestClassName.testMethodName  Time elapsed: 0.001 s  <<< FAILURE!
                        String testIdPart = line.substring("[ERROR]".length()).trim();
                        testIdPart = testIdPart.substring(0, testIdPart.indexOf("Time elapsed:")).trim(); // Remove time part
                        if (testIdPart.contains(".")) {
                            int lastDot = testIdPart.lastIndexOf('.');
                            currentTestClassHolder[0] = testIdPart.substring(0, lastDot);
                            currentTestMethodHolder[0] = testIdPart.substring(lastDot + 1);
                        } else {
                            currentTestClassHolder[0] = testIdPart; // Unlikely, but handle if no method name
                            currentTestMethodHolder[0] = "unknownMethod";
                        }
                    } else if (line.startsWith("Running ") && line.contains(".")) {
                         // Example: Running some.package.ClassName
                        String runningClass = line.substring("Running ".length()).trim();
                        // If we don't have a specific failing method yet, use this as the class
                        if (currentTestClassHolder[0] == null || !currentTestClassHolder[0].equals(runningClass)) {
                           // currentTestClassHolder[0] = runningClass; // Prefer the one from FAILURE line if available
                           // currentTestMethodHolder[0] = "unknownMethod"; // Reset method if class changes
                        }
                    }


                    // Look for error messages, typically following a <<< FAILURE! or indication of error
                    // This is a simplified approach: takes the next line that looks like an error message.
                    if ((line.startsWith("java.") || line.startsWith("org.junit.") || line.startsWith("org.opentest4j.")) && currentTestClassHolder[0] != null) {
                        // Heuristic: if the previous line indicated a failure, or we are in a stack trace context
                        boolean isFailureContext = false;
                        if (i > 0) {
                            String prevLine = lines[i-1].trim();
                            if (prevLine.contains("<<< FAILURE!") || prevLine.contains("<<< ERROR!")) {
                                isFailureContext = true;
                            }
                        }
                        if (isFailureContext || (line.matches("^\\s+at .+$") && i > 0 && lines[i-1].matches("^.+Exception: .+$"))) { // If it's part of a stack trace for an already identified failure
                            // This is a potential error message line
                            Map<String, String> failureDetails = new HashMap<>();
                            failureDetails.put("testClass", currentTestClassHolder[0]);
                            failureDetails.put("testMethod", currentTestMethodHolder[0] != null ? currentTestMethodHolder[0] : "unknownMethod");
                            failureDetails.put("errorMessage", line);

                            // Avoid adding duplicate error messages for the same test method if error spans multiple lines
                            boolean alreadyExists = testReportList.stream().anyMatch(entry ->
                                entry.get("testClass").equals(currentTestClassHolder[0]) &&
                                entry.get("testMethod").equals(currentTestMethodHolder[0]) &&
                                entry.get("errorMessage").startsWith(line.substring(0, Math.min(line.length(), 50))) // check start of message
                            );
                            if(!alreadyExists) {
                                testReportList.add(failureDetails);
                            }
                            // Reset after capturing one error message for this failure,
                            // to avoid associating subsequent unrelated errors/logs.
                            // currentTestClass = null;
                            // currentTestMethod = null;
                        }
                    } else if (line.startsWith("[ERROR] Failed tests:") || line.startsWith("[ERROR] Errors:")) {
                        // Example: [ERROR] Failed tests:   testSomething(com.example.MyTest): expected:<true> but was:<false>
                        // Example: [ERROR] Errors:   initializationError(com.example.MyTest): java.lang.RuntimeException
                        String failedTestInfo = line.substring(line.indexOf(":") + 1).trim();
                        String errorMessage = failedTestInfo; // Default error message to the whole info

                        if (failedTestInfo.contains("(")) { // Format: methodName(className)
                            String methodName = failedTestInfo.substring(0, failedTestInfo.indexOf("("));
                            String className = failedTestInfo.substring(failedTestInfo.indexOf("(") + 1, failedTestInfo.indexOf(")"));
                             if (failedTestInfo.contains(":")) { // If there's a message after class/method
                                errorMessage = failedTestInfo.substring(failedTestInfo.indexOf("):") + 2).trim();
                            }
                            Map<String, String> failureDetails = new HashMap<>();
                            failureDetails.put("testClass", className);
                            failureDetails.put("testMethod", methodName);
                            failureDetails.put("errorMessage", errorMessage);
                            testReportList.add(failureDetails);
                        }
                    }
                }
                resultJson.put("testReport", new JSONArray(testReportList));
            } else {
                // If not "mvn test", or if config.runCommand is null, put an empty array or null.
                // For consistency, let's use an empty array.
                resultJson.put("testReport", new JSONArray(new ArrayList<>()));
            }
            // --- End Jaider AI Agent change ---

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            // Ensure testReport is added even in case of exceptions before it's populated
            if (!resultJson.has("testReport")) {
                 resultJson.put("testReport", new JSONArray(new ArrayList<>()));
            }
            resultJson.put("error", "Failed to run command '" + commandToExecute + "': " + e.getClass().getSimpleName() + " - " + e.getMessage());
            resultJson.put("success", false);
            resultJson.put("exitCode", -1);
        } catch (Exception e) { // Catch all other exceptions
             resultJson.put("error", "An unexpected error occurred while running command '" + commandToExecute + "': " + e.getClass().getSimpleName() + " - " + e.getMessage());
             resultJson.put("success", false);
             resultJson.put("exitCode", -1);
             // Ensure testReport is added even in case of general exceptions
            if (!resultJson.has("testReport")) {
                 resultJson.put("testReport", new JSONArray(new ArrayList<>()));
            }
        }
        return resultJson.toString();
    }

    @Tool("Provides an overview of the project: type (e.g., Maven), key dependencies from pom.xml (if applicable), and main source directories.")
    public String getProjectOverview() {
        StringBuilder report = new StringBuilder();

        // Project Type and Dependencies (Maven)
        Path pomPath = model.dir.resolve("pom.xml");
        if (Files.exists(pomPath)) {
            report.append("Project Type: Maven\n");
            report.append("Main Dependencies (from pom.xml):\n");
            try {
                String pomContent = Files.readString(pomPath);
                // Basic regex to find <dependency> blocks
                Pattern dependencyPattern = Pattern.compile("<dependency>(.*?)</dependency>", Pattern.DOTALL);
                Matcher dependencyMatcher = dependencyPattern.matcher(pomContent);
                int count = 0;
                while (dependencyMatcher.find() && count < 10) { // Limit to first 10 dependencies
                    String depBlock = dependencyMatcher.group(1);
                    String groupId = extractTagValue(depBlock, "groupId");
                    String artifactId = extractTagValue(depBlock, "artifactId");
                    String version = extractTagValue(depBlock, "version");

                    if (groupId != null && artifactId != null) {
                        report.append("  - ").append(groupId).append(":").append(artifactId);
                        if (version != null) {
                            report.append(":").append(version);
                        }
                        report.append("\n");
                        count++;
                    } else if (!depBlock.trim().isEmpty()){ // Fallback for complex/malformed blocks
                        String simplifiedBlock = depBlock.replaceAll("\\s*\\n\\s*", " ").trim();
                        if (simplifiedBlock.length() > 100) simplifiedBlock = simplifiedBlock.substring(0, 97) + "...";
                        report.append("  - (Partial/Raw) ").append(simplifiedBlock).append("\n");
                        count++;
                    }
                }
                if (count == 0) {
                    report.append("  - No dependencies found or parsed from <dependencies> section.\n");
                }
            } catch (IOException e) {
                report.append("  Error reading or parsing pom.xml: ").append(e.getMessage()).append("\n");
            }
        } else {
            report.append("Project Type: Unknown (pom.xml not found)\n");
        }

        // Source Directories
        report.append("Common Source Directories Found:\n");
        String[] commonDirs = {
            "src/main/java", "src/test/java",
            "src/main/kotlin", "src/test/kotlin",
            "src/main/scala", "src/test/scala",
            "src/main/resources", "src/test/resources"
        };
        boolean foundSrcDir = false;
        for (String dirPath : commonDirs) {
            if (Files.exists(model.dir.resolve(dirPath))) {
                report.append("  - ").append(dirPath).append("\n");
                foundSrcDir = true;
            }
        }
        if (!foundSrcDir) {
            report.append("  - (No common source directories detected)\n");
        }

        return report.toString();
    }

    // Helper method to extract tag value, can be placed within the class or as a static utility
    private String extractTagValue(String xmlBlock, String tagName) {
        Pattern pattern = Pattern.compile("<" + tagName + ">(.*?)</" + tagName + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xmlBlock);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
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

    @Tool("Lists files and directories in a given path, respecting .gitignore. Path is relative to project root. If no path is given, lists project root.")
    public String listFiles(String directoryPath) {
        try {
            GitService gitService = new GitService(this.model.dir);
            String pathToScan = (directoryPath == null || directoryPath.isBlank()) ? "" : directoryPath;
            List<String> files = gitService.listFiles(pathToScan); // Assuming GitService has such a method

            if (files.isEmpty()) {
                return "No files found in " + (pathToScan.isEmpty() ? "project root" : pathToScan);
            }

            StringBuilder result = new StringBuilder();
            for (String filePath : files) {
                java.io.File file = this.model.dir.resolve(filePath).toFile();
                if (file.isDirectory()) {
                    result.append("[DIR] ").append(filePath).append("\n");
                } else {
                    result.append("[FILE] ").append(filePath).append("\n");
                }
            }
            return result.toString();
        } catch (Exception e) {
            return "Error listing files: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    @Tool("Writes content to a file, creating parent directories if necessary. Path is relative to project root.")
    public String writeFile(String filePath, String content) {
        if (filePath == null || filePath.isBlank()) {
            return "Error: File path cannot be null or empty.";
        }
        if (content == null) {
            // Or decide if writing an empty string is permissible. For now, let's assume null is an error.
            return "Error: Content cannot be null.";
        }

        try {
            Path targetPath = this.model.dir.resolve(filePath);

            // Ensure parent directories exist
            Path parentDir = targetPath.getParent();
            if (parentDir != null) {
                if (!Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                } else if (!Files.isDirectory(parentDir)) {
                    return "Error: Cannot create parent directory. A file with the same name as the parent directory already exists: " + parentDir;
                }
            }

            boolean existed = Files.exists(targetPath);
            Files.writeString(targetPath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            if (existed) {
                return "File overwritten successfully: " + filePath;
            } else {
                return "File created successfully: " + filePath;
            }

        } catch (IOException e) {
            return "Error writing file '" + filePath + "': " + e.getClass().getSimpleName() + " - " + e.getMessage();
        } catch (Exception e) {
            return "An unexpected error occurred while writing file '" + filePath + "': " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }
}
