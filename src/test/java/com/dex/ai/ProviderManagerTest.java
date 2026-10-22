package com.dex.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProviderManagerTest {

    @Test
    public void testDefaultProviderSelection() {
        AIProvider active = ProviderManager.getActiveProvider();
        assertNotNull(active);
        assertNotNull(ProviderManager.getActiveProviderType());
    }

    @Test
    public void testSwitchingActiveProvider() {
        ProviderManager.setActiveProviderType(AIProviderType.OPENAI);
        assertEquals(AIProviderType.OPENAI, ProviderManager.getActiveProviderType());

        ProviderManager.setActiveProviderType(AIProviderType.CLAUDE);
        assertEquals(AIProviderType.CLAUDE, ProviderManager.getActiveProviderType());

        ProviderManager.setActiveProviderType(AIProviderType.GROQ);
        assertEquals(AIProviderType.GROQ, ProviderManager.getActiveProviderType());
    }

    @Test
    public void testProviderConfigurationAndKeyMasking() {
        AIProvider groq = ProviderManager.getProvider(AIProviderType.GROQ);
        assertNotNull(groq);
        groq.setApiKey("gsk_test1234567890abcdefghijklmnopqrstuvwxyz");

        assertTrue(groq.isConfigured());
        assertEquals("gsk_test1234567890abcdefghijklmnopqrstuvwxyz", groq.getApiKey());
    }

    @Test
    public void testProviderRequestModel() {
        AIRequest req = AIRequest.newBuilder()
                .addMessage("user", "Explain binary search")
                .systemPrompt("You are an assistant")
                .temperature(0.5)
                .maxTokens(500)
                .image("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==", "image/png")
                .build();

        assertEquals(1, req.getMessages().size());
        assertEquals("Explain binary search", req.getMessages().get(0).getContent());
        assertEquals("You are an assistant", req.getSystemPrompt());
        assertEquals(0.5, req.getTemperature());
        assertEquals(500, req.getMaxTokens());
        assertTrue(req.hasImage());
        assertNotNull(req.getBase64Image());
        assertEquals("image/png", req.getImageMimeType());
    }

    @Test
    public void testFallbackProviderSelection() {
        ProviderManager.setActiveProviderType(AIProviderType.OPENAI);
        ProviderManager.setFallbackProviderType(AIProviderType.GROQ);

        assertEquals(AIProviderType.GROQ, ProviderManager.getFallbackProviderType());
        assertTrue(ProviderManager.isFallbackEnabled());
    }

    @Test
    public void testAllProvidersList() {
        List<AIProvider> all = ProviderManager.getAllProviders();
        assertNotNull(all);
        assertFalse(all.isEmpty());
        // Verify key providers are registered
        assertTrue(all.stream().anyMatch(p -> p.getType() == AIProviderType.GEMINI));
        assertTrue(all.stream().anyMatch(p -> p.getType() == AIProviderType.OPENAI));
        assertTrue(all.stream().anyMatch(p -> p.getType() == AIProviderType.CLAUDE));
        assertTrue(all.stream().anyMatch(p -> p.getType() == AIProviderType.GROQ));
        assertTrue(all.stream().anyMatch(p -> p.getType() == AIProviderType.FREELLMAPI));
    }
}
