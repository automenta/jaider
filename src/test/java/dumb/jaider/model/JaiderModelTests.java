package dumb.jaider.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class JaiderModelTests {

    private JaiderModel model;
    private final String defaultGlobalConfig = "TestGlobalConfig";

    @BeforeEach
    void setUp() {
        // Initialize with a default path and global config for each test
        model = new JaiderModel(Paths.get("initial/path"), defaultGlobalConfig);
        // Add some initial data to files to test clearing
        model.files.add(Paths.get("initial/path/file1.txt"));
        model.isIndexed = true;
        model.lastAppliedDiff = "some diff";
        model.statusBarText = "Initial status";
    }

    @Test
    void testConstructorWithGlobalConfig() {
        assertEquals(defaultGlobalConfig, model.globalConfig, "Global config should be set by constructor.");
        assertEquals(Paths.get("initial/path").toAbsolutePath(), model.getDir().toAbsolutePath(), "Initial directory should be set by constructor.");
    }

    @Test
    void testDefaultConstructorWithGlobalConfig() {
        JaiderModel defaultModel = new JaiderModel(defaultGlobalConfig);
        assertEquals(defaultGlobalConfig, defaultModel.globalConfig, "Global config should be set by default constructor.");
        assertEquals(Paths.get("").toAbsolutePath(), defaultModel.getDir().toAbsolutePath(), "Default directory should be the current working directory.");
    }

    @Test
    void setDir_shouldUpdateDirectoryAndClearContext() {
        Path newTestPath = Paths.get("new/test/path").toAbsolutePath();

        // Ensure initial state is as expected
        assertTrue(model.files.contains(Paths.get("initial/path/file1.txt")), "Files should contain initial file before setDir.");
        assertTrue(model.isIndexed, "isIndexed should be true before setDir.");
        assertNotNull(model.lastAppliedDiff, "lastAppliedDiff should not be null before setDir.");

        model.setDir(newTestPath);

        assertEquals(newTestPath, model.getDir().toAbsolutePath(), "getDir() should return the new path.");
        assertTrue(model.files.isEmpty(), "Files list should be cleared after setDir.");
        assertFalse(model.isIndexed, "isIndexed should be false after setDir.");
        assertNull(model.lastAppliedDiff, "lastAppliedDiff should be null after setDir.");
        assertTrue(model.statusBarText.contains(newTestPath.getFileName().toString()), "StatusBarText should be updated with the new directory name.");
        assertTrue(model.statusBarText.contains("Index may need to be rebuilt"), "StatusBarText should suggest re-indexing.");
    }

    @Test
    void getGlobalConfig_shouldReturnConstructorValue() {
        // This is somewhat redundant with testConstructorWithGlobalConfig but good for direct getter test
        String specificConfig = "SpecificConfigForGetterTest";
        JaiderModel specificModel = new JaiderModel(Paths.get("some/path"), specificConfig);
        // There's no direct getGlobalConfig() method, it's a public final field.
        assertEquals(specificConfig, specificModel.globalConfig, "globalConfig field should match value passed to constructor.");
    }
}
