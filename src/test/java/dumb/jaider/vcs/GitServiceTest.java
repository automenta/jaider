package dumb.jaider.vcs;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

class GitServiceTest {

    @TempDir
    Path tempDir;

    private GitService gitService;
    private Path projectDir; // This will be our git repository
    private Git git; // JGit object

    @BeforeEach
    void setUp() throws IOException, GitAPIException {
        projectDir = tempDir.resolve("test-repo");
        Files.createDirectories(projectDir);
        gitService = new GitService(projectDir); // Corrected

        // Initialize a Git repository for most tests
        git = Git.init().setDirectory(projectDir.toFile()).setInitialBranch("main").call();
        // Perform an initial commit so the repo is in a clean state for many tests
        Files.writeString(projectDir.resolve("initial.txt"), "Initial content");
        git.add().addFilepattern("initial.txt").call();
        git.commit().setMessage("Initial commit").setAuthor("Test User", "test@example.com").call();
    }

    @AfterEach
    void tearDown() {
        if (git != null) {
            git.close();
        }
        // tempDir should be cleaned up automatically by JUnit
    }

    // --- Helper method to create a non-git directory for specific tests ---
    private Path createNonGitDir() throws IOException {
        Path nonGitRepoDir = tempDir.resolve("non-git-repo");
        Files.createDirectories(nonGitRepoDir);
        return nonGitRepoDir;
    }

    // --- Tests for commitChanges(String message) ---

    @Test
    void commitChanges_successCase_shouldCommitAndCleanStatus() throws IOException, GitAPIException {
        // Setup: Modify a file
        Path existingFile = projectDir.resolve("initial.txt");
        Files.writeString(existingFile, "New content", StandardOpenOption.APPEND);

        // Action
        String commitMessage = "Test commit for new content";
        String result = gitService.commitChanges(commitMessage);

        // Assertions
        assertNull(result, "Commit should be successful, so no error message expected.");
        assertTrue(git.status().call().isClean(), "Repository should be clean after commit.");

        // Verify the commit message
        RevCommit lastCommit = git.log().setMaxCount(1).call().iterator().next();
        assertEquals(commitMessage, lastCommit.getFullMessage());
        assertEquals("Test User", lastCommit.getAuthorIdent().getName()); // Check author if needed

        // Verify another file addition
        Path newFile = projectDir.resolve("another.txt");
        Files.writeString(newFile, "This is another new file.");

        String secondCommitMessage = "Added another.txt";
        result = gitService.commitChanges(secondCommitMessage);
        assertNull(result, "Second commit should also be successful.");
        assertTrue(git.status().call().isClean(), "Repository should be clean after second commit.");

        lastCommit = git.log().setMaxCount(1).call().iterator().next();
        assertEquals(secondCommitMessage, lastCommit.getFullMessage());
    }

    @Test
    void commitChanges_nonGitDirectory_shouldReturnError() throws IOException {
        // Setup
        Path nonGitDir = createNonGitDir();
        GitService serviceForNonGit = new GitService(nonGitDir); // Corrected

        // Action
        String result = serviceForNonGit.commitChanges("Attempt commit in non-git dir");

        // Assertions
        assertNotNull(result, "Error message should be returned for a non-git directory.");
        assertTrue(result.contains("is not a Git repository"), "Error message should indicate it's not a Git repository.");
    }

    @Test
    void commitChanges_noChanges_shouldHandleGracefully() throws IOException, GitAPIException {
        // Setup: Repository is already clean due to @BeforeEach and initial commit.
        assertTrue(git.status().call().isClean(), "Repository should be clean before this test.");
        RevCommit initialHead = git.log().setMaxCount(1).call().iterator().next();

        // Action
        String commitMessage = "Attempt commit with no changes";
        String result = gitService.commitChanges(commitMessage);

        // Assertions
        assertNull(result, "Should return null (no error) when there are no changes.");

        // Verify that no new commit was made
        RevCommit currentHead = git.log().setMaxCount(1).call().iterator().next();
        assertEquals(initialHead.getId(), currentHead.getId(), "HEAD should not change if there were no changes to commit.");
        assertNotEquals(commitMessage, currentHead.getFullMessage(), "The commit message should not be the new one if no commit happened.");
    }

    // --- Tests for isGitRepoClean() ---

    @Test
    void isGitRepoClean_cleanRepository_shouldReturnTrue() {
        // Setup: Repository is already clean due to @BeforeEach
        // Action
        boolean isClean = gitService.isGitRepoClean();

        // Assertions
        assertTrue(isClean, "isGitRepoClean should return true for a clean repository.");
    }

    @Test
    void isGitRepoClean_dirtyWithNewUntrackedFile_shouldReturnFalse() throws IOException {
        // Setup: Create a new, untracked file
        Path newUntrackedFile = projectDir.resolve("untracked.txt");
        Files.writeString(newUntrackedFile, "This is an untracked file.");

        // Action
        boolean isClean = gitService.isGitRepoClean();

        // Assertions
        assertFalse(isClean, "isGitRepoClean should return false when an untracked file exists.");
    }

