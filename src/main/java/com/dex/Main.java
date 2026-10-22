package com.dex;

import com.dex.config.DEXConfig;
import com.dex.memory.MemoryManager;
import com.dex.server.DEXWebServer;
import com.dex.ui.DEXDesktopApp;

import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;

/**
 * Main application entry point for DEX — Personal AI Assistant for Windows.
 * Coordinates system validation, persistent memory, embedded web server,
 * graceful shutdown hooks, native Windows desktop application, and CLI console.
 */
public class Main {

    private static DEXWebServer webServer;

    public static void main(String[] args) {
        List<String> argList = Arrays.asList(args);
        boolean serverOnly = argList.contains("--server-only") || argList.contains("-s");
        boolean cliOnly = argList.contains("--cli") || argList.contains("-c");
        boolean desktopExplicit = argList.contains("--desktop") || argList.contains("-d");

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

        // 7. Determine Execution Mode
        if (serverOnly) {
            System.out.println("[DEX] Running in --server-only mode. Access dashboard at: http://127.0.0.1:" + DEXConfig.getWebPort());
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (cliOnly || GraphicsEnvironment.isHeadless()) {
            System.out.println("[DEX] Running in interactive CLI console mode.");
            aiClient.runConsole();
        } else {
            // Default: Launch Native Windows Desktop Application
            System.out.println("[DEX] Launching Native Windows Desktop Application...");
            DEXDesktopApp.launch(aiClient);
        }
    }

    private static void printStartupBanner(int loadedMessages) {
        String webStatus = (webServer != null && webServer.isRunning())
                ? "http://" + DEXConfig.getWebHost() + ":" + DEXConfig.getWebPort()
                : "OFFLINE";

        System.out.println();
        System.out.println("=======================================================================");
        System.out.println("               DEX — PERSONAL AI ASSISTANT FOR WINDOWS");
        System.out.println("=======================================================================");
        System.out.println(" Host OS       : " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        System.out.println(" Java Runtime  : " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        System.out.println(" AI Provider   : " + com.dex.ai.ProviderManager.getActiveProviderType().getDisplayName());
        System.out.println(" Model         : " + com.dex.ai.ProviderManager.getActiveProvider().getModel());
        System.out.println(" Memory File   : data/memory.json (" + loadedMessages + " previous messages loaded)");
        System.out.println(" Workspace     : " + DEXConfig.getWorkspaceRoot());
        System.out.println(" Web Dashboard : " + webStatus);
        System.out.println(" Active Tools  : " + ToolManager.listTools());
        System.out.println("=======================================================================");
        System.out.println("Desktop application launching. Web dashboard available simultaneously.");
        System.out.println("=======================================================================");
        System.out.println();
    }
}