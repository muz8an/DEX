package com.dex.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.dex.config.DEXConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Secure local secret storage for DEX.
 * Encrypts API keys and sensitive tokens using AES-256-GCM with a user-and-machine-derived key.
 * Secrets are never stored in plaintext on disk, committed to Git, or returned in logs.
 */
public class SecretStorage {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH = 256;

    private static final Path SECRETS_FILE = Paths.get("data", "secrets.enc");
    private static final Path SALT_FILE = Paths.get("data", ".dex_salt");

    private static final Map<String, String> IN_MEMORY_CACHE = new HashMap<>();
    private static boolean initialized = false;

    private SecretStorage() {}

    /**
     * Initializes the secret storage and loads saved keys into memory.
     */
    public static synchronized void init() {
        if (initialized) return;
        try {
            ensureDirectories();
            loadSecrets();
            initialized = true;
        } catch (Exception e) {
            System.err.println("[DEX Security] SecretStorage initialization note: " + e.getMessage());
            initialized = true;
        }
    }

    public static synchronized void setSecret(String key, String secretValue) {
        init();
        if (key == null || key.isBlank()) return;

        if (secretValue == null || secretValue.isBlank()) {
            IN_MEMORY_CACHE.remove(key.toLowerCase());
        } else {
            IN_MEMORY_CACHE.put(key.toLowerCase(), secretValue.trim());
        }
        saveSecrets();
    }

    public static synchronized String getSecret(String key) {
        init();
        if (key == null) return null;
        String val = IN_MEMORY_CACHE.get(key.toLowerCase());
        if (val != null && !val.isBlank()) {
            return val;
        }
        // Fallback to environment variable (e.g. DEX_API_KEY, OPENAI_API_KEY, GEMINI_API_KEY)
        String envVal = System.getenv(key);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        String envUpper = System.getenv(key.toUpperCase().replace("-", "_").replace(".", "_"));
        if (envUpper != null && !envUpper.isBlank()) {
            return envUpper.trim();
        }
        return null;
    }

    public static synchronized boolean hasSecret(String key) {
        String s = getSecret(key);
        return s != null && !s.isBlank();
    }

    public static synchronized String getMaskedSecret(String key) {
        String s = getSecret(key);
        if (s == null || s.isBlank()) {
            return "NOT CONFIGURED";
        }
        return maskSecret(s);
    }

    public static String maskSecret(String s) {
        if (s == null || s.isBlank()) {
            return "(none)";
        }
        if (s.length() <= 8) {
            return "****";
        }
        int prefixLen = Math.min(8, s.length() / 3);
        int suffixLen = Math.min(4, s.length() / 4);
        return s.substring(0, prefixLen) + "..." + s.substring(s.length() - suffixLen);
    }

    private static void ensureDirectories() throws IOException {
        Path parent = SECRETS_FILE.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private static byte[] getOrCreateSalt() throws IOException {
        if (Files.exists(SALT_FILE)) {
            return Files.readAllBytes(SALT_FILE);
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        Files.write(SALT_FILE, salt);
        return salt;
    }

    private static SecretKey deriveKey() throws Exception {
        byte[] salt = getOrCreateSalt();
        String userSeed = System.getProperty("user.name") + ":" + System.getProperty("user.home") + ":dex-v1";
        KeySpec spec = new PBEKeySpec(userSeed.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static synchronized void loadSecrets() {
        if (!Files.exists(SECRETS_FILE)) {
            return;
        }
        try {
            byte[] fileBytes = Files.readAllBytes(SECRETS_FILE);
            if (fileBytes.length <= GCM_IV_LENGTH) {
                return;
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(fileBytes, 0, iv, 0, GCM_IV_LENGTH);

            byte[] ciphertext = new byte[fileBytes.length - GCM_IV_LENGTH];
            System.arraycopy(fileBytes, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            SecretKey key = deriveKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintextBytes = cipher.doFinal(ciphertext);

            JsonNode root = MAPPER.readTree(new String(plaintextBytes, StandardCharsets.UTF_8));
            if (root.isObject()) {
                root.fieldNames().forEachRemaining(k -> {
                    String v = root.get(k).asText("");
                    if (!v.isBlank()) {
                        IN_MEMORY_CACHE.put(k.toLowerCase(), v);
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("[DEX Security] Could not decrypt stored secrets (will reinitialize): " + e.getMessage());
        }
    }

    private static synchronized void saveSecrets() {
        try {
            ensureDirectories();
            ObjectNode root = MAPPER.createObjectNode();
            for (Map.Entry<String, String> e : IN_MEMORY_CACHE.entrySet()) {
                root.put(e.getKey(), e.getValue());
            }

            byte[] plaintextBytes = MAPPER.writeValueAsString(root).getBytes(StandardCharsets.UTF_8);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            SecretKey key = deriveKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintextBytes);

            byte[] output = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, output, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, output, GCM_IV_LENGTH, ciphertext.length);

            // Write atomically via tmp file
            Path tmpFile = SECRETS_FILE.resolveSibling("secrets.tmp");
            Files.write(tmpFile, output);
            Files.move(tmpFile, SECRETS_FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        } catch (Exception e) {
            System.err.println("[DEX Security] Failed to save encrypted secrets: " + e.getMessage());
        }
    }
}
