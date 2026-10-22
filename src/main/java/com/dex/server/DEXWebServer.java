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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
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
        server.createContext("/api/network-info", new NetworkInfoHandler());
        server.createContext("/api/tools", new ToolsHandler());
        server.createContext("/api/tools/run", new ToolRunHandler());
        server.createContext("/api/tasks", new TasksHandler());
        server.createContext("/api/tasks/cancel", new TaskCancelHandler());
        server.createContext("/api/chat", new ChatHandler(aiClient));
        server.createContext("/api/clear", new ClearChatHandler(aiClient));
        server.createContext("/api/screenshot", new ScreenshotHandler());
        server.createContext("/api/screenshots/", new ScreenshotFileHandler());
        server.createContext("/api/set-key", new SetKeyHandler());
        server.createContext("/api/memory", new MemoryHandler(aiClient));
        server.createContext("/api/providers", new ProvidersHandler());
        server.createContext("/api/browser/navigate", new BrowserNavigateHandler());
        server.createContext("/api/browser/search", new BrowserSearchHandler());
        server.createContext("/api/voice/speak", new VoiceSpeakHandler());
        server.createContext("/api/voice/stop", new VoiceStopHandler());
        server.createContext("/api/screen/analyze", new ScreenAnalyzeHandler(aiClient));

        server.start();
        System.out.println("[DEX] Web Dashboard started at http://" + host + ":" + port);
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
        try {
            InetSocketAddress remoteSock = exchange.getRemoteAddress();
            if (remoteSock != null) {
                InetAddress addr = remoteSock.getAddress();
                if (addr != null && (addr.isLoopbackAddress()
                        || "127.0.0.1".equals(addr.getHostAddress())
                        || "::1".equals(addr.getHostAddress())
                        || addr.getHostAddress().startsWith("127."))) {
                    return true;
                }
                String hostStr = remoteSock.getHostString();
                if ("127.0.0.1".equals(hostStr) || "localhost".equalsIgnoreCase(hostStr) || "::1".equals(hostStr)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
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
            status.put("hasApiKey", DEXConfig.hasApiKey() || com.dex.ai.ProviderManager.getActiveProvider().isConfigured());
            status.put("isOfflineMode", DEXConfig.isOfflineMode());
            status.put("activeProvider", com.dex.ai.ProviderManager.getActiveProviderType().getDisplayName());
            status.put("model", com.dex.ai.ProviderManager.getActiveProvider().getModel());
            status.put("baseUrl", com.dex.ai.ProviderManager.getActiveProvider().getBaseUrl());
            status.put("workspace", DEXConfig.getWorkspaceRoot().toString());
            status.put("toolsCount", ToolManager.getAllTools().size());
            status.put("remoteAuthEnabled", true);
            status.put("remoteToken", DEXConfig.getRemoteAuthToken());

            sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(status));
        }
    }

    private static class NetworkInfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }

            ObjectNode root = MAPPER.createObjectNode();
            root.put("host", DEXConfig.getWebHost());
            root.put("port", DEXConfig.getWebPort());
            root.put("remoteToken", DEXConfig.getRemoteAuthToken());

            ArrayNode list = root.putArray("interfaces");
            try {
                Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
                while (ifaces != null && ifaces.hasMoreElements()) {
                    NetworkInterface iface = ifaces.nextElement();
                    if (!iface.isUp() || iface.isLoopback()) continue;
                    Enumeration<InetAddress> addrs = iface.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                            ObjectNode node = list.addObject();
                            node.put("name", iface.getDisplayName());
                            node.put("ip", addr.getHostAddress());
                            node.put("url", "http://" + addr.getHostAddress() + ":" + DEXConfig.getWebPort() + "/?token=" + DEXConfig.getRemoteAuthToken());
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(root));
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

    private static class ToolRunHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized.\"}");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Use POST.\"}");
                return;
            }

            try {
                String body = readRequestBody(exchange);
                JsonNode json = MAPPER.readTree(body);
                String toolName = json.path("name").asText("").trim();
                String args = json.path("args").asText("").trim();

                ToolManager.ToolDefinition tool = ToolManager.getTool(toolName);
                if (tool == null) {
                    sendJsonResponse(exchange, 404, "{\"error\": \"Tool not found.\"}");
                    return;
                }

                if (tool.isSensitive()) {
                    sendJsonResponse(exchange, 403, "{\"error\": \"Sensitive tool requires explicit confirmation.\"}");
                    return;
                }

                String result = ToolManager.executeTool(toolName, args);
                ObjectNode resp = MAPPER.createObjectNode();
                resp.put("tool", toolName);
                resp.put("result", result);
                sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\": \"Execution error: " + e.getMessage() + "\"}");
            }
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

            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized. Valid remote token required.\"}");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Method not allowed. Use POST.\"}");
                return;
            }

            try {
                String body = readRequestBody(exchange);
                JsonNode json = MAPPER.readTree(body);
                String userMessage = json.path("message").asText("").trim();

                if (userMessage.isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"error\": \"Message cannot be empty.\"}");
                    return;
                }

                String reply = aiClient.processMessage(userMessage);

                ObjectNode responseNode = MAPPER.createObjectNode();
                responseNode.put("reply", reply);
                responseNode.put("timestamp", System.currentTimeMillis());

                sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(responseNode));
            } catch (Throwable e) {
                ObjectNode errNode = MAPPER.createObjectNode();
                errNode.put("error", "Failed to process chat: " + e.getMessage());
                sendJsonResponse(exchange, 500, MAPPER.writeValueAsString(errNode));
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
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized.\"}");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Use POST.\"}");
                return;
            }

            aiClient.getMemoryManager().clearMemory();
            ObjectNode resp = MAPPER.createObjectNode();
            resp.put("status", "ok");
            resp.put("message", "Conversation memory cleared.");
            sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
        }
    }

    private static class ScreenshotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized.\"}");
                return;
            }

            String result = SystemTools.takeScreenshot("");
            ObjectNode resp = MAPPER.createObjectNode();
            resp.put("status", "ok");
            resp.put("message", result);

            if (result.contains("Saved to: ")) {
                int start = result.indexOf("Saved to: ") + 10;
                int end = result.indexOf(" (", start);
                if (end > start) {
                    String fullPath = result.substring(start, end).trim();
                    java.io.File file = new java.io.File(fullPath);
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

    private static class SetKeyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized.\"}");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Use POST.\"}");
                return;
            }

            try {
                String body = readRequestBody(exchange);
                JsonNode json = MAPPER.readTree(body);
                String key = json.path("key").asText("").trim();

                if (key.isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"error\": \"API key cannot be empty.\"}");
                    return;
                }

                DEXConfig.setRuntimeApiKey(key);
                ObjectNode resp = MAPPER.createObjectNode();
                resp.put("status", "ok");
                resp.put("message", "API key set for this session. AI chat is now enabled.");
                resp.put("hasApiKey", true);
                sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\": \"Failed to set key: " + e.getMessage() + "\"}");
            }
        }
    }

    private static class MemoryHandler implements HttpHandler {
        private final AIClient aiClient;

        public MemoryHandler(AIClient aiClient) {
            this.aiClient = aiClient;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }

            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized.\"}");
                return;
            }

            ArrayNode array = MAPPER.createArrayNode();
            for (com.dex.memory.MemoryManager.ChatMessage msg : aiClient.getMemoryManager().getAllMessages()) {
                ObjectNode node = array.addObject();
                node.put("role", msg.getRole());
                node.put("content", msg.getContent());
                node.put("timestamp", msg.getTimestamp());
            }
            sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(array));
        }
    }

    private static class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (!"/".equals(path) && !"/index.html".equals(path)) {
                sendJsonResponse(exchange, 404, "{\"error\": \"Not found.\"}");
                return;
            }

            String html = loadDashboardHtml();
            byte[] htmlBytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, htmlBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(htmlBytes);
            }
        }

        private String loadDashboardHtml() {
            // 1. Try class loader resource (packaged jar)
            try (InputStream is = DEXWebServer.class.getResourceAsStream("/index.html")) {
                if (is != null) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
            }

            // 2. Try direct source path on filesystem
            try {
                Path p = Path.of("src", "main", "resources", "index.html");
                if (Files.exists(p)) {
                    return Files.readString(p, StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
            }

            return "<!DOCTYPE html><html><head><title>DEX</title></head><body><h1>DEX Assistant</h1><p>Dashboard resource not found.</p></body></html>";
        }
    }

    private static class TaskCancelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized.\"}");
                return;
            }
            try {
                String body = readRequestBody(exchange);
                JsonNode json = MAPPER.readTree(body);
                String taskId = json.path("taskId").asText("").trim();

                boolean cancelled = false;
                if (!taskId.isEmpty()) {
                    cancelled = TaskManager.cancelTask(taskId);
                } else if (!TaskManager.getHistory().isEmpty()) {
                    TaskManager.TaskRecord last = TaskManager.getHistory().get(TaskManager.getHistory().size() - 1);
                    cancelled = TaskManager.cancelTask(last.getTaskId());
                }

                ObjectNode resp = MAPPER.createObjectNode();
                resp.put("cancelled", cancelled);
                resp.put("message", cancelled ? "Task cancelled successfully." : "Task not found or already completed.");
                sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\": \"" + e.getMessage() + "\"}");
            }
        }
    }

    private static class ProvidersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized.\"}");
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                ObjectNode root = MAPPER.createObjectNode();
                root.put("activeProvider", com.dex.ai.ProviderManager.getActiveProviderType().name());
                root.put("activeDisplayName", com.dex.ai.ProviderManager.getActiveProviderType().getDisplayName());
                root.put("fallbackEnabled", com.dex.ai.ProviderManager.isFallbackEnabled());
                if (com.dex.ai.ProviderManager.getFallbackProviderType() != null) {
                    root.put("fallbackProvider", com.dex.ai.ProviderManager.getFallbackProviderType().name());
                }

                ArrayNode array = root.putArray("providers");
                for (com.dex.ai.AIProvider p : com.dex.ai.ProviderManager.getAllProviders()) {
                    ObjectNode node = array.addObject();
                    node.put("id", p.getType().name());
                    node.put("displayName", p.getType().getDisplayName());
                    node.put("baseUrl", p.getBaseUrl());
                    node.put("model", p.getModel());
                    node.put("configured", p.isConfigured());
                    node.put("visionSupported", p.isVisionSupported());
                    node.put("maskedKey", com.dex.security.SecretStorage.getMaskedSecret(p.getType().name().toLowerCase() + "_key"));
                }
                sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(root));
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    String body = readRequestBody(exchange);
                    JsonNode json = MAPPER.readTree(body);
                    String providerName = json.path("provider").asText("").trim();
                    String baseUrl = json.path("baseUrl").asText("").trim();
                    String model = json.path("model").asText("").trim();
                    String apiKey = json.has("apiKey") ? json.path("apiKey").asText("").trim() : null;

                    com.dex.ai.AIProviderType type = com.dex.ai.AIProviderType.fromString(providerName);
                    com.dex.ai.ProviderManager.configureProvider(type, baseUrl.isEmpty() ? null : baseUrl, model.isEmpty() ? null : model, apiKey);
                    com.dex.ai.ProviderManager.setActiveProviderType(type);

                    ObjectNode resp = MAPPER.createObjectNode();
                    resp.put("status", "ok");
                    resp.put("activeProvider", type.getDisplayName());
                    sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
                } catch (Exception e) {
                    sendJsonResponse(exchange, 500, "{\"error\": \"Failed to configure provider: " + e.getMessage() + "\"}");
                }
            }
        }
    }

    private static class BrowserNavigateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized.\"}");
                return;
            }
            try {
                String body = readRequestBody(exchange);
                JsonNode json = MAPPER.readTree(body);
                String url = json.path("url").asText("").trim();
                String result = com.dex.browser.BrowserAutomation.navigate(url);

                ObjectNode resp = MAPPER.createObjectNode();
                resp.put("url", url);
                resp.put("content", result);
                sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\": \"" + e.getMessage() + "\"}");
            }
        }
    }

    private static class BrowserSearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized.\"}");
                return;
            }
            try {
                String body = readRequestBody(exchange);
                JsonNode json = MAPPER.readTree(body);
                String query = json.path("query").asText("").trim();
                String platform = json.path("platform").asText("google").trim();
                String result = com.dex.browser.BrowserAutomation.search(query, platform);

                ObjectNode resp = MAPPER.createObjectNode();
                resp.put("query", query);
                resp.put("platform", platform);
                resp.put("result", result);
                sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\": \"" + e.getMessage() + "\"}");
            }
        }
    }

    private static class VoiceSpeakHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized.\"}");
                return;
            }
            try {
                String body = readRequestBody(exchange);
                JsonNode json = MAPPER.readTree(body);
                String text = json.path("text").asText("").trim();
                String result = com.dex.voice.VoiceController.speak(text);

                ObjectNode resp = MAPPER.createObjectNode();
                resp.put("result", result);
                sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\": \"" + e.getMessage() + "\"}");
            }
        }
    }

    private static class VoiceStopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized.\"}");
                return;
            }
            com.dex.voice.VoiceController.stopSpeaking();
            ObjectNode resp = MAPPER.createObjectNode();
            resp.put("status", "stopped");
            sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
        }
    }

    private static class ScreenAnalyzeHandler implements HttpHandler {
        private final AIClient aiClient;
        public ScreenAnalyzeHandler(AIClient aiClient) { this.aiClient = aiClient; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized.\"}");
                return;
            }
            try {
                String body = readRequestBody(exchange);
                JsonNode json = MAPPER.readTree(body);
                String prompt = json.path("prompt").asText("Analyze what is currently shown on the screen.").trim();
                String base64Img = com.dex.tools.SystemTools.captureScreenshotBase64();

                if (base64Img == null) {
                    sendJsonResponse(exchange, 500, "{\"error\": \"Could not capture desktop screen.\"}");
                    return;
                }

                String reply = aiClient.processMessage(prompt, base64Img, "image/png");
                ObjectNode resp = MAPPER.createObjectNode();
                resp.put("prompt", prompt);
                resp.put("analysis", reply);
                sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\": \"" + e.getMessage() + "\"}");
            }
        }
    }
}
