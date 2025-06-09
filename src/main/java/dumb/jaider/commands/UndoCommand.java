package dumb.jaider.commands;

import com.github.difflib.unifieddiff.UnifiedDiff;
import dumb.jaider.utils.Util;
import dumb.jaider.vcs.GitService; // Added import
// import org.eclipse.jgit.api.Git; // No longer directly used
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
        GitService gitService = new GitService(context.getAppInstance().getProjectDir());
        try {
            // The Git.open() call is now handled by GitService.
            // We still need to read the diff to know which files were affected.
            UnifiedDiff unifiedDiff = Util.diffReader(context.getModel().lastAppliedDiff);
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
                    String undoResult = gitService.undoFileChange(fileName);
                    context.getModel().addLog(AiMessage.from("[Jaider] " + undoResult));
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
