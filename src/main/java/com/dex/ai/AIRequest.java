package com.dex.ai;

import com.dex.memory.MemoryManager.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Encapsulates an AI inference request across any configured provider.
 */
public class AIRequest {
    private final List<ChatMessage> messages;
    private final String systemPrompt;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final String base64Image;
    private final String imageMimeType;
    private final boolean includeTools;

    public AIRequest(Builder builder) {
        this.messages = Collections.unmodifiableList(new ArrayList<>(builder.messages));
        this.systemPrompt = builder.systemPrompt;
        this.model = builder.model;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.base64Image = builder.base64Image;
        this.imageMimeType = builder.imageMimeType != null ? builder.imageMimeType : "image/png";
        this.includeTools = builder.includeTools;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getModel() {
        return model;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public String getBase64Image() {
        return base64Image;
    }

    public String getImageMimeType() {
        return imageMimeType;
    }

    public boolean hasImage() {
        return base64Image != null && !base64Image.isBlank();
    }

    public boolean isIncludeTools() {
        return includeTools;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private List<ChatMessage> messages = new ArrayList<>();
        private String systemPrompt = "";
        private String model = "";
        private double temperature = 0.7;
        private int maxTokens = 2048;
        private String base64Image = null;
        private String imageMimeType = "image/png";
        private boolean includeTools = true;

        public Builder messages(List<ChatMessage> messages) {
            if (messages != null) {
                this.messages = new ArrayList<>(messages);
            }
            return this;
        }

        public Builder addMessage(String role, String content) {
            this.messages.add(new ChatMessage(role, content));
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder image(String base64Image, String mimeType) {
            this.base64Image = base64Image;
            this.imageMimeType = mimeType;
            return this;
        }

        public Builder includeTools(boolean includeTools) {
            this.includeTools = includeTools;
            return this;
        }

        public AIRequest build() {
            return new AIRequest(this);
        }
    }
}
