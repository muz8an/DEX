package com.dex.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SecretStorageTest {

    @Test
    public void testSecretStorageEncryptionAndDecryption() {
        String testKey = "sk-test-secret-value-abcdef-987654";
        SecretStorage.setSecret("TEST_PROVIDER_KEY", testKey);

        String retrieved = SecretStorage.getSecret("TEST_PROVIDER_KEY");
        assertEquals(testKey, retrieved, "Decrypted secret must match original secret.");
    }

    @Test
    public void testSecretsFileIsEncryptedOnDisk() throws Exception {
        SecretStorage.setSecret("SENSITIVE_KEY", "SuperSecretPassword123!");

        // Read raw bytes from data/secrets.enc
        Path secretsFile = Path.of("data", "secrets.enc");
        if (Files.exists(secretsFile)) {
            byte[] rawBytes = Files.readAllBytes(secretsFile);
            String rawDiskContent = new String(rawBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
            assertFalse(rawDiskContent.contains("SuperSecretPassword123!"),
                    "Raw disk file must NEVER contain the plaintext secret!");
        }
    }

    @Test
    public void testMaskSecret() {
        assertEquals("(none)", SecretStorage.maskSecret(null));
        assertEquals("(none)", SecretStorage.maskSecret(""));
        assertEquals("****", SecretStorage.maskSecret("1234"));

        String longKey = "sk-proj-abc123xyz789def456";
        String masked = SecretStorage.maskSecret(longKey);
        assertTrue(masked.startsWith("sk-proj-"));
        assertTrue(masked.endsWith("f456"));
        assertTrue(masked.contains("..."));
        assertFalse(masked.contains("abc123xyz789"));
    }
}
