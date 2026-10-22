package com.dex;

import java.awt.Desktop;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Manages Windows application launches and desktop navigation with fuzzy matching,
 * typo tolerance, dedicated YouTube/Chrome/Google/GitHub URL handling,
 * mockable process execution, and strict diagnostic reporting.
 */
public class WindowsController {

    @FunctionalInterface
    public interface ProcessLauncher {
        Process launch(List<String> command) throws IOException;
    }

    @FunctionalInterface
    public interface CommandSupplier {
        List<String> getCommand() throws IOException;
    }

    private static ProcessLauncher processLauncher = command -> new ProcessBuilder(command).start();

    public static synchronized void setProcessLauncher(ProcessLauncher launcher) {
        processLauncher = (launcher != null) ? launcher : (command -> new ProcessBuilder(command).start());
    }

    public static synchronized void resetProcessLauncher() {
        processLauncher = command -> new ProcessBuilder(command).start();
    }

    /**
     * Structured launch result representing execution status and diagnostic metadata.
     */
    public static class LaunchResult {
        private final boolean success;
        private final String action;
        private final String target;
        private final String details;
        private final String error;
        private final Long pid;

        public LaunchResult(boolean success, String action, String target, String details, String error, Long pid) {
            this.success = success;
            this.action = action;
            this.target = target;
            this.details = details;
            this.error = error;
            this.pid = pid;
        }

        public static LaunchResult success(String action, String target, String details, Long pid) {
            return new LaunchResult(true, action, target, details, null, pid);
        }

