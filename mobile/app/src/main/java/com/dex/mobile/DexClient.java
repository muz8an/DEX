package com.dex.mobile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * REST client for communicating with the DEX Windows backend (LAN) or Vercel Cloud Relay.
 * No AI provider keys are stored in the mobile client; requests carry device telemetry so
 * connected sessions appear in DEX Desktop and CLI diagnostics.
 */
public class DexClient {

    public static final String CLOUD_RELAY_PRIMARY = "https://dex-phi-bay.vercel.app";
    public static final int DEFAULT_PORT = 31416;

    /** Parsed connection details extracted from a QR URL, IP:port string, or bare PIN. */
    public static class PairingInfo {
        public final String serverUrl;
        public final String host;
        public final int port;
        public final String token;
        public final String pin;

        public PairingInfo(String serverUrl, String host, int port, String token, String pin) {
            this.serverUrl = serverUrl;
            this.host = host;
            this.port = port;
            this.token = token;
            this.pin = pin;
        }
    }

    /** Device metadata attached to requests so Desktop/Terminal shows active phone info. */
    public static class DeviceTelemetry {
        public String deviceId = "";
        public String deviceName = "Android Companion";
        public String osBrowser = "Android DEX App";
        public String screen = "";
        public String orientation = "portrait";
        public boolean fullscreen = false;
    }

    private String serverUrl;
    private String authToken;
    private String deviceId;
    private DeviceTelemetry telemetry = new DeviceTelemetry();

    public DexClient() {
        this("http://10.0.2.2:" + DEFAULT_PORT, null);
    }

    public DexClient(String serverUrl, String authToken) {
        this.serverUrl = normalizeUrl(serverUrl);
        this.authToken = authToken;
    }

