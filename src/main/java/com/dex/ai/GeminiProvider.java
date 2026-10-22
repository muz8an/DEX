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
 * Native Google Gemini API provider supporting generateContent, vision images, and system instructions.
 */
public class GeminiProvider implements AIProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private String baseUrl;
    private String model;
    private String apiKey;

    public GeminiProvider(String baseUrl, String model, String apiKey) {
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.replaceAll("/+$", "") : AIProviderType.GEMINI.getDefaultBaseUrl();
        this.model = (model != null && !model.isBlank()) ? model : AIProviderType.GEMINI.getDefaultModel();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    @Override
    public AIProviderType getType() {
        return AIProviderType.GEMINI;
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
            throw new IOException("Gemini API key is not configured.");
        }

        String targetModel = (request.getModel() != null && !request.getModel().isBlank()) ? request.getModel() : this.model;
        String endpoint = baseUrl + "/v1beta/models/" + targetModel + ":generateContent?key=" + apiKey;

        ObjectNode payload = MAPPER.createObjectNode();

        // 1. System Instruction
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            ObjectNode sysInst = payload.putObject("system_instruction");
            ArrayNode parts = sysInst.putArray("parts");
            parts.addObject().put("text", request.getSystemPrompt());
        }

        // 2. Contents
        ArrayNode contents = payload.putArray("contents");
        for (int i = 0; i < request.getMessages().size(); i++) {
            ChatMessage msg = request.getMessages().get(i);
            boolean isLast = (i == request.getMessages().size() - 1);

            ObjectNode c = contents.addObject();
            // Gemini roles: "user" or "model"
            c.put("role", "assistant".equalsIgnoreCase(msg.getRole()) ? "model" : "user");

            ArrayNode parts = c.putArray("parts");
            parts.addObject().put("text", msg.getContent());

            if (isLast && "user".equalsIgnoreCase(msg.getRole()) && request.hasImage()) {
                ObjectNode imgPart = parts.addObject();
                ObjectNode inlineData = imgPart.putObject("inline_data");
                inlineData.put("mime_type", request.getImageMimeType());
                inlineData.put("data", request.getBase64Image());
            }
        }

        // 3. Generation Config
        ObjectNode genConfig = payload.putObject("generationConfig");
        genConfig.put("temperature", request.getTemperature());
        if (request.getMaxTokens() > 0) {
            genConfig.put("maxOutputTokens", request.getMaxTokens());
        }

        String requestJson = MAPPER.writeValueAsString(payload);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException e) {
            throw new IOException("Cannot connect to Google Gemini endpoint. Check network connection.");
        } catch (HttpTimeoutException e) {
            throw new IOException("Google Gemini request timed out after 60 seconds.");
        }

        int statusCode = response.statusCode();
        if (statusCode == 400) {
            throw new IOException("Gemini HTTP 400 Bad Request: " + response.body());
        } else if (statusCode == 401 || statusCode == 403) {
            throw new IOException("Gemini Authentication failed (HTTP " + statusCode + "). Please verify your Gemini API key.");
        } else if (statusCode == 429) {
            throw new IOException("Gemini HTTP 429: Rate limit exceeded or quota exhausted.");
        } else if (statusCode >= 500) {
            throw new IOException("Gemini internal server error (HTTP " + statusCode + ").");
        } else if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("Gemini error (HTTP " + statusCode + "): " + response.body());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode candidate = root.path("candidates").path(0);
        JsonNode parts = candidate.path("content").path("parts");

        StringBuilder sb = new StringBuilder();
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                if (part.has("text")) {
                    sb.append(part.get("text").asText());
                }
            }
        }

        return AIResponse.text(sb.toString().trim(), getType().name(), targetModel);
    }
}
