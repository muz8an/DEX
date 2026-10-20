package com.dex.server;

import com.dex.AIClient;
import com.dex.TaskManager;
import com.dex.ToolManager;
import com.dex.config.DEXConfig;
import com.dex.tools.SystemTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Embedded lightweight HTTP Web Dashboard and Remote Access Server for DEX.
 * Built entirely with Java's standard library HttpServer with zero external framework dependencies.
 * Provides localhost web UI and authenticated phone-to-laptop remote control.
 */
public class DEXWebServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_REQUEST_BODY_BYTES = 1024 * 1024; // 1 MB limit

    private final AIClient aiClient;
    private final String host;
    private final int port;
    private HttpServer server;

    public DEXWebServer(AIClient aiClient) {
        this(aiClient, DEXConfig.getWebHost(), DEXConfig.getWebPort());
    }

    public DEXWebServer(AIClient aiClient, String host, int port) {
        this.aiClient = aiClient;
        this.host = host;
        this.port = port;
    }

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        // Dashboard UI handler
        server.createContext("/", new DashboardHandler());

        // REST API Endpoints
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/tools", new ToolsHandler());
        server.createContext("/api/tasks", new TasksHandler());
        server.createContext("/api/chat", new ChatHandler(aiClient));
        server.createContext("/api/clear", new ClearChatHandler(aiClient));
        server.createContext("/api/screenshot", new ScreenshotHandler());
        server.createContext("/api/screenshots", new ScreenshotFileHandler());

        server.start();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public int getPort() {
        return server != null ? server.getAddress().getPort() : port;
    }

    public String getHost() {
        return host;
    }

    /**
     * Checks if the request is authenticated.
     * Localhost connections (127.0.0.1, [::1]) are permitted by default for seamless desktop use.
     * Remote connections require valid Bearer token matching DEXConfig.getRemoteAuthToken().
     */
    public static boolean isAuthorized(HttpExchange exchange) {
        String remoteAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
        boolean isLocal = "127.0.0.1".equals(remoteAddress)
                || "0:0:0:0:0:0:0:1".equals(remoteAddress)
                || "::1".equals(remoteAddress);

        if (isLocal) {
            return true;
        }

        // Check Authorization header: "Bearer <token>"
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (DEXConfig.validateRemoteToken(token)) {
                return true;
            }
        }

        // Check query parameter: "?token=<token>"
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && "token".equalsIgnoreCase(pair[0])) {
                    if (DEXConfig.validateRemoteToken(pair[1])) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        int total = 0;
        while ((read = is.read(buffer)) != -1) {
            total += read;
            if (total > MAX_REQUEST_BODY_BYTES) {
                throw new IOException("Request body exceeds maximum size limit (1 MB).");
            }
            baos.write(buffer, 0, read);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    // --- HTTP HANDLERS ---

    private static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }

            ObjectNode status = MAPPER.createObjectNode();
            status.put("assistant", "DEX");
            status.put("status", "ONLINE");
            status.put("hasApiKey", DEXConfig.hasApiKey());
            status.put("isOfflineMode", DEXConfig.isOfflineMode());
            status.put("model", DEXConfig.getModel());
            status.put("baseUrl", DEXConfig.getBaseUrl());
            status.put("workspace", DEXConfig.getWorkspaceRoot().toString());
            status.put("toolsCount", ToolManager.getAllTools().size());
            status.put("remoteAuthEnabled", true);

            sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(status));
        }
    }

    private static class ToolsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }

            ArrayNode array = MAPPER.createArrayNode();
            for (ToolManager.ToolDefinition tool : ToolManager.getAllTools()) {
                ObjectNode node = array.addObject();
                node.put("name", tool.getName());
                node.put("description", tool.getDescription());
                node.put("parameters", tool.getParameterDescription());
                node.put("sensitive", tool.isSensitive());
            }

            sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(array));
        }
    }

    private static class TasksHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }

            ArrayNode array = MAPPER.createArrayNode();
            for (TaskManager.TaskRecord rec : TaskManager.getHistory()) {
                ObjectNode rNode = array.addObject();
                rNode.put("taskId", rec.getTaskId());
                rNode.put("query", rec.getOriginalQuery());
                rNode.put("timestamp", rec.getTimestamp());
                ArrayNode stepsNode = rNode.putArray("steps");
                for (TaskManager.TaskStep s : rec.getSteps()) {
                    ObjectNode sNode = stepsNode.addObject();
                    sNode.put("stepNumber", s.getStepNumber());
                    sNode.put("description", s.getDescription());
                    sNode.put("status", s.getStatus().name());
                    sNode.put("result", s.getResult());
                }
            }

            sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(array));
        }
    }

    private static class ChatHandler implements HttpHandler {
        private final AIClient aiClient;

        public ChatHandler(AIClient aiClient) {
            this.aiClient = aiClient;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Method not allowed. Use POST.\"}");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized. Provide Bearer token or ?token= parameter.\"}");
                return;
            }

            try {
                String body = readRequestBody(exchange);
                JsonNode json = MAPPER.readTree(body);
                String userMessage = json.path("message").asText("").trim();

                if (userMessage.isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"error\": \"Message field is required.\"}");
                    return;
                }

                String reply = aiClient.processMessage(userMessage);

                ObjectNode resp = MAPPER.createObjectNode();
                resp.put("reply", reply);
                resp.put("timestamp", System.currentTimeMillis());

                sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));

            } catch (Exception e) {
                ObjectNode err = MAPPER.createObjectNode();
                err.put("error", "Failed to process chat: " + e.getMessage());
                sendJsonResponse(exchange, 500, MAPPER.writeValueAsString(err));
            }
        }
    }

    private static class ClearChatHandler implements HttpHandler {
        private final AIClient aiClient;

        public ClearChatHandler(AIClient aiClient) {
            this.aiClient = aiClient;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized.\"}");
                return;
            }

            aiClient.getMemoryManager().clearChat();
            ObjectNode resp = MAPPER.createObjectNode();
            resp.put("status", "cleared");
            resp.put("message", "Chat session cleared. Persistent memory preserved.");
            sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
        }
    }

    private static class ScreenshotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized.\"}");
                return;
            }

            String result = SystemTools.takeScreenshot("");
            ObjectNode resp = MAPPER.createObjectNode();
            resp.put("result", result);

            if (result.contains("Saved to: ")) {
                int start = result.indexOf("Saved to: ") + 10;
                int end = result.indexOf(" (", start);
                if (end > start) {
                    String fullPath = result.substring(start, end).trim();
                    File file = new File(fullPath);
                    resp.put("filename", file.getName());
                    resp.put("url", "/api/screenshots/" + file.getName());
                }
            }

            sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
        }
    }

    private static class ScreenshotFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized.\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String prefix = "/api/screenshots/";
            if (!path.startsWith(prefix)) {
                sendJsonResponse(exchange, 404, "{\"error\": \"Not found.\"}");
                return;
            }

            String filename = path.substring(prefix.length());
            // Sanitize filename to prevent directory traversal
            if (!filename.matches("^[a-zA-Z0-9_-]+\\.png$")) {
                sendJsonResponse(exchange, 400, "{\"error\": \"Invalid screenshot filename.\"}");
                return;
            }

            Path file = DEXConfig.getScreenshotsDirectory().resolve(filename);
            if (!Files.exists(file)) {
                sendJsonResponse(exchange, 404, "{\"error\": \"Screenshot file not found.\"}");
                return;
            }

            byte[] bytes = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"/".equals(exchange.getRequestURI().getPath())) {
                sendJsonResponse(exchange, 404, "{\"error\": \"Not found.\"}");
                return;
            }

            byte[] htmlBytes = DASHBOARD_HTML.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, htmlBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(htmlBytes);
            }
        }
    }

    // --- EMBEDDED MODERN GLASSMORPHISM DASHBOARD HTML ---
    private static final String DASHBOARD_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>DEX — Personal AI Assistant for Windows</title>
    <style>
        :root {
            --bg-base: #0a0e17;
            --bg-card: rgba(18, 24, 38, 0.85);
            --bg-card-hover: rgba(28, 36, 56, 0.95);
            --accent-cyan: #00e5ff;
            --accent-purple: #7c4dff;
            --accent-green: #00e676;
            --accent-orange: #ff9100;
            --accent-red: #ff5252;
            --text-main: #f0f4fc;
            --text-muted: #8c9ba5;
            --border-glow: rgba(0, 229, 255, 0.15);
            --border-subtle: rgba(255, 255, 255, 0.08);
            --font-stack: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            background-color: var(--bg-base);
            background-image: radial-gradient(circle at 10% 20%, rgba(124, 77, 255, 0.1) 0%, transparent 40%),
                              radial-gradient(circle at 90% 80%, rgba(0, 229, 255, 0.08) 0%, transparent 40%);
            color: var(--text-main);
            font-family: var(--font-stack);
            display: flex;
            flex-direction: column;
            height: 100vh;
            overflow: hidden;
        }

        /* Top Header */
        header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 12px 24px;
            background: rgba(10, 14, 23, 0.8);
            backdrop-filter: blur(10px);
            border-bottom: 1px solid var(--border-subtle);
            z-index: 10;
        }

        .brand {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .brand-logo {
            width: 32px;
            height: 32px;
            border-radius: 8px;
            background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple));
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 800;
            font-size: 16px;
            color: #000;
            box-shadow: 0 0 16px rgba(0, 229, 255, 0.4);
        }

        .brand-name {
            font-size: 18px;
            font-weight: 700;
            letter-spacing: 0.5px;
            background: linear-gradient(90deg, #fff, var(--accent-cyan));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }

        .status-indicators {
            display: flex;
            align-items: center;
            gap: 16px;
        }

        .status-pill {
            display: flex;
            align-items: center;
            gap: 6px;
            padding: 4px 12px;
            background: rgba(0, 230, 118, 0.1);
            border: 1px solid rgba(0, 230, 118, 0.3);
            border-radius: 20px;
            font-size: 12px;
            font-weight: 600;
            color: var(--accent-green);
        }

        .status-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: var(--accent-green);
            box-shadow: 0 0 8px var(--accent-green);
            animation: pulse 2s infinite;
        }

        @keyframes pulse {
            0%, 100% { opacity: 1; transform: scale(1); }
            50% { opacity: 0.5; transform: scale(0.9); }
        }

        .model-pill {
            font-size: 12px;
            color: var(--text-muted);
            background: rgba(255, 255, 255, 0.05);
            padding: 4px 10px;
            border-radius: 12px;
            border: 1px solid var(--border-subtle);
        }

        /* Layout */
        .container {
            display: flex;
            flex: 1;
            overflow: hidden;
        }

        /* Sidebar */
        aside {
            width: 320px;
            background: var(--bg-card);
            backdrop-filter: blur(12px);
            border-right: 1px solid var(--border-subtle);
            display: flex;
            flex-direction: column;
            overflow-y: auto;
            padding: 16px;
            gap: 20px;
        }

        .panel-section h3 {
            font-size: 13px;
            text-transform: uppercase;
            letter-spacing: 1px;
            color: var(--text-muted);
            margin-bottom: 10px;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        .quick-actions {
            display: flex;
            flex-direction: column;
            gap: 6px;
        }

        .action-chip {
            background: rgba(255, 255, 255, 0.03);
            border: 1px solid var(--border-subtle);
            border-radius: 8px;
            padding: 8px 12px;
            font-size: 13px;
            color: var(--text-main);
            text-align: left;
            cursor: pointer;
            transition: all 0.2s ease;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .action-chip:hover {
            background: rgba(0, 229, 255, 0.08);
            border-color: var(--border-glow);
            color: var(--accent-cyan);
            transform: translateX(4px);
        }

        .tool-list {
            display: flex;
            flex-direction: column;
            gap: 6px;
            max-height: 220px;
            overflow-y: auto;
        }

        .tool-item {
            font-size: 12px;
            padding: 6px 10px;
            background: rgba(0, 0, 0, 0.2);
            border-radius: 6px;
            border-left: 2px solid var(--accent-purple);
        }

        .tool-item-name {
            font-weight: 600;
            color: #fff;
        }

        .tool-item-desc {
            color: var(--text-muted);
            font-size: 11px;
            margin-top: 2px;
        }

        .task-list {
            display: flex;
            flex-direction: column;
            gap: 8px;
            font-size: 12px;
        }

        .task-item {
            padding: 8px;
            background: rgba(0, 0, 0, 0.25);
            border-radius: 6px;
            border: 1px solid var(--border-subtle);
        }

        .task-badge {
            display: inline-block;
            font-size: 10px;
            font-weight: 700;
            padding: 2px 6px;
            border-radius: 4px;
            background: rgba(0, 230, 118, 0.2);
            color: var(--accent-green);
            margin-bottom: 4px;
        }

        /* Chat Area */
        main {
            flex: 1;
            display: flex;
            flex-direction: column;
            background: transparent;
            position: relative;
        }

        #chatHistory {
            flex: 1;
            overflow-y: auto;
            padding: 24px;
            display: flex;
            flex-direction: column;
            gap: 16px;
        }

        .message {
            max-width: 80%;
            display: flex;
            flex-direction: column;
            gap: 4px;
            animation: fadeIn 0.2s ease-out;
        }

        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(8px); }
            to { opacity: 1; transform: translateY(0); }
        }

        .message.user {
            align-self: flex-end;
        }

        .message.assistant {
            align-self: flex-start;
        }

        .bubble {
            padding: 12px 16px;
            border-radius: 14px;
            font-size: 14px;
            line-height: 1.5;
            white-space: pre-wrap;
            word-break: break-word;
        }

        .user .bubble {
            background: linear-gradient(135deg, rgba(0, 229, 255, 0.2), rgba(124, 77, 255, 0.3));
            border: 1px solid rgba(0, 229, 255, 0.3);
            color: #fff;
            border-bottom-right-radius: 2px;
        }

        .assistant .bubble {
            background: var(--bg-card);
            border: 1px solid var(--border-subtle);
            color: var(--text-main);
            border-bottom-left-radius: 2px;
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
        }

        .message-time {
            font-size: 11px;
            color: var(--text-muted);
            padding: 0 4px;
        }

        .user .message-time {
            align-self: flex-end;
        }

        /* Chat Input Bar */
        .input-bar {
            padding: 16px 24px;
            background: rgba(10, 14, 23, 0.9);
            border-top: 1px solid var(--border-subtle);
            display: flex;
            gap: 12px;
            align-items: center;
        }

        .input-bar input {
            flex: 1;
            background: rgba(255, 255, 255, 0.05);
            border: 1px solid var(--border-subtle);
            border-radius: 10px;
            padding: 14px 16px;
            color: #fff;
            font-size: 14px;
            font-family: inherit;
            outline: none;
            transition: all 0.2s ease;
        }

        .input-bar input:focus {
            border-color: var(--accent-cyan);
            box-shadow: 0 0 12px rgba(0, 229, 255, 0.2);
            background: rgba(255, 255, 255, 0.08);
        }

        .btn {
            background: rgba(255, 255, 255, 0.06);
            border: 1px solid var(--border-subtle);
            border-radius: 10px;
            color: var(--text-main);
            padding: 12px 18px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s ease;
            display: flex;
            align-items: center;
            gap: 6px;
        }

        .btn:hover {
            background: rgba(255, 255, 255, 0.12);
            border-color: rgba(255, 255, 255, 0.2);
        }

        .btn-primary {
            background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple));
            color: #000;
            font-weight: 700;
            border: none;
            box-shadow: 0 0 14px rgba(0, 229, 255, 0.3);
        }

        .btn-primary:hover {
            box-shadow: 0 0 20px rgba(0, 229, 255, 0.5);
            transform: translateY(-1px);
        }

        .spinner {
            display: inline-block;
            width: 14px;
            height: 14px;
            border: 2px solid rgba(255, 255, 255, 0.3);
            border-radius: 50%;
            border-top-color: #fff;
            animation: spin 0.8s linear infinite;
        }

        @keyframes spin {
            to { transform: rotate(360deg); }
        }
    </style>
