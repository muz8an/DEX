package com.dex;

import com.dex.config.DEXConfig;
import com.dex.memory.MemoryManager;
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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FreeLLMAPI Client and Conversational Agent for DEX.
 * Handles OpenAI-compatible chat completions, dynamic tool calling loops,
 * persistent memory coordination, timeout management, and robust error recovery.
 */
public class AIClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final Pattern JSON_TOOL_PATTERN = Pattern.compile(
            "(?s)(?:```(?:json)?\\s*)?(\\{\\s*\"(?:tool|action)\"\\s*:\\s*\"[^\"]+\"[^}]*\\})(?:\\s*```)?"
    );

    private final MemoryManager memoryManager;

    public AIClient() {
        this(new MemoryManager());
    }

    public AIClient(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    /**
     * Builds the system instructions injected into every FreeLLMAPI request.
     */
    public String getSystemPrompt() {
        return "You are DEX, an intelligent, personal AI assistant running locally on Windows 11.\n"
                + "User Information: Address the user respectfully (e.g. Muzain).\n"
                + "Tone: Helpful, direct, technically capable, and security-conscious.\n\n"
                + "Capabilities & Tools:\n"
                + "You have access to approved Windows tools on this machine:\n"
                + ToolManager.getToolsDescription() + "\n"
                + "Tool Usage Rules:\n"
                + "1. When the user requests an action (opening an app, reading/writing files, system diagnostics, running tests, screenshots), "
                + "invoke the appropriate tool using this exact JSON format:\n"
                + "```json\n"
                + "{\"tool\": \"<tool_name>\", \"argument\": \"<argument_value>\"}\n"
                + "```\n"
                + "2. Never claim you performed an action without receiving the verified execution result.\n"
                + "3. Never attempt to run arbitrary shell commands outside the approved allowlist.\n"
                + "4. Never reveal API keys, credentials, or secrets.";
    }

    /**
     * Central message processing pipeline.
     * Evaluates local tasks first, then delegates to FreeLLMAPI with tool loop when necessary.
     */
    public String processMessage(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return "Please enter a message or command.";
        }

        String input = userInput.trim();

        // 1. Check for local commands
        if (input.equalsIgnoreCase("clear chat") || input.equalsIgnoreCase("clear-chat")) {
            memoryManager.clearChat();
            return "Current chat session cleared. Persistent memory file remains safely preserved.";
        }

        if (input.equalsIgnoreCase("list tools") || input.equalsIgnoreCase("tools")) {
            return ToolManager.listToolsDetailed();
        }

        // 2. Local deterministic task interception
        String localTaskResult = null;
        try {
            localTaskResult = TaskManager.handle(input);
        } catch (Exception e) {
            localTaskResult = "Task execution error: " + e.getMessage();
        }

        if (localTaskResult != null) {
            memoryManager.addMessage("user", input);
            memoryManager.addMessage("assistant", localTaskResult);
            return localTaskResult;
        }

        // 3. Check FreeLLMAPI availability
        if (!DEXConfig.hasApiKey()) {
            return "DEX AI Chat is currently offline because the API key is not configured.\n"
                    + "Local Windows tools (apps, system info, time, workspace files) are active and fully operational!\n\n"
                    + "To enable AI reasoning, set the environment variable in PowerShell:\n"
                    + "  $env:DEX_API_KEY = \"your_api_key_here\"\n"
                    + "Or type 'set-key <your_key>' in this console.";
        }

        if (DEXConfig.isOfflineMode()) {
            return "DEX is currently running in offline local-only mode. Use local tools or restart to connect to FreeLLMAPI.";
        }

        // 4. Send to FreeLLMAPI with iterative tool calling
        memoryManager.addMessage("user", input);

        try {
            String aiResponse = executeChatWithToolLoop(input);
            memoryManager.addMessage("assistant", aiResponse);
            return aiResponse;
        } catch (Exception e) {
            // Remove the unanswered user message so history stays clean
            memoryManager.removeLastMessage();
            return handleApiError(e);
        }
    }

    /**
     * Sends conversation to FreeLLMAPI and handles up to 2 tool execution iterations.
     */
    private String executeChatWithToolLoop(String latestInput) throws Exception {
        int maxToolIterations = 2;
        List<MemoryManager.ChatMessage> sessionContext = new ArrayList<>(
                memoryManager.getTrimmedHistory(14, 7000)
        );

        String currentAssistantReply = callChatCompletion(sessionContext);

        for (int iteration = 0; iteration < maxToolIterations; iteration++) {
            String toolActionJson = extractToolActionJson(currentAssistantReply);
            if (toolActionJson == null) {
                break;
            }

            // Execute the proposed tool
            String executionResult = ToolManager.executeJsonAction(toolActionJson);

            // Feed execution result back into temporary context
            sessionContext.add(new MemoryManager.ChatMessage("assistant", currentAssistantReply));
            sessionContext.add(new MemoryManager.ChatMessage("user",
                    "[System Tool Execution Result]:\n" + executionResult + "\nPlease summarize or complete the response for the user."));

            currentAssistantReply = callChatCompletion(sessionContext);
        }

        return currentAssistantReply;
    }

    /**
     * Executes single HTTP chat completion to FreeLLMAPI.
     */
    private String callChatCompletion(List<MemoryManager.ChatMessage> messages) throws Exception {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("model", DEXConfig.getModel());
        payload.put("temperature", 0.7);

        ArrayNode messagesArray = payload.putArray("messages");

        // 1. System Prompt
        ObjectNode systemMsg = messagesArray.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", getSystemPrompt());

        // 2. History
        for (MemoryManager.ChatMessage msg : messages) {
            ObjectNode m = messagesArray.addObject();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
        }

        String requestJson = MAPPER.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DEXConfig.getChatEndpoint()))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + DEXConfig.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        if (statusCode == 401 || statusCode == 403) {
            throw new IOException("HTTP " + statusCode + ": Authentication failed. Please check that DEX_API_KEY is valid.");
        } else if (statusCode == 429) {
            throw new IOException("HTTP 429: FreeLLMAPI rate limit exceeded. Please wait a moment.");
        } else if (statusCode >= 500) {
            throw new IOException("HTTP " + statusCode + ": FreeLLMAPI internal server error.");
        } else if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("HTTP " + statusCode + " from FreeLLMAPI: " + response.body());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode choiceNode = root.path("choices").path(0).path("message").path("content");

        if (!choiceNode.isTextual()) {
            throw new IOException("API response did not contain textual assistant content.");
        }

        return choiceNode.asText().trim();
    }

    /**
     * Extracts JSON tool invocation block if present in the LLM response.
     */
    public static String extractToolActionJson(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        Matcher matcher = JSON_TOOL_PATTERN.matcher(response);
        if (matcher.find()) {
            String candidate = matcher.group(1).trim();
            try {
                JsonNode parsed = MAPPER.readTree(candidate);
                String tool = parsed.has("tool") ? parsed.get("tool").asText() :
                             (parsed.has("action") ? parsed.get("action").asText() : null);
                if (tool != null && ToolManager.hasTool(tool)) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private String handleApiError(Exception e) {
        if (e instanceof ConnectException) {
            return "Cannot connect to FreeLLMAPI at " + DEXConfig.getBaseUrl() + ".\n"
                    + "Please ensure FreeLLMAPI is running locally on port 31415.\n"
                    + "Local tools (apps, time, diagnostics, file operations) remain fully operational!";
        } else if (e instanceof HttpTimeoutException) {
            return "FreeLLMAPI request timed out (limit: 60s). The model took too long to generate a response.";
        } else {
            return "AI Communication Error: " + e.getMessage();
        }
    }

    /**
     * Interactive console chat loop for CLI usage.
     */
    public void runConsole() {
        System.out.println("\nReady for commands or questions. Type 'exit' to quit.\n");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("You: ");
                if (!scanner.hasNextLine()) {
                    break;
                }

                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    continue;
                }

                if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                    break;
                }

                if (input.toLowerCase(Locale.ROOT).startsWith("set-key ")) {
                    String newKey = input.substring(8).trim();
                    DEXConfig.setRuntimeApiKey(newKey);
                    System.out.println("DEX: API key updated for this session.\n");
                    continue;
                }

                if (input.equalsIgnoreCase("reset memory")) {
                    System.out.print("Are you sure you want to completely erase persistent memory.json? (yes/no): ");
                    if (scanner.hasNextLine()) {
                        String confirm = scanner.nextLine().trim();
                        if (confirm.equalsIgnoreCase("yes") || confirm.equalsIgnoreCase("y")) {
                            memoryManager.resetMemory(true);
                            System.out.println("DEX: Memory file reset.\n");
                        } else {
                            System.out.println("DEX: Memory reset cancelled.\n");
                        }
                    }
                    continue;
                }

                String reply = processMessage(input);
                System.out.println("\nDEX: " + reply + "\n");
            }
        } catch (Exception e) {
            System.err.println("Console session ended: " + e.getMessage());
        }
    }
}