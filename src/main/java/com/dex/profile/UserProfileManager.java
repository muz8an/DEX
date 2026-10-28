package com.dex.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe persistence and access manager for the user's personal profile.
 * Ensures isolation between users and stores profile in data/user_profile.json.
 */
public class UserProfileManager {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static Path profileFile = Paths.get("data", "user_profile.json");

    private static UserProfile cachedProfile = null;
    private static final List<Runnable> PROFILE_LISTENERS = new CopyOnWriteArrayList<>();

    public static synchronized void setProfileFilePathForTesting(Path path) {
        profileFile = path;
        cachedProfile = null;
    }

    public static synchronized void resetProfileFilePath() {
        profileFile = Paths.get("data", "user_profile.json");
        cachedProfile = null;
    }

    public static synchronized UserProfile getProfile() {
        if (cachedProfile == null) {
            cachedProfile = loadProfile();
        }
        return cachedProfile;
    }

    public static synchronized boolean isFirstTimeUser() {
        return !getProfile().isSetupCompleted();
    }

    public static synchronized boolean isSetupCompleted() {
        return getProfile().isSetupCompleted();
    }

    public static synchronized String getUserName() {
        return getProfile().getUserName();
    }

    public static String formatDisplayName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String[] parts = raw.trim().split("[._\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.length() > 0 ? sb.toString() : raw;
    }

    public static synchronized boolean hasCustomName() {
        return getProfile().hasCustomName();
    }

    /**
     * Completes first-time user setup with the entered name (or empty string if skipped).
     */
    public static synchronized void completeSetup(String name) {
        UserProfile profile = getProfile();
        profile.setUserName(name != null ? name.trim() : "");
        profile.setSetupCompleted(true);
        saveProfile(profile);
        notifyListeners();
    }

    /**
     * Updates the user's name in their existing profile.
     */
    public static synchronized void setUserName(String name) {
        UserProfile profile = getProfile();
        profile.setUserName(name != null ? name.trim() : "");
        saveProfile(profile);
        notifyListeners();
    }

    /**
     * Resets the profile so first-time setup can be triggered again.
     * Note: Existing conversation history is safely preserved.
     */
    public static synchronized void resetProfile() {
        UserProfile profile = getProfile();
        profile.setUserName("");
        profile.setSetupCompleted(false);
        saveProfile(profile);
        notifyListeners();
    }

    public static void addProfileListener(Runnable listener) {
        if (listener != null) {
            PROFILE_LISTENERS.add(listener);
        }
    }

    public static void removeProfileListener(Runnable listener) {
        if (listener != null) {
            PROFILE_LISTENERS.remove(listener);
        }
    }

    private static void notifyListeners() {
        for (Runnable r : PROFILE_LISTENERS) {
            try {
                r.run();
            } catch (Exception e) {
                System.err.println("UserProfileManager listener error: " + e.getMessage());
            }
        }
    }

    private static synchronized UserProfile loadProfile() {
        try {
            if (profileFile != null && Files.exists(profileFile) && Files.size(profileFile) > 0) {
                UserProfile profile = MAPPER.readValue(profileFile.toFile(), UserProfile.class);
                if (profile != null) {
                    return profile;
                }
            }
        } catch (Exception e) {
            System.err.println("DEX Warning: Failed to parse user_profile.json (" + e.getMessage() + "). Initializing fresh profile.");
        }
        return new UserProfile("", false);
    }

    private static synchronized boolean saveProfile(UserProfile profile) {
        if (profileFile == null) {
            return false;
        }
        try {
            if (profileFile.getParent() != null && !Files.exists(profileFile.getParent())) {
                Files.createDirectories(profileFile.getParent());
            }
            Path tmpFile = profileFile.resolveSibling(profileFile.getFileName().toString() + ".tmp");
            MAPPER.writeValue(tmpFile.toFile(), profile);
            try {
                Files.move(tmpFile, profileFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmpFile, profileFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            System.err.println("DEX Error: Failed to save user_profile.json: " + e.getMessage());
            return false;
        }
    }
}
