package com.dex;

import com.dex.config.DEXConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class DEXConfigTest {

    @BeforeEach
    @AfterEach
    public void setup() {
        DEXConfig.resetRuntimeOverrides();
    }

    @Test
    public void testDefaultConfiguration() {
        assertEquals("http://127.0.0.1:31415/v1", DEXConfig.getBaseUrl());
        assertEquals("http://127.0.0.1:31415/v1/chat/completions", DEXConfig.getChatEndpoint());
        assertEquals("auto", DEXConfig.getModel());
        assertEquals("0.0.0.0", DEXConfig.getWebHost());
        assertEquals(31416, DEXConfig.getWebPort());
        assertNotNull(DEXConfig.getWorkspaceRoot());
        assertNotNull(DEXConfig.getMemoryFilePath());
        assertNotNull(DEXConfig.getScreenshotsDirectory());
        assertNotNull(DEXConfig.getRemoteAuthToken());
        assertFalse(DEXConfig.getDetectedHostIps().isEmpty());
    }

    @Test
    public void testRuntimeApiKey() {
        assertFalse(DEXConfig.hasApiKey());
        DEXConfig.setRuntimeApiKey("test-runtime-api-key-12345");
        assertTrue(DEXConfig.hasApiKey());
        assertEquals("test-runtime-api-key-12345", DEXConfig.getApiKey());
        assertFalse(DEXConfig.isOfflineMode());
    }

    @Test
    public void testOfflineModeToggle() {
        DEXConfig.setOfflineMode(true);
        assertTrue(DEXConfig.isOfflineMode());
        DEXConfig.setOfflineMode(false);
        assertFalse(DEXConfig.isOfflineMode());
    }

    @Test
    public void testRemoteTokenValidation() {
        String token = DEXConfig.getRemoteAuthToken();
        assertTrue(DEXConfig.validateRemoteToken(token));
        assertFalse(DEXConfig.validateRemoteToken("wrong-token-abc"));
        assertFalse(DEXConfig.validateRemoteToken(null));
        assertFalse(DEXConfig.validateRemoteToken(""));
    }

    @Test
    public void testEnvironmentValidation() {
        assertTrue(DEXConfig.validateEnvironment());
    }
}
