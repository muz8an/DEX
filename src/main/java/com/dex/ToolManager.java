package com.dex;

import com.dex.tools.CommandRunner;
import com.dex.tools.FileTools;
import com.dex.tools.SystemTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.function.Function;

/**
 * Registry and execution dispatcher for DEX tools with metadata,
 * input validation, and execution controls.
 */
public class ToolManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static class ToolDefinition {
        private final String name;
        private final String description;
        private final String parameterDescription;
        private final boolean sensitive;
        private final Function<String, String> action;

        public ToolDefinition(String name, String description, String parameterDescription, boolean sensitive, Function<String, String> action) {
            this.name = name.toLowerCase(Locale.ROOT);
            this.description = description;
            this.parameterDescription = parameterDescription;
            this.sensitive = sensitive;
            this.action = action;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getParameterDescription() {
            return parameterDescription;
        }

        public boolean isSensitive() {
            return sensitive;
        }

        public String execute(String argument) {
            return action.apply(argument);
        }
    }

    private static final Map<String, ToolDefinition> TOOLS = new LinkedHashMap<>();

    static {
        registerDefaultTools();
    }

    private static void registerDefaultTools() {
        // Windows application launcher
        register("open_app",
                "Opens any Windows application, Steam game, or standard folder (WhatsApp, Steam, Spotify, Games, Chrome, Notepad, Calculator, VS Code, File Explorer, Downloads, etc.).",
                "Name of the application or game (e.g. 'whatsapp', 'steam', 'dead as disco', 'gta', 'spotify')",
                false,
                WindowsController::openApp);

        // Music and song playback
        register("play_music",
                "Searches and plays a requested song, music track, or video on YouTube or Spotify (e.g. 'arz kiya hai', 'lofi hip hop', 'starboy on spotify').",
                "Song or artist query (e.g. 'arz kiya hai', 'believer')",
                false,
                WindowsController::playMusic);

        // System diagnostics and time
        register("get_current_time",
                "Returns the formatted current date, time, and timezone of this Windows host.",
                "None (ignored)",
                false,
                SystemTools::getCurrentTime);

        register("get_system_info",
                "Returns Windows host diagnostics, CPU cores, JVM memory allocation, OS version, and workspace path.",
                "None (ignored)",
                false,
                SystemTools::getSystemInfo);

        register("take_screenshot",
                "Captures a desktop screenshot and saves it as a PNG file in data/screenshots/.",
                "None (ignored)",
                false,
                SystemTools::takeScreenshot);

        register("open_url",
                "Validates and opens an approved web URL (HTTP/HTTPS) in the default browser.",
                "The web URL to open (e.g. 'https://github.com')",
                false,
                SystemTools::openUrl);

        register("speak",
                "Speaks text aloud using Windows PowerShell speech synthesis.",
                "The text to speak",
                false,
                SystemTools::speak);

        register("list_processes",
                "Lists active running Windows processes with Process IDs.",
                "Optional filter keyword",
                false,
                SystemTools::listRunningProcesses);

        register("browser_navigate",
                "Navigates to a webpage and extracts accessible text content.",
                "The URL to navigate to (e.g. 'https://en.wikipedia.org')",
                false,
                com.dex.browser.BrowserAutomation::navigate);

        register("browser_search",
                "Searches Google, YouTube, or GitHub and opens the results in your browser.",
                "Search query (e.g. 'Java 21 virtual threads')",
                false,
                arg -> com.dex.browser.BrowserAutomation.search(arg, "google"));

        // File operations
        register("list_files",
                "Lists files and directories in the approved workspace folder or a subfolder.",
                "Optional subfolder relative to workspace (empty or '.' for root)",
                false,
                FileTools::listFiles);

        register("read_file",
                "Reads the text content of a file in the approved workspace up to 500 KB.",
                "Relative path to workspace file",
                false,
                FileTools::readFile);

        register("write_file",
                "Writes text to an approved workspace file (format: 'path:::content' or 'path\\ncontent').",
                "File path and content",
                true,
                FileTools::writeFileFromString);

        register("save_code",
                "Saves generated code directly to an explicitly named workspace file.",
                "File path and code content",
                true,
                FileTools::writeFileFromString);

        register("save_and_open_code",
                "Saves code to an explicitly named workspace file and immediately opens it inside Visual Studio Code in a new window (format: 'filepath:::code_content').",
                "File path and code content",
                true,
                arg -> {
                    String saveResult = FileTools.writeFileFromString(arg);
                    String path = (arg != null && arg.contains(":::")) ? arg.split(":::", 2)[0].trim() : ((arg != null && arg.contains("\n")) ? arg.substring(0, arg.indexOf("\n")).trim() : arg);
                    WindowsController.LaunchResult launch = WindowsController.executeOpenVsCode(path, true);
                    return saveResult + "\n" + launch.toDisplayString();
                });

        register("write_program",
                "Generates code for a requested programming task, saves it to the workspace, and immediately opens it inside Visual Studio Code in a new window (e.g. 'write a program to add two numbers', 'calculator in python').",
                "Description of the program to write",
                false,
                arg -> {
                    com.dex.tools.ProgramGenerator.GeneratedProgram gen = com.dex.tools.ProgramGenerator.generate(arg);
                    FileTools.saveCode(gen.getFilename(), gen.getCode());
                    WindowsController.LaunchResult launch = WindowsController.executeOpenVsCode(gen.getFilename(), true);
                    return "Created " + gen.getTitle() + " in '" + gen.getFilename() + "'. " + launch.toDisplayString();
                });

        register("search_files",
                "Searches for filenames matching a pattern inside the approved workspace.",
                "Filename pattern to search",
                false,
                FileTools::searchFiles);

        register("open_path",
                "Opens an approved file or folder in Windows File Explorer.",
                "Relative path inside workspace",
                false,
                FileTools::openPath);

        // Coding assistant restricted command runner
        register("run_command",
                "Executes an approved build or test command (mvn, javac, java, git, python, node) with strict validation.",
                "Command string to execute (e.g. 'mvn test', 'git status')",
                true,
                CommandRunner::runCommand);

        register("run_shell_command",
                "Executes a command or shell task safely.",
                "Command string to execute",
                true,
                CommandRunner::runCommand);

        register("create_file",
                "Creates or writes a file in the workspace (format: 'filepath:::content').",
                "File path and content",
                true,
                FileTools::writeFileFromString);

        register("save_file",
                "Saves code or text directly to a file (format: 'filepath:::content').",
                "File path and content",
                true,
                FileTools::writeFileFromString);

        // Tool discovery
        register("list_tools",
                "Lists all registered DEX capabilities and tool signatures.",
                "None (ignored)",
                false,
                arg -> listToolsDetailed());
    }

