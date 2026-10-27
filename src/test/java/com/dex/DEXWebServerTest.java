package com.dex;

import com.dex.config.DEXConfig;
import com.dex.memory.MemoryManager;
import com.dex.server.DEXWebServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class DEXWebServerTest {

    private DEXWebServer server;
    private HttpClient client;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    public void setup(@TempDir Path tempDir) throws IOException {
        Path tempMemory = tempDir.resolve("memory.json");
        MemoryManager memory = new MemoryManager(tempMemory);
        AIClient aiClient = new AIClient(memory);

        // Start server on an open test port
        server = new DEXWebServer(aiClient, "127.0.0.1", 31419);
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    public void teardown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testStatusEndpoint() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:31419/api/status"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = MAPPER.readTree(resp.body());
        assertEquals("DEX", root.get("assistant").asText());
        assertEquals("ONLINE", root.get("status").asText());
        assertTrue(root.has("toolsCount"));
    }

    @Test
    public void testToolsEndpoint() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:31419/api/tools"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = MAPPER.readTree(resp.body());
        assertTrue(root.isArray());
        assertTrue(root.size() >= 10);
    }

    @Test
    public void testChatEndpointWithLocalCommand() throws Exception {
        String payload = "{\"message\": \"what time is it\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:31419/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = MAPPER.readTree(resp.body());
        assertTrue(root.has("reply"));
        assertTrue(root.get("reply").asText().contains("Current Date & Time:"));
    }

    @Test
    public void testDashboardHtmlEndpoint() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:31419/"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("DEX AI ASSISTANT"));
        assertTrue(resp.body().contains("<!DOCTYPE html>"));
    }

    @Test
    public void testDevicePairingWorkflow() throws Exception {
        com.dex.security.DeviceManager.clearAllDevices();
        String pin = com.dex.security.DeviceManager.generatePairingPin();

        // 1. Pair device with valid PIN
        String pairPayload = "{\"deviceName\": \"Test Mobile Device\", \"pin\": \"" + pin + "\"}";
        HttpRequest pairReq = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:31419/api/pair"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(pairPayload))
                .build();

        HttpResponse<String> pairResp = client.send(pairReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, pairResp.statusCode());

        JsonNode pairJson = MAPPER.readTree(pairResp.body());
        assertEquals("ok", pairJson.get("status").asText());
        assertTrue(pairJson.has("token"));
        String deviceToken = pairJson.get("token").asText();
        String deviceId = pairJson.get("deviceId").asText();
        assertTrue(deviceToken.startsWith("dex_dev_"));

        // 2. Pair device with invalid PIN should fail 401
        String badPayload = "{\"deviceName\": \"Bad Phone\", \"pin\": \"999999\"}";
        HttpRequest badReq = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:31419/api/pair"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(badPayload))
                .build();

        HttpResponse<String> badResp = client.send(badReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, badResp.statusCode());

        // 3. List devices
        HttpRequest listReq = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:31419/api/devices"))
                .GET()
                .build();

        HttpResponse<String> listResp = client.send(listReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listResp.statusCode());
        JsonNode listJson = MAPPER.readTree(listResp.body());
        assertTrue(listJson.isArray());
        assertEquals(1, listJson.size());

        // 4. Revoke device
        String revokePayload = "{\"deviceId\": \"" + deviceId + "\"}";
        HttpRequest revokeReq = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:31419/api/devices/revoke"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(revokePayload))
                .build();

        HttpResponse<String> revokeResp = client.send(revokeReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, revokeResp.statusCode());
        assertFalse(com.dex.security.DeviceManager.isValidDeviceToken(deviceToken));
    }
}
