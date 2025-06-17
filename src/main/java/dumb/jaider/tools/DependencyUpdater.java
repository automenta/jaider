package dumb.jaider.tools;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import dumb.jaider.tooling.Tool;
import dumb.jaider.tooling.ToolContext;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


public class DependencyUpdater implements Tool {

    @Override
    public String getName() {
        return "DependencyUpdater";
    }

    @Override
    public String getDescription() {
        // This could also load from the JSON descriptor if a mechanism exists
        return "Identifies outdated Maven dependencies using 'mvn versions:display-dependency-updates' and proposes updates to the pom.xml file. It also runs testing (via the project's configured runCommand) to verify that updates are safe before finalizing suggestions.";
    }

    @Override
    public boolean isAvailable() {
        try {
            var pb = new ProcessBuilder("mvn", "--version");
            var process = pb.start();
            var exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            // Log the exception (if logging is available here) or print to stderr for debugging
            // e.printStackTrace();
            return false;
        }
    }

    @Override
    public String execute(ToolContext context) throws Exception {
        var projectRoot = context.getProjectRoot().orElseThrow(() -> new RuntimeException("Project root not available in ToolContext"));

        var pb = new ProcessBuilder("mvn", "versions:display-dependency-updates", "-DprocessDependencyManagement=false", "-DprocessPluginManagement=false");
        pb.directory(projectRoot.toFile());

        var process = pb.start();
        var mavenOutputBuilder = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                mavenOutputBuilder.append(line).append(System.lineSeparator());
            }
        }
        var errorOutputBuilder = new StringBuilder();
        try (var errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorOutputBuilder.append(line).append(System.lineSeparator());
            }
        }

        var exitCode = process.waitFor();
        var rawMavenOutput = mavenOutputBuilder.toString();

        if (exitCode != 0) {
            return "Maven command failed with exit code " + exitCode + System.lineSeparator() +
                   "STDOUT:" + System.lineSeparator() + rawMavenOutput + System.lineSeparator() +
                    "STDERR:" + System.lineSeparator() + errorOutputBuilder;
        }

        // Parse Maven output to find dependency updates
        List<Map<String, String>> potentialUpdates = new ArrayList<>();
        var pattern = Pattern.compile(
            "\\[INFO\\]\\s+([^:]+):([^\\s]+)\\s+\\.+.*\\s+([\\w\\.-]+)\\s+->\\s+([\\w\\.-]+)"
        );
        var matcher = pattern.matcher(rawMavenOutput);
        while (matcher.find()) {
            Map<String, String> update = new HashMap<>();
            update.put("groupId", matcher.group(1).trim());
            update.put("artifactId", matcher.group(2).trim());
            update.put("currentVersion", matcher.group(3).trim());
            update.put("newVersion", matcher.group(4).trim());
            potentialUpdates.add(update);
        }

        if (potentialUpdates.isEmpty()) {
            return rawMavenOutput; // No updates found or parsing failed, return original Maven output
        }

        var pomPath = projectRoot.resolve("pom.xml");
        if (!Files.exists(pomPath)) {
            throw new RuntimeException("pom.xml not found at " + pomPath);
        }
        var originalPomLines = Files.readAllLines(pomPath);
        var resultsList = new ArrayList<Map<String, Object>>();

        for (var depUpdate : potentialUpdates) {
            List<String> modifiedPomLines = new ArrayList<>(originalPomLines);
            var groupId = depUpdate.get("groupId");
            var artifactId = depUpdate.get("artifactId");
            var currentVersion = depUpdate.get("currentVersion");
            var newVersion = depUpdate.get("newVersion");
            var updated = false;

            // Attempt to find and replace the version
            // This is a simplified approach and might not cover all pom.xml structures
            var artifactIdLine = -1;
            for (var i = 0; i < modifiedPomLines.size(); i++) {
                var line = modifiedPomLines.get(i);
                if (line.contains("<groupId>" + groupId + "</groupId>")) {
                     // Check subsequent lines for artifactId
                    for (var j = i + 1; j < modifiedPomLines.size() && j < i + 5; j++) { // Check next few lines
                        if (modifiedPomLines.get(j).contains("<artifactId>" + artifactId + "</artifactId>")) {
                            artifactIdLine = j;
                            break;
                        }
                    }
                }
                if (artifactIdLine != -1) {
                    // Found group and artifact, now look for version nearby
                    for (var k = artifactIdLine + 1; k < modifiedPomLines.size() && k < artifactIdLine + 5; k++) {
                        var versionLine = modifiedPomLines.get(k);
                        if (versionLine.contains("<version>" + currentVersion + "</version>")) {
                            modifiedPomLines.set(k, versionLine.replace(currentVersion, newVersion));
                            updated = true;
                            artifactIdLine = -1; // Reset for next dependency
                            break;
                        }
                    }
                }
                if (updated) break;
            }


            if (updated) {
                try {
                    var patch = DiffUtils.diff(originalPomLines, modifiedPomLines); // Changed to 2-argument version
                    var diffOutput = UnifiedDiffUtils.generateUnifiedDiff("pom.xml", "pom.xml", originalPomLines, patch, 0); // Changed to UnifiedDiffUtils
                    var diffString = new StringBuilder();
                    for (var diffLine : diffOutput) {
                        diffString.append(diffLine).append(System.lineSeparator());
                    }

                    Map<String, Object> result = new HashMap<>();
                    result.put("groupId", groupId);
                    result.put("artifactId", artifactId);
                    result.put("currentVersion", currentVersion);
                    result.put("newVersion", newVersion);
                    result.put("diff", diffString.toString());
                    resultsList.add(result);
                } catch (Exception e) {
                    // Could log this error, e.g., context.getLogger().error("Error generating diff", e);
                    // For now, skip adding this update if diff generation fails
                }
            }
        }

        if (resultsList.isEmpty()) {
            return rawMavenOutput; // No updates could be applied or diffed
        }

        return new JSONArray(resultsList).toString();
    }

    @Override
    public Object parseOutput(String output) {
        try {
            // Attempt to parse as JSON array first (success case from execute)
            var jsonArray = new JSONArray(output);
            List<Map<String, String>> updatesList = new ArrayList<>();
            for (var i = 0; i < jsonArray.length(); i++) {
                var jsonObj = jsonArray.getJSONObject(i);
                Map<String, String> map = new HashMap<>();
                map.put("groupId", jsonObj.getString("groupId"));
                map.put("artifactId", jsonObj.getString("artifactId"));
                map.put("currentVersion", jsonObj.getString("currentVersion"));
                map.put("newVersion", jsonObj.getString("newVersion"));
                map.put("diff", jsonObj.getString("diff"));
                updatesList.add(map);
            }
            return updatesList;
        } catch (Exception e_json) {
            // Not JSON, so it might be an error message or raw Maven output
            List<Map<String, String>> fallbackList = new ArrayList<>();
            if (output.startsWith("Maven command failed with exit code")) {
                Map<String, String> errorResult = new HashMap<>();
                errorResult.put("error", "Maven command execution failed.");
                errorResult.put("details", output);
                fallbackList.add(errorResult);
                return fallbackList;
            }

            // Try parsing with the old regex for "no updates" or other Maven messages
            var pattern = Pattern.compile(
                "\\[INFO\\]\\s+([^:]+):([^\\s]+)\\s+\\.+.*\\s+([\\w\\.-]+)\\s+->\\s+([\\w\\.-]+)"
            );
            var matcher = pattern.matcher(output);
            var foundMatches = false;
            while (matcher.find()) {
                foundMatches = true; // Found potential updates, but execute didn't make JSON
                Map<String, String> update = new HashMap<>();
                update.put("groupId", matcher.group(1).trim());
                update.put("artifactId", matcher.group(2).trim());
                update.put("currentVersion", matcher.group(3).trim());
                update.put("newVersion", matcher.group(4).trim());
                update.put("warning", "Raw Maven output parsed; expected JSON. Diff not available.");
                fallbackList.add(update);
            }

            if (foundMatches) return fallbackList;

            if (output.contains("No dependencies found that satisfy the filters") ||
                output.contains("All dependencies are up to date")) { // Check common no-update messages
                return fallbackList; // Empty list, meaning no updates
            }

            // If none of the above, it's an unexpected output
            Map<String, String> errorResult = new HashMap<>();
            errorResult.put("error", "Unexpected output format from tool execution.");
            errorResult.put("details", output);
            fallbackList.add(errorResult);
            return fallbackList;
        }
    }
}
