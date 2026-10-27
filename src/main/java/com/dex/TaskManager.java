package com.dex;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Task Execution and Orchestration Engine for DEX.
 * Interprets natural-language local requests, handles multi-step tasks,
 * enforces tool safety limits, and records task execution history.
 */
public class TaskManager {

    public enum StepStatus {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED,
        SKIPPED,
        CANCELLED
    }

    public static class TaskStep {
        private final int stepNumber;
        private final String description;
        private final String toolName;
        private final String argument;
        private StepStatus status;
        private String result;

        public TaskStep(int stepNumber, String description, String toolName, String argument) {
            this.stepNumber = stepNumber;
            this.description = description;
            this.toolName = toolName;
            this.argument = argument;
            this.status = StepStatus.PENDING;
            this.result = "";
        }

        public int getStepNumber() {
            return stepNumber;
        }

        public String getDescription() {
            return description;
        }

        public String getToolName() {
            return toolName;
        }

        public String getArgument() {
            return argument;
        }

        public StepStatus getStatus() {
            return status;
        }

        public void setStatus(StepStatus status) {
            this.status = status;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }
    }

    public static class TaskRecord {
        private final String taskId;
        private final String originalQuery;
        private final long timestamp;
        private final List<TaskStep> steps;
        private boolean cancelled = false;

