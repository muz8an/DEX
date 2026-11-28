package com.dex.mobile;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class DexClientTest {

    @Test
    public void testUrlNormalization() {
        assertEquals("http://192.168.1.10:31416", DexClient.normalizeUrl("192.168.1.10:31416"));
        assertEquals("http://192.168.1.10:31416", DexClient.normalizeUrl("http://192.168.1.10:31416/"));
        assertEquals("https://dex.example.com", DexClient.normalizeUrl("https://dex.example.com/"));
        assertEquals("https://dex-phi-bay.vercel.app", DexClient.normalizeUrl("dex-phi-bay.vercel.app/"));
        assertEquals("http://127.0.0.1:31416", DexClient.normalizeUrl(null));
        assertEquals("http://127.0.0.1:31416", DexClient.normalizeUrl("   "));
    }

    @Test
    public void testBuildServerUrlHandlesLanAndCloud() {
        assertEquals("http://192.168.1.35:31416", DexClient.buildServerUrl("192.168.1.35", 31416));
        assertEquals("http://192.168.1.35:8765", DexClient.buildServerUrl("http://192.168.1.35:8765/?token=abc", 31416));
        assertEquals("https://dex-phi-bay.vercel.app", DexClient.buildServerUrl("https://dex-phi-bay.vercel.app/", 31416));
        assertEquals("https://dexai-gamma.vercel.app", DexClient.buildServerUrl("dexai-gamma.vercel.app", 31416));
    }

    @Test
    public void testParsePairingInputExtractsQrTokenAndPin() {
        DexClient.PairingInfo qr = DexClient.parsePairingInput("http://192.168.0.42:31416/?token=secretToken99", 31416);
        assertEquals("http://192.168.0.42:31416", qr.serverUrl);
        assertEquals("192.168.0.42", qr.host);
        assertEquals(31416, qr.port);
        assertEquals("secretToken99", qr.token);
        assertNull(qr.pin);

        DexClient.PairingInfo cloud = DexClient.parsePairingInput("https://dex-phi-bay.vercel.app/?pin=482910", 31416);
        assertEquals("https://dex-phi-bay.vercel.app", cloud.serverUrl);
        assertEquals("482910", cloud.pin);

        DexClient.PairingInfo barePin = DexClient.parsePairingInput("849201", 31416);
        assertNull(barePin.serverUrl);
        assertEquals("849201", barePin.pin);
    }

    @Test
    public void testClientStateAndCloudRelayReadiness() {
        DexClient client = new DexClient("192.168.1.50:31416", null);
        assertTrue(client.isConfigured());
        assertFalse(client.isPaired());
        assertFalse(client.isCloudRelay());
        assertFalse(client.isReady());

        client.setAuthToken("dex_dev_testtoken123");
        assertTrue(client.isPaired());
        assertTrue(client.isReady());
        assertEquals("dex_dev_testtoken123", client.getAuthToken());

        DexClient cloudClient = new DexClient("https://dex-phi-bay.vercel.app", null);
        assertTrue(cloudClient.isCloudRelay());
        assertTrue(cloudClient.isReady());
    }

    @Test
    public void testTelemetryPayloadIncludesDeviceMetadata() throws Exception {
        DexClient client = new DexClient("http://192.168.1.10:31416", "tok1");
        DexClient.DeviceTelemetry t = new DexClient.DeviceTelemetry();
        t.deviceId = "android-test-01";
        t.deviceName = "Android Pixel 8";
        t.osBrowser = "Android 14 (DEX App)";
        t.screen = "1080x2400";
        t.orientation = "portrait";
        t.fullscreen = true;
        client.setTelemetry(t);

        JSONObject payload = client.buildTelemetryPayload("open youtube");
        assertEquals("open youtube", payload.getString("message"));
        assertTrue(payload.getBoolean("isMobile"));
        assertEquals("mobile", payload.getString("source"));
        assertEquals("android-test-01", payload.getString("deviceId"));
        assertEquals("Android Pixel 8", payload.getString("deviceName"));
        assertEquals("1080x2400", payload.getString("screen"));
        assertEquals("portrait", payload.getString("orientation"));
        assertTrue(payload.getBoolean("fullscreen"));
    }

    @Test
    public void testVoiceCleanTextAndMarkdownStripping() {
        String raw = "### Step 1\n- Launched **YouTube** (PID: 1234)\n```json\n{\"tool\":\"open_app\"}\n```\nVisit https://example.com";
        String speech = VoiceController.cleanTextForSpeech(raw);
        assertFalse(speech.contains("PID:"));
        assertFalse(speech.contains("https://"));
        assertTrue(speech.contains("YouTube"));

        String plain = ChatBubbleRenderer.cleanMarkdownPlainText(raw);
        assertFalse(plain.contains("{\"tool\":\"open_app\"}"));
        assertTrue(plain.contains("Step 1"));
        assertTrue(plain.contains("• Launched **YouTube**"));
    }
}
