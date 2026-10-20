package com.dex;

import com.dex.config.DEXConfig;
import com.dex.memory.MemoryManager;
import com.dex.server.DEXWebServer;

import java.util.Arrays;
import java.util.List;

/**
 * Main application entry point for DEX — Personal AI Assistant for Windows.
 * Coordinates system validation, memory initialization, embedded web dashboard,
 * graceful shutdown hooks, and the interactive conversational console.
 */
public class Main {

    private static DEXWebServer webServer;

    public static void main(String[] args) {
        List<String> argList = Arrays.asList(args);
        boolean serverOnly = argList.contains("--server-only") || argList.contains("-s");
        boolean isRemoteEnabled = argList.contains("--remote");

        // 1. Validate environment & configuration
        boolean envOk = DEXConfig.validateEnvironment();
        if (!envOk) {
            System.err.println("DEX Warning: Failed to initialize all storage directories properly.");
        }

        // 2. Initialize Persistent Memory Manager
        MemoryManager memoryManager = new MemoryManager();
        int loadedMessages = memoryManager.size();

        // 3. Initialize AI Client
        AIClient aiClient = new AIClient(memoryManager);

        // 4. Start Embedded Web Dashboard Server in background
        try {
            webServer = new DEXWebServer(aiClient);
            webServer.start();
        } catch (Exception e) {
            System.err.println("DEX Warning: Could not start Web Dashboard on port " + DEXConfig.getWebPort() + ": " + e.getMessage());
        }

        // 5. Register Graceful JVM Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[DEX] Graceful shutdown initiated...");
            if (webServer != null) {
                webServer.stop();
            }
            memoryManager.saveMemory();
            System.out.println("[DEX] Memory flushed to disk. System offline. Goodbye!");
        }, "DEX-Shutdown-Hook"));

        // 6. Display Startup Banner
        printStartupBanner(loadedMessages);

        // 7. Check for interactive key prompt if key is not configured and interactive console exists
        if (!DEXConfig.hasApiKey() && System.console() != null) {
            System.out.println("Notice: FreeLLMAPI key is not configured (DEX_API_KEY environment variable is unset).");
            System.out.println("You can use all local Windows tools immediately without a key.");
            System.out.print("If you have an API key and wish to enter it for this session, paste it now (or press Enter to skip): ");
            char[] keyChars = System.console().readPassword();
            if (keyChars != null && keyChars.length > 0) {
                String inputKey = new String(keyChars).trim();
                if (!inputKey.isBlank()) {
                    DEXConfig.setRuntimeApiKey(inputKey);
                    System.out.println("API key set for this session.");
                }
            }
        }

        // 8. Run CLI Console or wait if server-only
        if (serverOnly) {
            System.out.println("[DEX] Running in --server-only mode. Access dashboard at: http://127.0.0.1:" + DEXConfig.getWebPort());
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            aiClient.runConsole();
        }
    }

    private static void printStartupBanner(int loadedMessages) {
        String apiKeyStatus = DEXConfig.hasApiKey() ? "CONFIGURED" : "NOT SET (Local Tools Active)";
        String webStatus = (webServer != null && webServer.isRunning())
                ? "http://" + DEXConfig.getWebHost() + ":" + DEXConfig.getWebPort()
                : "OFFLINE";

        System.out.println();
        System.out.println("=======================================================================");
        System.out.println("               DEX — PERSONAL AI ASSISTANT FOR WINDOWS");
        System.out.println("=======================================================================");
        System.out.println(" Host OS       : " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        System.out.println(" Java Runtime  : " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        System.out.println(" AI Endpoint   : " + DEXConfig.getBaseUrl() + " (Model: " + DEXConfig.getModel() + ")");
        System.out.println(" DEX_API_KEY   : " + apiKeyStatus);
        System.out.println(" Memory File   : data/memory.json (" + loadedMessages + " previous messages loaded)");
        System.out.println(" Workspace     : " + DEXConfig.getWorkspaceRoot());
        System.out.println(" Web Dashboard : " + webStatus);
        System.out.println(" Remote Token  : " + DEXConfig.getRemoteAuthToken());
        System.out.println(" Active Tools  : " + ToolManager.listTools());
        System.out.println("=======================================================================");
        System.out.println("Type 'help', 'list tools', or a natural language command (e.g. 'open calculator').");
        System.out.println("Type 'exit' to quit.");
        System.out.println();
    }
}