</head>
<body>

    <header>
        <div class="brand">
            <div class="brand-logo">D</div>
            <div class="brand-name">DEX AI ASSISTANT</div>
        </div>
        <div class="status-indicators">
            <div class="status-pill" id="statusPill">
                <div class="status-dot"></div>
                <span id="statusText">SYSTEM ONLINE</span>
            </div>
            <div class="model-pill" id="modelPill">Model: auto</div>
        </div>
    </header>

    <div class="container">
        <!-- Sidebar -->
        <aside>
            <div class="panel-section">
                <h3>Quick Actions</h3>
                <div class="quick-actions">
                    <button class="action-chip" onclick="sendQuick('Open Calculator')">🧮 Open Calculator</button>
                    <button class="action-chip" onclick="sendQuick('What time is it?')">⏰ Current Time</button>
                    <button class="action-chip" onclick="sendQuick('Show system info')">💻 System Specs</button>
                    <button class="action-chip" onclick="sendQuick('Take a screenshot')">📸 Screenshot</button>
                    <button class="action-chip" onclick="sendQuick('Open VS Code')">📝 Open VS Code</button>
                    <button class="action-chip" onclick="sendQuick('List files')">📁 Workspace Files</button>
                    <button class="action-chip" onclick="sendQuick('Open Notepad and tell me the current time')">⚡ Multi-Step Demo</button>
                </div>
            </div>

            <div class="panel-section">
                <h3>Registered Capabilities (<span id="toolsCount">0</span>)</h3>
                <div class="tool-list" id="toolList">Loading tools...</div>
            </div>

            <div class="panel-section">
                <h3>Recent Tasks</h3>
                <div class="task-list" id="taskList">No recent tasks.</div>
            </div>
        </aside>

        <!-- Main Chat Area -->
        <main>
            <div id="chatHistory">
                <div class="message assistant">
                    <div class="bubble">Hello! I am DEX, your personal AI assistant running locally on Windows 11.
