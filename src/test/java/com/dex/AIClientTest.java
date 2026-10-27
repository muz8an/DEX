package com.dex;

import com.dex.config.DEXConfig;
import com.dex.memory.MemoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AIClientTest {

    @BeforeEach
    @AfterEach
    public void setup() {
        DEXConfig.resetRuntimeOverrides();
        com.dex.ai.ProviderManager.setActiveProviderType(com.dex.ai.AIProviderType.OPENAI);
        com.dex.ai.AIProvider p = com.dex.ai.ProviderManager.getActiveProvider();
        if (p != null) p.setApiKey(null);
        com.dex.ai.AIProvider groq = com.dex.ai.ProviderManager.getProvider(com.dex.ai.AIProviderType.GROQ);
        if (groq != null) groq.setApiKey(null);
    }

    @Test
    public void testMissingApiKeyBehavior(@TempDir Path tempDir) {
        Path tempMemory = tempDir.resolve("memory.json");
        MemoryManager memory = new MemoryManager(tempMemory);
        AIClient client = new AIClient(memory);

        // When no API key is set, conversational questions explain how to configure DEX_API_KEY
        String reply = client.processMessage("Tell me a story about robots");
        assertNotNull(reply);
        assertTrue(reply.contains("DEX AI Chat is currently offline because the API key is not configured"));
        assertTrue(reply.contains("DEX_API_KEY"));
        assertTrue(reply.contains("Local Windows tools"));
    }

    @Test
    public void testLocalToolInterceptedWithoutApiKey(@TempDir Path tempDir) {
        Path tempMemory = tempDir.resolve("memory.json");
        MemoryManager memory = new MemoryManager(tempMemory);
        AIClient client = new AIClient(memory);

        // Local tools do NOT require an API key
        String reply = client.processMessage("what time is it");
        assertNotNull(reply);
        assertTrue(reply.contains("Current Date & Time:"));
    }

    @Test
    public void testExtractToolActionJson() {
        String input1 = "Sure, I will open Calculator for you:\n```json\n{\"tool\": \"open_app\", \"argument\": \"calculator\"}\n```";
        String extracted1 = AIClient.extractToolActionJson(input1);
        assertNotNull(extracted1);
        assertTrue(extracted1.contains("\"open_app\""));
        assertTrue(extracted1.contains("\"calculator\""));

        String input2 = "Here is the raw action: {\"tool\": \"get_current_time\", \"argument\": \"\"}";
        String extracted2 = AIClient.extractToolActionJson(input2);
        assertNotNull(extracted2);
        assertTrue(extracted2.contains("get_current_time"));

        String noAction = "Just a regular conversational reply with no tools.";
        assertNull(AIClient.extractToolActionJson(noAction));
    }

    @Test
    public void testClearChatCommand(@TempDir Path tempDir) {
        Path tempMemory = tempDir.resolve("memory.json");
        MemoryManager memory = new MemoryManager(tempMemory);
        memory.addMessage("user", "Hello");
        memory.addMessage("assistant", "Hi");
        assertEquals(2, memory.size());

        AIClient client = new AIClient(memory);
        String reply = client.processMessage("clear chat");
        assertNotNull(reply);
        assertTrue(reply.contains("Current chat session cleared"));
        assertEquals(0, memory.size());
    }
}
