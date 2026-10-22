package com.dex.memory;

import com.dex.config.DEXConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Manages persistent and session conversation memory with atomic writes,
 * sliding window pruning for LLM context, and secret sanitization.
 */
public class MemoryManager {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    // Common secret patterns to redact from logs/memory
    private static final Pattern SECRET_PATTERN_1 = Pattern.compile("(?i)(sk-[a-zA-Z0-9_-]{16,})");
    private static final Pattern SECRET_PATTERN_2 = Pattern.compile("(?i)(bearer\\s+[a-zA-Z0-9_\\-\\.]{20,})");
    private static final Pattern SECRET_PATTERN_3 = Pattern.compile("(?i)(api[_-]?key[\"':\\s=]+)([a-zA-Z0-9_-]{16,})");

    private final Path memoryFile;
    private final List<ChatMessage> conversationHistory;

    public static class ChatMessage {
        private final String role;
        private final String content;
        private final long timestamp;

        public ChatMessage(String role, String content) {
            this(role, content, System.currentTimeMillis());
        }

        public ChatMessage(String role, String content, long timestamp) {
            this.role = role != null ? role.trim().toLowerCase() : "user";
            this.content = sanitize(content);
            this.timestamp = timestamp;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public ObjectNode toJsonNode() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("role", role);
            node.put("content", content);
            return node;
        }

        private static String sanitize(String text) {
            if (text == null) {
                return "";
            }
            String result = text;
            String currentKey = DEXConfig.getApiKey();
            if (currentKey != null && !currentKey.isBlank() && currentKey.length() > 6) {
                result = result.replace(currentKey, "[REDACTED_API_KEY]");
            }
            result = SECRET_PATTERN_1.matcher(result).replaceAll("[REDACTED_SECRET]");
            result = SECRET_PATTERN_2.matcher(result).replaceAll("Bearer [REDACTED_TOKEN]");
            result = SECRET_PATTERN_3.matcher(result).replaceAll("$1[REDACTED_SECRET]");
            return result;
        }
    }

    public MemoryManager() {
        this(DEXConfig.getMemoryFilePath());
    }

    public MemoryManager(Path memoryFile) {
        this.memoryFile = memoryFile;
        this.conversationHistory = new ArrayList<>();
        loadMemory();
        loadFacts();
    }

    /**
     * Loads previous conversation history from disk.
     */
    public synchronized int loadMemory() {
        conversationHistory.clear();
        try {
            if (memoryFile.getParent() != null) {
                Files.createDirectories(memoryFile.getParent());
            }

            if (!Files.exists(memoryFile)) {
                return 0;
            }

            if (Files.size(memoryFile) == 0) {
                return 0;
            }

            JsonNode root = MAPPER.readTree(memoryFile.toFile());
            if (root == null || !root.isArray()) {
                System.err.println("DEX Warning: Memory file format is not a JSON array. Initializing clean session.");
                return 0;
            }

            for (JsonNode node : root) {
                String role = node.path("role").asText("");
                String content = node.path("content").asText("");
                if ((role.equals("user") || role.equals("assistant") || role.equals("system")) && !content.isBlank()) {
                    conversationHistory.add(new ChatMessage(role, content));
                }
            }
            return conversationHistory.size();
        } catch (Exception e) {
            System.err.println("DEX Warning: Failed to parse memory file (" + e.getMessage() + "). Starting clean session.");
            return 0;
        }
    }

    /**
     * Appends a message to in-memory conversation and persists atomically to disk.
     */
    public synchronized void addMessage(String role, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        conversationHistory.add(new ChatMessage(role, content));
        saveMemory();
    }

    /**
     * Removes the last added message (e.g., when an AI request fails).
     */
    public synchronized void removeLastMessage() {
        if (!conversationHistory.isEmpty()) {
            conversationHistory.remove(conversationHistory.size() - 1);
            saveMemory();
        }
    }

