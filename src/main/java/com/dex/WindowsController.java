package com.dex;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.dex.config.DEXConfig;

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
    private static final Map<String, ApplicationEntry> DYNAMIC_APPS = new ConcurrentHashMap<>();
    private static volatile boolean systemAppsScanned = false;

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
        registerCommonProtocols();
        scanSystemAppsAsync();
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

    private static void registerCommonProtocols() {
        registerDynamicProtocol("whatsapp", "whatsapp:", "WhatsApp Messenger", "wa", "whats app", "whatsapp desktop");
        registerDynamicProtocol("spotify", "spotify:", "Spotify Music Player", "spotify music", "spot");
        registerDynamicProtocol("steam", "steam:", "Steam Gaming Platform", "steam client", "valve steam");
        registerDynamicProtocol("discord", "discord:", "Discord Chat", "discord app");
        registerDynamicProtocol("telegram", "tg:", "Telegram Desktop", "tg", "telegram messenger");
    }

    private static void registerDynamicProtocol(String canonical, String protocolUri, String desc, String... aliases) {
        List<String> aliasList = new ArrayList<>(Arrays.asList(aliases));
        if (!aliasList.contains(canonical)) {
            aliasList.add(canonical);
        }
        ApplicationEntry entry = new ApplicationEntry(
                canonical,
                new String[]{"cmd.exe", "/c", "start", "", protocolUri},
                () -> Arrays.asList("cmd.exe", "/c", "start", "", protocolUri),
                desc,
                aliasList.toArray(new String[0])
        );
        DYNAMIC_APPS.put(canonical, entry);
        for (String a : aliasList) {
            DYNAMIC_APPS.put(a.toLowerCase(Locale.ROOT), entry);
        }
    }

    public static void scanSystemAppsAsync() {
        Thread t = new Thread(() -> {
            try {
                scanSystemApps();
            } catch (Exception ignored) {
            }
        }, "DEX-SystemApp-Scanner");
        t.setDaemon(true);
        t.start();
    }

    public static synchronized void scanSystemApps() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "Get-StartApps | Select-Object -Property Name, AppID | ConvertTo-Csv -NoTypeInformation"
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean isHeader = true;
                while ((line = reader.readLine()) != null) {
                    if (isHeader) {
                        isHeader = false;
                        continue;
                    }
                    parseAndRegisterAppCsvLine(line);
                }
            }
            proc.waitFor(15, TimeUnit.SECONDS);
            systemAppsScanned = true;
        } catch (Exception ignored) {
        }
    }

    private static void parseAndRegisterAppCsvLine(String line) {
        if (line == null || line.isBlank()) return;
        String[] parts = line.split("\",\"");
        if (parts.length < 2) return;
        String name = parts[0].replaceAll("^\"|\"$", "").trim();
        String appId = parts[1].replaceAll("^\"|\"$", "").trim();
        if (name.isBlank() || appId.isBlank()) return;

        String canonical = name.toLowerCase(Locale.ROOT);
        List<String> aliases = new ArrayList<>();
        aliases.add(canonical);

        String cleanName = canonical.replaceAll("[\\(\\)\\[\\]\\{\\}\\-\\._]", " ").replaceAll("\\s+", " ").trim();
        if (!cleanName.equals(canonical)) {
            aliases.add(cleanName);
        }

        // Specific smart aliases for famous apps & games
        if (canonical.contains("whatsapp")) {
            aliases.add("whatsapp");
            aliases.add("wa");
            aliases.add("whats app");
            aliases.add("whatsapp desktop");
        } else if (canonical.contains("spotify")) {
            aliases.add("spotify");
            aliases.add("spot");
        } else if (canonical.contains("steam") && !canonical.contains("tools") && !canonical.contains("support")) {
            aliases.add("steam");
            aliases.add("steam client");
        } else if (canonical.contains("grand theft auto")) {
            aliases.add("gta");
            aliases.add("gta 5");
            aliases.add("gta v");
            aliases.add("gtav");
            aliases.add("gta five");
        } else if (canonical.contains("red dead redemption")) {
            aliases.add("rdr");
            aliases.add("rdr 2");
            aliases.add("rdr2");
            aliases.add("red dead 2");
        } else if (canonical.contains("forza")) {
            aliases.add("forza");
            aliases.add("forza horizon");
            aliases.add("forza 6");
        } else if (canonical.contains("pubg")) {
            aliases.add("pubg");
            aliases.add("battlegrounds");
        } else if (canonical.contains("dead as disco")) {
            aliases.add("dead as disco");
            aliases.add("disco");
        } else if (canonical.contains("detroit")) {
            aliases.add("detroit");
            aliases.add("detroit become human");
        } else if (canonical.contains("ea sports") || canonical.contains("fc 26")) {
            aliases.add("ea sports");
            aliases.add("fc 26");
            aliases.add("fifa");
        } else if (canonical.contains("discord")) {
            aliases.add("discord");
        } else if (canonical.contains("telegram")) {
            aliases.add("telegram");
            aliases.add("tg");
        } else if (canonical.contains("valorant")) {
            aliases.add("valorant");
        } else if (canonical.contains("notion")) {
            aliases.add("notion");
        } else if (canonical.contains("capcut")) {
            aliases.add("capcut");
        }

        String desc = appId.startsWith("steam://") ? "Steam Game: " + name : "Windows Application: " + name;

        ApplicationEntry entry = new ApplicationEntry(
                canonical,
                new String[]{"cmd.exe", "/c", "start", "", appId},
                () -> buildLaunchCommand(appId),
                desc,
                aliases.toArray(new String[0])
        );

        DYNAMIC_APPS.put(canonical, entry);
        for (String alias : aliases) {
            DYNAMIC_APPS.put(alias.toLowerCase(Locale.ROOT), entry);
        }
    }

    public static List<String> buildLaunchCommand(String appId) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("AppID cannot be blank.");
        }
        String trimmed = appId.trim();
        if (trimmed.startsWith("steam://") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return Arrays.asList("cmd.exe", "/c", "start", "", trimmed);
        }
        if (trimmed.contains("!") || trimmed.startsWith("windows.") || trimmed.startsWith("Microsoft.")) {
            return Arrays.asList("cmd.exe", "/c", "start", "", "shell:AppsFolder\\" + trimmed);
        }
        if (trimmed.endsWith(".exe") || trimmed.contains("\\")) {
            return Arrays.asList("cmd.exe", "/c", "start", "", trimmed);
        }
        return Arrays.asList("cmd.exe", "/c", "start", "", "shell:AppsFolder\\" + trimmed);
    }

    /**
     * Executes an application launch and returns the display-friendly status string.
     */
    public static String openApp(String appName) {
        LaunchResult result = executeAppLaunch(appName);
        return result.toDisplayString();
    }

    /**
     * Plays a requested song, music track, or video on YouTube or Spotify.
     */
    public static LaunchResult executePlayMusic(String songOrQuery, String preferredPlatform) {
        String raw = (songOrQuery != null) ? songOrQuery.trim() : "";
        String clean = raw
                .replaceAll("(?i)^(?:can you |could you |please )?(?:open youtube and play|play on youtube|play on spotify|play song|play music|play track|play|listen to|search for|search)\\s*", "")
                .replaceAll("(?i)\\s+(?:on youtube|in youtube|on spotify|in spotify|for me|please|now)$", "")
                .replaceAll("[\\.\\?!,;]+$", "")
                .trim();

        boolean isGenericMusic = clean.isBlank()
                || clean.equalsIgnoreCase("any music")
                || clean.equalsIgnoreCase("music")
                || clean.equalsIgnoreCase("some music")
                || clean.equalsIgnoreCase("a song")
                || clean.equalsIgnoreCase("songs")
                || clean.equalsIgnoreCase("song")
                || clean.equalsIgnoreCase("tracks");

        boolean useSpotify = (preferredPlatform != null && preferredPlatform.equalsIgnoreCase("spotify"))
                || raw.toLowerCase(Locale.ROOT).contains("spotify");

        if (useSpotify) {
            if (isGenericMusic) {
                String url = "https://open.spotify.com";
                List<String> cmd = Arrays.asList("cmd.exe", "/c", "start", "", url);
                return startProcess("play_music", "Spotify Music", cmd, "Opened Spotify music player in default browser.");
            } else {
                String encoded = URLEncoder.encode(clean, StandardCharsets.UTF_8);
                String url = "https://open.spotify.com/search/" + encoded;
                List<String> cmd = Arrays.asList("cmd.exe", "/c", "start", "", url);
                return startProcess("play_music", "Spotify: " + clean, cmd, "Playing '" + clean + "' on Spotify in default browser.");
            }
        } else {
            if (isGenericMusic) {
                // Autoplays live music stream
                String url = "https://www.youtube.com/watch?v=jfKfPfyJRdk";
                List<String> cmd = Arrays.asList("cmd.exe", "/c", "start", "", url);
                return startProcess("play_music", "YouTube Music Stream", cmd, "Playing music stream on YouTube for you in your browser.");
            } else {
                String encoded = URLEncoder.encode(clean, StandardCharsets.UTF_8);
                String url = "https://www.youtube.com/results?search_query=" + encoded;
                List<String> cmd = Arrays.asList("cmd.exe", "/c", "start", "", url);
                return startProcess("play_music", "YouTube: " + clean, cmd, "Playing '" + clean + "' on YouTube for you in your browser.");
            }
        }
    }

    public static String playMusic(String query) {
        return executePlayMusic(query, null).toDisplayString();
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
            if (normalized.contains("play ") || normalized.contains("search ") || normalized.contains("find ")) {
                return executePlayMusic(normalized, "youtube");
            }
            boolean preferChrome = normalized.contains("chrome");
            return executeOpenYouTube(preferChrome);
        }

        if (normalized.contains("spotify")) {
            if (normalized.contains("play ") || normalized.contains("search ")) {
                return executePlayMusic(normalized, "spotify");
            }
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

        // New window requests for VS Code or generic
        if (normalized.equals("new window") || normalized.equals("a new window")
                || normalized.equals("open a new window") || normalized.equals("open new window")
                || normalized.equals("vs code in a new window") || normalized.equals("vscode in a new window")
                || normalized.equals("open vs code in a new window") || normalized.equals("open vs code in new window")
                || normalized.equals("open vscode in a new window") || normalized.equals("open vscode in new window")) {
            return executeOpenVsCode(null, true);
        }

        // Open specific file in VS Code (e.g. "test.py in vs code", "test.py in vscode", "workspace/test.py with vs code")
        Pattern fileInVsCodePattern = Pattern.compile("(?i)^(?:open\\s+)?([a-zA-Z0-9_\\-\\.\\/\\\\]+\\.[a-zA-Z0-9]+)\\s+(?:in|with)\\s+(?:vscode|vs\\s*code|visual\\s*studio\\s*code|code)(?:\\s+in\\s+(?:a\\s+)?new\\s+window)?$");
        Matcher fileInVsCodeMatcher = fileInVsCodePattern.matcher(normalized);
        if (fileInVsCodeMatcher.matches()) {
            String filePath = fileInVsCodeMatcher.group(1).trim();
            boolean newWin = normalized.contains("new window");
            return executeOpenVsCode(filePath, newWin);
        }

        // Open VS Code with path and optional new window flag
        if (normalized.matches("^(?:vscode|vs\\s*code|code)\\s+(?:in\\s+(?:a\\s+)?new\\s+window\\s+)?(?:with\\s+)?(.+)$")) {
            String target = normalized.replaceFirst("^(?:vscode|vs\\s*code|code)\\s+", "").trim();
            boolean newWin = target.contains("new window") || target.contains("-n");
            String cleanedTarget = target.replaceAll("(?i)\\s*in\\s+(?:a\\s+)?new\\s+window\\s*", " ")
                    .replaceAll("(?i)\\s*with\\s+", " ")
                    .replaceAll("\\s*-n\\s*", " ")
                    .trim();
            return executeOpenVsCode(cleanedTarget.isEmpty() ? null : cleanedTarget, newWin);
        }

        if (normalized.equals("vscode") || normalized.equals("vs code") || normalized.equals("visual studio code") || normalized.equals("code") || normalized.equals("vsc")) {
            return executeOpenVsCode(null, false);
        }

        // Open file in Notepad (e.g. "notepad test.txt", "test.txt in notepad")
        if (normalized.matches("^notepad\\s+(.+)$") || normalized.matches("^(?:open\\s+)?([a-zA-Z0-9_\\-\\.\\/\\\\]+\\.[a-zA-Z0-9]+)\\s+(?:in|with)\\s+notepad$")) {
            String path = normalized.replaceFirst("^notepad\\s+", "")
                    .replaceFirst("^(?:open\\s+)?", "")
                    .replaceFirst("\\s+(?:in|with)\\s+notepad$", "")
                    .trim();
            String resolvedPath = path;
            File f = new File(path);
            if (!f.isAbsolute() && DEXConfig.getWorkspaceRoot() != null) {
                resolvedPath = DEXConfig.getWorkspaceRoot().resolve(path).toAbsolutePath().toString();
            }
            List<String> cmd = Arrays.asList("cmd.exe", "/c", "start", "", "notepad.exe", resolvedPath);
            return startProcess("open_app", "Notepad: " + path, cmd, "Opened Notepad with: " + path);
        }

        // Direct code file request (e.g. "test.py", "open test.py")
        if (normalized.matches("^(?:open\\s+)?([a-zA-Z0-9_\\-\\.\\/\\\\]+\\.(?:py|c|cpp|java|js|ts|html|css|json|txt|md))$")) {
            String fileName = normalized.replaceFirst("^open\\s+", "").trim();
            return executeOpenVsCode(fileName, false);
        }

        ApplicationEntry target = findApplication(normalized);

        if (target == null) {
            logDiagnostic("open_app", appName, "none", null, "UNSUPPORTED");
            return LaunchResult.failure("open_app", appName, "Unsupported application: " + appName + ". "
                    + "Available options: WhatsApp, Steam, Chrome, Spotify, YouTube, Games, VS Code, "
                    + "File Explorer, Downloads, Documents, Desktop, and any installed Windows application.");
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
        return executeOpenVsCode(null).toDisplayString();
    }

    public static LaunchResult executeOpenVsCode() {
        return executeOpenVsCode(null, false);
    }

    public static LaunchResult executeOpenVsCode(String targetPath) {
        return executeOpenVsCode(targetPath, false);
    }

    public static LaunchResult executeOpenVsCode(String targetPath, boolean newWindow) {
        String vsCodePath = findVsCodeExecutable();
        if (vsCodePath == null) {
            logDiagnosticError("open_app", "Visual Studio Code", "Code.exe", null, new FileNotFoundException("VS Code not found"));
            return LaunchResult.failure("open_app", "Visual Studio Code", "Visual Studio Code (Code.exe or code.cmd) was not found in standard installation paths or PATH.");
        }

        boolean requestedNewWindow = newWindow;
        String cleanPath = null;

        if (targetPath != null && !targetPath.isBlank()) {
            String trimmed = targetPath.trim();
            if (trimmed.contains("-n") || trimmed.contains("--new-window")) {
                requestedNewWindow = true;
                trimmed = trimmed.replaceAll("\\s*(?:-n|--new-window)\\s*", " ").trim();
            }
            if (!trimmed.isBlank()) {
                cleanPath = trimmed;
            }
        }

        // Resolve cleanPath against workspace if not absolute
        String resolvedTarget = null;
        if (cleanPath != null) {
            File directFile = new File(cleanPath);
            if (directFile.isAbsolute() && directFile.exists()) {
                resolvedTarget = directFile.getAbsolutePath();
            } else {
                java.nio.file.Path wsRoot = com.dex.config.DEXConfig.getWorkspaceRoot();
                if (wsRoot != null) {
                    java.nio.file.Path candidate = wsRoot.resolve(cleanPath).normalize();
                    resolvedTarget = candidate.toAbsolutePath().toString();
                } else {
                    resolvedTarget = directFile.getAbsolutePath();
                }
            }
        }

        List<String> cmd = new ArrayList<>();
        if (vsCodePath.toLowerCase(Locale.ROOT).endsWith(".cmd")) {
            cmd.add("cmd.exe");
            cmd.add("/c");
            cmd.add("start");
            cmd.add("");
            cmd.add(vsCodePath);
            if (requestedNewWindow) {
                cmd.add("-n");
            }
            if (resolvedTarget != null) {
                cmd.add(resolvedTarget);
            }
        } else {
            cmd.add(vsCodePath);
            if (requestedNewWindow) {
                cmd.add("-n");
            }
            if (resolvedTarget != null) {
                cmd.add(resolvedTarget);
            }
        }

        StringBuilder targetDesc = new StringBuilder("Visual Studio Code");
        if (requestedNewWindow) {
            targetDesc.append(" (New Window)");
        }
        if (cleanPath != null) {
            targetDesc.append(" [").append(cleanPath).append("]");
        }

        String details = "Opened Visual Studio Code"
                + (requestedNewWindow ? " in a new window" : "")
                + (resolvedTarget != null ? " with '" + (cleanPath != null ? cleanPath : resolvedTarget) + "'" : "");

        return startProcess("open_app", targetDesc.toString(), cmd, details);
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

        String targetUrl = url.trim();
        String cmdUrl = targetUrl.contains("&") ? targetUrl.replace("&", "^&") : targetUrl;
        List<String> cmd = Arrays.asList("cmd.exe", "/c", "start", "", cmdUrl);
        return startProcess("open_url", "Default Browser (" + targetUrl + ")", cmd, "Opened URL via Windows Shell");
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
        if (DYNAMIC_APPS.containsKey(q)) {
            return DYNAMIC_APPS.get(q);
        }

        // 2. Strip "my " prefix (e.g. "my downloads" -> "downloads")
        if (q.startsWith("my ")) {
            String stripped = q.substring(3).trim();
            if (APPS.containsKey(stripped)) {
                return APPS.get(stripped);
            }
            if (DYNAMIC_APPS.containsKey(stripped)) {
                return DYNAMIC_APPS.get(stripped);
            }
        }

        // 3. Word-boundary phrase match for aliases
        for (Map.Entry<String, ApplicationEntry> entry : APPS.entrySet()) {
            String key = entry.getKey();
            if (Pattern.compile("\\b" + Pattern.quote(key) + "\\b").matcher(q).find()) {
                return entry.getValue();
            }
        }
        for (Map.Entry<String, ApplicationEntry> entry : DYNAMIC_APPS.entrySet()) {
            String key = entry.getKey();
            if (Pattern.compile("\\b" + Pattern.quote(key) + "\\b").matcher(q).find()) {
                return entry.getValue();
            }
        }

        // 4. Substring / contains match for dynamic games & apps (e.g. "gta", "disco", "red dead")
        if (q.length() >= 3) {
            for (Map.Entry<String, ApplicationEntry> entry : DYNAMIC_APPS.entrySet()) {
                String key = entry.getKey();
                if (key.contains(q) || q.contains(key)) {
                    return entry.getValue();
                }
            }
        }

        // 5. Fuzzy Levenshtein distance check (only for single-word queries with length >= 4, e.g. typos like "calculater")
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
            for (Map.Entry<String, ApplicationEntry> entry : DYNAMIC_APPS.entrySet()) {
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
        for (ApplicationEntry entry : DYNAMIC_APPS.values()) {
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
