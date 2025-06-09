package dumb.jaider.commands;

// import com.github.difflib.unifieddiff.UnifiedDiff; // Commented out
// import dumb.jaider.utils.Util; // Util.diffReader is commented out
import dumb.jaider.vcs.GitService;
import dev.langchain4j.data.message.AiMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UndoCommand implements Command {
    @Override
    public void execute(String args, AppContext appContext) { // Changed context to appContext to match usage
        appContext.getModel().addLog(AiMessage.from("[UndoCommand] Error: Undo functionality is temporarily disabled due to library issues."));
        /*
        if (appContext.getModel().lastAppliedDiff == null) {
            appContext.getModel().addLog(AiMessage.from("[Jaider] No change to undo."));
            return;
        }
        GitService gitService = new GitService(appContext.getAppInstance().getProjectDir());
        try {
            // The Git.open() call is now handled by GitService.
            // We still need to read the diff to know which files were affected.
            com.github.difflib.unifieddiff.UnifiedDiff unifiedDiff = dumb.jaider.utils.Util.diffReader(appContext.getModel().lastAppliedDiff);
            for (var fileDiff : unifiedDiff.getFiles()) {
                Path filePath = appContext.getAppInstance().getProjectDir().resolve(fileDiff.getFromFile());
                String fileName = fileDiff.getFromFile();
                boolean isNewFile = "/dev/null".equals(fileDiff.getOldName());

                if (isNewFile) {
                    try {
                        if (Files.deleteIfExists(filePath)) {
                            appContext.getModel().addLog(AiMessage.from("[Jaider] Reverted (deleted) newly created file: " + fileName));
                            appContext.getModel().filesInContext.remove(filePath);
                        } else {
                            appContext.getModel().addLog(AiMessage.from("[Jaider] Undo: File intended for deletion was already gone: " + fileName));
                        }
                    } catch (IOException e) {
                        appContext.getModel().addLog(AiMessage.from("[Error] Failed to delete newly created file for undo: " + fileName + " - " + e.getMessage()));
                    }
                } else {
                    String undoResult = gitService.undoFileChange(fileName);
                    appContext.getModel().addLog(AiMessage.from("[Jaider] " + undoResult));
                }
            }
            appContext.getModel().addLog(AiMessage.from("[Jaider] Undo attempt finished. Note: For existing files, this reverts them to their last committed state. For files newly created by the last diff, they are deleted. Please review changes carefully."));
            appContext.getModel().lastAppliedDiff = null;
        } catch (Exception e) {
            appContext.getModel().addLog(AiMessage.from("[Error] Failed to process undo operation: " + e.getMessage()));
            appContext.getModel().lastAppliedDiff = null;
        }
        */
    }
}
