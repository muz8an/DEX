package com.dex.tools;

import com.dex.config.DEXConfig;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Safe local file operations confined strictly to approved workspace directories.
 * Implements strict path-traversal prevention and sensitive file shielding.
 */
public class FileTools {

    private static final long MAX_READ_SIZE_BYTES = 500 * 1024; // 500 KB limit
    private static final Set<String> SENSITIVE_EXTENSIONS = Set.of(
            ".key", ".pem", ".pkcs12", ".pfx", ".p12", ".env", ".kdbx"
    );
    private static final Set<String> SENSITIVE_FILENAMES = Set.of(
            "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519", "credentials", "secrets.json", ".env"
    );

    /**
     * Resolves a user-supplied relative path against the approved workspace,
     * throwing SecurityException if path traversal outside workspace is detected.
     */
    public static Path resolveSafePath(String relativePath) throws SecurityException, IOException {
        Path root = DEXConfig.getWorkspaceRoot().toRealPath();
        Path candidate;

        if (relativePath == null || relativePath.isBlank() || relativePath.trim().equals(".") || relativePath.trim().equals("/")) {
            candidate = root;
        } else {
            String sanitized = relativePath.trim().replace('\\', '/');
            if (sanitized.startsWith("/")) {
                sanitized = sanitized.substring(1);
            }
            candidate = root.resolve(sanitized).normalize();
        }

        // Check if candidate is within root
        Path realCandidate;
        if (Files.exists(candidate)) {
            realCandidate = candidate.toRealPath();
        } else {
            // For not-yet-created files, verify parent
            Path parent = candidate.getParent();
            if (parent != null && Files.exists(parent)) {
                realCandidate = parent.toRealPath().resolve(candidate.getFileName());
            } else {
                realCandidate = candidate.toAbsolutePath().normalize();
            }
        }

        if (!realCandidate.startsWith(root)) {
            throw new SecurityException("Access Denied: Path traversal outside approved workspace is strictly prohibited (" + relativePath + ").");
        }

        // Check sensitive names
        String fileName = candidate.getFileName() != null ? candidate.getFileName().toString().toLowerCase(Locale.ROOT) : "";
        if (SENSITIVE_FILENAMES.contains(fileName)) {
            throw new SecurityException("Access Denied: Access to sensitive file '" + fileName + "' is blocked.");
        }
        for (String ext : SENSITIVE_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                throw new SecurityException("Access Denied: File extension '" + ext + "' is considered sensitive and blocked.");
            }
        }

        return realCandidate;
    }

    /**
     * Lists files and folders in the approved workspace or an approved subfolder.
     */
    public static String listFiles(String subDir) {
        try {
            Path targetDir = resolveSafePath(subDir);
            if (!Files.exists(targetDir)) {
                return "Directory not found: " + targetDir.getFileName();
            }
            if (!Files.isDirectory(targetDir)) {
                return "Target is a file, not a directory: " + targetDir.getFileName();
            }

            List<String> items = new ArrayList<>();
            try (Stream<Path> stream = Files.list(targetDir)) {
                List<Path> sorted = stream.sorted().limit(100).collect(Collectors.toList());
                for (Path p : sorted) {
                    boolean isDir = Files.isDirectory(p);
                    long size = isDir ? 0 : Files.size(p);
                    String label = (isDir ? "[DIR]  " : "[FILE] ") + p.getFileName().toString()
                            + (!isDir ? " (" + (size / 1024) + " KB)" : "");
                    items.add(label);
                }
            }

            if (items.isEmpty()) {
                return "Workspace folder is currently empty: " + targetDir;
            }

            return "Files in " + DEXConfig.getWorkspaceRoot().relativize(targetDir) + ":\n" + String.join("\n", items);
        } catch (SecurityException | IOException e) {
            return "File listing failed: " + e.getMessage();
        }
    }

    /**
     * Reads the text contents of a file in the workspace up to 500 KB.
     */
    public static String readFile(String relativePath) {
        try {
            Path target = resolveSafePath(relativePath);
            if (!Files.exists(target)) {
                return "File does not exist: " + relativePath;
            }
            if (Files.isDirectory(target)) {
                return "Target is a directory. Use list_files instead.";
            }

            long size = Files.size(target);
            if (size > MAX_READ_SIZE_BYTES) {
                return "File size (" + (size / 1024) + " KB) exceeds maximum allowed read limit (" + (MAX_READ_SIZE_BYTES / 1024) + " KB).";
            }

            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (SecurityException | IOException e) {
            return "Failed to read file: " + e.getMessage();
        }
    }

    /**
     * Writes text to an approved workspace file.
     * Overwriting requires allowOverwrite=true.
     */
    public static String writeFile(String relativePath, String content, boolean allowOverwrite) {
        try {
            Path target = resolveSafePath(relativePath);
            if (Files.exists(target) && !allowOverwrite) {
                return "File already exists: '" + relativePath + "'. Confirmation is required before overwriting existing files.";
            }

            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }

            Files.writeString(target, content != null ? content : "", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            return "File successfully saved to workspace: " + relativePath + " (" + (content != null ? content.length() : 0) + " chars)";
        } catch (SecurityException | IOException e) {
            return "Failed to write file: " + e.getMessage();
        }
    }

    /**
     * Searches for files by pattern inside workspace up to depth 3 and 50 results.
     */
    public static String searchFiles(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "Please provide a filename pattern to search.";
        }

        String query = pattern.trim().toLowerCase(Locale.ROOT);
        Path root = DEXConfig.getWorkspaceRoot();
        List<String> matches = new ArrayList<>();

        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 3, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().toLowerCase(Locale.ROOT).contains(query)) {
                        matches.add(root.relativize(file).toString());
                        if (matches.size() >= 50) {
                            return FileVisitResult.TERMINATE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (matches.isEmpty()) {
                return "No files matched '" + pattern + "' in workspace.";
            }

            return "Matched files (" + matches.size() + "):\n• " + String.join("\n• ", matches);
        } catch (IOException e) {
            return "File search error: " + e.getMessage();
        }
    }

    /**
     * Saves AI-generated code to a specific file in workspace.
     */
    public static String saveCode(String relativePath, String code) {
        if (relativePath == null || relativePath.isBlank()) {
            return "Error: File path is required to save code.";
        }
        return writeFile(relativePath, code, true);
    }

    /**
     * Overloaded writeFile parsing format "path:::content" or "path\ncontent"
     */
    public static String writeFileFromString(String combinedInput) {
        if (combinedInput == null || combinedInput.isBlank()) {
            return "Error: Path and content are required.";
        }

        String path;
        String content;

        if (combinedInput.contains(":::")) {
            String[] parts = combinedInput.split(":::", 2);
            path = parts[0].trim();
            content = parts[1];
        } else if (combinedInput.contains("\n")) {
            int firstNewline = combinedInput.indexOf("\n");
            path = combinedInput.substring(0, firstNewline).trim();
            content = combinedInput.substring(firstNewline + 1);
        } else {
            path = combinedInput.trim();
            content = "";
        }

        return writeFile(path, content, true);
    }

    /**
     * Opens an approved file or folder in Windows.
     */
    public static String openPath(String relativePath) {
        try {
            Path target = resolveSafePath(relativePath);
            if (!Files.exists(target)) {
                return "Path does not exist: " + relativePath;
            }

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(target.toFile());
                return "Opened: " + target.getFileName();
            } else {
                new ProcessBuilder("explorer.exe", target.toString()).start();
                return "Opened in Explorer: " + target.getFileName();
            }
        } catch (SecurityException | IOException e) {
            return "Failed to open path: " + e.getMessage();
        }
    }
}