        public static LaunchResult failure(String action, String target, String error) {
            return new LaunchResult(false, action, target, null, error, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getAction() {
            return action;
        }

        public String getTarget() {
            return target;
        }

        public String getDetails() {
            return details;
        }

        public String getError() {
            return error;
        }

        public Long getPid() {
            return pid;
        }

        public String toDisplayString() {
            if (success) {
                StringBuilder sb = new StringBuilder();
                sb.append("Successfully launched ").append(target);
                if (pid != null && pid > 0) {
                    sb.append(" (PID: ").append(pid).append(")");
                }
                if (details != null && !details.isBlank()) {
                    sb.append(". ").append(details);
                } else {
                    sb.append(".");
                }
                return sb.toString();
            } else {
                return "Failed to launch " + target + (error != null ? ": " + error : ".");
            }
        }

        @Override
        public String toString() {
            return toDisplayString();
        }
    }

    private static final Map<String, ApplicationEntry> APPS = new LinkedHashMap<>();

    public static class ApplicationEntry {
        private final String canonicalName;
        private final String[] command;
        private final CommandSupplier dynamicCommandSupplier;
        private final List<String> aliases;
        private final String description;

        public ApplicationEntry(String canonicalName, String[] command, String description, String... aliases) {
            this(canonicalName, command, null, description, aliases);
        }

        public ApplicationEntry(String canonicalName, String[] command, CommandSupplier dynamicCommandSupplier, String description, String... aliases) {
            this.canonicalName = canonicalName.toLowerCase(Locale.ROOT);
            this.command = command != null ? command.clone() : new String[0];
            this.dynamicCommandSupplier = dynamicCommandSupplier;
            this.description = description;
            this.aliases = new ArrayList<>(Arrays.asList(aliases));
            if (!this.aliases.contains(this.canonicalName)) {
                this.aliases.add(this.canonicalName);
            }
        }

        public String getCanonicalName() {
            return canonicalName;
        }

        public String[] getCommand() {
            return command.clone();
        }

        public CommandSupplier getDynamicCommandSupplier() {
            return dynamicCommandSupplier;
        }

        public List<String> getAliases() {
            return Collections.unmodifiableList(aliases);
        }

        public String getDescription() {
            return description;
        }
    }

    static {
        registerDefaultApps();
    }

    private static void registerDefaultApps() {
        String userHome = System.getProperty("user.home");
        String downloadsPath = userHome + File.separator + "Downloads";
        String documentsPath = userHome + File.separator + "Documents";
        String desktopPath = userHome + File.separator + "Desktop";

        // Web Browsers & Web Destinations
        registerApp(new ApplicationEntry(
                "chrome",
                new String[]{"cmd.exe", "/c", "start", "", "https://www.google.com"},
                () -> {
                    String chrome = findChromeExecutable();
                    if (chrome != null) {
                        return Collections.singletonList(chrome);
                    }
                    throw new FileNotFoundException("Google Chrome is not installed in standard Windows directories.");
                },
                "Google Chrome Browser",
                "google chrome", "browser", "crome", "chrom"
        ));

        registerApp(new ApplicationEntry(
                "youtube",
                new String[]{"cmd.exe", "/c", "start", "", "https://www.youtube.com"},
                () -> Arrays.asList("cmd.exe", "/c", "start", "", "https://www.youtube.com"),
                "YouTube (Default Browser)",
                "yt", "you tube", "video", "youtube.com", "youtu.be", "watch youtube",
                "go to youtube", "launch youtube", "open youtube", "play youtube",
                "youtube in default browser", "youtube in chrome"
        ));

        registerApp(new ApplicationEntry(
                "google",
                new String[]{"cmd.exe", "/c", "start", "", "https://www.google.com"},
                () -> Arrays.asList("cmd.exe", "/c", "start", "", "https://www.google.com"),
                "Google Search (Default Browser)",
                "google search", "google.com", "open google", "launch google", "go to google",
                "google in default browser", "google in chrome"
        ));

        registerApp(new ApplicationEntry(
                "github",
                new String[]{"cmd.exe", "/c", "start", "", "https://github.com"},
                () -> Arrays.asList("cmd.exe", "/c", "start", "", "https://github.com"),
                "GitHub (Default Browser)",
                "git hub", "github.com", "open github", "launch github", "go to github",
                "github in default browser"
        ));

        // Core Windows Utilities
        registerApp(new ApplicationEntry(
                "notepad",
                new String[]{"notepad.exe"},
                () -> Arrays.asList("cmd.exe", "/c", "start", "", "notepad.exe"),
                "Windows Notepad Text Editor",
                "note pad", "editor", "text editor"
        ));

        registerApp(new ApplicationEntry(
                "calculator",
                new String[]{"calc.exe"},
                () -> Arrays.asList("cmd.exe", "/c", "start", "", "calculator:"),
                "Windows Calculator",
                "calc", "calculater", "calulator", "cal"
        ));

        registerApp(new ApplicationEntry(
                "vscode",
                new String[]{"cmd.exe", "/c", "start", "", "code"},
                () -> {
                    String vsCodePath = findVsCodeExecutable();
                    if (vsCodePath != null) {
                        if (vsCodePath.toLowerCase(Locale.ROOT).endsWith(".cmd")) {
                            return Arrays.asList("cmd.exe", "/c", "start", "", vsCodePath);
                        } else {
                            return Collections.singletonList(vsCodePath);
                        }
                    }
                    throw new FileNotFoundException("Visual Studio Code (Code.exe or code.cmd) was not found in standard paths or PATH.");
                },
                "Visual Studio Code",
                "vs code", "visual studio code", "vsc", "code editor", "code"
        ));

        registerApp(new ApplicationEntry(
                "file explorer",
                new String[]{"explorer.exe"},
                () -> Collections.singletonList("explorer.exe"),
                "Windows File Explorer",
                "explorer", "files", "my files", "file manager"
        ));

        registerApp(new ApplicationEntry(
                "settings",
                new String[]{"cmd.exe", "/c", "start", "", "ms-settings:"},
                () -> Arrays.asList("cmd.exe", "/c", "start", "", "ms-settings:"),
                "Windows Settings",
                "windows settings", "pc settings", "control panel", "system settings"
        ));

        registerApp(new ApplicationEntry(
                "cmd",
                new String[]{"cmd.exe", "/c", "start", "", "cmd.exe"},
                () -> Arrays.asList("cmd.exe", "/c", "start", "", "cmd.exe"),
                "Windows Command Prompt",
                "command prompt", "terminal", "cmd.exe", "cmd prompt"
        ));

        registerApp(new ApplicationEntry(
                "powershell",
                new String[]{"cmd.exe", "/c", "start", "", "powershell.exe"},
                () -> Arrays.asList("cmd.exe", "/c", "start", "", "powershell.exe"),
                "Windows PowerShell",
                "powershell.exe", "windows powershell", "pwsh"
        ));

        // Approved User Directories
        registerApp(new ApplicationEntry(
                "downloads",
                new String[]{"explorer.exe", downloadsPath},
                () -> Arrays.asList("explorer.exe", downloadsPath),
                "Downloads Folder",
                "downloads folder", "my downloads", "download", "download folder"
        ));

        registerApp(new ApplicationEntry(
                "documents",
                new String[]{"explorer.exe", documentsPath},
                () -> Arrays.asList("explorer.exe", documentsPath),
                "Documents Folder",
                "documents folder", "my documents", "docs", "doc folder"
        ));

        registerApp(new ApplicationEntry(
                "desktop",
                new String[]{"explorer.exe", desktopPath},
                () -> Arrays.asList("explorer.exe", desktopPath),
                "Desktop Folder",
                "desktop folder", "my desktop"
        ));
    }

    public static synchronized void registerApp(ApplicationEntry entry) {
        if (entry != null) {
            APPS.put(entry.getCanonicalName(), entry);
            for (String alias : entry.getAliases()) {
                APPS.put(alias.toLowerCase(Locale.ROOT), entry);
            }
        }
    }

    /**
     * Executes an application launch and returns the display-friendly status string.
     */
    public static String openApp(String appName) {
        LaunchResult result = executeAppLaunch(appName);
        return result.toDisplayString();
    }

    /**
     * Attempts to open an approved application, folder, or web destination with full structured result.
     */
    public static LaunchResult executeAppLaunch(String appName) {
        if (appName == null || appName.isBlank()) {
            return LaunchResult.failure("open_app", "unknown", "Please specify an application or destination to open.");
        }

        String raw = appName.trim();
        String normalized = raw.toLowerCase(Locale.ROOT)
                .replaceAll("^(can you |could you |please )?(open|launch|start|run|go to|browse to|visit|show)\\s+", "")
                .replaceAll("\\s+(for me|please|now)$", "")
                .replaceAll("[\\.\\?!,;]+$", "")
                .trim();

        if (normalized.startsWith("my ")) {
            normalized = normalized.substring(3).trim();
        }

        // Special destination routing
        if (normalized.contains("youtube") || normalized.contains("youtu.be") || normalized.equals("yt")) {
            boolean preferChrome = normalized.contains("chrome");
            return executeOpenYouTube(preferChrome);
        }

        if (normalized.contains("google") && !normalized.contains("chrome")) {
            return executeOpenGoogle(false);
        }

        if (normalized.contains("github") || normalized.equals("git hub")) {
            return executeOpenGitHub();
        }

        if (normalized.equals("chrome") || normalized.equals("google chrome") || normalized.equals("browser") || normalized.equals("crome")) {
            return executeOpenChrome(null);
        }

        if (normalized.equals("vscode") || normalized.equals("vs code") || normalized.equals("visual studio code") || normalized.equals("code") || normalized.equals("vsc")) {
            return executeOpenVsCode();
        }

        ApplicationEntry target = findApplication(normalized);

        if (target == null) {
            logDiagnostic("open_app", appName, "none", null, "UNSUPPORTED");
            return LaunchResult.failure("open_app", appName, "Unsupported application: " + appName + ". "
                    + "Available options: Chrome, YouTube, Google, GitHub, Notepad, Calculator, VS Code, "
                    + "File Explorer, Downloads, Documents, Desktop.");
        }

        try {
            List<String> cmd;
            if (target.getDynamicCommandSupplier() != null) {
                cmd = target.getDynamicCommandSupplier().getCommand();
            } else {
                cmd = Arrays.asList(target.getCommand());
            }

            return startProcess("open_app", target.getCanonicalName(), cmd, "Application window requested");
        } catch (FileNotFoundException e) {
            logDiagnosticError("open_app", target.getCanonicalName(), target.getCanonicalName(), null, e);
            return LaunchResult.failure("open_app", target.getCanonicalName(), e.getMessage());
        } catch (Exception e) {
            logDiagnosticError("open_app", target.getCanonicalName(), target.getCanonicalName(), null, e);
            return LaunchResult.failure("open_app", target.getCanonicalName(), "Failed to prepare command: " + e.getMessage());
        }
    }

    /**
     * Opens YouTube in Google Chrome or the system default browser.
     */
    public static String openYouTube(boolean preferChrome) {
        return executeOpenYouTube(preferChrome).toDisplayString();
    }

    public static LaunchResult executeOpenYouTube(boolean preferChrome) {
        String url = "https://www.youtube.com";
        String targetDesc = preferChrome ? "YouTube in Google Chrome" : "YouTube in default browser";

        if (preferChrome) {
            String chrome = findChromeExecutable();
            if (chrome != null) {
                List<String> cmd = Arrays.asList(chrome, url);
                return startProcess("open_url", targetDesc, cmd, "Opened in Google Chrome");
            }
        }

        List<String> cmd = Arrays.asList("cmd.exe", "/c", "start", "", url);
        return startProcess("open_url", targetDesc, cmd, "Opened in default browser");
    }

    /**
     * Opens Google Search in Google Chrome or the system default browser.
     */
    public static String openGoogle(boolean preferChrome) {
        return executeOpenGoogle(preferChrome).toDisplayString();
    }

    public static LaunchResult executeOpenGoogle(boolean preferChrome) {
        String url = "https://www.google.com";
        String targetDesc = preferChrome ? "Google in Google Chrome" : "Google in default browser";

        if (preferChrome) {
            String chrome = findChromeExecutable();
            if (chrome != null) {
                List<String> cmd = Arrays.asList(chrome, url);
                return startProcess("open_url", targetDesc, cmd, "Opened in Google Chrome");
            }
        }

        List<String> cmd = Arrays.asList("cmd.exe", "/c", "start", "", url);
        return startProcess("open_url", targetDesc, cmd, "Opened in default browser");
    }

    /**
     * Opens GitHub in the system default browser.
     */
    public static String openGitHub() {
        return executeOpenGitHub().toDisplayString();
    }

    public static LaunchResult executeOpenGitHub() {
        String url = "https://github.com";
        List<String> cmd = Arrays.asList("cmd.exe", "/c", "start", "", url);
        return startProcess("open_url", "GitHub in default browser", cmd, "Opened in default browser");
    }

    /**
     * Opens Google Chrome with an optional destination URL.
     */
    public static String openChrome(String url) {
        return executeOpenChrome(url).toDisplayString();
    }

    public static LaunchResult executeOpenChrome(String url) {
        String chrome = findChromeExecutable();
        if (chrome == null) {
            logDiagnosticError("open_app", "Google Chrome", "chrome.exe", null, new FileNotFoundException("chrome.exe not found"));
            return LaunchResult.failure("open_app", "Google Chrome", "Google Chrome is not installed in standard Windows directories.");
        }

        List<String> cmd;
        if (url != null && !url.isBlank()) {
            cmd = Arrays.asList(chrome, url.trim());
        } else {
            cmd = Collections.singletonList(chrome);
        }

        return startProcess("open_app", "Google Chrome", cmd, url != null ? "Opened URL: " + url : "Opened Chrome browser");
    }

    /**
     * Opens Visual Studio Code, resolving Code.exe or code.cmd.
     */
    public static String openVsCode() {
        return executeOpenVsCode().toDisplayString();
    }

    public static LaunchResult executeOpenVsCode() {
        String vsCodePath = findVsCodeExecutable();
        if (vsCodePath == null) {
            logDiagnosticError("open_app", "Visual Studio Code", "Code.exe", null, new FileNotFoundException("VS Code not found"));
            return LaunchResult.failure("open_app", "Visual Studio Code", "Visual Studio Code (Code.exe or code.cmd) was not found in standard installation paths or PATH.");
        }

        List<String> cmd;
        if (vsCodePath.toLowerCase(Locale.ROOT).endsWith(".cmd")) {
            cmd = Arrays.asList("cmd.exe", "/c", "start", "", vsCodePath);
        } else {
            cmd = Collections.singletonList(vsCodePath);
        }

        return startProcess("open_app", "Visual Studio Code", cmd, "Launcher path: " + vsCodePath);
    }

    /**
     * Opens an approved web URL in the system default browser.
     */
    public static String openDefaultBrowser(String url) {
        return executeOpenDefaultBrowser(url).toDisplayString();
    }

    public static LaunchResult executeOpenDefaultBrowser(String url) {
        if (url == null || url.isBlank()) {
            return LaunchResult.failure("open_url", "default_browser", "URL cannot be empty.");
        }

        List<String> cmd = Arrays.asList("cmd.exe", "/c", "start", "", url.trim());
        return startProcess("open_url", "Default Browser (" + url + ")", cmd, "Opened URL via Windows Shell");
    }

    /**
     * Core process starter executing commands via the configured ProcessLauncher.
     */
    private static LaunchResult startProcess(String action, String target, List<String> command, String successDetails) {
        try {
            logDiagnostic(action, target, command.get(0), command, "LAUNCHING");
            Process process = processLauncher.launch(command);

            Long pid = null;
            try {
                pid = process.pid();
            } catch (UnsupportedOperationException ignored) {
            }

            logDiagnostic(action, target, command.get(0), command, "SUCCESS" + (pid != null ? " (PID: " + pid + ")" : ""));
            return LaunchResult.success(action, target, successDetails, pid);

        } catch (IOException e) {
            logDiagnosticError(action, target, command != null && !command.isEmpty() ? command.get(0) : "unknown", command, e);
            return LaunchResult.failure(action, target, e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (Exception e) {
            logDiagnosticError(action, target, command != null && !command.isEmpty() ? command.get(0) : "unknown", command, e);
            return LaunchResult.failure(action, target, "Unexpected error: " + e.getMessage());
        }
    }

    private static void logDiagnostic(String action, String target, String executableOrHandler, List<String> command, String status) {
        System.out.println("[DEX WindowsController] action=" + action
                + " | target=" + target
                + " | handler=" + executableOrHandler
                + " | cmd=" + (command != null ? String.join(" ", command) : "none")
                + " | status=" + status);
    }

    private static void logDiagnosticError(String action, String target, String executableOrHandler, List<String> command, Throwable t) {
        System.err.println("[DEX WindowsController ERROR] action=" + action
                + " | target=" + target
                + " | handler=" + (executableOrHandler != null ? executableOrHandler : "unknown")
                + " | cmd=" + (command != null ? String.join(" ", command) : "none")
                + " | exception=" + t.getClass().getName()
                + " | error=" + t.getMessage());
    }

    /**
     * Locates the Google Chrome binary on standard Windows installation directories or PATH.
     */
    public static String findChromeExecutable() {
        String localAppData = System.getenv("LOCALAPPDATA");
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");

        List<String> candidatePaths = new ArrayList<>();
        if (programFiles != null) {
            candidatePaths.add(programFiles + "\\Google\\Chrome\\Application\\chrome.exe");
        }
        if (programFilesX86 != null) {
            candidatePaths.add(programFilesX86 + "\\Google\\Chrome\\Application\\chrome.exe");
        }
        if (localAppData != null) {
            candidatePaths.add(localAppData + "\\Google\\Chrome\\Application\\chrome.exe");
        }
        candidatePaths.add("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
        candidatePaths.add("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe");

        for (String path : candidatePaths) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                return path;
            }
        }

        return findInPath("chrome.exe");
    }

    /**
     * Locates the Microsoft Edge binary on standard Windows installation directories.
     */
    public static String findEdgeExecutable() {
        String localAppData = System.getenv("LOCALAPPDATA");
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");

        List<String> candidatePaths = new ArrayList<>();
        if (programFilesX86 != null) {
            candidatePaths.add(programFilesX86 + "\\Microsoft\\Edge\\Application\\msedge.exe");
        }
        if (programFiles != null) {
            candidatePaths.add(programFiles + "\\Microsoft\\Edge\\Application\\msedge.exe");
        }
        if (localAppData != null) {
            candidatePaths.add(localAppData + "\\Microsoft\\Edge\\Application\\msedge.exe");
        }
        candidatePaths.add("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe");
        candidatePaths.add("C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe");

        for (String path : candidatePaths) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                return path;
            }
        }

        return findInPath("msedge.exe");
    }