    /**
     * Atomically saves the entire conversation history to disk.
     */
    public synchronized boolean saveMemory() {
        try {
            if (memoryFile.getParent() != null) {
                Files.createDirectories(memoryFile.getParent());
            }

            ArrayNode arrayNode = MAPPER.createArrayNode();
            for (ChatMessage msg : conversationHistory) {
                arrayNode.add(msg.toJsonNode());
            }

            // Write to temporary file first for atomic commit
            Path tempFile = memoryFile.resolveSibling(memoryFile.getFileName().toString() + ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), arrayNode);

            try {
                Files.move(tempFile, memoryFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, memoryFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            System.err.println("DEX Error: Failed to save memory to disk: " + e.getMessage());
            return false;
        }
    }

    /**
     * Clears the active session conversation in memory.
     * The persistent file on disk is intentionally preserved.
     */
    public synchronized void clearChat() {
        conversationHistory.clear();
    }

    /**
     * Alias for clearChat().
     */
    public synchronized void clearMemory() {
        clearChat();
    }

    /**
     * Explicit memory reset that wipes both in-memory and disk records.
     * Must only be invoked after explicit user confirmation.
     */
    public synchronized boolean resetMemory(boolean userConfirmed) {
        if (!userConfirmed) {
            return false;
        }
        conversationHistory.clear();
        try {
            if (Files.exists(memoryFile)) {
                Files.delete(memoryFile);
            }
            return true;
        } catch (IOException e) {
            System.err.println("DEX Error: Could not delete memory file: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns an unmodifiable copy of all messages in history.
     */
    public synchronized List<ChatMessage> getAllMessages() {
        return Collections.unmodifiableList(new ArrayList<>(conversationHistory));
    }

    /**
     * Returns total number of messages in memory.
     */
    public synchronized int size() {
        return conversationHistory.size();
    }

    /**
     * Sliding window history trimmer for LLM queries.
     * Preserves recent conversation turns while staying within token/character limits.
     *
     * @param maxMessages Maximum number of past turns to include.
     * @param maxChars    Maximum total character budget for past context.
     * @return Trimmed list of messages suitable for LLM request payload.
     */
    public synchronized List<ChatMessage> getTrimmedHistory(int maxMessages, int maxChars) {
        if (conversationHistory.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChatMessage> result = new ArrayList<>();
        int startIndex = Math.max(0, conversationHistory.size() - maxMessages);
        int totalChars = 0;

        for (int i = conversationHistory.size() - 1; i >= startIndex; i--) {
            ChatMessage msg = conversationHistory.get(i);
            int msgLen = msg.getContent().length();
            if (totalChars + msgLen > maxChars && !result.isEmpty()) {
                break;
            }
            result.add(0, msg);
            totalChars += msgLen;
        }

        return result;
    }

    // --- LONG-TERM USER FACTS & PREFERENCES ---
    private final Map<String, String> userFacts = new LinkedHashMap<>();
    private final Path factsFile = Paths.get("data", "facts.json");

    public synchronized void addFact(String key, String value) {
        if (key != null && !key.isBlank() && value != null) {
            userFacts.put(key.trim(), value.trim());
            saveFacts();
        }
    }

    public synchronized String getFact(String key) {
        if (key == null) return null;
        return userFacts.get(key.trim());
    }

    public synchronized Map<String, String> getAllFacts() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(userFacts));
    }

    public synchronized void removeFact(String key) {
        if (key != null) {
            userFacts.remove(key.trim());
            saveFacts();
        }
    }

    public synchronized void clearFacts() {
        userFacts.clear();
        saveFacts();
    }

    public synchronized String getFactsPromptSummary() {
        if (userFacts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\nUser Preferences & Facts:\n");
        for (Map.Entry<String, String> e : userFacts.entrySet()) {
            sb.append("• ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }

    private synchronized void loadFacts() {
        userFacts.clear();
        try {
            if (Files.exists(factsFile)) {
                JsonNode root = MAPPER.readTree(factsFile.toFile());
                if (root.isObject()) {
                    root.fields().forEachRemaining(entry -> {
                        userFacts.put(entry.getKey(), entry.getValue().asText(""));
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("DEX Warning: Failed to parse facts.json: " + e.getMessage());
        }
    }

    private synchronized void saveFacts() {
        try {
            if (factsFile.getParent() != null && !Files.exists(factsFile.getParent())) {
                Files.createDirectories(factsFile.getParent());
            }
            ObjectNode root = MAPPER.createObjectNode();
            for (Map.Entry<String, String> e : userFacts.entrySet()) {
                root.put(e.getKey(), e.getValue());
            }
            Path tmp = factsFile.resolveSibling("facts.tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), root);
            Files.move(tmp, factsFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.err.println("DEX Error: Failed to save facts.json: " + e.getMessage());
        }
    }
}
