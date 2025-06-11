package dumb.jaider.vcs;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GitService {
    private static final Logger logger = LoggerFactory.getLogger(GitService.class);
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
            // It's common for this to be called on non-git dirs, so debug or trace might be better
            // For now, let's use warn, but this could be noisy if routinely called on non-git dirs.
            logger.warn("Not a git repository or git error during isClean check for directory '{}': {}", dir, e.getMessage());
            return true; // Defaulting to "clean" (or effectively, "not a concern for git") if not a repo or error
        } catch (Exception e) {
            logger.error("Unexpected error during Git operation in isClean check for directory '{}': {}", dir, e.getMessage(), e);
            return true; // Defaulting to "clean" in case of unexpected errors
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

    public List<String> listFiles(String relativePath) throws IOException {
        Set<String> results = new HashSet<>();
        // Normalize relativePath: null or empty means root, ensure no leading/trailing slashes for directory logic later
        String normalizedPath = relativePath == null ? "" : relativePath.trim();
        if (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        if (normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
        }

        try (Repository repository = Git.open(dir.toFile()).getRepository();
             TreeWalk treeWalk = new TreeWalk(repository)) {

            ObjectId head = repository.resolve("HEAD^{tree}");
            if (head == null) {
                logger.warn("No HEAD commit found in repository at {}. Cannot list files.", dir);
                return new ArrayList<>(); // Empty repository or no commits
            }
            treeWalk.addTree(head);
            treeWalk.setRecursive(false); // We only want one level deep from the relativePath

            if (!normalizedPath.isEmpty()) {
                // Filter by the given path. If normalizedPath is a directory, this will enter it.
                // If it's a file, it will position on that file.
                PathFilter pathFilter = PathFilter.create(normalizedPath);
                treeWalk.setFilter(pathFilter);

                // Need to advance treewalk to the first match of the pathfilter.
                // If the path doesn't exist, treeWalk.next() after setFilter might return false immediately or after a few calls.
                boolean foundPath = false;
                while (treeWalk.next()) {
                    if (treeWalk.getPathString().equals(normalizedPath) || treeWalk.getPathString().startsWith(normalizedPath + "/")) {
                        foundPath = true;
                        break;
                    }
                }
                if (!foundPath) {
                     logger.debug("Path '{}' not found in Git repository at {}.", normalizedPath, dir);
                     return new ArrayList<>(); // Path does not exist
                }
                // If the path itself is a file, list only that file
                if (treeWalk.getFileMode(0) != FileMode.TREE) {
                    results.add(treeWalk.getPathString());
                    return new ArrayList<>(results);
                }
                // If it's a directory, we need to re-walk its contents for one level
                treeWalk.enterSubtree();
            }

            // Now, treeWalk is either at the root or inside the specified directory (normalizedPath)
            while (treeWalk.next()) {
                String pathString = treeWalk.getPathString();
                if (treeWalk.getFileMode(0) == FileMode.TREE) {
                    results.add(pathString + "/");
                } else {
                    results.add(pathString);
                }
            }
        } catch (org.eclipse.jgit.errors.RepositoryNotFoundException e) {
            logger.warn("Attempted to list files on a non-Git directory: {}", dir, e);
            throw e; // Re-throw, or return empty list if that's preferred for non-repos
        } catch (IOException e) {
            logger.error("Error listing files in Git repository at {}: {}", dir, e.getMessage(), e);
            throw e; // Re-throw
        }
        return results.stream().sorted().collect(Collectors.toList());
    }
}
