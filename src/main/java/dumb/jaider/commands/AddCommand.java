package dumb.jaider.commands;

import java.util.Arrays;

public class AddCommand implements Command {
    @Override
    public void execute(String args, AppContext context) {
        if (args == null || args.isBlank()) {
            context.getModel().addLog(dev.langchain4j.data.message.AiMessage.from("[Jaider] Usage: /add <file1> [file2] ..."));
            return;
        }
        String[] filesToAdd = args.split("\\s+");
        Arrays.stream(filesToAdd)
              .map(context.getModel().projectDir::resolve)
              .forEach(context.getModel().filesInContext::add);
        context.getAppInstance().updateTokenCountPublic(); // Needs to be public or called via a public method in App
        context.getModel().addLog(dev.langchain4j.data.message.AiMessage.from("[Jaider] Added files to context: " + String.join(", ", filesToAdd)));
    }
}