        public TaskRecord(String taskId, String originalQuery, List<TaskStep> steps) {
            this.taskId = taskId;
            this.originalQuery = originalQuery;
            this.timestamp = System.currentTimeMillis();
            this.steps = steps;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getOriginalQuery() {
            return originalQuery;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public List<TaskStep> getSteps() {
            return Collections.unmodifiableList(steps);
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void cancel() {
            this.cancelled = true;
            for (TaskStep s : steps) {
                if (s.getStatus() == StepStatus.PENDING || s.getStatus() == StepStatus.RUNNING) {
                    s.setStatus(StepStatus.CANCELLED);
                }
            }
        }
    }

    private static final int MAX_STEPS = 5;
    private static final List<TaskRecord> TASK_HISTORY = new CopyOnWriteArrayList<>();
    private static int taskCounter = 1;

    // Pattern to split multi-step instructions ("... and then ...", "... then ...", "... and ...")
    private static final Pattern MULTI_STEP_PATTERN =
            Pattern.compile("(?i)\\s+(?:and\\s+then|then|and\\s+also|and)\\s+");

    private TaskManager() {
        // Utility class
    }

    /**
     * Determines if user input can be resolved locally as a registered single-step or multi-step task.
     * Returns the formatted execution report if handled locally, or null to route to the AI LLM.
     */
    public static String handle(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return "Please enter a task or question.";
        }

        String input = userInput.trim();

        // 1. Check for Task History commands
        if (input.equalsIgnoreCase("task history") || input.equalsIgnoreCase("tasks") || input.equalsIgnoreCase("task status")) {
            return getHistorySummary();
        }

        // 2. Decompose input into candidate action steps
        List<String> rawSteps = splitIntoSteps(input);

        // Check if all steps match known deterministic local tools
        List<TaskStep> plannedSteps = new ArrayList<>();
        for (int i = 0; i < rawSteps.size(); i++) {
            TaskStep step = parseSingleStep(i + 1, rawSteps.get(i));
            if (step != null) {
                plannedSteps.add(step);
            } else {
                // If this was a multi-step query but one part couldn't be parsed as a local tool,
                // do not partially execute; return null to let LLM handle intent
                if (rawSteps.size() > 1) {
                    return null;
                }
                return null;
            }
        }

        if (plannedSteps.isEmpty()) {
            return null;
        }

        if (plannedSteps.size() > MAX_STEPS) {
            return "Task limit exceeded: DEX supports up to " + MAX_STEPS + " sequential steps per request for security.";
        }

        // 3. Execute the planned task
        String taskId = "TASK-" + (taskCounter++);
        TaskRecord record = new TaskRecord(taskId, input, plannedSteps);
        TASK_HISTORY.add(record);
        if (TASK_HISTORY.size() > 50) {
            TASK_HISTORY.remove(0);
        }

        return executeTaskRecord(record);
    }

    private static List<String> splitIntoSteps(String input) {
        List<String> result = new ArrayList<>();
        String normalized = input.trim();

        // Avoid splitting queries like "open calculator and add 10+10" incorrectly
        // If the query contains " and add ", it's a compound app query handled directly
        if (normalized.toLowerCase(Locale.ROOT).contains(" and add ")) {
            result.add(normalized);
            return result;
        }

        String[] parts = MULTI_STEP_PATTERN.split(normalized);
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Inspects a single phrase and matches it against DEX's natural language tool patterns.
     */
    public static TaskStep parseSingleStep(int stepNumber, String phrase) {
        String p = phrase.trim();
        String lower = p.toLowerCase(Locale.ROOT)
                .replaceAll("^[\\.\\?!,;]+", "")
                .replaceAll("[\\.\\?!,;]+$", "")
                .trim();

        // 1. Tools discovery
        if (lower.equals("list tools")
                || lower.equals("list the tools you can use")
                || lower.equals("what tools can you use")
                || lower.equals("what can you do locally")
                || lower.equals("show tools")
                || lower.equals("tools")) {
            return new TaskStep(stepNumber, "List Registered Tools", "list_tools", "");
        }

        // 2. System time / date
        if (lower.equals("what time is it")
                || lower.equals("what is the time")
                || lower.equals("tell me the current time")
                || lower.equals("tell me the time")
                || lower.equals("current time")
                || lower.equals("what date is it")
                || lower.equals("what's the time")
                || lower.equals("time")) {
            return new TaskStep(stepNumber, "Retrieve Current Time", "get_current_time", "");
        }

        // 3. System Diagnostics / specs
        if (lower.equals("show me my computer's basic system information")
                || lower.equals("show system info")
                || lower.equals("system info")
                || lower.equals("system information")
                || lower.equals("pc info")
                || lower.equals("computer info")
                || lower.equals("system diagnostics")) {
            return new TaskStep(stepNumber, "Retrieve System Diagnostics", "get_system_info", "");
        }

        // 4. Desktop Screenshot
        if (lower.equals("take a screenshot")
                || lower.equals("capture screenshot")
                || lower.equals("capture screen")
                || lower.equals("screenshot")) {
            return new TaskStep(stepNumber, "Capture Desktop Screenshot", "take_screenshot", "");
        }

        // 5. Workspace file listing
        if (lower.equals("list files")
                || lower.equals("list workspace files")
                || lower.equals("show files")
                || lower.equals("show workspace files")) {
            return new TaskStep(stepNumber, "List Workspace Files", "list_files", "");
        }

        // 5b. Running processes listing
        if (lower.equals("list processes")
                || lower.equals("list running processes")
                || lower.equals("show running processes")
                || lower.equals("running apps")
                || lower.equals("running processes")
                || lower.equals("active processes")) {
            return new TaskStep(stepNumber, "List Running Processes", "list_processes", "");
        }

        // 5c. Web Search intent
        if (lower.startsWith("search ") || lower.startsWith("search for ")) {
            String q = p.replaceAll("(?i)^(?:search for|search)\\s+", "").trim();
            if (q.toLowerCase(Locale.ROOT).startsWith("youtube for ")) {
                String ytQuery = q.substring(12).trim();
                return new TaskStep(stepNumber, "Search YouTube: " + ytQuery, "open_url", "https://www.youtube.com/results?search_query=" + ytQuery);
            } else if (q.toLowerCase(Locale.ROOT).startsWith("github for ")) {
                String ghQuery = q.substring(11).trim();
                return new TaskStep(stepNumber, "Search GitHub: " + ghQuery, "open_url", "https://github.com/search?q=" + ghQuery);
            } else {
                return new TaskStep(stepNumber, "Search Web: " + q, "browser_search", q);
            }
        }

        // 5d. Music & Song Playback Intent (e.g. "play arz kiya hai", "open youtube and play arz kiya hai", "play believer on spotify", "listen to lofi")
        String cleanPhrase = p.replaceAll("[\\.\\?!,;]+$", "").trim();
        boolean isMusicIntent = Pattern.compile("(?i)^(?:can you |could you |please )?(?:open youtube and play|play on youtube|play on spotify|play song|play music|play track|listen to|play)\\b").matcher(cleanPhrase).find();
        if (isMusicIntent) {
            String musicQuery = cleanPhrase.replaceAll("(?i)^(?:can you |could you |please )?(?:open youtube and play|play on youtube|play on spotify|play song|play music|play track|listen to|play)\\s+", "")
                    .replaceAll("(?i)\\s+(?:for me|please|now)$", "")
                    .trim();

            if (!musicQuery.isBlank()) {
                // Check if target is actually an installed game (e.g. "play dead as disco", "play gta", "play rdr2", "play forza", "play pubg")
                WindowsController.ApplicationEntry maybeGame = WindowsController.findApplication(musicQuery);
                if (maybeGame != null && (maybeGame.getDescription().toLowerCase().contains("game")
                        || maybeGame.getDescription().toLowerCase().contains("steam")
                        || maybeGame.getCanonicalName().contains("game")
                        || isLikelyGame(maybeGame.getCanonicalName()))) {
                    return new TaskStep(stepNumber, "Launch Game: " + maybeGame.getCanonicalName(), "open_app", maybeGame.getCanonicalName());
                }

                return new TaskStep(stepNumber, "Play Music: " + musicQuery, "play_music", musicQuery);
            }
        }

        // 6. Application & Web Destinations (e.g., "open whatsapp", "open notepad", "go to youtube", "play dead as disco", "open vs code")
        boolean hasActionVerb = Pattern.compile("(?i)^(?:can you |could you |please )?(?:open|launch|start|run|play game|go to|browse to|visit|watch)\\b").matcher(cleanPhrase).find();
        if (hasActionVerb) {
            String targetQuery = cleanPhrase.replaceAll("(?i)^(?:can you |could you |please )?(?:open|launch|start|run|play game|go to|browse to|visit|watch)\\s+(?:my\\s+)?", "")
                    .replaceAll("(?i)\\s+(?:for me|please|now)$", "")
                    .trim();

            if (targetQuery.toLowerCase(Locale.ROOT).contains("youtube") || targetQuery.equalsIgnoreCase("yt") || targetQuery.contains("youtu.be")) {
                if (targetQuery.toLowerCase(Locale.ROOT).contains("play ") || targetQuery.toLowerCase(Locale.ROOT).contains("search ")) {
                    String song = targetQuery.replaceAll("(?i).*?\\b(?:play|search(?: for)?)\\s+", "").trim();
                    return new TaskStep(stepNumber, "Play on YouTube: " + song, "play_music", song);
                }
                return new TaskStep(stepNumber, "Open YouTube" + (targetQuery.toLowerCase(Locale.ROOT).contains("chrome") ? " in Chrome" : ""), "open_app", targetQuery);
            }
            if (targetQuery.toLowerCase(Locale.ROOT).contains("google") && !targetQuery.toLowerCase(Locale.ROOT).contains("chrome")) {
                return new TaskStep(stepNumber, "Open Google in default browser", "open_app", targetQuery);
            }
            if (targetQuery.toLowerCase(Locale.ROOT).contains("github")) {
                return new TaskStep(stepNumber, "Open GitHub in default browser", "open_app", targetQuery);
            }

            WindowsController.ApplicationEntry app = WindowsController.findApplication(targetQuery);
            if (app != null) {
                return new TaskStep(stepNumber, "Launch Application: " + app.getCanonicalName(), "open_app", app.getCanonicalName());
            } else {
                return new TaskStep(stepNumber, "Launch Application: " + targetQuery, "open_app", targetQuery);
            }
        }

        // Direct app alias check (only for short 1-3 word queries like 'notepad', 'calc', 'whatsapp', 'steam', 'youtube')
        if (lower.split("\\s+").length <= 3) {
            if (lower.contains("youtube") || lower.equals("yt") || lower.contains("youtu.be")) {
                return new TaskStep(stepNumber, "Open YouTube", "open_app", lower);
            }
            if (lower.equals("google") || lower.equals("google search") || lower.equals("google.com")) {
                return new TaskStep(stepNumber, "Open Google", "open_app", lower);
            }
            if (lower.equals("github") || lower.equals("git hub") || lower.equals("github.com")) {
                return new TaskStep(stepNumber, "Open GitHub", "open_app", lower);
            }
            WindowsController.ApplicationEntry directApp = WindowsController.findApplication(lower);
            if (directApp != null) {
                return new TaskStep(stepNumber, "Launch Application: " + directApp.getCanonicalName(), "open_app", directApp.getCanonicalName());
            }
        }

        // 7. Open URL
        Matcher urlMatcher = Pattern.compile("(?i)^(?:open url|browse to|visit)\\s+(https?://\\S+|\\S+\\.\\S+)$").matcher(p);
        if (urlMatcher.matches()) {
            String url = urlMatcher.group(1).trim();
            return new TaskStep(stepNumber, "Open Web URL: " + url, "open_url", url);
        }

        // 8. Restricted command runner
        Matcher cmdMatcher = Pattern.compile("(?i)^(?:run command|execute command|exec)\\s+(.+)$").matcher(p);
        if (cmdMatcher.matches()) {
            String cmd = cmdMatcher.group(1).trim();
            return new TaskStep(stepNumber, "Execute Build Command: " + cmd, "run_command", cmd);
        }

        // 9. Read file
        Matcher readMatcher = Pattern.compile("(?i)^(?:read file|show file|open file)\\s+(.+)$").matcher(p);
        if (readMatcher.matches()) {
            String path = readMatcher.group(1).trim();
            return new TaskStep(stepNumber, "Read File: " + path, "read_file", path);
        }

        return null;
    }

    private static String executeTaskRecord(TaskRecord record) {
        StringBuilder output = new StringBuilder();
        boolean isMultiStep = record.getSteps().size() > 1;

        if (isMultiStep) {
            output.append("=== Multi-Step Task: ").append(record.getTaskId()).append(" ===\n");
        }

        boolean previousStepSucceeded = true;

        for (TaskStep step : record.getSteps()) {
            if (record.isCancelled()) {
                step.setStatus(StepStatus.CANCELLED);
                step.setResult("Cancelled by user request.");
                output.append("• Step ").append(step.getStepNumber()).append(": [CANCELLED] ").append(step.getDescription()).append("\n");
                continue;
            }

            if (!previousStepSucceeded) {
                step.setStatus(StepStatus.SKIPPED);
                step.setResult("Skipped due to earlier step failure.");
                output.append("• Step ").append(step.getStepNumber()).append(": [SKIPPED] ").append(step.getDescription()).append("\n");
                continue;
            }

            step.setStatus(StepStatus.RUNNING);

            try {
                String result = ToolManager.execute(step.getToolName(), step.getArgument());
                step.setResult(result);

                if (result != null && (result.startsWith("Failed to")
                        || result.startsWith("Error:")
                        || result.startsWith("Couldn't open")
                        || result.startsWith("Unsupported application")
                        || result.startsWith("Security Error:"))) {
                    step.setStatus(StepStatus.FAILED);
                    previousStepSucceeded = false;
                    output.append(isMultiStep ? "• Step " + step.getStepNumber() + ": [FAILED] " : "")
                            .append(step.getDescription()).append("\n  Result: ").append(result).append("\n");
                } else {
                    step.setStatus(StepStatus.SUCCESS);
                    if (isMultiStep) {
                        output.append("• Step ").append(step.getStepNumber()).append(": [SUCCESS] ")
                                .append(step.getDescription()).append("\n  Result: ").append(result).append("\n");
                    } else {
                        output.append(result);
                    }
                }

            } catch (Exception e) {
                step.setStatus(StepStatus.FAILED);
                step.setResult("Execution error: " + e.getMessage());
                previousStepSucceeded = false;
                output.append(isMultiStep ? "• Step " + step.getStepNumber() + ": [FAILED] " : "")
                        .append(step.getDescription()).append("\n  Error: ").append(e.getMessage()).append("\n");
            }
        }

        return output.toString().trim();
    }

    public static List<TaskRecord> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(TASK_HISTORY));
    }

