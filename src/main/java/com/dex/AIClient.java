package com.dex;

import com.dex.ai.*;
import com.dex.config.DEXConfig;
import com.dex.memory.MemoryManager;
import com.dex.security.SecretStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Client and Conversational Agent for DEX.
 * Supports multi-provider routing (Google Gemini, OpenAI GPT, Anthropic Claude, Groq,
 * OpenRouter, FreeLLMAPI, Ollama, Custom), iterative tool-calling loop,
 * vision analysis, persistent memory, and automatic fallback.
 */
public class AIClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
     * Builds the system instructions injected into AI requests.
     */
    public String getSystemPrompt() {
        return "You are DEX, an advanced, personal AI assistant for Windows 10 and 11 inspired by JARVIS.\n"
                + "User: Address the user respectfully (e.g. Sir or Muzain).\n"
                + "Tone: Direct, proactive, highly capable, professional, and security-conscious.\n\n"
                + "Capabilities & Available Tools:\n"
                + ToolManager.getToolsDescription() + "\n"
                + "Rules for Tool Execution:\n"
                + "1. When the user requests an action (opening applications, browser actions, workspace files, screenshots, diagnostics, commands), "
                + "invoke the appropriate tool using this exact JSON block:\n"
                + "```json\n"
                + "{\"tool\": \"<tool_name>\", \"argument\": \"<argument_value>\"}\n"
                + "```\n"
                + "2. Never claim an action succeeded unless you have received the verified execution result.\n"
                + "3. Never run destructive shell commands without user confirmation.\n"
                + "4. Never reveal API keys or secret credentials."
                + memoryManager.getFactsPromptSummary();
    }

    public String chat(String userInput) {
        return processMessage(userInput);
    }

    public String processMessage(String userInput) {
        return processMessage(userInput, null, null);
    }

    /**
     * Central message processing pipeline with optional vision image support.
     */
    public String processMessage(String userInput, String base64Image, String imageMimeType) {
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

        if (input.equalsIgnoreCase("providers") || input.equalsIgnoreCase("list providers")) {
            return listProvidersSummary();
        }

        // 2. Local deterministic task interception
        if (base64Image == null) {
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
        }

        // 3. Check AI Provider availability
        AIProvider activeProvider = ProviderManager.getActiveProvider();
        if (activeProvider == null || !activeProvider.isConfigured()) {
            // Check if DEXConfig has a runtime key that can be synced
            if (DEXConfig.hasApiKey()) {
                activeProvider.setApiKey(DEXConfig.getApiKey());
            } else {
                return "DEX AI Chat is currently offline because the API key is not configured for "
                        + (activeProvider != null ? activeProvider.getType().getDisplayName() : "the active provider") + ".\n"
                        + "Local Windows tools (apps, system info, time, workspace files) are active and fully operational!\n\n"
                        + "Supported Providers: Google Gemini, OpenAI GPT, Anthropic Claude, Groq, OpenRouter, FreeLLMAPI, Ollama.\n"
                        + "To configure a key, use the Desktop Application Settings or in PowerShell:\n"
                        + "  $env:DEX_API_KEY = \"your_key\" (or GEMINI_API_KEY, OPENAI_API_KEY, GROQ_API_KEY)\n"
                        + "Or type 'set-key <key>' in this console.";
            }
        }

        if (DEXConfig.isOfflineMode()) {
            return "DEX is currently running in offline local-only mode. Use local tools or restart to connect to online providers.";
        }

        // Check vision requirements
        if (base64Image != null && !activeProvider.isVisionSupported()) {
            return "Notice: The currently active provider (" + activeProvider.getType().getDisplayName()
                    + ") does not support image analysis. Please switch to Google Gemini, OpenAI GPT-4o, or Anthropic Claude in Settings.";
        }

        // 4. Send to AI Provider with iterative tool calling loop
        memoryManager.addMessage("user", input);

        try {
            String aiResponse = executeChatWithToolLoop(input, base64Image, imageMimeType);
            memoryManager.addMessage("assistant", aiResponse);
            return aiResponse;
        } catch (Exception e) {
            memoryManager.removeLastMessage();
            return handleApiError(e);
        }
    }

    /**
     * Sends conversation to the active provider and handles up to 3 tool execution iterations.
     */
    private String executeChatWithToolLoop(String latestInput, String base64Image, String imageMimeType) throws Exception {
        int maxToolIterations = 3;
        List<MemoryManager.ChatMessage> sessionContext = new ArrayList<>(
                memoryManager.getTrimmedHistory(14, 7000)
        );

        AIRequest.Builder reqBuilder = AIRequest.newBuilder()
                .messages(sessionContext)
                .systemPrompt(getSystemPrompt())
                .temperature(0.7);

        if (base64Image != null && !base64Image.isBlank()) {
            reqBuilder.image(base64Image, imageMimeType);
        }

        AIResponse response = ProviderManager.executeInference(reqBuilder.build());
        String currentAssistantReply = response.getContent();

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
                    "[System Tool Execution Result]:\n" + executionResult + "\nPlease provide the final answer or next step."));

            AIRequest followupRequest = AIRequest.newBuilder()
                    .messages(sessionContext)
                    .systemPrompt(getSystemPrompt())
                    .temperature(0.7)
                    .build();

            response = ProviderManager.executeInference(followupRequest);
            currentAssistantReply = response.getContent();
        }

        return currentAssistantReply;
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
        AIProvider p = ProviderManager.getActiveProvider();
        String pName = p != null ? p.getType().getDisplayName() : "AI Provider";

        if (e instanceof ConnectException) {
            return "Cannot connect to " + pName + ".\n"
                    + "Please ensure network connectivity and endpoint availability.\n"
                    + "Local Windows tools (apps, time, diagnostics, file operations) remain fully operational!";
        } else if (e instanceof HttpTimeoutException) {
            return pName + " request timed out (limit: 60s). The model took too long to generate a response.";
        } else {
            return "AI Communication Error (" + pName + "): " + e.getMessage();
        }
    }

    public static String listProvidersSummary() {
        StringBuilder sb = new StringBuilder("=== DEX Supported AI Providers ===\n");
        AIProviderType active = ProviderManager.getActiveProviderType();

        for (AIProvider p : ProviderManager.getAllProviders()) {
            boolean isActive = p.getType() == active;
            String status = p.isConfigured() ? "CONFIGURED (" + SecretStorage.getMaskedSecret(p.getType().name().toLowerCase() + "_key") + ")" : "KEY REQUIRED";
            if (p.getType() == AIProviderType.OLLAMA) status = "LOCAL SERVER";

            sb.append(isActive ? "► " : "  ")
                    .append(p.getType().getDisplayName())
                    .append(" [").append(p.getModel()).append("] - ")
                    .append(status)
                    .append(isActive ? " (ACTIVE)" : "")
                    .append("\n");
        }
        sb.append("\nTo switch active provider: type 'use provider <name>' (e.g. 'use provider gemini')");
        return sb.toString();
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
                    AIProvider active = ProviderManager.getActiveProvider();
                    if (active != null) {
                        ProviderManager.configureProvider(active.getType(), null, null, newKey);
                        System.out.println("DEX: API key updated securely for " + active.getType().getDisplayName() + ".\n");
                    }
                    continue;
                }

                if (input.toLowerCase(Locale.ROOT).startsWith("use provider ")) {
                    String pName = input.substring(13).trim();
                    AIProviderType t = AIProviderType.fromString(pName);
                    ProviderManager.setActiveProviderType(t);
                    System.out.println("DEX: Active AI provider switched to " + t.getDisplayName() + ".\n");
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