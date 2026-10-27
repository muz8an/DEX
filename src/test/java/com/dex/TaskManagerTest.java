package com.dex;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TaskManagerTest {

    private static class MockProcess extends Process {
        private final long pid;

        public MockProcess(long pid) {
            this.pid = pid;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public long pid() {
            return pid;
        }
    }

    @BeforeEach
    public void setUp() {
        WindowsController.setProcessLauncher(cmd -> new MockProcess(5555L));
    }

    @AfterEach
    public void tearDown() {
        WindowsController.resetProcessLauncher();
    }

    @Test
    public void testSingleStepNaturalLanguageParsing() {
        TaskManager.TaskStep step1 = TaskManager.parseSingleStep(1, "open calculator");
        assertNotNull(step1);
        assertEquals("open_app", step1.getToolName());
        assertEquals("calculator", step1.getArgument());

        TaskManager.TaskStep step2 = TaskManager.parseSingleStep(2, "Can you open Notepad for me?");
        assertNotNull(step2);
        assertEquals("open_app", step2.getToolName());
        assertEquals("notepad", step2.getArgument());

        TaskManager.TaskStep step3 = TaskManager.parseSingleStep(3, "what time is it?");
        assertNotNull(step3);
        assertEquals("get_current_time", step3.getToolName());

        TaskManager.TaskStep step4 = TaskManager.parseSingleStep(4, "show me my computer's basic system information");
        assertNotNull(step4);
        assertEquals("get_system_info", step4.getToolName());

        TaskManager.TaskStep step5 = TaskManager.parseSingleStep(5, "list tools");
        assertNotNull(step5);
        assertEquals("list_tools", step5.getToolName());
    }

    @Test
    public void testIntentRecognitionForOpenYouTube() {
        TaskManager.TaskStep step1 = TaskManager.parseSingleStep(1, "Open YouTube");
        assertNotNull(step1);
        assertEquals("open_app", step1.getToolName());
        assertTrue(step1.getArgument().toLowerCase().contains("youtube"));

        TaskManager.TaskStep step2 = TaskManager.parseSingleStep(2, "Open YouTube in the default browser");
        assertNotNull(step2);
        assertEquals("open_app", step2.getToolName());

        TaskManager.TaskStep step3 = TaskManager.parseSingleStep(3, "Open YouTube in Chrome");
        assertNotNull(step3);
        assertEquals("open_app", step3.getToolName());
        assertTrue(step3.getArgument().toLowerCase().contains("chrome"));
    }

    @Test
    public void testIntentRecognitionForOpenGoogleAndGitHub() {
        TaskManager.TaskStep googleStep = TaskManager.parseSingleStep(1, "Open Google");
        assertNotNull(googleStep);
        assertEquals("open_app", googleStep.getToolName());

        TaskManager.TaskStep githubStep = TaskManager.parseSingleStep(1, "Open GitHub");
        assertNotNull(githubStep);
        assertEquals("open_app", githubStep.getToolName());
    }

    @Test
    public void testIntentRecognitionForOtherCoreApps() {
        TaskManager.TaskStep calc = TaskManager.parseSingleStep(1, "Open Calculator");
        assertNotNull(calc);
        assertEquals("open_app", calc.getToolName());
        assertEquals("calculator", calc.getArgument());

        TaskManager.TaskStep explorer = TaskManager.parseSingleStep(1, "Open File Explorer");
        assertNotNull(explorer);
        assertEquals("open_app", explorer.getToolName());
        assertEquals("file explorer", explorer.getArgument());

        TaskManager.TaskStep vscode = TaskManager.parseSingleStep(1, "Open VS Code");
        assertNotNull(vscode);
        assertEquals("open_app", vscode.getToolName());
        assertEquals("vscode", vscode.getArgument());

        TaskManager.TaskStep chrome = TaskManager.parseSingleStep(1, "Open Chrome");
        assertNotNull(chrome);
        assertEquals("open_app", chrome.getToolName());
        assertEquals("chrome", chrome.getArgument());
    }

    @Test
    public void testRoutingToWindowsControllerWithMock() {
        String result = TaskManager.handle("open notepad");
        assertNotNull(result);
        assertTrue(result.contains("Successfully launched notepad (PID: 5555)"),
                "Expected success result with PID. Got: " + result);

        List<TaskManager.TaskRecord> history = TaskManager.getHistory();
        assertFalse(history.isEmpty());
        TaskManager.TaskRecord last = history.get(history.size() - 1);
        assertEquals(TaskManager.StepStatus.SUCCESS, last.getSteps().get(0).getStatus());
    }

    @Test
    public void testTaskManagerHandlesFailureAccurately() {
        WindowsController.setProcessLauncher(cmd -> {
            throw new IOException("Simulated launch failure");
        });

        String result = TaskManager.handle("open notepad");
        assertNotNull(result);
        assertTrue(result.contains("Failed to launch notepad"), "Expected failure message. Got: " + result);

        List<TaskManager.TaskRecord> history = TaskManager.getHistory();
        assertFalse(history.isEmpty());
        TaskManager.TaskRecord last = history.get(history.size() - 1);
        assertEquals(TaskManager.StepStatus.FAILED, last.getSteps().get(0).getStatus());
    }

    @Test
    public void testMultiStepTaskExecution() {
        String result = TaskManager.handle("what time is it and show system info");
        assertNotNull(result);
        assertTrue(result.contains("Multi-Step Task:"));
        assertTrue(result.contains("Retrieve Current Time"));
        assertTrue(result.contains("Retrieve System Diagnostics"));

        List<TaskManager.TaskRecord> history = TaskManager.getHistory();
        assertFalse(history.isEmpty());
        TaskManager.TaskRecord last = history.get(history.size() - 1);
        assertEquals(2, last.getSteps().size());
        assertEquals(TaskManager.StepStatus.SUCCESS, last.getSteps().get(0).getStatus());
        assertEquals(TaskManager.StepStatus.SUCCESS, last.getSteps().get(1).getStatus());
    }

    @Test
    public void testTaskHistoryRetrieval() {
        String historySummary = TaskManager.handle("task history");
        assertNotNull(historySummary);
        assertTrue(historySummary.contains("DEX Task Execution History"));
    }

    @Test
    public void testNonToolInputReturnsNullForLLM() {
        assertNull(TaskManager.handle("explain quantum computing in simple terms"));
        assertNull(TaskManager.handle("write a python script to parse csv files"));
        assertNull(TaskManager.handle("what is the capital of France?"));
    }

    @Test
    public void testTaskCancellation() {
        assertFalse(TaskManager.cancelTask("non-existent-id"));
        TaskManager.handle("what time is it");
        List<TaskManager.TaskRecord> history = TaskManager.getHistory();
        assertFalse(history.isEmpty());
        String lastId = history.get(history.size() - 1).getTaskId();
        assertTrue(TaskManager.cancelTask(lastId));
        assertTrue(history.get(history.size() - 1).isCancelled());
    }

    @Test
    public void testMusicPlaybackParsing() {
        TaskManager.TaskStep step1 = TaskManager.parseSingleStep(1, "open youtube and play arz kiya hai");
        assertNotNull(step1);
        assertEquals("play_music", step1.getToolName());
        assertEquals("arz kiya hai", step1.getArgument());

        TaskManager.TaskStep step2 = TaskManager.parseSingleStep(1, "play arz kiya hai");
        assertNotNull(step2);
        assertEquals("play_music", step2.getToolName());
        assertEquals("arz kiya hai", step2.getArgument());

        TaskManager.TaskStep step3 = TaskManager.parseSingleStep(1, "play starboy on spotify");
        assertNotNull(step3);
        assertEquals("play_music", step3.getToolName());
        assertTrue(step3.getArgument().contains("starboy"));
    }

    @Test
    public void testDynamicAppParsing() {
        TaskManager.TaskStep wa = TaskManager.parseSingleStep(1, "open whatsapp");
        assertNotNull(wa);
        assertEquals("open_app", wa.getToolName());

        TaskManager.TaskStep steam = TaskManager.parseSingleStep(1, "open steam");
        assertNotNull(steam);
        assertEquals("open_app", steam.getToolName());
    }
}
