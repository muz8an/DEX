package com.dex.ai;

/**
 * Universal interface for all AI provider integrations in DEX.
 */
public interface AIProvider {

    AIProviderType getType();

    String getBaseUrl();

    void setBaseUrl(String baseUrl);

    String getModel();

    void setModel(String model);

    String getApiKey();

    void setApiKey(String apiKey);

    boolean isConfigured();

    boolean isVisionSupported();

    boolean isToolCallingSupported();

    AIResponse sendChat(AIRequest request) throws Exception;
}
