package com.dex.mobile;

import org.junit.Test;

import static org.junit.Assert.*;

public class DexClientTest {

    @Test
    public void testUrlNormalization() {
        assertEquals("http://192.168.1.10:31416", DexClient.normalizeUrl("192.168.1.10:31416"));
        assertEquals("http://192.168.1.10:31416", DexClient.normalizeUrl("http://192.168.1.10:31416/"));
        assertEquals("https://dex.example.com", DexClient.normalizeUrl("https://dex.example.com/"));
        assertEquals("http://127.0.0.1:31416", DexClient.normalizeUrl(null));
        assertEquals("http://127.0.0.1:31416", DexClient.normalizeUrl("   "));
    }

    @Test
    public void testClientState() {
        DexClient client = new DexClient("192.168.1.50:31416", null);
        assertTrue(client.isConfigured());
        assertFalse(client.isPaired());

        client.setAuthToken("dex_dev_testtoken123");
        assertTrue(client.isPaired());
        assertEquals("dex_dev_testtoken123", client.getAuthToken());
    }
}
