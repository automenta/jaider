package dumb.jaider.tools;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class StandardToolsTest {

    @Mock
    private Config config;

    @Mock
    private EmbeddingModel embeddingModel;

    private StandardTools standardTools;

    @TempDir
    Path tempDir; // JUnit 5 temporary directory

    @BeforeEach
    void setUp() throws GitAPIException {
        MockitoAnnotations.openMocks(this);
        // It seems model.dir is public, if not, need a getter like getDir()
        // For StandardTools constructor model.dir is accessed directly. Let's assume it is accessible or change to use getter.
        // If model.dir is final and set in constructor, this mock might be tricky.
        // Let's assume model.dir is a Path field that can be mocked via a getter.
        // Re-reading StandardTools, it uses a public field model.dir. So, we need to set that field.
        // This is not ideal. A getter `model.getProjectRootPath()` would be better.
        // For now, let's assume we can make `when(model.dir).thenReturn(tempDir);` work if dir is public
        // If not, I'll have to adjust.
        // The JaiderModel mock's `dir` field needs to be set. Mockito can't mock fields directly.
        // Let's assume JaiderModel has a public `dir` field or a setter for it for testing,
        // or StandardTools uses a getter from JaiderModel.

        // Simplest for now: StandardTools takes JaiderModel, and JaiderModel.dir is public.
        // So, the `when(model.getDir()).thenReturn(tempDir);` is actually for a method I'm assuming exists.
        // Let's check JaiderModel structure from previous files if possible, or assume a getter.
        // From previous files: `JaiderModel model` has `public final Path dir;`
        // This means we cannot mock `model.dir` directly on a mocked JaiderModel instance after its construction.
        // We need to pass a real or a carefully crafted JaiderModel.
        // For simplicity in this step, I will create a real JaiderModel instance for testing StandardTools,
        // and ensure its `dir` field points to `tempDir`.

        var realModel = new JaiderModel(tempDir);
        standardTools = new StandardTools(realModel, config, embeddingModel);

        // Initialize a Git repository in the tempDir for relevant tests
        org.eclipse.jgit.api.Git.init().setDirectory(tempDir.toFile()).call();
    }

    // --- listFiles Tests ---

    @Test
    void listFiles_rootDirectory() throws Exception {
        Files.createFile(tempDir.resolve("file1.txt"));
        Files.createDirectory(tempDir.resolve("dir1"));
        Files.createFile(tempDir.resolve("dir1/file2.txt"));
        commitAll();

        var output = standardTools.listFiles("");
        assertTrue(output.contains("[FILE] file1.txt\n"));
        assertTrue(output.contains("[DIR] dir1/\n"));
        assertFalse(output.contains("file2.txt")); // Should not list nested files
    }

    @Test
    void listFiles_subDirectory() throws Exception {
        Files.createDirectory(tempDir.resolve("subdir"));
        Files.createFile(tempDir.resolve("subdir/subfile.txt"));
        Files.createDirectory(tempDir.resolve("subdir/nesteddir"));
        Files.createFile(tempDir.resolve("subdir/nesteddir/deepfile.txt"));
        commitAll();

        var output = standardTools.listFiles("subdir");
        // GitService.listFiles might return "subdir/subfile.txt" and "subdir/nesteddir/"
        // StandardTools then checks isDirectory on these resolved paths.
        assertTrue(output.contains("[FILE] subdir/subfile.txt\n"));
        assertTrue(output.contains("[DIR] subdir/nesteddir/\n"));
        assertFalse(output.contains("deepfile.txt"));
    }

    @Test
    void listFiles_emptyDirectory() throws Exception {
        Files.createDirectory(tempDir.resolve("emptyDir"));
        commitAll();

        var output = standardTools.listFiles("emptyDir");
        assertEquals("No files found in emptyDir", output.trim());
    }

    @Test
    void listFiles_pathIsFile() throws Exception {
        Files.createFile(tempDir.resolve("myFile.txt"));
        commitAll();

        var output = standardTools.listFiles("myFile.txt");
        assertTrue(output.contains("[FILE] myFile.txt\n"));
    }

    @Test
    void listFiles_nonExistentPath() throws Exception {
        commitAll(); // Commit just in case, though no files exist
        var output = standardTools.listFiles("nonexistent");
        assertEquals("No files found in nonexistent", output.trim());
    }

    @Test
    void listFiles_gitIgnoredFile() throws Exception {
        Files.createFile(tempDir.resolve("ignored.txt"));
        Files.createFile(tempDir.resolve("regular.txt"));
        var gitignore = tempDir.resolve(".gitignore");
        Files.writeString(gitignore, "ignored.txt\n");
        commitAll(); // This will commit .gitignore and regular.txt

        var output = standardTools.listFiles("");
        assertFalse(output.contains("ignored.txt"));
        assertTrue(output.contains("[FILE] regular.txt\n"));
        assertTrue(output.contains("[FILE] .gitignore\n")); // .gitignore itself is usually tracked
    }

    // --- writeFile Tests ---

    @Test
    void writeFile_createNewFile() throws Exception {
        var filePath = "newFile.txt";
        var content = "Hello, world!";
        var result = standardTools.writeFile(filePath, content);

        assertEquals("File created successfully: " + filePath, result);
        assertTrue(Files.exists(tempDir.resolve(filePath)));
        assertEquals(content, Files.readString(tempDir.resolve(filePath)));
    }

    @Test
    void writeFile_overwriteExistingFile() throws Exception {
        var filePath = "existingFile.txt";
        var initialContent = "Initial content";
        var newContent = "New content";

        Files.writeString(tempDir.resolve(filePath), initialContent);
        commitAll(); // So it exists for overwrite

        var result = standardTools.writeFile(filePath, newContent);
        assertEquals("File overwritten successfully: " + filePath, result);
        assertEquals(newContent, Files.readString(tempDir.resolve(filePath)));
    }

    @Test
    void writeFile_createInNewSubdirectory() throws Exception {
        var filePath = "newDir/newSubDir/file.txt";
        var content = "Deep content";
        var result = standardTools.writeFile(filePath, content);

        assertEquals("File created successfully: " + filePath, result);
        assertTrue(Files.exists(tempDir.resolve(filePath)));
        assertEquals(content, Files.readString(tempDir.resolve(filePath)));
        assertTrue(Files.isDirectory(tempDir.resolve("newDir")));
        assertTrue(Files.isDirectory(tempDir.resolve("newDir/newSubDir")));
    }

    @Test
    void writeFile_pathIsExistingDirectory() throws Exception {
        var dirPath = "existingDir";
        Files.createDirectory(tempDir.resolve(dirPath));
        commitAll();

        var result = standardTools.writeFile(dirPath, "Trying to write to a dir");
        // This should fail because Files.writeString cannot write to a directory.
        // The exact error message might depend on the OS/NIO implementation.
        assertTrue(result.startsWith("Error writing file '" + dirPath + "':"), "Expected an error message, but got: " + result);
    }

    @Test
    void writeFile_parentPathComponentIsFile() throws Exception {
        var filePathParent = "fileAsParent";
        Files.createFile(tempDir.resolve(filePathParent)); // Create a file
        commitAll();

        var filePathChild = "fileAsParent/childFile.txt";
        var result = standardTools.writeFile(filePathChild, "Content");

        // The StandardTools.writeFile has a check:
        // else if (!Files.isDirectory(parentDir)) { return "Error: Cannot create parent directory..."
        assertEquals("Error: Cannot create parent directory. A file with the same name as the parent directory already exists: " + tempDir.resolve(filePathParent), result);
        assertFalse(Files.exists(tempDir.resolve(filePathChild)));
    }

    // Helper to commit all changes in the temp git repo
    private void commitAll() throws Exception {
        try (var git = org.eclipse.jgit.api.Git.open(tempDir.toFile())) {
            git.add().addFilepattern(".").call();
            // Check if there's anything to commit (e.g. status is not clean)
            var status = git.status().call();
            if (!status.isClean() || !status.getUntracked().isEmpty()) {
                 git.commit().setMessage("Test commit").setAuthor("Test User", "test@example.com").call();
            }
        }
    }

    // --- runValidationCommand Tests ---

    private void makeScriptExecutable(Path scriptPath) {
        // Simplified executable setting
        scriptPath.toFile().setExecutable(true, false); // ownerOnly = false for wider applicability
    }

    @Test
    @Timeout(5) // Add a 5-second timeout for this specific test
    void runValidationCommand_success() throws Exception {
        Path scriptFile = tempDir.resolve("test_script.sh");
        String scriptContent = """
                #!/bin/bash
                echo "output from script"
                exit 0
                """;
        Files.writeString(scriptFile, scriptContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        makeScriptExecutable(scriptFile);

        when(config.getRunCommand()).thenReturn(scriptFile.toString());

        String jsonResult = standardTools.runValidationCommand(null);
        JSONObject result = new JSONObject(jsonResult);

        assertTrue(result.getBoolean("success"));
        assertEquals(0, result.getInt("exitCode"));
        assertTrue(result.getString("output").contains("output from script"));
    }

    @Test
    void runValidationCommand_failure() throws Exception {
        Path scriptFile = tempDir.resolve("fail_script.sh");
        String scriptContent = """
                #!/bin/bash
                echo "error output"
                exit 1
                """;
        Files.writeString(scriptFile, scriptContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        makeScriptExecutable(scriptFile);

        when(config.getRunCommand()).thenReturn(scriptFile.toString());

        String jsonResult = standardTools.runValidationCommand(null);
        JSONObject result = new JSONObject(jsonResult);

        assertFalse(result.getBoolean("success"));
        assertEquals(1, result.getInt("exitCode"));
        assertTrue(result.getString("output").contains("error output"));
    }

    @Test
    void runValidationCommand_withArguments() throws Exception {
        Path scriptFile = tempDir.resolve("args_script.sh");
        String scriptContent = """
                #!/bin/bash
                echo "Args: $1 $2"
                exit 0
                """;
        Files.writeString(scriptFile, scriptContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        makeScriptExecutable(scriptFile);

        when(config.getRunCommand()).thenReturn(scriptFile.toString());

        String jsonResult = standardTools.runValidationCommand("arg1 arg2");
        JSONObject result = new JSONObject(jsonResult);

        assertTrue(result.getBoolean("success"));
        assertEquals(0, result.getInt("exitCode"));
        assertTrue(result.getString("output").contains("Args: arg1 arg2"));
    }

    @Test
    void runValidationCommand_notConfigured() {
        when(config.getRunCommand()).thenReturn(null);

        String jsonResult = standardTools.runValidationCommand(null);
        JSONObject result = new JSONObject(jsonResult);

        assertFalse(result.getBoolean("success"));
        assertEquals(-1, result.getInt("exitCode"));
        assertEquals("Run command is not configured.", result.getString("output"));

        when(config.getRunCommand()).thenReturn("");
        jsonResult = standardTools.runValidationCommand(null);
        result = new JSONObject(jsonResult);
        assertFalse(result.getBoolean("success"));
        assertEquals(-1, result.getInt("exitCode"));
        assertEquals("Run command is not configured.", result.getString("output"));
    }

    @Test
    void runValidationCommand_timeout() throws Exception {
        Path scriptFile = tempDir.resolve("timeout_script.sh");
        // StandardTools.COMMAND_TIMEOUT_SECONDS is 1.
        String scriptContent = """
                #!/bin/bash
                sleep 3
                echo "should not see this"
                exit 0
                """;
        Files.writeString(scriptFile, scriptContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        makeScriptExecutable(scriptFile);

        when(config.getRunCommand()).thenReturn(scriptFile.toString());

        String jsonResult = standardTools.runValidationCommand(null);
        JSONObject result = new JSONObject(jsonResult);

        assertFalse(result.getBoolean("success"));
        assertEquals(1, result.getInt("exitCode"));
        assertTrue(result.getString("output").contains("Command execution timed out after 1 seconds."));
    }
}
