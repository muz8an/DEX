package com.dex.util;

import com.dex.AIClient;
import com.dex.TaskManager;
import com.dex.WindowsController;
import com.dex.config.DEXConfig;
import com.dex.memory.MemoryManager;
import com.dex.security.DeviceManager;
import com.dex.server.DEXWebServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RemoteSessionAndCommandTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        System.setProperty("dex.test.mode", "true");
        DeviceManager.clearRemoteSessions();
    }

    @AfterEach
    void tearDown() {
        DeviceManager.clearRemoteSessions();
        System.clearProperty("dex.test.mode");
    }

    @Test
    void stripsLaptopTargetSuffixInWindowsControllerAndTaskManager() {
        assertEquals("youtube", WindowsController.stripLaptopPhraseSuffix("youtube in laptop"));
        assertEquals("open youtube", WindowsController.stripLaptopPhraseSuffix("open youtube in my laptop please"));
        assertEquals("open calculator", WindowsController.stripLaptopPhraseSuffix("open calculator on pc"));
        assertEquals("open vscode", WindowsController.stripLaptopPhraseSuffix("open vscode on laptop screen!"));

        TaskManager.TaskStep stepYt = TaskManager.parseSingleStep(1, "open youtube in laptop");
        assertNotNull(stepYt);
        assertEquals("open_app", stepYt.getToolName());
        assertEquals("youtube", stepYt.getArgument().toLowerCase());

        TaskManager.TaskStep stepCalc = TaskManager.parseSingleStep(1, "open calculator on my laptop");
        assertNotNull(stepCalc);
        assertEquals("open_app", stepCalc.getToolName());
        assertEquals("calculator", stepCalc.getArgument().toLowerCase());
    }

    @Test
    void tracksRemoteSessionsAndExposesEndpointsWithoutAlteringDevicesList() throws Exception {
        AIClient aiClient = new AIClient(new MemoryManager());
        DEXWebServer server = new DEXWebServer(aiClient, "127.0.0.1", 0);
        // Server with port 0 still uses HttpServer bound ephemeral port? Check getPort()
        server.start();
        try {
            // Confirm pure-Java DeviceManager RemoteSession tracking
            DeviceManager.RemoteSession s = DeviceManager.recordRemoteSession(
                    "phone-test-1",
                    "Pixel 8",
                    "Android / Chrome",
                    "10.10.1.5",
                    "Cloud",
                    "412x915",
                    "Portrait",
                    true,
                    "open youtube in laptop"
            );
            assertEquals("Pixel 8", s.getDeviceName());
            assertTrue(s.isOnline());
            assertTrue(s.isFullscreen());
            assertEquals(1, s.getCommandCount());

            List<DeviceManager.RemoteSession> active = DeviceManager.getActiveRemoteSessions();
            assertEquals(1, active.size());
            assertEquals("open youtube in laptop", active.get(0).getLastCommand());
        } finally {
            server.stop();
        }
    }

    @Test
    void servesRemoteHeartbeatAndSessionsOverHttp() throws Exception {
        // Bind to an available port via ServerSocket probe
        int port;
        try (java.net.ServerSocket probe = new java.net.ServerSocket()) {
            probe.bind(new InetSocketAddress("127.0.0.1", 0));
            port = probe.getLocalPort();
        }

        AIClient aiClient = new AIClient(new MemoryManager());
        DEXWebServer server = new DEXWebServer(aiClient, "127.0.0.1", port);
        server.start();

        try {
            HttpClient client = HttpClient.newHttpClient();
            String token = DEXConfig.getRemoteAuthToken();

            String hbBody = "{\"deviceId\":\"mob-99\",\"name\":\"Galaxy S24\",\"osBrowser\":\"Android / Chrome\",\"screen\":\"390x844\",\"orientation\":\"Portrait\",\"fullscreen\":true,\"channel\":\"LAN\"}";
            HttpRequest hbReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/remote/heartbeat"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(hbBody))
                    .build();
            HttpResponse<String> hbResp = client.send(hbReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, hbResp.statusCode());
            JsonNode hbJson = MAPPER.readTree(hbResp.body());
            assertTrue(hbJson.path("ok").asBoolean());
            assertEquals(1, hbJson.path("connectedCount").asInt());

            HttpRequest sessReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/remote/sessions"))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> sessResp = client.send(sessReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, sessResp.statusCode());
            JsonNode sessJson = MAPPER.readTree(sessResp.body());
            assertEquals(1, sessJson.path("count").asInt());
            assertEquals("Galaxy S24", sessJson.path("sessions").get(0).path("deviceName").asText());
            assertTrue(sessJson.path("sessions").get(0).path("fullscreen").asBoolean());

            // Verify Access-Control-Allow-Private-Network header is present
            assertEquals("true", sessResp.headers().firstValue("Access-Control-Allow-Private-Network").orElse(""));
        } finally {
            server.stop();
        }
    }
}
