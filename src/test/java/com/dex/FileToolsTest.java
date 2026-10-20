package com.dex;

import com.dex.config.DEXConfig;
import com.dex.tools.FileTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class FileToolsTest {

    private Path originalWorkspace;

    @BeforeEach
    public void setup(@TempDir Path tempWorkspace) {
        originalWorkspace = DEXConfig.getWorkspaceRoot();
        DEXConfig.setWorkspaceRoot(tempWorkspace);
    }

    @AfterEach
    public void cleanup() {
        DEXConfig.setWorkspaceRoot(originalWorkspace);
    }

    @Test
    public void testSafePathResolution() throws IOException {
        Path resolved = FileTools.resolveSafePath("test.txt");
        assertNotNull(resolved);
        assertTrue(resolved.startsWith(DEXConfig.getWorkspaceRoot()));
    }

    @Test
    public void testPathTraversalBlocked() {
        assertThrows(SecurityException.class, () -> FileTools.resolveSafePath("../outside.txt"));
        assertThrows(SecurityException.class, () -> FileTools.resolveSafePath("../../windows/system32"));
        assertThrows(SecurityException.class, () -> FileTools.resolveSafePath("../../../NTUSER.DAT"));
    }

    @Test
    public void testSensitiveFilesBlocked() {
        assertThrows(SecurityException.class, () -> FileTools.resolveSafePath("id_rsa"));
        assertThrows(SecurityException.class, () -> FileTools.resolveSafePath(".env"));
        assertThrows(SecurityException.class, () -> FileTools.resolveSafePath("server.key"));
        assertThrows(SecurityException.class, () -> FileTools.resolveSafePath("cert.pem"));
    }

    @Test
    public void testWriteAndReadFile() {
        String writeResult = FileTools.writeFile("hello.txt", "Hello DEX Assistant!", false);
        assertTrue(writeResult.contains("File successfully saved"));

        String content = FileTools.readFile("hello.txt");
        assertEquals("Hello DEX Assistant!", content);
    }

    @Test
    public void testFileOverwriteProtection() {
        FileTools.writeFile("existing.txt", "Initial content", false);

        // Attempt overwrite with allowOverwrite = false
        String blockedResult = FileTools.writeFile("existing.txt", "Overwritten content", false);
        assertTrue(blockedResult.contains("File already exists"));
        assertEquals("Initial content", FileTools.readFile("existing.txt"));

        // Overwrite with allowOverwrite = true
        String allowedResult = FileTools.writeFile("existing.txt", "Overwritten content", true);
        assertTrue(allowedResult.contains("File successfully saved"));
        assertEquals("Overwritten content", FileTools.readFile("existing.txt"));
    }

    @Test
    public void testSaveCode() {
        String code = "public class Hello { public static void main(String[] args) {} }";
        String result = FileTools.saveCode("Hello.java", code);
        assertTrue(result.contains("File successfully saved"));
        assertEquals(code, FileTools.readFile("Hello.java"));
    }

    @Test
    public void testSearchFiles() {
        FileTools.writeFile("alpha.txt", "data", false);
        FileTools.writeFile("beta.log", "data", false);

        String searchResult = FileTools.searchFiles("alpha");
        assertTrue(searchResult.contains("alpha.txt"));
        assertFalse(searchResult.contains("beta.log"));
    }
}