    private ToolManager() {
        // Utility class
    }

    /**
     * Legacy registration method for backward compatibility.
     */
    public static synchronized void register(String name, Function<String, String> action) {
        register(name, "Action for " + name, "Argument string", false, action);
    }

    /**
     * Full tool registration with documentation and safety metadata.
     */
    public static synchronized void register(String name, String description, String paramDesc, boolean sensitive, Function<String, String> action) {
        if (name == null || name.isBlank() || action == null) {
            throw new IllegalArgumentException("Tool name and action are required.");
        }
        TOOLS.put(name.toLowerCase(Locale.ROOT), new ToolDefinition(name, description, paramDesc, sensitive, action));
    }

    /**
     * Executes a registered tool by name with safety checks.
     */
    public static String execute(String toolName, String argument) {
        if (toolName == null || toolName.isBlank()) {
            return "Error: Please specify a tool name.";
        }

        ToolDefinition tool = TOOLS.get(toolName.toLowerCase(Locale.ROOT));
        if (tool == null) {
            return "Error: Unknown tool '" + toolName + "'. Available tools: " + listTools();
        }

        try {
            return tool.execute(argument != null ? argument.trim() : "");
        } catch (Exception e) {
            return "Tool '" + toolName + "' execution failed: " + e.getMessage();
        }
    }

    /**
     * Alias for execute(toolName, argument).
     */
    public static String executeTool(String toolName, String argument) {
        return execute(toolName, argument);
    }

    /**
     * Parses and executes a structured JSON action protocol payload.
     * Expected format: {"tool": "tool_name", "argument": "arg_value"}
     */
    public static String executeJsonAction(String jsonAction) {
        if (jsonAction == null || jsonAction.isBlank()) {
            return "Error: Action payload is empty.";
        }

        try {
            JsonNode root = MAPPER.readTree(jsonAction);
            String toolName = null;
            if (root.has("tool")) {
                toolName = root.get("tool").asText();
            } else if (root.has("action")) {
                toolName = root.get("action").asText();
            } else if (root.has("name")) {
                toolName = root.get("name").asText();
            }

            if (toolName == null || toolName.isBlank()) {
                return "Error: Action payload missing 'tool' name.";
            }

            String argument = "";
            if (root.has("argument")) {
                argument = root.get("argument").asText("");
            } else if (root.has("parameters")) {
                JsonNode params = root.get("parameters");
                argument = params.isTextual() ? params.asText() : params.toString();
            } else if (root.has("input")) {
                argument = root.get("input").asText("");
            }

            return execute(toolName, argument);

        } catch (Exception e) {
            return "Error: Malformed action JSON: " + e.getMessage();
        }
    }

    public static boolean hasTool(String toolName) {
        if (toolName == null) return false;
        return TOOLS.containsKey(toolName.toLowerCase(Locale.ROOT));
    }

    public static ToolDefinition getTool(String toolName) {
        if (toolName == null) return null;
        return TOOLS.get(toolName.toLowerCase(Locale.ROOT));
    }

    public static synchronized List<ToolDefinition> getAllTools() {
        return new ArrayList<>(TOOLS.values());
    }

    public static synchronized String listTools() {
        return String.join(", ", TOOLS.keySet());
    }

    public static synchronized String listToolsDetailed() {
        StringBuilder sb = new StringBuilder("DEX Registered Capabilities:\n");
        for (ToolDefinition tool : TOOLS.values()) {
            sb.append("• ").append(tool.getName())
                    .append(" (").append(tool.getParameterDescription()).append("): ")
                    .append(tool.getDescription())
                    .append("\n");
        }
        return sb.toString();
    }

    /**
     * Generates a concise schema description for LLM prompt injection.
     */
    public static synchronized String getToolsDescription() {
        StringBuilder sb = new StringBuilder();
        for (ToolDefinition tool : TOOLS.values()) {
            sb.append("- ").append(tool.getName())
                    .append(" (").append(tool.getParameterDescription()).append("): ")
                    .append(tool.getDescription())
                    .append("\n");
        }
        return sb.toString();
    }
}
