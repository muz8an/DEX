package com.dex;

import com.dex.ai.*;
import com.dex.config.DEXConfig;
import com.dex.memory.MemoryManager;
import com.dex.profile.UserProfileManager;
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
        String userName = UserProfileManager.getUserName();
        boolean hasName = UserProfileManager.hasCustomName();

        return "You are X, an advanced, personal AI assistant for Windows 10 and 11 inspired by JARVIS.\n"
                + "Identity: Your name is X. Always introduce yourself as X. When asked 'What is your name?', you must always respond: 'My name is X.' Never claim your name is DEX or anything other than X.\n"
                + (hasName
                    ? "User: The user's name is " + userName + ". When asked 'What is my name?', respond: 'Your name is " + userName + ".' Address the user by their name (" + userName + ") or respectfully.\n"
                    : "User: The user has not provided a name yet. When asked 'What is my name?', explain that you do not know their name yet. Address the user respectfully (e.g. Sir or Friend).\n")
                + "Tone: Direct, proactive, highly capable, professional, and security-conscious.\n\n"
                + "Strict Rules & Limitations:\n"
                + "1. Image Generation: You CANNOT generate, create, draw, or render images. Image generation is disabled and not supported in DEX. Never claim you can generate images. You CAN capture desktop screenshots and analyze images if vision is supported.\n"
                + "2. Available Windows Automation Tools:\n"
                + ToolManager.getToolsDescription() + "\n"
                + "3. Multi-Step & Coding Tasks Instructions:\n"
                + "   - If the user asks to write a program, script, or code and open it in VS Code (e.g. 'open vs code in a new window and write a program to add two numbers', 'write code in vs code'):\n"
                + "     Preferred method: Invoke `save_and_open_code` (format: 'filename:::code_content') to save the code to the workspace AND open it inside Visual Studio Code in a new window immediately!\n"
                + "     Alternatively:\n"
                + "     Step 1: Save the code using `save_code` (format: 'filename:::code_content').\n"
                + "     Step 2: Open VS Code with that file using `open_app` with argument 'vscode -n <filename>' (e.g. 'vscode -n add_numbers.py').\n"
                + "     CRITICAL: Always make sure the code is saved AND opened inside VS Code so the user sees their code active in the editor!\n"
                + "   - To open a new window in VS Code without a file, invoke `open_app` with argument 'vscode -n'.\n"
                + "   - When executing an action, ALWAYS invoke the appropriate tool using this exact JSON block:\n"
                + "```json\n"
                + "{\"tool\": \"<tool_name>\", \"argument\": \"<argument_value>\"}\n"
                + "```\n"
                + "4. Application & Game Launching: To open ANY application or game (e.g. WhatsApp, Steam, Spotify, Games, VS Code, Discord, etc.), invoke the `open_app` tool.\n"
                + "5. Music & Video Playback: To play a song, track, or music (e.g. 'play arz kiya hai', 'play any music', 'play believer on spotify', 'open youtube and play ...'), invoke the `play_music` tool with the song query.\n"
                + "6. Never claim an action succeeded unless you have executed the tool and received the verified execution result.\n"
                + "7. Never run destructive shell commands without user confirmation.\n"
                + "8. Never reveal API keys or secret credentials."
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

        // 2. Identity and Personalization Interceptions (Always active)
        if (isAskingAssistantName(input)) {
            String resp = "My name is X.";
            memoryManager.addMessage("user", input);
            memoryManager.addMessage("assistant", resp);
            return resp;
        }

        if (isAskingWhoAreYou(input)) {
            String resp = "My name is X, your personal AI assistant.";
            memoryManager.addMessage("user", input);
            memoryManager.addMessage("assistant", resp);
            return resp;
        }

        if (isAskingUserName(input)) {
            String resp;
            if (UserProfileManager.hasCustomName()) {
                resp = "Your name is " + UserProfileManager.getUserName() + ".";
            } else {
                resp = "I don't know your name yet, as you haven't mentioned it. If you tell me what it is or set it in your profile, I will remember it!";
            }
            memoryManager.addMessage("user", input);
            memoryManager.addMessage("assistant", resp);
            return resp;
        }

        String introName = parseUserIntroduction(input);
        if (introName != null) {
            UserProfileManager.setUserName(introName);
            String resp = "It's nice to meet you, " + introName + "! I'll remember that. How can I help you today?";
            memoryManager.addMessage("user", input);
            memoryManager.addMessage("assistant", resp);
            return resp;
        }

        // 3. Image Generation Requests - Explicitly rejected per DEX specifications
        if (isImageGenerationRequest(input)) {
            String resp = "I cannot generate images. Image generation is not supported in DEX, but I can help you with text, code, file analysis, desktop screenshots, and system automation.";
            memoryManager.addMessage("user", input);
            memoryManager.addMessage("assistant", resp);
            return resp;
        }

        // 4. Local deterministic task interception
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

        // 5. Check AI Provider availability
        AIProvider activeProvider = ProviderManager.getActiveProvider();
        if (activeProvider == null) {
            return "X AI Chat is currently offline because no AI provider is configured.\n"
                    + "Local Windows tools (apps, system info, time, workspace files) are active and fully operational!\n\n"
                    + "Supported Providers: Google Gemini, OpenAI GPT, Anthropic Claude, Groq, OpenRouter, FreeLLMAPI, Ollama.\n"
                    + "To configure a key, use the Desktop Application Settings or in PowerShell:\n"
                    + "  $env:DEX_API_KEY = \"your_key\" (or GEMINI_API_KEY, OPENAI_API_KEY, GROQ_API_KEY)\n"
                    + "Or type 'set-key <key>' in this console.";
        }

        if (!activeProvider.isConfigured()) {
            // Check if DEXConfig has a runtime key that can be synced
            if (DEXConfig.hasApiKey()) {
                activeProvider.setApiKey(DEXConfig.getApiKey());
            } else {
                return "X AI Chat is currently offline because the API key is not configured for "
                        + activeProvider.getType().getDisplayName() + ".\n"
                        + "Local Windows tools (apps, system info, time, workspace files) are active and fully operational!\n\n"
                        + "Supported Providers: Google Gemini, OpenAI GPT, Anthropic Claude, Groq, OpenRouter, FreeLLMAPI, Ollama.\n"
                        + "To configure a key, use the Desktop Application Settings or in PowerShell:\n"
                        + "  $env:DEX_API_KEY = \"your_key\" (or GEMINI_API_KEY, OPENAI_API_KEY, GROQ_API_KEY)\n"
                        + "Or type 'set-key <key>' in this console.";
            }
        }

        if (DEXConfig.isOfflineMode()) {
            return "X is currently running in offline local-only mode. Use local tools or restart to connect to online providers.";
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
        int maxToolIterations = 5;
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
        String lastExecutionResult = null;

        for (int iteration = 0; iteration < maxToolIterations; iteration++) {
            String toolActionJson = extractToolActionJson(currentAssistantReply);
            if (toolActionJson == null) {
                break;
            }

            // Execute the proposed tool
            String executionResult = ToolManager.executeJsonAction(toolActionJson);
            lastExecutionResult = executionResult;

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

        return cleanToolJsonFromReply(currentAssistantReply, lastExecutionResult);
    }

    /**
     * Removes raw JSON tool invocation blocks from the final response delivered to the user.
     */
    public static String cleanToolJsonFromReply(String reply, String lastToolResult) {
        if (reply == null || reply.isBlank()) {
            return lastToolResult != null ? "Task executed: " + lastToolResult : "Done!";
        }

        String cleaned = reply.replaceAll("(?s)```(?:json)?\\s*\\{\\s*\"(?:tool|action|name)\"\\s*:[^`]*\\}\\s*```", "")
                .replaceAll("(?s)\\{\\s*\"(?:tool|action|name)\"\\s*:[^}]+\\}", "")
                .trim();

        if (cleaned.isBlank()) {
            return lastToolResult != null ? "Task completed successfully.\n" + lastToolResult : "Action executed successfully.";
        }
        return cleaned;
    }

    /**
     * Extracts JSON tool invocation block if present in the LLM response with brace-counting support.
     */
    public static String extractToolActionJson(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        int idx = 0;
        while (idx < response.length()) {
            int toolIdx = response.indexOf("\"tool\"", idx);
            int actionIdx = response.indexOf("\"action\"", idx);
            int targetIdx = -1;
            if (toolIdx != -1 && actionIdx != -1) {
                targetIdx = Math.min(toolIdx, actionIdx);
            } else if (toolIdx != -1) {
                targetIdx = toolIdx;
            } else {
                targetIdx = actionIdx;
            }

            if (targetIdx == -1) {
                break;
            }

            int startBrace = response.lastIndexOf('{', targetIdx);
            if (startBrace == -1) {
                idx = targetIdx + 6;
                continue;
            }

            String candidate = extractBalancedJsonObject(response, startBrace);
            if (candidate != null) {
                try {
                    JsonNode parsed = MAPPER.readTree(candidate);
                    String tool = parsed.has("tool") ? parsed.get("tool").asText() :
                                 (parsed.has("action") ? parsed.get("action").asText() :
                                 (parsed.has("name") ? parsed.get("name").asText() : null));
                    if (tool != null && ToolManager.hasTool(tool)) {
                        return candidate;
                    }
                } catch (Exception ignored) {
                }
            }
            idx = targetIdx + 6;
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

    public static String extractBalancedJsonObject(String text, int start) {
        if (text == null || start < 0 || start >= text.length() || text.charAt(start) != '{') {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
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
    private boolean isAskingAssistantName(String input) {
        String clean = input.trim().toLowerCase(Locale.ROOT).replaceAll("[\\?\\.!]+$", "").trim();
        return clean.equals("what is your name")
                || clean.equals("what's your name")
                || clean.equals("whats your name")
                || clean.equals("tell me your name")
                || clean.equals("what are you called");
    }

    private boolean isAskingWhoAreYou(String input) {
        String clean = input.trim().toLowerCase(Locale.ROOT).replaceAll("[\\?\\.!]+$", "").trim();
        return clean.equals("who are you")
                || clean.equals("who are u")
                || clean.equals("what is your identity");
    }

    private boolean isAskingUserName(String input) {
        String clean = input.trim().toLowerCase(Locale.ROOT).replaceAll("[\\?\\.!]+$", "").trim();
        return clean.equals("what is my name")
                || clean.equals("what's my name")
                || clean.equals("whats my name")
                || clean.equals("do you know my name")
                || clean.equals("tell me my name")
                || clean.equals("who am i");
    }

    private String parseUserIntroduction(String input) {
        Pattern pattern = Pattern.compile("(?i)^(?:my\\s+name\\s+is|call\\s+me|i\\s+am)\\s+([a-zA-Z0-9_\\-\\s]{2,40})$");
        Matcher m = pattern.matcher(input.trim());
        if (m.matches()) {
            String candidate = m.group(1).trim();
            // Don't mistake queries like "my name is what" or "call me later" for a name
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (!lower.equals("what") && !lower.equals("who") && !lower.startsWith("later") && !lower.startsWith("back")) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isImageGenerationRequest(String input) {
        String lower = input.trim().toLowerCase(Locale.ROOT);
        // Preserve screenshot capture tools
        if (lower.contains("screenshot") || lower.contains("screen capture") || lower.contains("capture screen")) {
            return false;
        }
        return lower.matches(".*\\b(generate|create|draw|make|render|produce)\\b.*\\b(image|picture|photo|artwork|illustration|drawing|portrait|wallpaper)\\b.*")
                || lower.startsWith("image of ")
                || lower.startsWith("picture of ");
    }

    public void runConsole() {
        if (UserProfileManager.isFirstTimeUser()) {
            System.out.println("=======================================================================");
            System.out.println("                             WELCOME TO X");
            System.out.println("=======================================================================");
            System.out.println("What is your name? (Press Enter to skip): ");
        }

        System.out.println("\nReady for commands or questions. Type 'exit' to quit.\n");

        try (Scanner scanner = new Scanner(System.in)) {
            if (UserProfileManager.isFirstTimeUser()) {
                System.out.print("> ");
                if (scanner.hasNextLine()) {
                    String enteredName = scanner.nextLine().trim();
                    UserProfileManager.completeSetup(enteredName);
                    if (!enteredName.isEmpty()) {
                        System.out.println("Nice to meet you, " + enteredName + "!\n");
                    } else {
                        System.out.println("Setup completed.\n");
                    }
                }
            }

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
                        System.out.println("X: API key updated securely for " + active.getType().getDisplayName() + ".\n");
                    }
                    continue;
                }

                if (input.toLowerCase(Locale.ROOT).startsWith("use provider ")) {
                    String pName = input.substring(13).trim();
                    AIProviderType t = AIProviderType.fromString(pName);
                    ProviderManager.setActiveProviderType(t);
                    System.out.println("X: Active AI provider switched to " + t.getDisplayName() + ".\n");
                    continue;
                }

                if (input.equalsIgnoreCase("reset memory")) {
                    System.out.print("Are you sure you want to completely erase persistent memory.json? (yes/no): ");
                    if (scanner.hasNextLine()) {
                        String confirm = scanner.nextLine().trim();
                        if (confirm.equalsIgnoreCase("yes") || confirm.equalsIgnoreCase("y")) {
                            memoryManager.resetMemory(true);
                            System.out.println("X: Memory file reset.\n");
                        } else {
                            System.out.println("X: Memory reset cancelled.\n");
                        }
                    }
                    continue;
                }

                String reply = processMessage(input);
                System.out.println("\nX: " + reply + "\n");
            }
        } catch (Exception e) {
            System.err.println("Console session ended: " + e.getMessage());
        }
    }
}