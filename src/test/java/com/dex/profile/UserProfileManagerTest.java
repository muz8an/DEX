package com.dex.profile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class UserProfileManagerTest {

    @BeforeEach
    @AfterEach
    public void cleanup() {
        UserProfileManager.resetProfileFilePath();
    }

    @Test
    public void testFirstTimeUserDetection(@TempDir Path tempDir) {
        Path tempProfile = tempDir.resolve("user_profile.json");
        UserProfileManager.setProfileFilePathForTesting(tempProfile);

        assertTrue(UserProfileManager.isFirstTimeUser(), "Unconfigured profile must register as first-time user");
        assertFalse(UserProfileManager.hasCustomName(), "Initial profile should not have custom name");
        assertEquals("", UserProfileManager.getUserName());
    }

    @Test
    public void testCompleteSetupWithCustomName(@TempDir Path tempDir) {
        Path tempProfile = tempDir.resolve("user_profile.json");
        UserProfileManager.setProfileFilePathForTesting(tempProfile);

        UserProfileManager.completeSetup("Muzain");

        assertFalse(UserProfileManager.isFirstTimeUser(), "Profile setup should be completed");
        assertTrue(UserProfileManager.hasCustomName());
        assertEquals("Muzain", UserProfileManager.getUserName());
    }

    @Test
    public void testCompleteSetupSkipped(@TempDir Path tempDir) {
        Path tempProfile = tempDir.resolve("user_profile.json");
        UserProfileManager.setProfileFilePathForTesting(tempProfile);

        UserProfileManager.completeSetup("");

        assertFalse(UserProfileManager.isFirstTimeUser(), "Skipping name should still mark setup as completed");
        assertFalse(UserProfileManager.hasCustomName());
        assertEquals("", UserProfileManager.getUserName());
    }

    @Test
    public void testProfileReset(@TempDir Path tempDir) {
        Path tempProfile = tempDir.resolve("user_profile.json");
        UserProfileManager.setProfileFilePathForTesting(tempProfile);

        UserProfileManager.completeSetup("Alice");
        assertFalse(UserProfileManager.isFirstTimeUser());

        UserProfileManager.resetProfile();
        assertTrue(UserProfileManager.isFirstTimeUser(), "Resetting profile should trigger first-time setup again");
        assertFalse(UserProfileManager.hasCustomName());
        assertEquals("", UserProfileManager.getUserName());
    }

    @Test
    public void testProfilePersistenceAcrossReloads(@TempDir Path tempDir) {
        Path tempProfile = tempDir.resolve("user_profile.json");
        UserProfileManager.setProfileFilePathForTesting(tempProfile);

        UserProfileManager.completeSetup("Bob");

        // Force cache reload
        UserProfileManager.setProfileFilePathForTesting(tempProfile);
        assertEquals("Bob", UserProfileManager.getUserName());
        assertFalse(UserProfileManager.isFirstTimeUser());
    }
}
