package com.dex.util;

import com.dex.ToolManager;
import com.dex.ai.AIProvider;
import com.dex.ai.ProviderManager;
import com.dex.config.DEXConfig;
import com.dex.security.DeviceManager;
import com.dex.server.DEXWebServer;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure Java console formatter for DEX terminal QR remote control,
 * connected mobile device telemetry, and system diagnostics.
 */
public final class TerminalRemotePrinter {

    public static final String CLOUD_PRIMARY_URL = "https://dex-phi-bay.vercel.app";
    public static final String CLOUD_SECONDARY_URL = "https://dexai-gamma.vercel.app";

    private TerminalRemotePrinter() {
    }

    /**
     * Resolves the best tokenized mobile remote URL for the host laptop.
     */
    public static String getPrimaryRemoteUrl() {
        List<String> urls = getDetectedRemoteUrls();
        if (!urls.isEmpty()) {
            return urls.get(0);
        }
        return "http://127.0.0.1:" + DEXConfig.getWebPort() + "/?token=" + DEXConfig.getRemoteAuthToken();
    }

    /**
     * Builds tokenized remote URLs for all detected LAN/Tailscale interfaces.
     */
    public static List<String> getDetectedRemoteUrls() {
        List<String> urls = new ArrayList<>();
        int port = DEXConfig.getWebPort();
        String token = DEXConfig.getRemoteAuthToken();
        for (String detectedIp : DEXConfig.getDetectedHostIps()) {
            String cleanIp = detectedIp.replaceAll("\\s*\\(.*\\)", "").trim();
            if (!cleanIp.isEmpty()) {
                urls.add("http://" + cleanIp + ":" + port + "/?token=" + token);
            }
        }
        return urls;
    }

    /**
     * Prints a scannable pure-Java QR Code card, LAN URLs, Cloud Relay URLs, and 6-digit PIN.
     */
    public static void printRemoteQrCard() {
        String primaryUrl = getPrimaryRemoteUrl();
        String pin = DeviceManager.getActivePin();
        long pinRemaining = DeviceManager.getPinRemainingSeconds();

        System.out.println();
        System.out.println("+---------------------------------------------------------------------+");
        System.out.println("|              DEX REMOTE CONTROL (SCAN QR ON MOBILE)                 |");
        System.out.println("+---------------------------------------------------------------------+");
        System.out.println(" Primary QR URL : " + primaryUrl);
        System.out.println(" Cloud Remote 1 : " + CLOUD_PRIMARY_URL);
        System.out.println(" Cloud Remote 2 : " + CLOUD_SECONDARY_URL);
        System.out.println(" Pairing PIN    : " + pin + " (valid " + pinRemaining + "s)");
        System.out.println(" Cloud Bridge   : " + (DEXWebServer.isRelayConnected() ? "ONLINE (" + DEXWebServer.getActiveRelayHost() + ")" : "POLLING / STANDBY"));
        System.out.println();

        String qrBlock = renderTerminalQr(primaryUrl);
        for (String line : qrBlock.split("\\R")) {
            System.out.println("   " + line);
        }

        List<String> detected = DEXConfig.getDetectedHostIps();
        if (!detected.isEmpty()) {
            System.out.println();
            System.out.println(" Detected Network Interfaces:");
            int port = DEXConfig.getWebPort();
            String token = DEXConfig.getRemoteAuthToken();
            for (String entry : detected) {
                String cleanIp = entry.replaceAll("\\s*\\(.*\\)", "").trim();
                String tag = entry.contains("(") ? " " + entry.substring(entry.indexOf("(")) : "";
                System.out.println("   -> http://" + cleanIp + ":" + port + "/?token=" + token + tag);
            }
        }
        System.out.println("+---------------------------------------------------------------------+");
        System.out.println();
    }

