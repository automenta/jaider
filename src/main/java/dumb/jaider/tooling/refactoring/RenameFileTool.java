package dumb.jaider.tooling.refactoring;

import dumb.jaider.tooling.Tool;
import dumb.jaider.tooling.ToolContext;
import org.eclipse.jgit.api.Git; // Will use JGit for staging
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class RenameFileTool implements Tool {

    @Override
    public String getName() {
        return "RenameFile";
    }

    @Override
    public String getDescription() {
        return "Renames a file and stages the change using Git.";
    }

    @Override
    public boolean isAvailable() {
        // This tool relies on basic file operations and JGit, which is a dependency.
        // We can check if we are in a Git repository.
        try {
            FileRepositoryBuilder.create(new File(".git").getAbsoluteFile());
            return true;
        } catch (Exception e) {
            // System.err.println("Not a Git repository or JGit not available: " + e.getMessage());
            return false; // Not in a git repo or other JGit issue
        }
    }

    @Override
    public String execute(ToolContext context) throws Exception {
        Path projectRoot = context.getProjectRoot()
                                .orElseThrow(() -> new IllegalArgumentException("Project root must be provided for RenameFileTool."));

        String oldFilePathStr = context.getParameter("oldFilePath", String.class)
                                    .orElseThrow(() -> new IllegalArgumentException("Missing 'oldFilePath' in ToolContext for RenameFile."));
        String newFilePathStr = context.getParameter("newFilePath", String.class)
                                    .orElseThrow(() -> new IllegalArgumentException("Missing 'newFilePath' in ToolContext for RenameFile."));

        Path oldFilePath = projectRoot.resolve(oldFilePathStr).normalize();
        Path newFilePath = projectRoot.resolve(newFilePathStr).normalize();

        if (!Files.exists(oldFilePath)) {
            throw new java.io.FileNotFoundException("Old file path does not exist: " + oldFilePath);
        }
        if (Files.exists(newFilePath)) {
            throw new java.nio.file.FileAlreadyExistsException("New file path already exists: " + newFilePath);
        }
        if (!oldFilePath.startsWith(projectRoot) || !newFilePath.startsWith(projectRoot)) {
            throw new IllegalArgumentException("File paths must be within the project root.");
        }

        // Ensure parent directory for new file path exists
        Path newFileParentDir = newFilePath.getParent();
        if (newFileParentDir != null && !Files.exists(newFileParentDir)) {
            Files.createDirectories(newFileParentDir);
        }

        // Perform the rename
        Files.move(oldFilePath, newFilePath, StandardCopyOption.ATOMIC_MOVE);

        // Stage the changes using JGit
        try (Repository repository = FileRepositoryBuilder.create(projectRoot.resolve(".git").toFile());
             Git git = new Git(repository)) {

            // `git add <newFilePath>` (for the renamed file)
            // `git rm <oldFilePath>` (if it was tracked)
            // JGit's AddCommand handles rename detection if the content is similar enough,
            // but explicitly using `git mv` semantics is more robust.
            // However, JGit's `mv` is not directly available.
            // We can `rm` the old path and `add` the new path.

            String oldRepoPath = projectRoot.relativize(oldFilePath).toString().replace('\\', '/');
            String newRepoPath = projectRoot.relativize(newFilePath).toString().replace('\\', '/');

            // Remove the old file path from index if it was tracked
            // This check is important because `git.rm()` will fail if the file is not tracked.
            if (repository.resolve(oldRepoPath) != null && git.status().call().getRemoved().contains(oldRepoPath)) {
                 // File already removed by Files.move, ensure git knows
            } else if (repository.resolve(oldRepoPath) != null && !git.status().call().getUntracked().contains(oldRepoPath)) {
                 git.rm().addFilepattern(oldRepoPath).call();
            }

            git.add().addFilepattern(newRepoPath).call();

            return String.format("File %s renamed to %s and staged.", oldFilePathStr, newFilePathStr);
        } catch (Exception e) {
            // If JGit fails, try to revert the file move if possible, though this can be tricky
            try {
                Files.move(newFilePath, oldFilePath, StandardCopyOption.ATOMIC_MOVE);
                return String.format("Failed to stage rename with Git: %s. File move reverted.", e.getMessage());
            } catch (java.io.IOException ex) {
                return String.format("Failed to stage rename with Git: %s. CRITICAL: File move could not be reverted: %s", e.getMessage(), ex.getMessage());
            }
        }
    }

    @Override
    public Object parseOutput(String rawOutput) {
        // For this tool, the raw output is a simple success/failure message.
        // We can return it directly or a map with a "message" key.
        java.util.Map<String, String> result = new java.util.HashMap<>();
        result.put("message", rawOutput);
        return result;
    }
}
