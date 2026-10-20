package com.dex;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages Windows application launches and folder navigation with fuzzy matching,
 * typo tolerance, and strict process execution validation.
 */
public class WindowsController {

    private static final Map<String, ApplicationEntry> APPS = new LinkedHashMap<>();

    public static class ApplicationEntry {
        private final String canonicalName;
        private final String[] command;
        private final List<String> aliases;
        private final String description;

        public ApplicationEntry(String canonicalName, String[] command, String description, String... aliases) {
            this.canonicalName = canonicalName.toLowerCase(Locale.ROOT);
            this.command = command;
            this.description = description;
            this.aliases = new ArrayList<>(Arrays.asList(aliases));
            this.aliases.add(this.canonicalName);
        }

        public String getCanonicalName() {
            return canonicalName;
        }

        public String[] getCommand() {
            return command.clone();
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

        // Web Browsers & Web Services
        registerApp(new ApplicationEntry(
                "chrome",
                new String[]{"cmd", "/c", "start", "", "https://www.google.com"},
                "Google Chrome Browser",
                "google chrome", "google", "browser", "crome", "chrom"
        ));

        registerApp(new ApplicationEntry(
                "youtube",
                new String[]{"cmd", "/c", "start", "", "https://www.youtube.com"},
                "YouTube (Default Browser)",
                "yt", "you tube", "video"
        ));

        // Core Windows Utilities
        registerApp(new ApplicationEntry(
                "notepad",
                new String[]{"notepad.exe"},
                "Windows Notepad Text Editor",
                "note pad", "editor", "text editor"
        ));

        registerApp(new ApplicationEntry(
                "calculator",
                new String[]{"calc.exe"},
                "Windows Calculator",
                "calc", "calculater", "calulator", "cal"
        ));

        registerApp(new ApplicationEntry(
                "vscode",
                new String[]{"cmd", "/c", "start", "", "code"},
                "Visual Studio Code",
                "vs code", "visual studio code", "vsc", "code editor", "code"
        ));

        registerApp(new ApplicationEntry(
                "file explorer",
                new String[]{"explorer.exe"},
                "Windows File Explorer",
                "explorer", "files", "my files", "file manager"
        ));

        // Approved User Directories
        registerApp(new ApplicationEntry(
                "downloads",
                new String[]{"explorer.exe", downloadsPath},
                "Downloads Folder",
                "downloads folder", "my downloads", "download", "download folder"
        ));

        registerApp(new ApplicationEntry(
                "documents",
                new String[]{"explorer.exe", documentsPath},
                "Documents Folder",
                "documents folder", "my documents", "docs", "doc folder"
        ));

        registerApp(new ApplicationEntry(
                "desktop",
                new String[]{"explorer.exe", desktopPath},
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
     * Attempts to open an approved application by name or alias,
     * applying typo tolerance and Levenshtein distance matching.
     *
     * @param appName User-supplied application or folder name.
     * @return User-friendly result status.
     */
    public static String openApp(String appName) {
        if (appName == null || appName.isBlank()) {
            return "Please specify an application or folder to open.";
        }

        String normalized = appName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("^(can you |could you |please )?(open|launch|start|run|show)\\s+", "")
                .replaceAll("\\s+(for me|please|now)$", "")
                .replaceAll("[\\.\\?!,;]+$", "")
                .trim();

        if (normalized.startsWith("my ")) {
            normalized = normalized.substring(3).trim();
        }

        ApplicationEntry target = findApplication(normalized);

        if (target == null) {
            return "Unsupported application: " + appName + ". "
                    + "Available options: Chrome, YouTube, Notepad, Calculator, VS Code, "
                    + "File Explorer, Downloads, Documents, Desktop.";
        }

        try {
            Process process = new ProcessBuilder(target.getCommand()).start();
            // Allow a short moment to ensure the process spawned without an instant startup crash
            return "Opening " + target.getCanonicalName() + "...";
        } catch (IOException e) {
            return "Couldn't open " + target.getCanonicalName()
                    + ". Check whether the application is installed and available on your system.";
        }
    }

    /**
     * Resolves an app query using direct match, substring match, and Levenshtein edit distance.
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
            if (java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(key) + "\\b").matcher(q).find()) {
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
