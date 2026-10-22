package com.dex.ai;

import com.dex.memory.MemoryManager.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Universal client for OpenAI and OpenAI-compatible API providers
 * (OpenAI GPT, Groq, OpenRouter, FreeLLMAPI, Ollama, LM Studio, Custom).
 */
public class OpenAICompatibleProvider implements AIProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final AIProviderType type;
    private String baseUrl;
    private String model;
    private String apiKey;

    public OpenAICompatibleProvider(AIProviderType type, String baseUrl, String model, String apiKey) {
        this.type = type;
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.replaceAll("/+$", "") : type.getDefaultBaseUrl();
        this.model = (model != null && !model.isBlank()) ? model : type.getDefaultModel();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    @Override
    public AIProviderType getType() {
        return type;
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public void setBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            this.baseUrl = baseUrl.replaceAll("/+$", "");
        }
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public void setModel(String model) {
        if (model != null && !model.isBlank()) {
            this.model = model;
        }
    }

    @Override
    public String getApiKey() {
        return apiKey;
    }

    @Override
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    @Override
    public boolean isConfigured() {
        if (type == AIProviderType.OLLAMA) {
            return true; // Local Ollama does not require an API key
        }
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public boolean isVisionSupported() {
        return type.isSupportsVision();
    }

    @Override
    public boolean isToolCallingSupported() {
        return type.isSupportsTools();
    }

    @Override
    public AIResponse sendChat(AIRequest request) throws Exception {
        String endpoint = baseUrl.endsWith("/chat/completions") ? baseUrl : baseUrl + "/chat/completions";

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("model", (request.getModel() != null && !request.getModel().isBlank()) ? request.getModel() : this.model);
        payload.put("temperature", request.getTemperature());
        if (request.getMaxTokens() > 0) {
            payload.put("max_tokens", request.getMaxTokens());
        }

        ArrayNode messagesArray = payload.putArray("messages");

        // 1. System Prompt
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            ObjectNode sys = messagesArray.addObject();
            sys.put("role", "system");
            sys.put("content", request.getSystemPrompt());
        }

        // 2. Conversation messages
        for (int i = 0; i < request.getMessages().size(); i++) {
            ChatMessage msg = request.getMessages().get(i);
            boolean isLast = (i == request.getMessages().size() - 1);

            ObjectNode m = messagesArray.addObject();
            m.put("role", msg.getRole());

            // Check if this is the last user message and an image is attached
            if (isLast && "user".equalsIgnoreCase(msg.getRole()) && request.hasImage() && isVisionSupported()) {
                ArrayNode contentParts = m.putArray("content");
                ObjectNode textPart = contentParts.addObject();
                textPart.put("type", "text");
                textPart.put("text", msg.getContent());

                ObjectNode imgPart = contentParts.addObject();
                imgPart.put("type", "image_url");
                ObjectNode imgUrl = imgPart.putObject("image_url");
                imgUrl.put("url", "data:" + request.getImageMimeType() + ";base64," + request.getBase64Image());
            } else {
                m.put("content", msg.getContent());
            }
        }

        String requestJson = MAPPER.writeValueAsString(payload);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson));

        if (apiKey != null && !apiKey.isBlank()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }

        if (type == AIProviderType.OPENROUTER) {
            reqBuilder.header("HTTP-Referer", "http://127.0.0.1:31416");
            reqBuilder.header("X-Title", "DEX Personal Assistant");
        }

        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException e) {
            throw new IOException("Cannot connect to " + type.getDisplayName() + " at " + endpoint + ". Please verify server status.");
        } catch (HttpTimeoutException e) {
            throw new IOException(type.getDisplayName() + " timed out after 60 seconds.");
        }

        int statusCode = response.statusCode();
        if (statusCode == 401 || statusCode == 403) {
            throw new IOException("HTTP " + statusCode + ": Authentication failed for " + type.getDisplayName() + ". Please verify API key.");
        } else if (statusCode == 429) {
            throw new IOException("HTTP 429: Rate limit or quota exceeded for " + type.getDisplayName() + ".");
        } else if (statusCode >= 500) {
            throw new IOException("HTTP " + statusCode + ": " + type.getDisplayName() + " internal server error.");
        } else if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("HTTP " + statusCode + " from " + type.getDisplayName() + ": " + response.body());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode choiceNode = root.path("choices").path(0).path("message");
        String content = choiceNode.path("content").asText("").trim();

        return AIResponse.text(content, type.name(), this.model);
    }
}
