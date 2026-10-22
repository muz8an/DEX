package com.dex.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BrowserAutomationTest {

    @Test
    public void testBuildSearchUrl() {
        String googleUrl = BrowserAutomation.buildSearchUrl("java spring boot", "Google");
        assertTrue(googleUrl.contains("google.com/search?q=java+spring+boot") || googleUrl.contains("google.com/search?q=java%20spring%20boot"));

        String ytUrl = BrowserAutomation.buildSearchUrl("lofi beats", "YouTube");
        assertTrue(ytUrl.contains("youtube.com/results?search_query="));

        String ghUrl = BrowserAutomation.buildSearchUrl("jarvis assistant", "GitHub");
        assertTrue(ghUrl.contains("github.com/search?q="));

        String ddgUrl = BrowserAutomation.buildSearchUrl("openai api", "DuckDuckGo");
        assertTrue(ddgUrl.contains("duckduckgo.com/?q="));
    }

    @Test
    public void testCleanHtmlText() {
        String sampleHtml = "<html><head><title>Test Page</title><style>.test{color:red;}</style></head>"
                + "<body><h1>Welcome to DEX</h1><p>DEX is an AI assistant.</p><script>console.log('hi');</script></body></html>";

        String text = BrowserAutomation.cleanHtmlText(sampleHtml);
        assertNotNull(text);
        assertTrue(text.contains("Welcome to DEX"));
        assertTrue(text.contains("DEX is an AI assistant."));
        assertFalse(text.contains(".test{color:red;}"), "Style tags must be stripped from clean text.");
        assertFalse(text.contains("console.log"), "Script tags must be stripped from clean text.");
    }
}
