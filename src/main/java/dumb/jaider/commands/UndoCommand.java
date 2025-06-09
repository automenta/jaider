package dumb.jaider.commands;

import com.github.difflib.unifieddiff.UnifiedDiff;
import dumb.jaider.tools.StandardTools; // For the static diffReader
import org.eclipse.jgit.api.Git;
import dev.langchain4j.data.message.AiMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UndoCommand implements Command {
    @Override
    public void execute(String args, AppContext context) {
        if (context.getModel().lastAppliedDiff == null) {
            context.getModel().addLog(AiMessage.from("[Jaider] No change to undo."));
            return;
        }
        try (var git = Git.open(context.getAppInstance().getProjectDir().resolve(".git").toFile())) { // getProjectDir via AppInstance
            UnifiedDiff unifiedDiff = StandardTools.diffReader(context.getModel().lastAppliedDiff); // Use static call
            for (var fileDiff : unifiedDiff.getFiles()) {
                Path filePath = context.getAppInstance().getProjectDir().resolve(fileDiff.getFromFile());
                String fileName = fileDiff.getFromFile();
                boolean isNewFile = "/dev/null".equals(fileDiff.getOldName());

                if (isNewFile) {
                    try {
                        if (Files.deleteIfExists(filePath)) {
                            context.getModel().addLog(AiMessage.from("[Jaider] Reverted (deleted) newly created file: " + fileName));
                            context.getModel().filesInContext.remove(filePath);
                        } else {
                            context.getModel().addLog(AiMessage.from("[Jaider] Undo: File intended for deletion was already gone: " + fileName));
                        }
                    } catch (IOException e) {
                        context.getModel().addLog(AiMessage.from("[Error] Failed to delete newly created file for undo: " + fileName + " - " + e.getMessage()));
                    }
                } else {
                    try {
                        git.checkout().addPath(fileName).call();
                        context.getModel().addLog(AiMessage.from("[Jaider] Reverted to last commit for file: " + fileName));
                    } catch (Exception e) {
                        context.getModel().addLog(AiMessage.from("[Error] Failed to 'git checkout' file for undo: " + fileName + " - " + e.getMessage()));
                    }
                }
            }
            context.getModel().addLog(AiMessage.from("[Jaider] Undo attempt finished. Note: For existing files, this reverts them to their last committed state. For files newly created by the last diff, they are deleted. Please review changes carefully."));
            context.getModel().lastAppliedDiff = null;
        } catch (Exception e) {
            context.getModel().addLog(AiMessage.from("[Error] Failed to process undo operation: " + e.getMessage()));
            context.getModel().lastAppliedDiff = null;
        }
    }
}
