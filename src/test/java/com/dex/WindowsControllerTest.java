package com.dex;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WindowsControllerTest {

    @Test
    public void testCanonicalAppLookups() {
        assertNotNull(WindowsController.findApplication("calculator"));
        assertNotNull(WindowsController.findApplication("notepad"));
        assertNotNull(WindowsController.findApplication("chrome"));
        assertNotNull(WindowsController.findApplication("youtube"));
        assertNotNull(WindowsController.findApplication("vscode"));
        assertNotNull(WindowsController.findApplication("file explorer"));
        assertNotNull(WindowsController.findApplication("downloads"));
        assertNotNull(WindowsController.findApplication("documents"));
        assertNotNull(WindowsController.findApplication("desktop"));
    }

    @Test
    public void testAppAliases() {
        // Calculator aliases
        assertEquals("calculator", WindowsController.findApplication("calc").getCanonicalName());
        assertEquals("calculator", WindowsController.findApplication("calculater").getCanonicalName());
        assertEquals("calculator", WindowsController.findApplication("cal").getCanonicalName());

        // VS Code aliases
        assertEquals("vscode", WindowsController.findApplication("vs code").getCanonicalName());
        assertEquals("vscode", WindowsController.findApplication("vsc").getCanonicalName());

        // YouTube aliases
        assertEquals("youtube", WindowsController.findApplication("yt").getCanonicalName());
        assertEquals("youtube", WindowsController.findApplication("you tube").getCanonicalName());

        // Folder aliases with "my " prefix
        assertEquals("downloads", WindowsController.findApplication("my downloads").getCanonicalName());
        assertEquals("documents", WindowsController.findApplication("my documents").getCanonicalName());
        assertEquals("desktop", WindowsController.findApplication("my desktop").getCanonicalName());
    }

    @Test
    public void testTypoTolerance() {
        // Levenshtein distance matching within threshold
        assertEquals("calculator", WindowsController.findApplication("calculater").getCanonicalName());
        assertEquals("chrome", WindowsController.findApplication("crome").getCanonicalName());
    }

    @Test
    public void testUnknownApplicationHandling() {
        assertNull(WindowsController.findApplication("super_fake_app_12345"));
        String result = WindowsController.openApp("super_fake_app_12345");
        assertTrue(result.startsWith("Unsupported application: super_fake_app_12345"),
                "Should return clear unsupported message. Got: " + result);
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
}