    /**
     * Locates Visual Studio Code (Code.exe or code.cmd) across standard directories and PATH.
     */
    public static String findVsCodeExecutable() {
        String localAppData = System.getenv("LOCALAPPDATA");
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");

        List<String> candidatePaths = new ArrayList<>();
        if (localAppData != null) {
            candidatePaths.add(localAppData + "\\Programs\\Microsoft VS Code\\Code.exe");
            candidatePaths.add(localAppData + "\\Programs\\Microsoft VS Code\\bin\\code.cmd");
        }
        if (programFiles != null) {
            candidatePaths.add(programFiles + "\\Microsoft VS Code\\Code.exe");
            candidatePaths.add(programFiles + "\\Microsoft VS Code\\bin\\code.cmd");
        }
        if (programFilesX86 != null) {
            candidatePaths.add(programFilesX86 + "\\Microsoft VS Code\\Code.exe");
            candidatePaths.add(programFilesX86 + "\\Microsoft VS Code\\bin\\code.cmd");
        }

        for (String path : candidatePaths) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                return path;
            }
        }

        String inPathExe = findInPath("Code.exe");
        if (inPathExe != null) {
            return inPathExe;
        }

        return findInPath("code.cmd");
    }

    /**
     * Helper to search for an executable in the system PATH.
     */
    public static String findInPath(String executableName) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return null;
        }
        for (String dir : pathEnv.split(Pattern.quote(File.pathSeparator))) {
            try {
                File candidate = new File(dir.trim(), executableName);
                if (candidate.exists() && candidate.isFile()) {
                    return candidate.getAbsolutePath();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Resolves an app query using direct match, word boundaries, and Levenshtein edit distance.
     */
    public static ApplicationEntry findApplication(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }

        String q = query.toLowerCase(Locale.ROOT)
                .replaceAll("^[\\.\\?!,;]+", "")
                .replaceAll("[\\.\\?!,;]+$", "")
                .trim();

        // 1. Exact alias/name match
        if (APPS.containsKey(q)) {
            return APPS.get(q);
        }

        // 2. Strip "my " prefix (e.g. "my downloads" -> "downloads")
        if (q.startsWith("my ")) {
            String stripped = q.substring(3).trim();
            if (APPS.containsKey(stripped)) {
                return APPS.get(stripped);
            }
        }

        // 3. Word-boundary phrase match for aliases
        for (Map.Entry<String, ApplicationEntry> entry : APPS.entrySet()) {
            String key = entry.getKey();
            if (Pattern.compile("\\b" + Pattern.quote(key) + "\\b").matcher(q).find()) {
                return entry.getValue();
            }
        }

        // 4. Fuzzy Levenshtein distance check (only for single-word queries with length >= 4, e.g. typos like "calculater")
        if (!q.contains(" ") && q.length() >= 4) {
            ApplicationEntry bestMatch = null;
            int bestDistance = 3; // Max allowable distance

            for (Map.Entry<String, ApplicationEntry> entry : APPS.entrySet()) {
                String key = entry.getKey();
                if (!key.contains(" ") && Math.abs(q.length() - key.length()) <= 2) {
                    int dist = computeLevenshteinDistance(q, key);
                    if (dist < bestDistance) {
                        bestDistance = dist;
                        bestMatch = entry.getValue();
                    }
                }
            }
            if (bestMatch != null) {
                return bestMatch;
            }
        }

        return null;
    }

    public static List<String> listSupportedApps() {
        Set<String> unique = new LinkedHashSet<>();
        for (ApplicationEntry entry : APPS.values()) {
            unique.add(entry.getCanonicalName() + " (" + entry.getDescription() + ")");
        }
        return new ArrayList<>(unique);
    }

    private static int computeLevenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) {
            costs[j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= s2.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        s1.charAt(i - 1) == s2.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[s2.length()];
    }
}