    public static String getHistorySummary() {
        if (TASK_HISTORY.isEmpty()) {
            return "No tasks have been executed in this session.";
        }

        StringBuilder sb = new StringBuilder("=== DEX Task Execution History ===\n");
        for (TaskRecord record : TASK_HISTORY) {
            sb.append("\n[").append(record.getTaskId()).append("] \"").append(record.getOriginalQuery()).append("\"\n");
            for (TaskStep step : record.getSteps()) {
                sb.append("  • Step ").append(step.getStepNumber()).append(" [").append(step.getStatus()).append("]: ")
                        .append(step.getDescription()).append("\n");
            }
        }
        return sb.toString();
    }

    public static boolean cancelTask(String taskId) {
        for (TaskRecord r : TASK_HISTORY) {
            if (r.getTaskId().equalsIgnoreCase(taskId)) {
                r.cancel();
                return true;
            }
        }
        return false;
    }

    private static boolean isLikelyGame(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("steam") || lower.contains("disco") || lower.contains("gta")
                || lower.contains("red dead") || lower.contains("forza") || lower.contains("pubg")
                || lower.contains("detroit") || lower.contains("valorant") || lower.contains("game")
                || lower.contains("motorfest") || lower.contains("ea sports") || lower.contains("fifa")
                || lower.contains("solitaire");
    }
}