    @Test
    void isGitRepoClean_dirtyWithModifiedFile_shouldReturnFalse() throws IOException, GitAPIException {
        // Setup: Modify a committed file
        Path trackedFile = projectDir.resolve("initial.txt");
        Files.writeString(trackedFile, "This content has been modified.", StandardOpenOption.TRUNCATE_EXISTING);

        // Action
        boolean isClean = gitService.isGitRepoClean();

        // Assertions
        assertFalse(isClean, "isGitRepoClean should return false when a tracked file is modified.");
    }

    @Test
    void isGitRepoClean_nonGitDirectory_shouldReturnTrueAndPrintError() throws IOException {
        // Setup
        Path nonGitDir = createNonGitDir();
        GitService serviceForNonGit = new GitService(nonGitDir); // Corrected

        // Action
        boolean isClean = serviceForNonGit.isGitRepoClean();

        // Assertions
        assertTrue(isClean, "isGitRepoClean should return true for a non-git directory as per current implementation.");
        // Note: Asserting System.err output is non-trivial in standard JUnit.
        // Manual verification or a custom stream capture would be needed if critical.
        System.out.println("Note: For isGitRepoClean_nonGitDirectory_shouldReturnTrueAndPrintError, manual check of stderr for message '" + nonGitDir.toString() + " is not a Git repository. Reporting as clean.' might be needed if stderr capturing is not set up.");
    }

    // --- Tests for undoFileChange(String relativeFilePath) ---

    @Test
    void undoFileChange_revertModifiedFile_shouldRestoreContent() throws IOException, GitAPIException {
        // Setup
        Path fileToModify = projectDir.resolve("initial.txt");
        String originalContent = Files.readString(fileToModify);
        String modifiedContent = "This is modified content.";

        Files.writeString(fileToModify, modifiedContent); // Modify the file
        assertNotEquals(originalContent, Files.readString(fileToModify), "File content should be modified before undo.");

        // Action
        String result = gitService.undoFileChange("initial.txt");

        // Assertions
        assertNull(result, "undoFileChange should return null on success.");
        assertEquals(originalContent, Files.readString(fileToModify), "File content should be reverted to original.");
        assertTrue(git.status().call().isClean(), "Repository should be clean after undoing a modification to a tracked file.");
    }

    @Test
    void undoFileChange_revertNewStagedFile_shouldRemoveOrReset() throws IOException, GitAPIException {
        // Setup: Create a new file and stage it
        Path newFile = projectDir.resolve("new_staged_file.txt");
        String newFileContent = "Content of a new file to be staged then undone.";
        Files.writeString(newFile, newFileContent);

        // Stage the new file
        git.add().addFilepattern("new_staged_file.txt").call();
        assertFalse(git.status().call().isClean(), "Repo should be dirty after staging a new file.");
        assertTrue(git.status().call().getAdded().contains("new_staged_file.txt"), "File should be in 'added' status.");


        // Action
        String result = gitService.undoFileChange("new_staged_file.txt");

        // Assertions
        assertNull(result, "undoFileChange should return null on success for staged file.");

        // JGit's checkout command for a path that is added but not committed
        // will typically reset it from the index (unstage it) and delete the working directory file.
        assertTrue(git.status().call().isClean(), "Repository should be clean after undoing a new staged file.");
        assertFalse(Files.exists(newFile), "The new staged file should be deleted from working directory after undo.");
        assertFalse(git.status().call().getAdded().contains("new_staged_file.txt"), "File should no longer be in 'added' status.");
        assertTrue(git.status().call().getUntracked().isEmpty(), "There should be no untracked files if it was deleted properly.");
    }

    @Test
    void undoFileChange_nonGitDirectory_shouldReturnError() throws IOException {
        // Setup
        Path nonGitDir = createNonGitDir();
        GitService serviceForNonGit = new GitService(nonGitDir); // Corrected

        // Action
        String result = serviceForNonGit.undoFileChange("somefile.txt");

        // Assertions
        assertNotNull(result, "Error message should be returned for a non-git directory.");
        assertTrue(result.contains("is not a Git repository"), "Error message should indicate it's not a Git repository.");
    }

    @Test
    void undoFileChange_nonExistentFileInRepo_shouldHandle() throws IOException {
        // Setup: repo is initialized and clean.
        String nonExistentFilePath = "this_file_does_not_exist.txt";
        assertFalse(Files.exists(projectDir.resolve(nonExistentFilePath)), "File should not exist before test.");

        // Action
        String result = gitService.undoFileChange(nonExistentFilePath);

        // Assertions
        assertNotNull(result, "Error message should be returned for a non-existent file.");
        // JGit's specific exception message might be "Entry not found by path" or similar.
        assertTrue(result.contains("Error undoing file change") && result.contains(nonExistentFilePath),
                "Error message should indicate failure for the specific non-existent file. Actual: " + result);
    }
}
