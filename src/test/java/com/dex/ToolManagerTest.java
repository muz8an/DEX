package com.dex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ToolManagerTest {

    @Test
    public void testRegisteredToolsExist() {
        assertTrue(ToolManager.hasTool("open_app"));
        assertTrue(ToolManager.hasTool("play_music"));
        assertTrue(ToolManager.hasTool("get_current_time"));
        assertTrue(ToolManager.hasTool("get_system_info"));
        assertTrue(ToolManager.hasTool("take_screenshot"));
        assertTrue(ToolManager.hasTool("open_url"));
        assertTrue(ToolManager.hasTool("speak"));
        assertTrue(ToolManager.hasTool("list_files"));
        assertTrue(ToolManager.hasTool("read_file"));
        assertTrue(ToolManager.hasTool("write_file"));
        assertTrue(ToolManager.hasTool("save_code"));
        assertTrue(ToolManager.hasTool("search_files"));
        assertTrue(ToolManager.hasTool("open_path"));
        assertTrue(ToolManager.hasTool("run_command"));
        assertTrue(ToolManager.hasTool("list_tools"));
    }

    @Test
    public void testGetCurrentTimeExecution() {
        String result = ToolManager.execute("get_current_time", "");
        assertNotNull(result);
        assertTrue(result.contains("Current Date & Time:"));
    }

    @Test
    public void testGetSystemInfoExecution() {
        String result = ToolManager.execute("get_system_info", "");
        assertNotNull(result);
        assertTrue(result.contains("=== System Diagnostics ==="));
        assertTrue(result.contains("OS:"));
        assertTrue(result.contains("Java Runtime:"));
    }

    @Test
    public void testListToolsExecution() {
        String result = ToolManager.execute("list_tools", "");
        assertNotNull(result);
        assertTrue(result.contains("DEX Registered Capabilities:"));
        assertTrue(result.contains("open_app"));
        assertTrue(result.contains("get_current_time"));
    }

    @Test
    public void testUnknownToolExecution() {
        String result = ToolManager.execute("fake_tool_xyz", "");
        assertTrue(result.startsWith("Error: Unknown tool 'fake_tool_xyz'"));
    }

    @Test
    public void testJsonActionExecution() {
        String json = "{\"tool\": \"get_current_time\", \"argument\": \"\"}";
        String result = ToolManager.executeJsonAction(json);
        assertNotNull(result);
        assertTrue(result.contains("Current Date & Time:"));
    }

    @Test
    public void testMalformedJsonAction() {
        String result = ToolManager.executeJsonAction("not valid json at all");
        assertTrue(result.startsWith("Error: Malformed action JSON:"));
    }

    @Test
    public void testToolsDescription() {
        String desc = ToolManager.getToolsDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("- open_app"));
        assertTrue(desc.contains("- get_current_time"));
    }
}
