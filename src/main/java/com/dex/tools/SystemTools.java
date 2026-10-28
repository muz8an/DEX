package com.dex.tools;

import com.dex.WindowsController;
import com.dex.config.DEXConfig;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * System utility tools for DEX: Time, System Diagnostics, Desktop Screenshots,
 * Safe URL Launching, and Speech Synthesis.
 */
public class SystemTools {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy HH:mm:ss (z)");

    /**
     * Returns formatted current local date and time.
     */
    public static String getCurrentTime(String ignored) {
        LocalDateTime now = LocalDateTime.now();
        ZoneId zone = ZoneId.systemDefault();
        return "Current Date & Time: " + now.atZone(zone).format(DATE_FORMATTER);
    }

    /**
     * Returns a summary of host OS, hardware resources, and Java runtime.
     */
    public static String getSystemInfo(String ignored) {
        Runtime runtime = Runtime.getRuntime();
        long maxMem = runtime.maxMemory() / (1024 * 1024);
        long totalMem = runtime.totalMemory() / (1024 * 1024);
        long freeMem = runtime.freeMemory() / (1024 * 1024);
        long usedMem = totalMem - freeMem;

        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        String userName = System.getProperty("user.name");
        int cores = runtime.availableProcessors();

        return String.format(Locale.ROOT,
                "=== System Diagnostics ===\n" +
                "• OS: %s (v%s, %s)\n" +
                "• User: %s\n" +
                "• CPU Cores: %d logical processors\n" +
                "• JVM Memory: %d MB used / %d MB allocated (Max: %d MB)\n" +
                "• Java Runtime: %s (%s)\n" +
                "• Workspace: %s",
                osName, osVersion, osArch, userName, cores,
                usedMem, totalMem, maxMem, javaVersion, javaVendor,
                DEXConfig.getWorkspaceRoot());
    }

    /**
     * Captures a full desktop screenshot and saves it as a PNG file in data/screenshots/.
     */
    public static String takeScreenshot(String ignored) {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return "Error: Cannot capture screenshot in a headless graphical environment.";
            }

            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage screenCapture = robot.createScreenCapture(screenRect);

            Path screenshotDir = DEXConfig.getScreenshotsDirectory();
            Files.createDirectories(screenshotDir);

            String filename = "dex_screenshot_" + System.currentTimeMillis() + ".png";
            Path targetFile = screenshotDir.resolve(filename);

            boolean written = ImageIO.write(screenCapture, "png", targetFile.toFile());
            if (!written) {
                return "Error: ImageIO failed to encode screenshot as PNG.";
            }

            return "Screenshot captured successfully! Saved to: " + targetFile.toString()
                    + " (" + (Files.size(targetFile) / 1024) + " KB)";

        } catch (AWTException e) {
            return "Screen capture error: Robot initialization failed: " + e.getMessage();
        } catch (IOException e) {
            return "Screen capture error: Could not save screenshot file: " + e.getMessage();
        } catch (Exception e) {
            return "Screen capture failed: " + e.getMessage();
        }
    }

    /**
     * Validates and opens an approved HTTP/HTTPS URL in the default browser.
     */
    public static String openUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return "Error: Please specify a valid web URL.";
        }

        String url = urlString.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        // Validate URL format and prevent command injection
        if (url.contains(" ") || url.contains("\"") || url.contains("|") || url.contains(";") || url.contains("`") || url.contains("\n") || url.contains("\r")) {
            return "Error: URL contains illegal characters or potential injection tokens.";
        }

        WindowsController.LaunchResult result = WindowsController.executeOpenDefaultBrowser(url);
        if (result.isSuccess()) {
            return "Opened URL in default browser: " + url + (result.getPid() != null ? " (PID: " + result.getPid() + ")" : "");
        } else {
            return "Failed to open URL: " + result.getError();
        }
    }

    /**
     * Optional Windows Text-to-Speech using PowerShell System.Speech.Synthesis.
     */
    public static String speak(String text) {
        if (text == null || text.isBlank()) {
            return "Error: No text provided to speak.";
        }

        // Sanitize text for PowerShell string literal
        String safeText = text.replace("\"", " ").replace("'", " ").replace("`", " ").trim();
        if (safeText.length() > 500) {
            safeText = safeText.substring(0, 500);
        }

        try {
            String psCommand = "Add-Type -AssemblyName System.Speech; "
                    + "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                    + "$synth.Speak('" + safeText + "');";

            new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", psCommand).start();
            return "Speaking: \"" + safeText + "\"";
        } catch (Exception e) {
            return "Voice synthesis unavailable: " + e.getMessage();
        }
    }

    /**
     * Lists running desktop processes safely using Java's ProcessHandle API.
     */
    public static String listRunningProcesses(String filter) {
        String filterLower = filter != null ? filter.trim().toLowerCase(Locale.ROOT) : "";
        StringBuilder sb = new StringBuilder("=== Active Windows Processes ===\n");
        int count = 0;

        List<ProcessHandle> processes = ProcessHandle.allProcesses()
                .filter(p -> p.info().command().isPresent())
                .sorted(Comparator.comparing(p -> p.info().command().orElse("")))
                .toList();

        for (ProcessHandle ph : processes) {
            String cmd = ph.info().command().orElse("");
            String shortName = cmd.contains(File.separator) ? cmd.substring(cmd.lastIndexOf(File.separator) + 1) : cmd;

            if (filterLower.isEmpty() || shortName.toLowerCase(Locale.ROOT).contains(filterLower) || cmd.toLowerCase(Locale.ROOT).contains(filterLower)) {
                sb.append("• [PID ").append(ph.pid()).append("] ").append(shortName);
                ph.info().totalCpuDuration().ifPresent(d -> sb.append(" (CPU: ").append(d.toMillis()).append("ms)"));
                sb.append("\n");
                count++;
                if (count >= 30) {
                    sb.append("... and ").append(processes.size() - count).append(" more processes.\n");
                    break;
                }
            }
        }

        if (count == 0) {
            return "No matching processes found" + (filterLower.isEmpty() ? "." : " for filter: " + filter);
        }
        return sb.toString();
    }

    /**
     * Captures screenshot as a Base64 string for Vision AI models.
     */
    public static String captureScreenshotBase64() {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return null;
            }
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage capture = robot.createScreenCapture(screenRect);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(capture, "png", baos);
            return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            System.err.println("[DEX Screen] Base64 screenshot capture failed: " + e.getMessage());
            return null;
        }
    }
}
