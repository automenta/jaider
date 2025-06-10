package dumb.jaider.vcs;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GitService {
    private final Path dir;

    public GitService(Path dir) {
        this.dir = dir;
    }

    public String commitChanges(String message) {
        try (var git = Git.open(dir.resolve(".git").toFile())) {
            var status = git.status().call();
            if (status.isClean() && status.getUntracked().isEmpty() && status.getMissing().isEmpty()) {
                return "No changes to commit.";
            }
            git.add().addFilepattern(".").call();
            status = git.status().call(); // Re-check
            if (status.getAdded().isEmpty() &&
                status.getChanged().isEmpty() &&
                status.getModified().isEmpty() &&
                status.getRemoved().isEmpty()) {
                return "No changes to commit.";
            }
            git.commit().setMessage(message).setAuthor("Jaider Committer", "jaider@example.com").call();
            return "Changes committed successfully.";
        } catch (IOException | GitAPIException e) {
            if (e instanceof org.eclipse.jgit.errors.RepositoryNotFoundException ||
                (e.getMessage() != null && e.getMessage().toLowerCase().contains("not a git repository"))) {
                return "Error: " + dir + " is not a Git repository.";
            }
            return "Git commit failed: " + e.getMessage();
        }
    }

    public boolean isGitRepoClean() {
        try (var git = Git.open(dir.resolve(".git").toFile())) {
            return git.status().call().isClean();
        } catch (IOException | GitAPIException e) {
            System.err.println("Not a git repository or git error during isClean check: " + e.getMessage());
            return true;
        } catch (Exception e) {
            System.err.println("Unexpected error during Git operation in isClean check: " + e.getMessage());
            return true;
        }
    }

    public String undoFileChange(String relativeFilePath) {
        try (var git = Git.open(dir.resolve(".git").toFile())) {
            var status = git.status().call();
            if (status.getAdded().contains(relativeFilePath)) {
                git.reset().addPath(relativeFilePath).call();
                Files.deleteIfExists(dir.resolve(relativeFilePath));
                return "Unstaged and deleted new file: " + relativeFilePath;
            } else {
                git.checkout().addPath(relativeFilePath).call();
                return "Reverted to last commit for file: " + relativeFilePath;
            }
        } catch (IOException | GitAPIException e) {
            if (e instanceof org.eclipse.jgit.errors.RepositoryNotFoundException ||
                (e.getMessage() != null && e.getMessage().toLowerCase().contains("not a git repository"))) {
                return "Error: " + dir + " is not a Git repository.";
            }
            return "Failed to undo changes for file: " + relativeFilePath + " - " + e.getMessage();
        }
    }
}
