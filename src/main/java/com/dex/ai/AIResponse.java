package com.dex.ai;

/**
 * Encapsulates the response from any AI provider.
 */
public class AIResponse {
    private final String content;
    private final String providerId;
    private final String model;
    private final boolean toolCall;
    private final String toolJson;

    public AIResponse(String content, String providerId, String model, boolean toolCall, String toolJson) {
        this.content = content != null ? content.trim() : "";
        this.providerId = providerId;
        this.model = model;
        this.toolCall = toolCall;
        this.toolJson = toolJson;
    }

    public static AIResponse text(String content, String providerId, String model) {
        return new AIResponse(content, providerId, model, false, null);
    }

    public static AIResponse tool(String content, String providerId, String model, String toolJson) {
        return new AIResponse(content, providerId, model, true, toolJson);
    }

    public String getContent() {
        return content;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getModel() {
        return model;
    }

    public boolean isToolCall() {
        return toolCall;
    }

    public String getToolJson() {
        return toolJson;
    }
}
