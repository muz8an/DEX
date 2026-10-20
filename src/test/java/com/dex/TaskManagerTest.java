package com.dex;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TaskManagerTest {

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
        // Conversational or complex AI questions should return null to pass to the LLM
        assertNull(TaskManager.handle("explain quantum computing in simple terms"));
        assertNull(TaskManager.handle("write a python script to parse csv files"));
        assertNull(TaskManager.handle("what is the capital of France?"));
    }
}
