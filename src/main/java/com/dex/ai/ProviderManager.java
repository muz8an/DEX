package com.dex.ai;

import com.dex.security.SecretStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Coordinates multiple AI providers, active provider selection, model switching,
 * fallback execution, and secure secret retrieval.
 */
public class ProviderManager {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path CONFIG_FILE = Paths.get("data", "providers.json");

    private static final Map<AIProviderType, AIProvider> PROVIDERS = new EnumMap<>(AIProviderType.class);
    private static AIProviderType activeProviderType = AIProviderType.FREELLMAPI;
    private static AIProviderType fallbackProviderType = null;
    private static boolean fallbackEnabled = true;

    static {
        initializeProviders();
        loadConfiguration();
    }

    private static synchronized void initializeProviders() {
        SecretStorage.init();

        for (AIProviderType type : AIProviderType.values()) {
            String key = SecretStorage.getSecret(type.name().toLowerCase() + "_key");
            if (key == null && !type.getDefaultEnvVar().isBlank()) {
                key = SecretStorage.getSecret(type.getDefaultEnvVar());
            }

            AIProvider provider;
            switch (type) {
                case GEMINI:
                    provider = new GeminiProvider(type.getDefaultBaseUrl(), type.getDefaultModel(), key);
                    break;
                case CLAUDE:
                    provider = new ClaudeProvider(type.getDefaultBaseUrl(), type.getDefaultModel(), key);
                    break;
                default:
                    provider = new OpenAICompatibleProvider(type, type.getDefaultBaseUrl(), type.getDefaultModel(), key);
                    break;
            }
            PROVIDERS.put(type, provider);
        }

        // Check if environment specifies a default provider (e.g. DEX_AI_PROVIDER=gemini)
        String envProvider = System.getenv("DEX_AI_PROVIDER");
        if (envProvider != null && !envProvider.isBlank()) {
            activeProviderType = AIProviderType.fromString(envProvider);
        }

        // If fallback provider is not set, find the first online provider with a valid key
        if (fallbackProviderType == null) {
            for (AIProvider p : PROVIDERS.values()) {
                if (p.getType() != activeProviderType && p.getType() != AIProviderType.FREELLMAPI && p.getType() != AIProviderType.OLLAMA && p.isConfigured()) {
                    fallbackProviderType = p.getType();
                    break;
                }
            }
        }
    }

    public static synchronized AIProvider getActiveProvider() {
        return PROVIDERS.get(activeProviderType);
    }

    public static synchronized AIProviderType getActiveProviderType() {
        return activeProviderType;
    }

    public static synchronized void setActiveProviderType(AIProviderType type) {
        if (type != null) {
            activeProviderType = type;
            saveConfiguration();
        }
    }

    public static synchronized AIProvider getProvider(AIProviderType type) {
        return PROVIDERS.get(type);
    }

    public static synchronized List<AIProvider> getAllProviders() {
        return new ArrayList<>(PROVIDERS.values());
    }

    public static synchronized void configureProvider(AIProviderType type, String baseUrl, String model, String apiKey) {
        AIProvider p = PROVIDERS.get(type);
        if (p == null) return;

        if (baseUrl != null && !baseUrl.isBlank()) {
            p.setBaseUrl(baseUrl);
        }
        if (model != null && !model.isBlank()) {
            p.setModel(model);
        }
        if (apiKey != null) {
            p.setApiKey(apiKey);
            SecretStorage.setSecret(type.name().toLowerCase() + "_key", apiKey);
        }
        saveConfiguration();
    }

    public static synchronized boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public static synchronized void setFallbackEnabled(boolean enabled) {
        fallbackEnabled = enabled;
        saveConfiguration();
    }

    public static synchronized AIProviderType getFallbackProviderType() {
        return fallbackProviderType;
    }

    public static synchronized void setFallbackProviderType(AIProviderType type) {
        fallbackProviderType = type;
        saveConfiguration();
    }

    /**
     * Executes AI inference using the active provider with automatic fallback if configured.
     */
    public static AIResponse executeInference(AIRequest request) throws Exception {
        AIProvider primary = getActiveProvider();
        if (primary == null) {
            throw new IllegalStateException("No active AI provider configured.");
        }

        try {
            return primary.sendChat(request);
        } catch (Exception primaryError) {
            if (fallbackEnabled && fallbackProviderType != null && fallbackProviderType != activeProviderType) {
                AIProvider fallback = getProvider(fallbackProviderType);
                if (fallback != null && fallback.isConfigured()) {
                    System.err.println("[DEX AI] Primary provider " + primary.getType().getDisplayName()
                            + " failed (" + primaryError.getMessage() + "). Invoking fallback provider: " + fallback.getType().getDisplayName());
                    try {
                        return fallback.sendChat(request);
                    } catch (Exception fallbackError) {
                        throw new IOException("Primary provider (" + primary.getType().getDisplayName() + ") failed: " + primaryError.getMessage()
                                + " | Fallback provider (" + fallback.getType().getDisplayName() + ") failed: " + fallbackError.getMessage());
                    }
                }
            }
            throw primaryError;
        }
    }

    private static synchronized void loadConfiguration() {
        if (!Files.exists(CONFIG_FILE)) {
            return;
        }
        try {
            JsonNode root = MAPPER.readTree(CONFIG_FILE.toFile());
            if (root.has("activeProvider")) {
                activeProviderType = AIProviderType.fromString(root.get("activeProvider").asText());
            }
            if (root.has("fallbackProvider") && !root.get("fallbackProvider").isNull()) {
                fallbackProviderType = AIProviderType.fromString(root.get("fallbackProvider").asText());
            }
            if (root.has("fallbackEnabled")) {
                fallbackEnabled = root.get("fallbackEnabled").asBoolean(true);
            }

            JsonNode providersNode = root.path("providers");
            if (providersNode.isObject()) {
                providersNode.fields().forEachRemaining(entry -> {
                    AIProviderType t = AIProviderType.fromString(entry.getKey());
                    AIProvider p = PROVIDERS.get(t);
                    if (p != null) {
                        JsonNode val = entry.getValue();
                        if (val.has("baseUrl") && !val.get("baseUrl").asText("").isBlank()) {
                            p.setBaseUrl(val.get("baseUrl").asText());
                        }
                        if (val.has("model") && !val.get("model").asText("").isBlank()) {
                            p.setModel(val.get("model").asText());
                        }
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("[DEX AI] Failed to load provider settings: " + e.getMessage());
        }
    }

    private static synchronized void saveConfiguration() {
        try {
            if (CONFIG_FILE.getParent() != null && !Files.exists(CONFIG_FILE.getParent())) {
                Files.createDirectories(CONFIG_FILE.getParent());
            }

            ObjectNode root = MAPPER.createObjectNode();
            root.put("activeProvider", activeProviderType.name());
            root.put("fallbackProvider", fallbackProviderType != null ? fallbackProviderType.name() : null);
            root.put("fallbackEnabled", fallbackEnabled);

            ObjectNode providersNode = root.putObject("providers");
            for (AIProvider p : PROVIDERS.values()) {
                ObjectNode node = providersNode.putObject(p.getType().name());
                node.put("baseUrl", p.getBaseUrl());
                node.put("model", p.getModel());
                // Notice: API keys are NEVER written to providers.json! They stay in SecretStorage!
            }

            Path tmp = CONFIG_FILE.resolveSibling("providers.tmp");
            MAPPER.writeValue(tmp.toFile(), root);
            Files.move(tmp, CONFIG_FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        } catch (Exception e) {
            System.err.println("[DEX AI] Failed to save provider settings: " + e.getMessage());
        }
    }
}
