package com.dex;

import com.dex.memory.MemoryManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MemoryManagerTest {

    @Test
    public void testLoadExistingMemoryFile() {
        MemoryManager manager = new MemoryManager();
        // data/memory.json exists in workspace and contains previous conversations
        assertTrue(manager.size() > 0, "Should have loaded messages from existing data/memory.json");
    }

    @Test
    public void testAddMessageAndAtomicSave(@TempDir Path tempDir) {
        Path tempMemory = tempDir.resolve("memory.json");
        MemoryManager manager = new MemoryManager(tempMemory);

        assertEquals(0, manager.size());
        manager.addMessage("user", "Hello DEX");
        manager.addMessage("assistant", "Hello! How can I help you?");

        assertEquals(2, manager.size());
        assertTrue(Files.exists(tempMemory));

        // Reload in new instance
        MemoryManager reloaded = new MemoryManager(tempMemory);
        assertEquals(2, reloaded.size());
        assertEquals("user", reloaded.getAllMessages().get(0).getRole());
        assertEquals("Hello DEX", reloaded.getAllMessages().get(0).getContent());
    }

    @Test
    public void testSecretSanitization(@TempDir Path tempDir) {
        Path tempMemory = tempDir.resolve("memory.json");
        MemoryManager manager = new MemoryManager(tempMemory);

        manager.addMessage("user", "My API key is sk-1234567890abcdef123456");
        manager.addMessage("assistant", "Using token Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz");

        List<MemoryManager.ChatMessage> msgs = manager.getAllMessages();
        assertFalse(msgs.get(0).getContent().contains("sk-1234567890abcdef123456"));
        assertTrue(msgs.get(0).getContent().contains("[REDACTED_SECRET]"));

        assertFalse(msgs.get(1).getContent().contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));
        assertTrue(msgs.get(1).getContent().contains("[REDACTED_TOKEN]"));
    }

    @Test
    public void testSlidingWindowTrimming(@TempDir Path tempDir) {
        Path tempMemory = tempDir.resolve("memory.json");
        MemoryManager manager = new MemoryManager(tempMemory);

        for (int i = 1; i <= 20; i++) {
            manager.addMessage("user", "Message " + i);
            manager.addMessage("assistant", "Response " + i);
        }
        assertEquals(40, manager.size());

        List<MemoryManager.ChatMessage> trimmed = manager.getTrimmedHistory(10, 5000);
        assertEquals(10, trimmed.size());
        assertEquals("Response 20", trimmed.get(trimmed.size() - 1).getContent());
    }

    @Test
    public void testClearChatDoesNotDeleteFile(@TempDir Path tempDir) {
        Path tempMemory = tempDir.resolve("memory.json");
        MemoryManager manager = new MemoryManager(tempMemory);
        manager.addMessage("user", "Save this");
        assertTrue(Files.exists(tempMemory));

        manager.clearChat();
        assertEquals(0, manager.size());
        assertTrue(Files.exists(tempMemory), "clearChat must not delete persistent disk file");
    }

    @Test
    public void testResetMemoryRequiresConfirmation(@TempDir Path tempDir) {
        Path tempMemory = tempDir.resolve("memory.json");
        MemoryManager manager = new MemoryManager(tempMemory);
        manager.addMessage("user", "Persistent message");
        assertTrue(Files.exists(tempMemory));

        // Attempt without confirmation
        boolean unconfirmedResult = manager.resetMemory(false);
        assertFalse(unconfirmedResult);
        assertTrue(Files.exists(tempMemory));

        // With explicit confirmation
        boolean confirmedResult = manager.resetMemory(true);
        assertTrue(confirmedResult);
        assertEquals(0, manager.size());
        assertFalse(Files.exists(tempMemory));
    }

    @Test
    public void testMalformedMemoryHandling(@TempDir Path tempDir) throws IOException {
        Path tempMemory = tempDir.resolve("memory.json");
        Files.writeString(tempMemory, "{ invalid json object here }}}");

        MemoryManager manager = new MemoryManager(tempMemory);
        assertEquals(0, manager.size(), "Should handle malformed file gracefully by starting clean session");
    }
}