    public static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "http://127.0.0.1:" + DEFAULT_PORT;
        }
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = (u.contains("vercel.app") ? "https://" : "http://") + u;
        }
        return u.replaceAll("/+$", "");
    }

    /** Builds a clean base URL from host/URL input and optional port without double-adding scheme/port. */
    public static String buildServerUrl(String hostOrUrl, int port) {
        if (hostOrUrl == null || hostOrUrl.isBlank()) {
            return "http://127.0.0.1:" + (port > 0 ? port : DEFAULT_PORT);
        }
        String raw = hostOrUrl.trim();
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            try {
                URI uri = URI.create(raw);
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (host != null && !host.isBlank()) {
                    int uriPort = uri.getPort();
                    String portPart = uriPort > 0
                            ? (":" + uriPort)
                            : ("http".equalsIgnoreCase(scheme) && port > 0 ? (":" + port) : "");
                    return scheme.toLowerCase() + "://" + host + portPart;
                }
            } catch (Exception ignored) {
            }
            return normalizeUrl(raw);
        }
        if (raw.contains("vercel.app")) {
            int slash = raw.indexOf('/');
            return "https://" + (slash > 0 ? raw.substring(0, slash) : raw);
        }
        String hostOnly = raw.contains("/") ? raw.substring(0, raw.indexOf('/')) : raw;
        if (hostOnly.contains(":")) {
            return "http://" + hostOnly;
        }
        int effectivePort = port > 0 ? port : DEFAULT_PORT;
        return "http://" + hostOnly + ":" + effectivePort;
    }

    /** Parses QR URLs (`http://ip:port/?token=...`, `https://...vercel.app`), IPs, or raw 6-digit PINs. */
    public static PairingInfo parsePairingInput(String rawInput, int defaultPort) {
        int fallbackPort = defaultPort > 0 ? defaultPort : DEFAULT_PORT;
        if (rawInput == null || rawInput.isBlank()) {
            return new PairingInfo(null, null, fallbackPort, null, null);
        }
        String trimmed = rawInput.trim();
        if (trimmed.matches("^\\d{6}$")) {
            return new PairingInfo(null, null, fallbackPort, null, trimmed);
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.contains("vercel.app")
                || trimmed.contains("/?token=") || trimmed.contains("?pin=")) {
            String candidate = (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
                    ? trimmed
                    : ((trimmed.contains("vercel.app") ? "https://" : "http://") + trimmed);
            try {
                URI uri = URI.create(candidate);
                String host = uri.getHost();
                int parsedPort = uri.getPort() > 0 ? uri.getPort()
                        : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : fallbackPort);
                String token = extractQueryParam(uri.getRawQuery(), "token");
                String pin = extractQueryParam(uri.getRawQuery(), "pin");
                String base = buildServerUrl(candidate, fallbackPort);
                String hostDisplay = "https".equalsIgnoreCase(uri.getScheme()) ? base : host;
                return new PairingInfo(base, hostDisplay, parsedPort, token, pin);
            } catch (Exception ignored) {
            }
        }
        String cleanHost = trimmed;
        int parsedPort = fallbackPort;
        int colon = trimmed.lastIndexOf(':');
        if (colon > 0 && !trimmed.contains("]")) {
            try {
                parsedPort = Integer.parseInt(trimmed.substring(colon + 1).trim());
                cleanHost = trimmed.substring(0, colon).trim();
            } catch (NumberFormatException ignored) {
            }
        }
        return new PairingInfo(buildServerUrl(cleanHost, parsedPort), cleanHost, parsedPort, null, null);
    }

    private static String extractQueryParam(String rawQuery, String key) {
        if (rawQuery == null || rawQuery.isBlank()) return null;
        for (String part : rawQuery.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equalsIgnoreCase(kv[0])) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8).trim();
            }
        }
        return null;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = normalizeUrl(serverUrl);
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
        if (telemetry != null && deviceId != null) {
            telemetry.deviceId = deviceId;
        }
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setTelemetry(DeviceTelemetry telemetry) {
        if (telemetry != null) {
            this.telemetry = telemetry;
            if (this.deviceId != null && !this.deviceId.isBlank()) {
                this.telemetry.deviceId = this.deviceId;
            }
        }
    }

    public boolean isCloudRelay() {
        return serverUrl != null && (serverUrl.startsWith("https://") || serverUrl.contains("vercel.app"));
    }

    public boolean isConfigured() {
        return serverUrl != null && !serverUrl.isBlank();
    }

    public boolean isPaired() {
        return authToken != null && !authToken.isBlank();
    }

    public boolean isReady() {
        return isPaired() || isCloudRelay();
    }

    // ==================== REST API METHODS ====================

    public JSONObject checkStatus() throws Exception {
        return getRequest("/api/status");
    }

    public JSONObject pairWithPin(String pin, String deviceName) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("pin", pin != null ? pin.trim() : "");
        payload.put("deviceName", deviceName != null ? deviceName.trim() : "Android Companion");

        JSONObject resp = postRequest("/api/pair", payload);
        if (resp.has("token")) {
            this.authToken = resp.getString("token");
        }
        if (resp.has("deviceId")) {
            setDeviceId(resp.getString("deviceId"));
        }
        return resp;
    }

    public void sendHeartbeat() throws Exception {
        JSONObject payload = new JSONObject();
        attachTelemetry(payload);
        if (isCloudRelay()) {
            payload.put("type", "heartbeat");
            payload.put("role", "mobile");
            payload.put("name", telemetry.deviceName);
            postRequest("/api/remote-queue", payload);
        } else {
            payload.put("name", telemetry.deviceName);
            payload.put("channel", "LAN");
            postRequest("/api/remote/heartbeat", payload);
        }
    }

    public String sendChat(String message) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("message", message);
        attachTelemetry(payload);
        if (isCloudRelay()) {
            payload.put("enqueue", true);
        }
        JSONObject resp = postRequest("/api/chat", payload);
        if (resp.has("reply")) {
            return resp.getString("reply");
        }
        if (resp.has("error")) {
            throw new Exception(resp.getString("error"));
        }
        return resp.toString();
    }

    public JSONObject updateProfile(String name) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("userName", name != null ? name.trim() : "");
        return postRequest("/api/profile", payload);
    }

    public String launchApp(String appName) throws Exception {
        JSONObject action = new JSONObject();
        action.put("type", "open_app");
        action.put("target", appName);
        action.put("label", appName);
        return executeRemoteCommand("open " + appName, action, "Dispatched to Laptop: " + appName);
    }

    public String sendRemoteControl(String controlCommand) throws Exception {
        return executeRemoteCommand(controlCommand, null, "Sent: " + controlCommand);
    }

    private String executeRemoteCommand(String command, JSONObject action, String fallbackResult) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("command", command);
        if (action != null) {
            payload.put("action", action);
        }
        attachTelemetry(payload);
        String endpoint = isCloudRelay() ? "/api/remote-queue" : "/api/remote/command";
        JSONObject resp = postRequest(endpoint, payload);
        return resp.optString("result", resp.optString("message", fallbackResult));
    }

    public JSONObject captureScreen() throws Exception {
        if (isCloudRelay()) {
            String result = executeRemoteCommand("take screenshot", null, "Screenshot queued on laptop.");
            return new JSONObject().put("status", "ok").put("message", result);
        }
        return getRequest("/api/screenshot");
    }

    public String analyzeScreen(String prompt) throws Exception {
        if (isCloudRelay()) {
            return sendChat(prompt != null && !prompt.isBlank()
                    ? prompt
                    : "Capture desktop screen and analyze active window");
        }
        JSONObject payload = new JSONObject();
        payload.put("prompt", prompt != null && !prompt.isBlank() ? prompt : "Analyze what is shown on screen.");
        JSONObject resp = postRequest("/api/screen/analyze", payload);
        if (resp.has("analysis")) {
            return resp.getString("analysis");
        }
        return resp.toString();
    }

    public JSONArray fetchTasks() throws Exception {
        HttpURLConnection conn = openConnection("/api/tasks", "GET");
        int code = conn.getResponseCode();
        String body = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (code >= 400) {
            throw new Exception("HTTP " + code + ": " + body);
        }
        return new JSONArray(body);
    }

    public boolean cancelTask(String taskId) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("taskId", taskId);
        JSONObject resp = postRequest("/api/tasks/cancel", payload);
        return resp.optBoolean("cancelled", false);
    }

    public JSONObject buildTelemetryPayload(String message) {
        JSONObject payload = new JSONObject();
        try {
            if (message != null) {
                payload.put("message", message);
            }
        } catch (Exception ignored) {
        }
        attachTelemetry(payload);
        return payload;
    }

    private void attachTelemetry(JSONObject payload) {
        try {
            payload.put("isMobile", true);
            payload.put("source", "mobile");
            payload.put("deviceId", effectiveDeviceId());
            payload.put("deviceName", telemetry.deviceName != null ? telemetry.deviceName : "Android Companion");
            payload.put("osBrowser", telemetry.osBrowser != null ? telemetry.osBrowser : "Android DEX App");
            payload.put("screen", telemetry.screen != null ? telemetry.screen : "");
            payload.put("orientation", telemetry.orientation != null ? telemetry.orientation : "portrait");
            payload.put("fullscreen", telemetry.fullscreen);
        } catch (Exception ignored) {
        }
    }

    private String effectiveDeviceId() {
        if (deviceId != null && !deviceId.isBlank()) return deviceId;
        if (telemetry != null && telemetry.deviceId != null && !telemetry.deviceId.isBlank()) return telemetry.deviceId;
        return "android-companion";
    }

    // ==================== HTTP HELPERS ====================

    private JSONObject getRequest(String path) throws Exception {
        return executeJsonRequest(path, "GET", null);
    }

    private JSONObject postRequest(String path, JSONObject payload) throws Exception {
        return executeJsonRequest(path, "POST", payload);
    }

    private JSONObject executeJsonRequest(String path, String method, JSONObject payload) throws Exception {
        HttpURLConnection conn = openConnection(path, method);
        if (payload != null) {
            conn.setDoOutput(true);
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }
        }

        int code = conn.getResponseCode();
        String body = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (code >= 400) {
            String errMsg = null;
            try {
                JSONObject errJson = new JSONObject(body);
                if (errJson.has("error")) {
                    errMsg = errJson.getString("error");
                }
            } catch (org.json.JSONException ignored) {
            }
            throw new Exception(errMsg != null ? errMsg : ("HTTP " + code + ": " + body));
        }
        return body.isEmpty() ? new JSONObject() : new JSONObject(body);
    }

    private HttpURLConnection openConnection(String path, String method) throws Exception {
        URL url = new URL(serverUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");

        if (authToken != null && !authToken.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken.trim());
        }
        return conn;
    }

    private String readStream(InputStream is) throws Exception {
        if (is == null) return "{}";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
