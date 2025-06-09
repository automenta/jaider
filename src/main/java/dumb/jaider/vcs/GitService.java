package dumb.jaider.vcs;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import java.io.IOException;
import java.nio.file.Path;

public class GitService {
    private final Path projectDir;

    public GitService(Path projectDir) {
        this.projectDir = projectDir;
    }

    public String commitChanges(String message) {
        try (Git git = Git.open(projectDir.resolve(".git").toFile())) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage(message).call();
            return "Changes committed successfully.";
        } catch (IOException | GitAPIException e) {
            return "Git commit failed: " + e.getMessage();
        }
    }

    public boolean isGitRepoClean() {
        try (Git git = Git.open(projectDir.resolve(".git").toFile())) {
            return git.status().call().isClean();
        } catch (IOException | GitAPIException e) {
            // Mimicking original App.isGitRepoClean behavior:
            // print error, return true if not a repo or error.
            System.err.println("Not a git repository or git error during isClean check: " + e.getMessage());
            return true; // Original behavior was to proceed.
        } catch (Exception e) {
            // Catch any other unexpected errors during Git open or status check
            System.err.println("Unexpected error during Git operation in isClean check: " + e.getMessage());
            return true; // Original behavior was to proceed.
        }
    }

    public String undoFileChange(String relativeFilePath) {
        try (Git git = Git.open(projectDir.resolve(".git").toFile())) {
            git.checkout().addPath(relativeFilePath).call();
            return "Reverted to last commit for file: " + relativeFilePath;
        } catch (IOException | GitAPIException e) {
            return "Failed to 'git checkout' file: " + relativeFilePath + " - " + e.getMessage();
        }
    }
}
