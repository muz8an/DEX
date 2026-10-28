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
        assertTrue(reply.contains("AI Chat is currently offline because the API key is not configured"));
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

    @Test
    public void testNullActiveProviderDoesNotThrowNpe(@TempDir Path tempDir) {
        Path tempMemory = tempDir.resolve("memory.json");
        MemoryManager memory = new MemoryManager(tempMemory);
        AIClient client = new AIClient(memory);

        com.dex.ai.AIProviderType prev = com.dex.ai.ProviderManager.getActiveProviderType();
        try {
            // Test with a query when provider is not configured or null
            String reply = client.processMessage("Tell me a joke");
            assertNotNull(reply);
            assertTrue(reply.contains("AI Chat is currently offline"));
        } finally {
            com.dex.ai.ProviderManager.setActiveProviderType(prev);
        }
    }

    @Test
    public void testAssistantIdentityAlwaysX(@TempDir Path tempDir) {
        Path tempMemory = tempDir.resolve("memory.json");
        AIClient client = new AIClient(new MemoryManager(tempMemory));

        assertEquals("My name is X.", client.processMessage("What is your name?"));
        assertEquals("My name is X.", client.processMessage("what is your name"));
        assertEquals("My name is X.", client.processMessage("What's your name?"));
        assertEquals("My name is X, your personal AI assistant.", client.processMessage("Who are you?"));
    }

    @Test
    public void testPersonalizedUserNameResolution(@TempDir Path tempDir) {
        Path tempProfile = tempDir.resolve("user_profile.json");
        com.dex.profile.UserProfileManager.setProfileFilePathForTesting(tempProfile);

        Path tempMemory = tempDir.resolve("memory.json");
        AIClient client = new AIClient(new MemoryManager(tempMemory));

        // When name is not set
        String noNameReply = client.processMessage("What is my name?");
        assertTrue(noNameReply.contains("I don't know your name yet"));

        // When user sets name in setup
        com.dex.profile.UserProfileManager.completeSetup("Muzain");
        String nameReply = client.processMessage("What is my name?");
        assertEquals("Your name is Muzain.", nameReply);

        // When user changes name in chat
        String introReply = client.processMessage("My name is Sarah");
        assertTrue(introReply.contains("Sarah"));
        assertEquals("Sarah", com.dex.profile.UserProfileManager.getUserName());
        assertEquals("Your name is Sarah.", client.processMessage("what's my name"));
    }

    @Test
    public void testImageGenerationRejectedExplicitly(@TempDir Path tempDir) {
        Path tempMemory = tempDir.resolve("memory.json");
        AIClient client = new AIClient(new MemoryManager(tempMemory));

        String reply1 = client.processMessage("generate an image of a red sports car");
        assertTrue(reply1.contains("I cannot generate images"));
        assertTrue(reply1.contains("Image generation is not supported"));

        String reply2 = client.processMessage("create a picture of mountains at sunset");
        assertTrue(reply2.contains("I cannot generate images"));

        String reply3 = client.processMessage("draw an illustration of a robot");
        assertTrue(reply3.contains("I cannot generate images"));
    }

    @Test
    public void testSystemPromptContainsXAndIdentity(@TempDir Path tempDir) {
        Path tempProfile = tempDir.resolve("user_profile.json");
        com.dex.profile.UserProfileManager.setProfileFilePathForTesting(tempProfile);
        com.dex.profile.UserProfileManager.completeSetup("Alex");

        AIClient client = new AIClient(new MemoryManager(tempDir.resolve("memory.json")));
        String prompt = client.getSystemPrompt();

        assertTrue(prompt.contains("You are X"));
        assertTrue(prompt.contains("Your name is X"));
        assertTrue(prompt.contains("Alex"));
        assertTrue(prompt.contains("Image Generation: You CANNOT generate, create, draw, or render images"));
        assertFalse(prompt.contains("Muzain"), "Prompt must not hardcode personal names when not configured");
    }
}
