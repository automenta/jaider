package dumb.jaider.suggestion;

import dev.langchain4j.agent.tool.Tool;
import dumb.jaider.toolmanager.ToolDescriptor;
import dumb.jaider.toolmanager.ToolManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class ProactiveSuggestionService {

    private final List<SuggestionRule> rules;
    private final ToolManager toolManager; // Added

    // Updated constructor to accept ToolManager
    public ProactiveSuggestionService(ToolManager toolManager) {
        this.rules = new ArrayList<>();
        this.toolManager = toolManager; // Store injected ToolManager
        populateRules();
    }

    private void populateRules() {
        // Example rules - these would be expanded
        rules.add(new SuggestionRule(
                Arrays.asList("read file", "open file", "get content of", "cat file"),
                "readFile",
                "It looks like you want to read a file. I can use the '{toolName}' tool for that: {toolDescription}. Example: !readFile filePath=your_file.txt"
        ));
        rules.add(new SuggestionRule(
                Arrays.asList("apply diff", "patch code", "change code"),
                "applyDiff",
                "If you have a diff, I can apply it with the '{toolName}' tool: {toolDescription}."
        ));
        rules.add(new SuggestionRule(
                Arrays.asList("search web", "find on internet", "google for"),
                "searchWeb",
                "Need to search the web? I can use '{toolName}': {toolDescription}. Example: !searchWeb query=your_search_term"
        ));
        rules.add(new SuggestionRule(
                Arrays.asList("list files", "show directory", "ls"),
                "listFiles",
                "To see files in a directory, I can use '{toolName}': {toolDescription}. Example: !listFiles directoryPath=src/main"
        ));
         rules.add(new SuggestionRule(
                Arrays.asList("write file", "save file", "create file"),
                "writeFile",
                "I can write or create files using the '{toolName}' tool: {toolDescription}. Example: !writeFile filePath=new_file.txt content=\"Hello World\""
        ));
    }

    // generateSuggestions now uses the class field this.toolManager
    public List<ActiveSuggestion> generateSuggestions(String userInput, List<Object> internalToolInstances) {
        List<ActiveSuggestion> activeSuggestions = new ArrayList<>();
        String lowerUserInput = userInput.toLowerCase();
        int displayNumber = 1;

        for (SuggestionRule rule : rules) {
            if (rule.matches(lowerUserInput)) {
                String toolName = rule.getTargetToolName();
                String toolDescription = "No description found."; // Default
                boolean foundTool = false;

                // 1. Check external tools via ToolManager (using the class field)
                if (this.toolManager != null) {
                    ToolDescriptor descriptor = this.toolManager.getToolDescriptor(toolName);
                    if (descriptor != null) {
                        toolDescription = descriptor.getDescription();
                        foundTool = true;
                    }
                }

                // 2. Check internal tool instances (if not found in ToolManager)
                // if (!foundTool && internalToolInstances != null) {
                //     for (Object toolInstance : internalToolInstances) {
                //         List<ToolSpecification> specifications = ToolSpecifications.toolSpecificationsFrom(toolInstance);
                //         for (ToolSpecification spec : specifications) {
                //             if (spec.name().equals(toolName)) {
                //                 toolDescription = spec.description();
                //                 foundTool = true;
                //                 break;
                //             }
                //         }
                //         if (foundTool) break;
                //     }
                // }

                // Fallback for internal tools if ToolSpecifications.toolSpecificationsFrom is problematic
                // or we need to be absolutely sure about method scanning.
                // This is more complex and might be redundant if ToolSpecifications.toolSpecificationsFrom works as expected.
                if (!foundTool && internalToolInstances != null) {
                    for (Object toolInstance : internalToolInstances) {
                        for (Method method : toolInstance.getClass().getDeclaredMethods()) {
                            Tool toolAnnotation = method.getAnnotation(Tool.class);
                            if (toolAnnotation != null) {
                                String currentToolName = toolAnnotation.name() != null && !toolAnnotation.name().isEmpty() ?
                                                         toolAnnotation.name() : method.getName();
                                if (currentToolName.equals(toolName)) {
                                    toolDescription = String.join("\n", toolAnnotation.value()); // Join if description is multi-line
                                    if (toolDescription.isEmpty()) {
                                        toolDescription = "No description provided for this tool.";
                                    }
                                    foundTool = true;
                                    break;
                                }
                            }
                        }
                        if (foundTool) break;
                    }
                }


                if (foundTool) {
                    String suggestionText = rule.getSuggestionFormat()
                            .replace("{toolName}", toolName)
                            .replace("{toolDescription}", toolDescription);

                    Suggestion originalSuggestion = new Suggestion(toolName, suggestionText, toolDescription, 1.0);
                    String prefillCommand = String.format("!%s ", toolName); // Basic prefill

                    activeSuggestions.add(new ActiveSuggestion(displayNumber++, originalSuggestion, prefillCommand));
                } else {
                     // Optionally, log if a rule's target tool is not found/described
                     System.err.println("ProactiveSuggestionService: Tool '" + toolName + "' from rule not found or has no description.");
                }
            }
        }
        return activeSuggestions;
    }
}
