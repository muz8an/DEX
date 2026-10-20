package com.dex.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Central configuration manager for DEX AI Assistant.
 * Handles environment variables, paths, runtime keys, and security parameters.
 */
public class DEXConfig {

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:31415/v1";
    private static final String DEFAULT_MODEL = "auto";
    private static final int DEFAULT_WEB_PORT = 31416;

    private static String runtimeApiKey = null;
    private static boolean offlineMode = false;
    private static String remoteAuthToken = null;
    private static Path workspaceRoot = null;

    static {
        // Initialize workspace path
        String userDir = System.getProperty("user.dir");
        workspaceRoot = Paths.get(userDir, "workspace").toAbsolutePath().normalize();
        try {
            Files.createDirectories(workspaceRoot);
            Files.createDirectories(Paths.get(userDir, "data", "screenshots"));
        } catch (IOException ignored) {
        }

        // Initialize remote auth token
        String envToken = System.getenv("DEX_REMOTE_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            remoteAuthToken = envToken.trim();
        } else {
            byte[] randomBytes = new byte[24];
            new SecureRandom().nextBytes(randomBytes);
            remoteAuthToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        }
    }

    public static String getApiKey() {
        if (runtimeApiKey != null && !runtimeApiKey.isBlank()) {
            return runtimeApiKey;
        }
        String envKey = System.getenv("DEX_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        return null;
    }

    public static void setRuntimeApiKey(String key) {
        if (key != null && !key.isBlank()) {
            runtimeApiKey = key.trim();
            offlineMode = false;
        }
    }

    public static boolean hasApiKey() {
        String key = getApiKey();
        return key != null && !key.isBlank();
    }

    public static boolean isOfflineMode() {
        return offlineMode;
    }

    public static void setOfflineMode(boolean offline) {
        offlineMode = offline;
    }

    public static String getBaseUrl() {
        String envUrl = System.getenv("DEX_API_BASE_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            return envUrl.trim().replaceAll("/+$", "");
        }
        return DEFAULT_BASE_URL;
    }

    public static String getChatEndpoint() {
        return getBaseUrl() + "/chat/completions";
    }

    public static String getModel() {
        String envModel = System.getenv("DEX_API_MODEL");
        if (envModel != null && !envModel.isBlank()) {
            return envModel.trim();
        }
        return DEFAULT_MODEL;
    }

    public static int getWebPort() {
        String envPort = System.getenv("DEX_PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_WEB_PORT;
    }

    private static final String DEFAULT_HOST = "127.0.0.1";

    public static String getWebHost() {
        String envHost = System.getenv("DEX_HOST");
        if (envHost != null && !envHost.isBlank()) {
            return envHost.trim();
        }
        return DEFAULT_HOST;
    }

    public static String getRemoteAuthToken() {
        return remoteAuthToken;
    }

    public static boolean validateRemoteToken(String candidateToken) {
        if (candidateToken == null || candidateToken.isBlank() || remoteAuthToken == null) {
            return false;
        }
        return remoteAuthToken.equals(candidateToken.trim());
    }

    public static Path getMemoryFilePath() {
        return Paths.get(System.getProperty("user.dir"), "data", "memory.json").toAbsolutePath().normalize();
    }

    public static Path getScreenshotsDirectory() {
        return Paths.get(System.getProperty("user.dir"), "data", "screenshots").toAbsolutePath().normalize();
    }

    public static Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public static void setWorkspaceRoot(Path customPath) {
        if (customPath != null) {
            try {
                Files.createDirectories(customPath);
                workspaceRoot = customPath.toRealPath();
            } catch (IOException e) {
                workspaceRoot = customPath.toAbsolutePath().normalize();
            }
        }
    }

    /**
     * Validates that runtime directories and settings are properly configured.
     */
    public static boolean validateEnvironment() {
        try {
            Files.createDirectories(workspaceRoot);
            Files.createDirectories(getScreenshotsDirectory());
            if (getMemoryFilePath().getParent() != null) {
                Files.createDirectories(getMemoryFilePath().getParent());
            }
            return true;
        } catch (Exception e) {
            System.err.println("DEX Warning: Failed to initialize directories: " + e.getMessage());
            return false;
        }
    }

    /**
     * Reset runtime overrides (useful for unit tests).
     */
    public static void resetRuntimeOverrides() {
        runtimeApiKey = null;
        offlineMode = false;
        String userDir = System.getProperty("user.dir");
        workspaceRoot = Paths.get(userDir, "workspace").toAbsolutePath().normalize();
    }
}

