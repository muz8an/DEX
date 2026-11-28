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
    private java.util.concurrent.ExecutorService executor;
    private volatile boolean relayRunning = false;
    private Thread relayThread;
    private static volatile long lastCloudRelaySuccessAt = 0L;
    private static volatile String activeRelayHost = "";

    public static boolean isRelayConnected() {
        return lastCloudRelaySuccessAt > 0L && (System.currentTimeMillis() - lastCloudRelaySuccessAt) < 15_000L;
    }

    public static String getActiveRelayHost() {
        return activeRelayHost;
    }

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
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "DEX-HTTP-Worker");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);

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
        server.createContext("/api/profile", new ProfileHandler());
        server.createContext("/api/memory", new MemoryHandler(aiClient));
        server.createContext("/api/providers", new ProvidersHandler());
        server.createContext("/api/browser/navigate", new BrowserNavigateHandler());
        server.createContext("/api/browser/search", new BrowserSearchHandler());
        server.createContext("/api/voice/speak", new VoiceSpeakHandler());
        server.createContext("/api/voice/stop", new VoiceStopHandler());
        server.createContext("/api/screen/analyze", new ScreenAnalyzeHandler(aiClient));
        server.createContext("/api/pair", new PairHandler());
        server.createContext("/api/pair/pin", new PairPinHandler());
        server.createContext("/api/devices", new DevicesHandler());
        server.createContext("/api/devices/revoke", new DeviceRevokeHandler());
        server.createContext("/api/remote/heartbeat", new RemoteHeartbeatHandler());
        server.createContext("/api/remote/sessions", new RemoteSessionsHandler());
        server.createContext("/api/remote/command", new RemoteCommandHandler(aiClient));

        server.start();
        System.out.println("[DEX] Web Dashboard started at http://" + host + ":" + port);

        // Start background Cloud Relay worker for mobile-to-laptop execution
        if (this.port != 0 && !"true".equalsIgnoreCase(System.getProperty("dex.test.mode"))) {
            startCloudRelay();
        }
    }

    private void startCloudRelay() {
        relayRunning = true;
        relayThread = new Thread(() -> {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(4))
                    .build();

            List<String> relayUrls = new ArrayList<>();
            String customRelay = System.getenv("DEX_CLOUD_RELAY_URL");
            if (customRelay != null && !customRelay.isBlank()) {
                for (String u : customRelay.split(",")) {
                    String trimmed = u.trim();
                    if (!trimmed.isEmpty()) relayUrls.add(trimmed);
                }
            }
            if (relayUrls.isEmpty()) {
                relayUrls.add("https://dex-phi-bay.vercel.app/api/remote-queue");
                relayUrls.add("https://dexai-gamma.vercel.app/api/remote-queue");
            }

            int cycle = 0;
            while (relayRunning) {
                try {
                    Thread.sleep(1400);
                    if (!relayRunning) break;
                    boolean sendHeartbeat = (cycle % 5 == 0);
                    cycle++;

                    for (String relayUrl : relayUrls) {
                        if (!relayRunning) break;
                        try {
                            if (sendHeartbeat) {
                                sendDesktopHeartbeat(client, relayUrl, port);
                            }

                            String pollUrl = relayUrl + (relayUrl.contains("?") ? "&" : "?") + "consumer=desktop";
                            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                                    .uri(java.net.URI.create(pollUrl))
                                    .timeout(java.time.Duration.ofSeconds(4))
                                    .header("Accept", "application/json")
                                    .GET()
                                    .build();
                            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                            if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                                lastCloudRelaySuccessAt = System.currentTimeMillis();
                                activeRelayHost = relayUrl;

                                JsonNode root = MAPPER.readTree(resp.body());
                                syncCloudDevices(root.get("devices"), relayUrl);

                                JsonNode cmds = root.get("commands");
                                if (cmds != null && cmds.isArray()) {
                                    for (JsonNode cmd : cmds) {
                                        int cmdId = cmd.has("id") ? cmd.get("id").asInt() : 0;
                                        String command = cmd.has("command") ? cmd.get("command").asText() : "";
                                        JsonNode act = cmd.get("action");
                                        String devId = cmd.path("deviceId").asText("");
                                        String devName = cmd.path("deviceName").asText("");
                                        String osBrowser = cmd.path("osBrowser").asText("");

                                        if (!devId.isBlank() || !devName.isBlank()) {
                                            com.dex.security.DeviceManager.recordRemoteSession(
                                                    devId.isBlank() ? "cloud-mobile" : devId,
                                                    devName.isBlank() ? "Mobile Web Client" : devName,
                                                    osBrowser.isBlank() ? "Mobile Browser" : osBrowser,
                                                    relayUrl.contains("dexai-gamma") ? "Cloud (dex.ai)" : "Cloud Relay",
                                                    "Cloud",
                                                    cmd.path("screen").asText(""),
                                                    cmd.path("orientation").asText(""),
                                                    cmd.path("fullscreen").asBoolean(false),
                                                    command
                                            );
                                        }

                                        System.out.println("[DEX CloudRelay] Executing phone command on Windows: " + command);
                                        String execResult = executeRemotePayloadOnWindows(command, act, aiClient);

                                        if (cmdId > 0) {
                                            ObjectNode ackPayload = MAPPER.createObjectNode();
                                            ackPayload.put("ackId", cmdId);
                                            ackPayload.put("consumer", "desktop");
                                            ackPayload.put("executed", true);
                                            ackPayload.put("result", execResult != null ? execResult : "Executed");
                                            java.net.http.HttpRequest ackReq = java.net.http.HttpRequest.newBuilder()
                                                    .uri(java.net.URI.create(relayUrl))
                                                    .header("Content-Type", "application/json")
                                                    .timeout(java.time.Duration.ofSeconds(4))
                                                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(ackPayload)))
                                                    .build();
                                            client.send(ackReq, java.net.http.HttpResponse.BodyHandlers.discarding());
                                        }
                                    }
                                }
                            }
                        } catch (InterruptedException ie) {
                            throw ie;
                        } catch (Exception ignored) {
                            // Individual relay host unavailable or offline
                        }
                    }
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }, "DEX-CloudRelayWorker");
        relayThread.setDaemon(true);
        relayThread.start();
    }

    private static void sendDesktopHeartbeat(java.net.http.HttpClient client, String relayUrl, int webPort) {
        try {
            String computerName = System.getenv("COMPUTERNAME");
            if (computerName == null || computerName.isBlank()) {
                computerName = System.getProperty("user.name", "Windows-PC");
            }
            ObjectNode hb = MAPPER.createObjectNode();
            hb.put("type", "heartbeat");
            hb.put("role", "desktop");
            hb.put("deviceId", "laptop-" + computerName.toLowerCase(Locale.ROOT));
            hb.put("name", computerName);
            hb.put("os", System.getProperty("os.name", "Windows") + " / Java " + Runtime.version().feature());
            hb.put("port", webPort);
            hb.put("lanIp", DEXConfig.getDetectedHostIps().isEmpty() ? "127.0.0.1" : DEXConfig.getDetectedHostIps().get(0));

            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(relayUrl))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(3))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(hb)))
                    .build();
            client.send(req, java.net.http.HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }

    private static void syncCloudDevices(JsonNode devicesNode, String relayUrl) {
        if (devicesNode == null || !devicesNode.isArray()) return;
        String channelLabel = relayUrl.contains("dexai-gamma") ? "Cloud (dex.ai)" : "Cloud Relay";
        for (JsonNode d : devicesNode) {
            if (!"mobile".equalsIgnoreCase(d.path("role").asText("mobile"))) continue;
            if (!d.path("online").asBoolean(true)) continue;
            String id = d.path("deviceId").asText("");
            String name = d.path("name").asText("Mobile Device");
            String osBrowser = d.path("osBrowser").asText(d.path("os").asText("Mobile Web"));
            String screen = d.path("screen").asText("");
            String orientation = d.path("orientation").asText("");
            boolean fs = d.path("fullscreen").asBoolean(false);
            com.dex.security.DeviceManager.recordRemoteSession(
                    id, name, osBrowser, channelLabel, "Cloud", screen, orientation, fs, null
            );
        }
    }

    private static String executeRemotePayloadOnWindows(String rawCommand, JsonNode act, AIClient aiClient) {
        String command = rawCommand != null ? com.dex.WindowsController.stripLaptopPhraseSuffix(rawCommand) : "";
        String lower = command.toLowerCase(Locale.ROOT).trim();

        // 1. Tactile remote controls (D-pad, volume, media, minimize)
        if (lower.equals("minimize_all") || lower.equals("vol_up") || lower.equals("vol_down")
                || lower.equals("mute") || lower.equals("media_play_pause")
                || lower.equals("media_next") || lower.equals("media_prev")
                || lower.startsWith("remote_key ")) {
            return com.dex.WindowsController.executeRemoteControlAction(lower).toDisplayString();
        }

        // 2. Desktop screenshot
        if (lower.equals("take a screenshot") || lower.equals("take screenshot")
                || lower.equals("capture screenshot") || lower.equals("screenshot")) {
            return SystemTools.takeScreenshot("");
        }

        // 3. Explicit structured action metadata
        if (act != null && !act.isMissingNode() && !act.isNull()) {
            String type = act.path("type").asText("");
            String target = act.path("target").asText("");
            String url = act.path("url").asText("");
            boolean isNativeApp = act.path("isNativeApp").asBoolean(false);
            boolean isFallbackSearch = act.path("isFallbackSearch").asBoolean(false);

            if ("open_app".equalsIgnoreCase(type) || isNativeApp) {
                String appTarget = !target.isBlank() ? target : command;
                if ("chrome".equalsIgnoreCase(appTarget)) {
                    return com.dex.WindowsController.executeOpenChrome(null).toDisplayString();
                }
                return com.dex.WindowsController.openApp(appTarget);
            }
            if (url != null && !url.isBlank() && !isFallbackSearch) {
                return com.dex.WindowsController.openDefaultBrowser(url);
            }
        }

        // 4. Natural-language dispatch through DEX TaskManager / WindowsController
        String taskOut = TaskManager.handle(command);
        if (taskOut != null && !taskOut.isBlank()) {
            if (taskOut.startsWith("Unsupported application") && act != null && act.has("url") && !act.path("url").asText("").isBlank()) {
                return com.dex.WindowsController.openDefaultBrowser(act.path("url").asText());
            }
            return taskOut;
        }
        return com.dex.WindowsController.openApp(command);
    }

    public synchronized void stop() {
        relayRunning = false;
        if (relayThread != null) {
            relayThread.interrupt();
            relayThread = null;
        }
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
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
            if (DEXConfig.validateRemoteToken(token) || com.dex.security.DeviceManager.isValidDeviceToken(token)) {
                return true;
            }
        }

        // Check query parameter: "?token=<token>"
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && "token".equalsIgnoreCase(pair[0])) {
                    if (DEXConfig.validateRemoteToken(pair[1]) || com.dex.security.DeviceManager.isValidDeviceToken(pair[1])) {
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
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Device-Token");
        exchange.getResponseHeaders().set("Access-Control-Allow-Private-Network", "true");
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

            boolean authorized = isAuthorized(exchange);
            com.dex.ai.AIProvider activeP = com.dex.ai.ProviderManager.getActiveProvider();
            ObjectNode status = MAPPER.createObjectNode();
            status.put("assistant", "X");
            String resolvedUser = com.dex.profile.UserProfileManager.hasCustomName()
                    ? com.dex.profile.UserProfileManager.getUserName()
                    : com.dex.profile.UserProfileManager.formatDisplayName(System.getProperty("user.name", "User"));
            status.put("userName", resolvedUser);
            status.put("setupCompleted", com.dex.profile.UserProfileManager.isSetupCompleted());
            status.put("status", "ONLINE");
            status.put("hasApiKey", DEXConfig.hasApiKey() || (activeP != null && activeP.isConfigured()));
            status.put("isOfflineMode", DEXConfig.isOfflineMode());
            status.put("activeProvider", com.dex.ai.ProviderManager.getActiveProviderType().getDisplayName());
            status.put("model", activeP != null ? activeP.getModel() : "");
            status.put("baseUrl", activeP != null ? activeP.getBaseUrl() : "");
            status.put("workspace", DEXConfig.getWorkspaceRoot().toString());
            status.put("toolsCount", ToolManager.getAllTools().size());
            status.put("remoteAuthEnabled", true);
            status.put("pairedDevicesCount", com.dex.security.DeviceManager.getPairedDevices().size());
            status.put("connectedRemotesCount", com.dex.security.DeviceManager.getActiveRemoteSessions().size());
            status.put("relayConnected", lastCloudRelaySuccessAt > 0 && (System.currentTimeMillis() - lastCloudRelaySuccessAt) < 15_000L);
            status.put("relayHost", activeRelayHost);
            status.put("authorized", authorized);
            if (authorized) {
                status.put("remoteToken", DEXConfig.getRemoteAuthToken());
            }

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

            boolean authorized = isAuthorized(exchange);
            ObjectNode root = MAPPER.createObjectNode();
            root.put("host", DEXConfig.getWebHost());
            root.put("port", DEXConfig.getWebPort());
            if (authorized) {
                root.put("remoteToken", DEXConfig.getRemoteAuthToken());
            }

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
                            String url = "http://" + addr.getHostAddress() + ":" + DEXConfig.getWebPort()
                                    + (authorized ? "/?token=" + DEXConfig.getRemoteAuthToken() : "/");
                            node.put("url", url);
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
                String name = tool.getName();
                node.put("name", name);
                node.put("description", tool.getDescription());
                node.put("parameters", tool.getParameterDescription());
                node.put("sensitive", tool.isSensitive());

                switch (name) {
                    case "open_app":
                        node.put("icon", "🚀");
                        node.put("symbol", "EXE");
                        node.put("category", "System Automation");
                        node.put("accent", "#3B82F6");
                        node.put("example", "open youtube");
                        break;
                    case "open_url":
                        node.put("icon", "🌐");
                        node.put("symbol", "URL");
                        node.put("category", "Web Navigation");
                        node.put("accent", "#10B981");
                        node.put("example", "open https://github.com");
                        break;
                    case "take_screenshot":
                        node.put("icon", "📸");
                        node.put("symbol", "VIS");
                        node.put("category", "Multimodal Vision");
                        node.put("accent", "#8B5CF6");
                        node.put("example", "take screenshot");
                        break;
                    case "run_command":
                        node.put("icon", "⚡");
                        node.put("symbol", "CMD");
                        node.put("category", "Shell Execution");
                        node.put("accent", "#F59E0B");
                        node.put("example", "dir");
                        break;
                    case "read_file":
                        node.put("icon", "📄");
                        node.put("symbol", "IO");
                        node.put("category", "File Systems");
                        node.put("accent", "#06B6D4");
                        node.put("example", "read pom.xml");
                        break;
                    case "write_file":
                        node.put("icon", "✍️");
                        node.put("symbol", "WRI");
                        node.put("category", "File Systems");
                        node.put("accent", "#EC4899");
                        node.put("example", "write notes.txt");
                        break;
                    case "list_files":
                        node.put("icon", "📂");
                        node.put("symbol", "DIR");
                        node.put("category", "File Systems");
                        node.put("accent", "#6366F1");
                        node.put("example", "list files");
                        break;
                    case "speak_text":
                        node.put("icon", "🎙️");
                        node.put("symbol", "TTS");
                        node.put("category", "Voice Synthesis");
                        node.put("accent", "#14B8A6");
                        node.put("example", "speak Hello Muzain");
                        break;
                    default:
                        node.put("icon", "⚡");
                        node.put("symbol", "SYS");
                        node.put("category", "System Utility");
                        node.put("accent", "#FFFFFF");
                        node.put("example", name);
                        break;
                }
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

                String remoteIp = exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null
                        ? exchange.getRemoteAddress().getAddress().getHostAddress()
                        : "127.0.0.1";
                boolean isLoopback = "127.0.0.1".equals(remoteIp) || "0:0:0:0:0:0:0:1".equals(remoteIp) || "::1".equals(remoteIp);
                boolean isMobileClient = json.path("isMobile").asBoolean(false)
                        || "mobile".equalsIgnoreCase(json.path("source").asText(""))
                        || !isLoopback;

                if (isMobileClient) {
                    String ua = exchange.getRequestHeaders().getFirst("User-Agent");
                    com.dex.security.DeviceManager.recordRemoteSession(
                            json.path("deviceId").asText(""),
                            json.path("deviceName").asText(""),
                            json.path("osBrowser").asText(ua != null ? summarizeUserAgent(ua) : "Mobile Browser"),
                            remoteIp,
                            "LAN",
                            json.path("screen").asText(""),
                            json.path("orientation").asText(""),
                            json.path("fullscreen").asBoolean(false),
                            userMessage
                    );
                }

                String lower = userMessage.toLowerCase(Locale.ROOT).trim();
                String reply;
                if (lower.equals("minimize_all") || lower.equals("vol_up") || lower.equals("vol_down")
                        || lower.equals("mute") || lower.equals("media_play_pause")
                        || lower.equals("media_next") || lower.equals("media_prev")
                        || lower.startsWith("remote_key ")) {
                    reply = com.dex.WindowsController.executeRemoteControlAction(userMessage).toDisplayString();
                } else {
                    reply = aiClient.processMessage(userMessage);
                }

                ObjectNode responseNode = MAPPER.createObjectNode();
                responseNode.put("reply", reply);
                responseNode.put("executedOnLaptop", true);
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
                com.dex.ai.AIProvider activeP = com.dex.ai.ProviderManager.getActiveProvider();
                if (activeP != null) {
                    com.dex.ai.ProviderManager.configureProvider(activeP.getType(), null, null, key);
                }
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

    private static class ProfileHandler implements HttpHandler {
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

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    String body = readRequestBody(exchange);
                    if (body != null && !body.isBlank()) {
                        JsonNode json = MAPPER.readTree(body);
                        boolean isReset = json.path("reset").asBoolean(false)
                                || "reset".equalsIgnoreCase(json.path("action").asText(""));
                        if (isReset) {
                            com.dex.profile.UserProfileManager.resetProfile();
                        } else {
                            String name = json.has("userName")
                                    ? json.path("userName").asText("").trim()
                                    : json.path("name").asText("").trim();
                            com.dex.profile.UserProfileManager.completeSetup(name);
                        }
                    }
                } catch (Exception e) {
                    sendJsonResponse(exchange, 400, "{\"error\": \"Malformed request: " + e.getMessage() + "\"}");
                    return;
                }
            }

            ObjectNode profile = MAPPER.createObjectNode();
            profile.put("userName", com.dex.profile.UserProfileManager.getUserName());
            profile.put("setupCompleted", com.dex.profile.UserProfileManager.isSetupCompleted());
            profile.put("hasCustomName", com.dex.profile.UserProfileManager.hasCustomName());
            sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(profile));
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

    private static class PairHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Use POST to pair device.\"}");
                return;
            }
            try {
                String body = readRequestBody(exchange);
                JsonNode json = MAPPER.readTree(body);
                String deviceName = json.path("deviceName").asText("Android Companion").trim();
                String pin = json.path("pin").asText("").trim();

                com.dex.security.DeviceManager.PairedDevice dev = com.dex.security.DeviceManager.pairDevice(deviceName, pin);
                if (dev == null) {
                    sendJsonResponse(exchange, 401, "{\"error\": \"Invalid or expired pairing PIN. Please check the PIN on DEX PC.\"}");
                    return;
                }

                ObjectNode resp = MAPPER.createObjectNode();
                resp.put("status", "ok");
                resp.put("deviceId", dev.getDeviceId());
                resp.put("deviceName", dev.getDeviceName());
                resp.put("token", dev.getToken());
                resp.put("pairedAt", dev.getPairedAt());
                resp.put("message", "Device paired successfully.");
                sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\": \"Pairing error: " + e.getMessage() + "\"}");
            }
        }
    }

    private static class PairPinHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendJsonResponse(exchange, 401, "{\"error\": \"Unauthorized to request pairing PIN.\"}");
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                com.dex.security.DeviceManager.generatePairingPin();
            }

            ObjectNode resp = MAPPER.createObjectNode();
            resp.put("pin", com.dex.security.DeviceManager.getActivePin());
            resp.put("remainingSeconds", com.dex.security.DeviceManager.getPinRemainingSeconds());
            sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
        }
    }

    private static class DevicesHandler implements HttpHandler {
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
            for (com.dex.security.DeviceManager.PairedDevice d : com.dex.security.DeviceManager.getPairedDevices()) {
                ObjectNode node = array.addObject();
                node.put("deviceId", d.getDeviceId());
                node.put("deviceName", d.getDeviceName());
                node.put("pairedAt", d.getPairedAt());
                node.put("lastUsedAt", d.getLastUsedAt());
                String t = d.getToken();
                node.put("maskedToken", t.length() > 12 ? t.substring(0, 8) + "..." : "****");
            }
            sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(array));
        }
    }

    private static class DeviceRevokeHandler implements HttpHandler {
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
                String deviceId = json.path("deviceId").asText("").trim();
                boolean revoked = com.dex.security.DeviceManager.revokeDevice(deviceId);

                ObjectNode resp = MAPPER.createObjectNode();
                resp.put("revoked", revoked);
                resp.put("message", revoked ? "Device revoked successfully." : "Device not found.");
                sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\": \"Revoke error: " + e.getMessage() + "\"}");
            }
        }
    }

    private static String summarizeUserAgent(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown Browser";
        String os = ua.contains("Android") ? "Android"
                : (ua.contains("iPhone") || ua.contains("iPad")) ? "iOS"
                : ua.contains("Windows") ? "Windows"
                : ua.contains("Mac OS") ? "macOS"
                : "Web";
        String browser = ua.contains("Edg") ? "Edge"
                : ua.contains("Chrome") ? "Chrome"
                : ua.contains("Firefox") ? "Firefox"
                : ua.contains("Safari") ? "Safari"
                : "Browser";
        return os + " / " + browser;
    }

    private static class RemoteHeartbeatHandler implements HttpHandler {
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
                String remoteIp = exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null
                        ? exchange.getRemoteAddress().getAddress().getHostAddress()
                        : "127.0.0.1";
                String ua = exchange.getRequestHeaders().getFirst("User-Agent");

                com.dex.security.DeviceManager.RemoteSession session = com.dex.security.DeviceManager.recordRemoteSession(
                        json.path("deviceId").asText(""),
                        json.path("name").asText(json.path("deviceName").asText("Mobile Device")),
                        json.path("osBrowser").asText(summarizeUserAgent(ua)),
                        remoteIp,
                        json.path("channel").asText("LAN"),
                        json.path("screen").asText(""),
                        json.path("orientation").asText(""),
                        json.path("fullscreen").asBoolean(false),
                        null
                );

                ObjectNode resp = MAPPER.createObjectNode();
                resp.put("ok", true);
                resp.put("sessionId", session.getSessionId());
                resp.put("connectedCount", com.dex.security.DeviceManager.getActiveRemoteSessions().size());
                sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\": \"Invalid heartbeat: " + e.getMessage() + "\"}");
            }
        }
    }

    private static class RemoteSessionsHandler implements HttpHandler {
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

            List<com.dex.security.DeviceManager.RemoteSession> active = com.dex.security.DeviceManager.getActiveRemoteSessions();
            ObjectNode root = MAPPER.createObjectNode();
            root.put("count", active.size());
            root.put("relayConnected", lastCloudRelaySuccessAt > 0 && (System.currentTimeMillis() - lastCloudRelaySuccessAt) < 15_000L);
            root.put("relayHost", activeRelayHost);
            root.put("hostName", System.getProperty("user.name", "Laptop"));
            root.put("port", DEXConfig.getWebPort());

            ArrayNode arr = root.putArray("sessions");
            for (com.dex.security.DeviceManager.RemoteSession s : active) {
                ObjectNode n = arr.addObject();
                n.put("sessionId", s.getSessionId());
                n.put("deviceName", s.getDeviceName());
                n.put("osBrowser", s.getOsBrowser());
                n.put("ipAddress", s.getIpAddress());
                n.put("channel", s.getChannel());
                n.put("screen", s.getScreen() != null ? s.getScreen() : "");
                n.put("orientation", s.getOrientation() != null ? s.getOrientation() : "");
                n.put("fullscreen", s.isFullscreen());
                n.put("connectedAt", s.getConnectedAt());
                n.put("lastSeenAt", s.getLastSeenAt());
                n.put("commandCount", s.getCommandCount());
                n.put("lastCommand", s.getLastCommand() != null ? s.getLastCommand() : "");
                n.put("online", s.isOnline());
            }
            sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(root));
        }
    }

    private static class RemoteCommandHandler implements HttpHandler {
        private final AIClient aiClient;

        public RemoteCommandHandler(AIClient aiClient) {
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
            try {
                String body = readRequestBody(exchange);
                JsonNode json = MAPPER.readTree(body);
                String command = json.path("command").asText("").trim();
                if (command.isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"error\": \"Command cannot be empty.\"}");
                    return;
                }

                String remoteIp = exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null
                        ? exchange.getRemoteAddress().getAddress().getHostAddress()
                        : "127.0.0.1";
                String ua = exchange.getRequestHeaders().getFirst("User-Agent");
                com.dex.security.DeviceManager.recordRemoteSession(
                        json.path("deviceId").asText(""),
                        json.path("deviceName").asText("Mobile Remote"),
                        json.path("osBrowser").asText(summarizeUserAgent(ua)),
                        remoteIp,
                        "LAN",
                        json.path("screen").asText(""),
                        json.path("orientation").asText(""),
                        json.path("fullscreen").asBoolean(false),
                        command
                );

                String result = executeRemotePayloadOnWindows(command, json.get("action"), aiClient);
                ObjectNode resp = MAPPER.createObjectNode();
                resp.put("ok", true);
                resp.put("executedOnLaptop", true);
                resp.put("command", command);
                resp.put("result", result);
                sendJsonResponse(exchange, 200, MAPPER.writeValueAsString(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\": \"Remote command failed: " + e.getMessage() + "\"}");
            }
        }
    }
}
