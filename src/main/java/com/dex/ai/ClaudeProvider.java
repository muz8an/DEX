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
 * Native Anthropic Claude API provider supporting /v1/messages, vision, and system prompts.
 */
public class ClaudeProvider implements AIProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private String baseUrl;
    private String model;
    private String apiKey;

    public ClaudeProvider(String baseUrl, String model, String apiKey) {
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.replaceAll("/+$", "") : AIProviderType.CLAUDE.getDefaultBaseUrl();
        this.model = (model != null && !model.isBlank()) ? model : AIProviderType.CLAUDE.getDefaultModel();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    @Override
    public AIProviderType getType() {
        return AIProviderType.CLAUDE;
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
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public boolean isVisionSupported() {
        return true;
    }

    @Override
    public boolean isToolCallingSupported() {
        return true;
    }

    @Override
    public AIResponse sendChat(AIRequest request) throws Exception {
        if (!isConfigured()) {
            throw new IOException("Anthropic Claude API key is not configured.");
        }

        String endpoint = baseUrl.endsWith("/messages") ? baseUrl : baseUrl + "/messages";
        String targetModel = (request.getModel() != null && !request.getModel().isBlank()) ? request.getModel() : this.model;

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("model", targetModel);
        payload.put("max_tokens", request.getMaxTokens() > 0 ? request.getMaxTokens() : 4096);
        payload.put("temperature", request.getTemperature());

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            payload.put("system", request.getSystemPrompt());
        }

        ArrayNode messagesArray = payload.putArray("messages");
        for (int i = 0; i < request.getMessages().size(); i++) {
            ChatMessage msg = request.getMessages().get(i);
            boolean isLast = (i == request.getMessages().size() - 1);

            ObjectNode m = messagesArray.addObject();
            m.put("role", msg.getRole());

            if (isLast && "user".equalsIgnoreCase(msg.getRole()) && request.hasImage()) {
                ArrayNode contentArray = m.putArray("content");
                ObjectNode textBlock = contentArray.addObject();
                textBlock.put("type", "text");
                textBlock.put("text", msg.getContent());

                ObjectNode imgBlock = contentArray.addObject();
                imgBlock.put("type", "image");
                ObjectNode source = imgBlock.putObject("source");
                source.put("type", "base64");
                source.put("media_type", request.getImageMimeType());
                source.put("data", request.getBase64Image());
            } else {
                m.put("content", msg.getContent());
            }
        }

        String requestJson = MAPPER.writeValueAsString(payload);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException e) {
            throw new IOException("Cannot connect to Anthropic Claude at " + endpoint + ". Check network connection.");
        } catch (HttpTimeoutException e) {
            throw new IOException("Anthropic Claude request timed out after 60 seconds.");
        }

        int statusCode = response.statusCode();
        if (statusCode == 401 || statusCode == 403) {
            throw new IOException("Anthropic Claude Authentication failed (HTTP " + statusCode + "). Please check ANTHROPIC_API_KEY.");
        } else if (statusCode == 429) {
            throw new IOException("Anthropic Claude rate limit exceeded (HTTP 429).");
        } else if (statusCode >= 500) {
            throw new IOException("Anthropic internal server error (HTTP " + statusCode + ").");
        } else if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("Anthropic error (HTTP " + statusCode + "): " + response.body());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode contentArray = root.path("content");
        StringBuilder sb = new StringBuilder();
        if (contentArray.isArray()) {
            for (JsonNode block : contentArray) {
                if ("text".equals(block.path("type").asText())) {
                    sb.append(block.path("text").asText(""));
                }
            }
        }

        return AIResponse.text(sb.toString().trim(), getType().name(), targetModel);
    }
}
