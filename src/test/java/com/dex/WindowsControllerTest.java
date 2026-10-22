package com.dex;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WindowsControllerTest {

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

    private List<List<String>> executedCommands;

    @BeforeEach
    public void setUp() {
        executedCommands = new ArrayList<>();
        WindowsController.setProcessLauncher(command -> {
            executedCommands.add(new ArrayList<>(command));
            return new MockProcess(12345L);
        });
    }

    @AfterEach
    public void tearDown() {
        WindowsController.resetProcessLauncher();
    }

    @Test
    public void testCanonicalAppLookups() {
        assertNotNull(WindowsController.findApplication("calculator"));
        assertNotNull(WindowsController.findApplication("notepad"));
        assertNotNull(WindowsController.findApplication("chrome"));
        assertNotNull(WindowsController.findApplication("youtube"));
        assertNotNull(WindowsController.findApplication("google"));
        assertNotNull(WindowsController.findApplication("github"));
        assertNotNull(WindowsController.findApplication("vscode"));
        assertNotNull(WindowsController.findApplication("file explorer"));
        assertNotNull(WindowsController.findApplication("downloads"));
        assertNotNull(WindowsController.findApplication("documents"));
        assertNotNull(WindowsController.findApplication("desktop"));
    }

    @Test
    public void testAppAliases() {
        assertEquals("calculator", WindowsController.findApplication("calc").getCanonicalName());
        assertEquals("calculator", WindowsController.findApplication("calculater").getCanonicalName());
        assertEquals("calculator", WindowsController.findApplication("cal").getCanonicalName());

        assertEquals("vscode", WindowsController.findApplication("vs code").getCanonicalName());
        assertEquals("vscode", WindowsController.findApplication("vsc").getCanonicalName());

        assertEquals("youtube", WindowsController.findApplication("yt").getCanonicalName());
        assertEquals("youtube", WindowsController.findApplication("you tube").getCanonicalName());
        assertEquals("youtube", WindowsController.findApplication("youtube.com").getCanonicalName());
        assertEquals("youtube", WindowsController.findApplication("launch youtube").getCanonicalName());
        assertEquals("youtube", WindowsController.findApplication("open youtube").getCanonicalName());

        assertEquals("google", WindowsController.findApplication("google search").getCanonicalName());
        assertEquals("google", WindowsController.findApplication("open google").getCanonicalName());

        assertEquals("github", WindowsController.findApplication("git hub").getCanonicalName());
        assertEquals("github", WindowsController.findApplication("open github").getCanonicalName());

        assertEquals("downloads", WindowsController.findApplication("my downloads").getCanonicalName());
        assertEquals("documents", WindowsController.findApplication("my documents").getCanonicalName());
        assertEquals("desktop", WindowsController.findApplication("my desktop").getCanonicalName());
    }

    @Test
    public void testTypoTolerance() {
        assertEquals("calculator", WindowsController.findApplication("calculater").getCanonicalName());
        assertEquals("chrome", WindowsController.findApplication("crome").getCanonicalName());
    }

    @Test
    public void testUnknownApplicationHandling() {
        assertNull(WindowsController.findApplication("super_fake_app_12345"));
        WindowsController.LaunchResult result = WindowsController.executeAppLaunch("super_fake_app_12345");
        assertFalse(result.isSuccess());
        assertTrue(result.getError().startsWith("Unsupported application: super_fake_app_12345"),
                "Should return clear unsupported message. Got: " + result.getError());
    }

    @Test
    public void testListSupportedApps() {
        List<String> apps = WindowsController.listSupportedApps();
        assertFalse(apps.isEmpty());
        assertTrue(apps.stream().anyMatch(a -> a.contains("calculator")));
        assertTrue(apps.stream().anyMatch(a -> a.contains("notepad")));
        assertTrue(apps.stream().anyMatch(a -> a.contains("chrome")));
        assertTrue(apps.stream().anyMatch(a -> a.contains("youtube")));
    }

    @Test
    public void testOpenNotepadWithMockLauncher() {
        WindowsController.LaunchResult result = WindowsController.executeAppLaunch("notepad");
        assertTrue(result.isSuccess());
        assertEquals("notepad", result.getTarget());
        assertEquals(12345L, result.getPid());
        assertTrue(result.toDisplayString().contains("Successfully launched notepad (PID: 12345)"));
        assertEquals(1, executedCommands.size());
        assertTrue(executedCommands.get(0).contains("notepad.exe"));
    }

    @Test
    public void testOpenCalculatorWithMockLauncher() {
        WindowsController.LaunchResult result = WindowsController.executeAppLaunch("calculator");
        assertTrue(result.isSuccess());
        assertEquals("calculator", result.getTarget());
        assertEquals(12345L, result.getPid());
        assertEquals(1, executedCommands.size());
        assertTrue(executedCommands.get(0).contains("calculator:"));
    }

    @Test
    public void testOpenYouTubeInDefaultBrowser() {
        WindowsController.LaunchResult result = WindowsController.executeOpenYouTube(false);
        assertTrue(result.isSuccess());
        assertEquals("YouTube in default browser", result.getTarget());
        assertEquals(1, executedCommands.size());
        assertTrue(executedCommands.get(0).contains("https://www.youtube.com"));
    }

    @Test
    public void testOpenGoogleAndGitHub() {
        WindowsController.LaunchResult googleRes = WindowsController.executeOpenGoogle(false);
        assertTrue(googleRes.isSuccess());
        assertEquals("Google in default browser", googleRes.getTarget());

        WindowsController.LaunchResult githubRes = WindowsController.executeOpenGitHub();
        assertTrue(githubRes.isSuccess());
        assertEquals("GitHub in default browser", githubRes.getTarget());
        assertTrue(executedCommands.get(executedCommands.size() - 1).contains("https://github.com"));
    }

    @Test
    public void testProcessLaunchExceptionHandling() {
        WindowsController.setProcessLauncher(cmd -> {
            throw new IOException("Access is denied (simulated)");
        });

        WindowsController.LaunchResult result = WindowsController.executeAppLaunch("notepad");
        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("Access is denied (simulated)"));
        assertTrue(result.toDisplayString().startsWith("Failed to launch notepad: IOException: Access is denied"));
    }

    @Test
    public void testYouTubeCommandInCmdArray() {
        WindowsController.ApplicationEntry yt = WindowsController.findApplication("youtube");
        assertNotNull(yt, "YouTube app must be registered");
        String[] cmd = yt.getCommand();
        assertEquals(5, cmd.length, "YouTube command should have 5 tokens (cmd.exe /c start \"\" URL)");
        assertEquals("cmd.exe", cmd[0]);
        assertEquals("/c", cmd[1]);
        assertEquals("start", cmd[2]);
        assertEquals("", cmd[3], "Empty title argument is required");
        assertEquals("https://www.youtube.com", cmd[4]);
    }

    @Test
    public void testChromeCommandHasEmptyTitle() {
        WindowsController.ApplicationEntry chrome = WindowsController.findApplication("chrome");
        assertNotNull(chrome);
        String[] cmd = chrome.getCommand();
        assertEquals(5, cmd.length, "Chrome command should have 5 tokens");
        assertEquals("", cmd[3], "Empty title argument required for cmd start");
    }

    @Test
    public void testSettingsCmdPowerShellRegistered() {
        WindowsController.ApplicationEntry settings = WindowsController.findApplication("settings");
        assertNotNull(settings);
        assertEquals("ms-settings:", settings.getCommand()[4]);

        WindowsController.ApplicationEntry cmd = WindowsController.findApplication("cmd");
        assertNotNull(cmd);
        assertEquals("cmd.exe", cmd.getCommand()[4]);

        WindowsController.ApplicationEntry ps = WindowsController.findApplication("powershell");
        assertNotNull(ps);
        assertEquals("powershell.exe", ps.getCommand()[4]);
    }
}
