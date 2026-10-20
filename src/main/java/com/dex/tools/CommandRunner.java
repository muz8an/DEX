package com.dex.tools;

import com.dex.config.DEXConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Restricted Command Runner for DEX Coding Assistant.
 * Enforces an allowlist of approved build and test commands (Maven, javac, java, git, python)
 * with strict argument validation, execution timeouts, and secret sanitization.
 */
public class CommandRunner {

    private static final long DEFAULT_TIMEOUT_SECONDS = 45;
    private static final int MAX_OUTPUT_CHARS = 10000;

    // Supported executable allowlist
    private static final Set<String> ALLOWED_EXECUTABLES = Set.of(
            "mvn", "mvn.cmd", "mvn.bat",
            "javac", "javac.exe",
            "java", "java.exe",
            "git", "git.exe",
            "python", "python.exe", "python3", "python3.exe",
            "node", "node.exe", "npm", "npm.cmd"
    );

    // Disallowed dangerous tokens preventing command injection / chaining
    private static final List<String> DANGEROUS_TOKENS = List.of(
            "&", "|", ";", ">", "<", "`", "$", "del ", "rmdir", "format",
            "reg ", "powershell", "certutil", "curl", "wget", "cmd.exe"
    );

    /**
     * Validates and executes an approved command string.
     *
     * @param commandLine User- or tool-supplied command string.
     * @return Execution report including exit code and sanitized output.
     */
    public static String runCommand(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return "Error: Command line cannot be empty.";
        }

        String trimmed = commandLine.trim();

        // 1. Check for command chaining and injection tokens
        for (String token : DANGEROUS_TOKENS) {
            if (trimmed.contains(token)) {
                return "Security Error: Execution blocked. Disallowed token detected: '" + token + "'. "
                        + "Command chaining, redirection, and shell execution are strictly prohibited.";
            }
        }

        // 2. Tokenize command safely
        List<String> tokens = tokenize(trimmed);
        if (tokens.isEmpty()) {
            return "Error: No executable specified in command.";
        }

        String executable = tokens.get(0).toLowerCase(Locale.ROOT);
        String exeName = new File(executable).getName().toLowerCase(Locale.ROOT);

        if (!ALLOWED_EXECUTABLES.contains(exeName)) {
            return "Security Error: Executable '" + exeName + "' is not in the approved build tools allowlist.\n"
                    + "Allowed tools: mvn, javac, java, git, python, node, npm.";
        }

        // 3. Command specific argument inspection
        String validationError = validateCommandArguments(exeName, tokens);
        if (validationError != null) {
            return "Security Error: " + validationError;
        }

        // 4. Determine working directory: Maven commands run in project root; workspace tools run in workspace root
        File workingDir;
        if (exeName.startsWith("mvn")) {
            workingDir = new File(System.getProperty("user.dir"));
        } else {
            workingDir = DEXConfig.getWorkspaceRoot().toFile();
        }

        // 5. Execute with timeout and stream capture
        return executeProcess(tokens, workingDir, DEFAULT_TIMEOUT_SECONDS);
    }

    private static String validateCommandArguments(String exeName, List<String> tokens) {
        if (exeName.startsWith("git")) {
            if (tokens.size() < 2) {
                return "Please provide a git subcommand (e.g. 'git status', 'git diff', 'git log').";
            }
            String sub = tokens.get(1).toLowerCase(Locale.ROOT);
            Set<String> safeGitSubcommands = Set.of("status", "diff", "log", "branch", "show", "version");
            if (!safeGitSubcommands.contains(sub)) {
                return "Git subcommand '" + sub + "' is restricted. Allowed read-only subcommands: status, diff, log, branch, show, version.";
            }
        } else if (exeName.startsWith("mvn")) {
            for (int i = 1; i < tokens.size(); i++) {
                String arg = tokens.get(i).toLowerCase(Locale.ROOT);
                if (arg.contains("exec:exec") || arg.contains("-dexec.executable")) {
                    return "Arbitrary exec:exec goals are blocked for security.";
                }
            }
        }
        return null;
    }

    private static String executeProcess(List<String> commandTokens, File workingDirectory, long timeoutSeconds) {
        StringBuilder output = new StringBuilder();
        int exitCode = -1;

        try {
            ProcessBuilder pb = new ProcessBuilder(commandTokens);
            pb.directory(workingDirectory);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            boolean finished;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

                finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < MAX_OUTPUT_CHARS) {
                        output.append(line).append("\n");
                    } else if (output.length() == MAX_OUTPUT_CHARS) {
                        output.append("\n[... Output truncated exceeding 10,000 characters ...]\n");
                    }
                }
            }

            if (!finished) {
                process.destroyForcibly();
                return "Command timed out after " + timeoutSeconds + " seconds: " + String.join(" ", commandTokens);
            }

            exitCode = process.exitValue();

        } catch (Exception e) {
            return "Command execution failed: " + e.getMessage();
        }

        String sanitizedOutput = sanitize(output.toString());
        return String.format(Locale.ROOT,
                "=== Command Report ===\n" +
                "Command: %s\n" +
                "Directory: %s\n" +
                "Exit Status: %d (%s)\n\n" +
                "Output:\n%s",
                String.join(" ", commandTokens),
                workingDirectory.getAbsolutePath(),
                exitCode,
                exitCode == 0 ? "SUCCESS" : "FAILED",
                sanitizedOutput.isBlank() ? "(No output produced)" : sanitizedOutput
        );
    }

    private static List<String> tokenize(String commandLine) {
        List<String> list = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    list.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            list.add(current.toString());
        }
        return list;
    }

    private static String sanitize(String text) {
        String key = DEXConfig.getApiKey();
        if (key != null && !key.isBlank() && key.length() > 6) {
            return text.replace(key, "[REDACTED_API_KEY]");
        }
        return text;
    }
}