I can launch applications, manage workspace files, capture screenshots, run builds, and assist you with programming and system tasks.

How can I help you today?</div>
                    <div class="message-time">Just now</div>
                </div>
            </div>

            <div class="input-bar">
                <input type="text" id="userInput" placeholder="Ask DEX or give a command (e.g., 'Open Calculator', 'What time is it?')..." autocomplete="off" />
                <button class="btn" id="clearBtn" title="Clear chat session" onclick="clearChat()">Clear</button>
                <button class="btn btn-primary" id="sendBtn" onclick="sendMessage()">Send</button>
            </div>
        </main>
    </div>

    <script>
        const chatHistory = document.getElementById('chatHistory');
        const userInput = document.getElementById('userInput');
        const sendBtn = document.getElementById('sendBtn');
        const statusText = document.getElementById('statusText');
        const modelPill = document.getElementById('modelPill');
        const toolList = document.getElementById('toolList');
        const toolsCount = document.getElementById('toolsCount');
        const taskList = document.getElementById('taskList');

        userInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') sendMessage();
        });

        async function fetchStatus() {
            try {
                const res = await fetch('/api/status');
                const data = await res.json();
                modelPill.innerText = 'Model: ' + data.model;
                if (!data.hasApiKey) {
                    statusText.innerText = 'LOCAL TOOLS ACTIVE';
                } else {
                    statusText.innerText = 'AI ONLINE';
                }
                toolsCount.innerText = data.toolsCount;
            } catch (e) {
                statusText.innerText = 'DISCONNECTED';
            }
        }

        async function fetchTools() {
            try {
                const res = await fetch('/api/tools');
                const tools = await res.json();
                toolList.innerHTML = '';
                tools.forEach(t => {
                    const item = document.createElement('div');
                    item.className = 'tool-item';
                    item.innerHTML = `<div class="tool-item-name">${t.name}</div><div class="tool-item-desc">${t.description}</div>`;
                    toolList.appendChild(item);
                });
            } catch (e) {
                toolList.innerText = 'Could not load tools.';
            }
        }

        async function fetchTasks() {
            try {
                const res = await fetch('/api/tasks');
                const tasks = await res.json();
                if (tasks.length === 0) return;
                taskList.innerHTML = '';
                tasks.slice(-5).reverse().forEach(task => {
                    const div = document.createElement('div');
                    div.className = 'task-item';
                    div.innerHTML = `<div class="task-badge">${task.taskId}</div><div>${task.query}</div>`;
                    taskList.appendChild(div);
                });
            } catch (e) {}
        }

        function appendMessage(role, text) {
            const msgDiv = document.createElement('div');
            msgDiv.className = 'message ' + role;

            const bubble = document.createElement('div');
            bubble.className = 'bubble';
            bubble.innerText = text;

            const time = document.createElement('div');
            time.className = 'message-time';
            const now = new Date();
            time.innerText = now.getHours().toString().padStart(2, '0') + ':' + now.getMinutes().toString().padStart(2, '0');

            msgDiv.appendChild(bubble);
            msgDiv.appendChild(time);
            chatHistory.appendChild(msgDiv);
            chatHistory.scrollTop = chatHistory.scrollHeight;
        }

        async function sendMessage() {
            const text = userInput.value.trim();
            if (!text) return;

            appendMessage('user', text);
            userInput.value = '';
            userInput.disabled = true;
            sendBtn.innerHTML = '<span class="spinner"></span>';
            sendBtn.disabled = true;

            try {
                const res = await fetch('/api/chat', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ message: text })
                });
                const data = await res.json();
                if (data.reply) {
                    appendMessage('assistant', data.reply);
                } else if (data.error) {
                    appendMessage('assistant', 'Error: ' + data.error);
                }
            } catch (e) {
                appendMessage('assistant', 'Network Error: Failed to communicate with DEX server.');
            } finally {
                userInput.disabled = false;
                sendBtn.innerHTML = 'Send';
                sendBtn.disabled = false;
                userInput.focus();
                fetchTasks();
            }
        }

        function sendQuick(text) {
            userInput.value = text;
            sendMessage();
        }

        async function clearChat() {
            try {
                await fetch('/api/clear', { method: 'POST' });
                chatHistory.innerHTML = '';
                appendMessage('assistant', 'Current conversation cleared. Persistent memory file remains unchanged.');
            } catch (e) {
                alert('Failed to clear chat.');
            }
        }

        fetchStatus();
        fetchTools();
        fetchTasks();
        setInterval(fetchTasks, 5000);
    </script>
</body>
</html>
""";
}