    /**
     * Prints live connected remote sessions (mobile web / cloud relay) and paired devices.
     */
    public static void printConnectedDevices() {
        List<DeviceManager.RemoteSession> sessions = DeviceManager.getActiveRemoteSessions();
        List<DeviceManager.PairedDevice> paired = DeviceManager.getPairedDevices();

        System.out.println();
        System.out.println("+---------------------------------------------------------------------+");
        System.out.println("|                 CONNECTED REMOTE DEVICES & SESSIONS                 |");
        System.out.println("+---------------------------------------------------------------------+");

        if (sessions.isEmpty()) {
            System.out.println(" [!] No active mobile remote session detected in the last 65s.");
            System.out.println("     Scan the QR code ('qr') or open " + CLOUD_PRIMARY_URL + " on your phone.");
        } else {
            long now = System.currentTimeMillis();
            for (DeviceManager.RemoteSession s : sessions) {
                long ageSec = Math.max(0L, (now - s.getLastSeenAt()) / 1000L);
                System.out.println(" * [ONLINE] " + s.getDeviceName() + "  (" + s.getOsBrowser() + ")");
                System.out.println("     Channel   : " + s.getChannel() + " | IP/Host: " + s.getIpAddress());
                String screenInfo = (s.getScreen() != null && !s.getScreen().isBlank()) ? s.getScreen() : "unknown";
                String orient = (s.getOrientation() != null && !s.getOrientation().isBlank()) ? " / " + s.getOrientation() : "";
                System.out.println("     Viewport  : " + screenInfo + orient + (s.isFullscreen() ? " [FULLSCREEN]" : ""));
                System.out.println("     Activity  : " + s.getCommandCount() + " commands | Last seen " + ageSec + "s ago"
                        + (s.getLastCommand() != null && !s.getLastCommand().isBlank() ? " | Last: \"" + s.getLastCommand() + "\"" : ""));
            }
        }

        System.out.println(" ---------------------------------------------------------------------");
        System.out.println(" Authorized Paired Devices (" + paired.size() + "):");
        if (paired.isEmpty()) {
            System.out.println("   (None paired via PIN yet)");
        } else {
            for (DeviceManager.PairedDevice d : paired) {
                System.out.println("   - " + d.getDeviceName() + " [ID: " + d.getDeviceId() + "]");
            }
        }
        System.out.println("+---------------------------------------------------------------------+");
        System.out.println();
    }

    /**
     * Prints current server, relay, provider, and remote diagnostics for terminal inspection.
     */
    public static void printSystemStatus() {
        AIProvider active = ProviderManager.getActiveProvider();
        List<DeviceManager.RemoteSession> sessions = DeviceManager.getActiveRemoteSessions();

        System.out.println();
        System.out.println("+---------------------------------------------------------------------+");
        System.out.println("|                        DEX SYSTEM DIAGNOSTICS                       |");
        System.out.println("+---------------------------------------------------------------------+");
        System.out.println(" Host OS          : " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        System.out.println(" Java Runtime     : " + System.getProperty("java.version"));
        System.out.println(" Local Server     : http://127.0.0.1:" + DEXConfig.getWebPort());
        System.out.println(" Cloud Relay      : " + (DEXWebServer.isRelayConnected() ? "CONNECTED (" + DEXWebServer.getActiveRelayHost() + ")" : "STANDBY / POLLING"));
        System.out.println(" Connected Phones : " + sessions.size() + " live remote session(s)");
        System.out.println(" AI Provider      : " + (active != null ? active.getType().getDisplayName() + " (" + active.getModel() + ")" : "None"));
        System.out.println(" API Key Status   : " + ((active != null && active.isConfigured()) || DEXConfig.hasApiKey() ? "CONFIGURED" : "NOT SET (Offline tools still active)"));
        System.out.println(" Registered Tools : " + ToolManager.getAllTools().size());
        System.out.println(" Workspace        : " + DEXConfig.getWorkspaceRoot());
        System.out.println("+---------------------------------------------------------------------+");
        System.out.println();
    }

    /**
     * Prints interactive terminal command guide.
     */
    public static void printHelp() {
        System.out.println();
        System.out.println("+---------------------------------------------------------------------+");
        System.out.println("|                     DEX TERMINAL COMMAND GUIDE                      |");
        System.out.println("+---------------------------------------------------------------------+");
        System.out.println("  qr / remote          Show scannable QR code, URLs & pairing PIN");
        System.out.println("  devices / connected  Inspect connected mobile remotes & paired list");
        System.out.println("  pin                  Generate a fresh 6-digit pairing PIN");
        System.out.println("  status               Show server, cloud relay, AI & tools status");
        System.out.println("  tools                List all executable Windows & workspace tools");
        System.out.println("  set-key <api-key>    Save API key for active AI provider");
        System.out.println("  use provider <name>  Switch provider (gemini, openai, groq, claude)");
        System.out.println("  clear                Clear active chat context in memory");
        System.out.println("  reset memory         Erase persistent memory.json after confirmation");
        System.out.println("  help                 Show this command guide");
        System.out.println("  exit / quit          Shut down DEX cleanly");
        System.out.println("+---------------------------------------------------------------------+");
        System.out.println(" Or type any natural command, e.g. 'open youtube', 'open vscode', 'screenshot'");
        System.out.println();
    }

    private static String renderTerminalQr(String url) {
        try {
            String prop = System.getProperty("sun.stdout.encoding");
            Charset cs = (prop != null && !prop.isBlank()) ? Charset.forName(prop) : Charset.defaultCharset();
            CharsetEncoder encoder = cs.newEncoder();
            if (encoder.canEncode('\u2588') && encoder.canEncode('\u2580') && encoder.canEncode('\u2584')) {
                return QRCodeGenerator.toTerminalString(url);
            }
        } catch (Exception ignored) {
        }
        return QRCodeGenerator.toAsciiTerminalString(url);
    }
}
