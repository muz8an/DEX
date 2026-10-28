package com.dex.mobile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * REST Client for communicating with the DEX Windows backend.
 * All AI queries and PC control commands are executed on the Windows PC;
 * no AI provider keys or secrets are stored in this client.
 */
public class DexClient {

    private String serverUrl;
    private String authToken;
    private String deviceId;

    public DexClient() {
        this("http://10.0.2.2:31416", null); // Default emulator loopback to host
    }

    public DexClient(String serverUrl, String authToken) {
        this.serverUrl = normalizeUrl(serverUrl);
        this.authToken = authToken;
    }

    public static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "http://127.0.0.1:31416";
        }
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://" + u;
        }
        return u.replaceAll("/+$", "");
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
    }

    public String getDeviceId() {
        return deviceId;
    }

    public boolean isConfigured() {
        return serverUrl != null && !serverUrl.isBlank();
    }

    public boolean isPaired() {
        return authToken != null && !authToken.isBlank();
    }

    // ==================== REST API METHODS ====================

    /**
     * Checks server reachability and status.
     */
    public JSONObject checkStatus() throws Exception {
        return getRequest("/api/status");
    }

    /**
     * Pairs this mobile device with DEX PC using the 6-digit one-time PIN.
     */
    public JSONObject pairWithPin(String pin, String deviceName) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("pin", pin != null ? pin.trim() : "");
        payload.put("deviceName", deviceName != null ? deviceName.trim() : "Android Companion");

        JSONObject resp = postRequest("/api/pair", payload);
        if (resp.has("token")) {
            this.authToken = resp.getString("token");
        }
        if (resp.has("deviceId")) {
            this.deviceId = resp.getString("deviceId");
        }
        return resp;
    }

    /**
     * Sends a chat message to DEX AI Assistant.
     */
    public String sendChat(String message) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("message", message);
        JSONObject resp = postRequest("/api/chat", payload);
        if (resp.has("reply")) {
            return resp.getString("reply");
        }
        if (resp.has("error")) {
            throw new Exception(resp.getString("error"));
        }
        return resp.toString();
    }

    /**
     * Updates the user's name on DEX PC.
     */
    public JSONObject updateProfile(String name) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("userName", name != null ? name.trim() : "");
        return postRequest("/api/profile", payload);
    }

    /**
     * Launches an approved Windows application (Notepad, Calculator, Chrome, YouTube, etc.)
     */
    public String launchApp(String appName) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("name", "open_app");
        payload.put("args", appName);
        JSONObject resp = postRequest("/api/tools/run", payload);
        return resp.optString("result", "Launched: " + appName);
    }

    /**
     * Takes a Windows desktop screenshot.
     */
    public JSONObject captureScreen() throws Exception {
        return getRequest("/api/screenshot");
    }

    /**
     * Requests vision AI analysis of the current Windows desktop screen.
     */
    public String analyzeScreen(String prompt) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("prompt", prompt != null && !prompt.isBlank() ? prompt : "Analyze what is shown on screen.");
        JSONObject resp = postRequest("/api/screen/analyze", payload);
        if (resp.has("analysis")) {
            return resp.getString("analysis");
        }
        return resp.toString();
    }

    /**
     * Retrieves task execution history from the Windows PC.
     */
    public JSONArray fetchTasks() throws Exception {
        HttpURLConnection conn = openConnection("/api/tasks", "GET");
        int code = conn.getResponseCode();
        String body = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (code >= 400) {
            throw new Exception("HTTP " + code + ": " + body);
        }
        return new JSONArray(body);
    }

    /**
     * Cancels an active or pending task.
     */
    public boolean cancelTask(String taskId) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("taskId", taskId);
        JSONObject resp = postRequest("/api/tasks/cancel", payload);
        return resp.optBoolean("cancelled", false);
    }

    // ==================== HTTP HELPERS ====================

    private JSONObject getRequest(String path) throws Exception {
        HttpURLConnection conn = openConnection(path, "GET");
        int code = conn.getResponseCode();
        String body = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (code >= 400) {
            throw new Exception("HTTP " + code + ": " + body);
        }
        return new JSONObject(body);
    }

    private JSONObject postRequest(String path, JSONObject payload) throws Exception {
        HttpURLConnection conn = openConnection(path, "POST");
        conn.setDoOutput(true);
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int code = conn.getResponseCode();
        String body = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (code >= 400) {
            try {
                JSONObject errJson = new JSONObject(body);
                if (errJson.has("error")) {
                    throw new Exception(errJson.getString("error"));
                }
            } catch (Exception ignored) {
            }
            throw new Exception("HTTP " + code + ": " + body);
        }
        return new JSONObject(body);
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
