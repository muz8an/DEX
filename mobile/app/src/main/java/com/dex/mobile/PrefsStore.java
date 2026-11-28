package com.dex.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * Manages persisted connection, device identity, and user preferences for DEX Mobile.
 */
public class PrefsStore {

    private static final String PREFS_NAME = "DEX_COMPANION_PREFS";
    private static final String KEY_HOST = "dex_host";
    private static final String KEY_PORT = "dex_port";
    private static final String KEY_TOKEN = "dex_auth_token";
    private static final String KEY_DEVICE_ID = "dex_device_id";
    private static final String KEY_USER_NAME = "dex_user_name";
    private static final String KEY_TTS_ENABLED = "dex_tts_enabled";
    private static final String KEY_FULLSCREEN = "dex_fullscreen";
    private static final String KEY_FIRST_LAUNCH = "first_launch_done";

    private final SharedPreferences prefs;

    public PrefsStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getHost() {
        return prefs.getString(KEY_HOST, "192.168.1.35");
    }

    public int getPort() {
        return prefs.getInt(KEY_PORT, DexClient.DEFAULT_PORT);
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    /** Returns an existing paired device ID or creates a stable mobile ID for Cloud/LAN telemetry. */
    public String getOrCreateDeviceId() {
        String id = prefs.getString(KEY_DEVICE_ID, null);
        if (id == null || id.isBlank()) {
            id = "android-" + UUID.randomUUID().toString().substring(0, 8);
            prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    public void saveConnection(String host, int port, String token, String deviceId) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_HOST, host != null ? host.trim() : "")
                .putInt(KEY_PORT, port > 0 ? port : DexClient.DEFAULT_PORT);
        if (token != null && !token.isBlank()) {
            editor.putString(KEY_TOKEN, token.trim());
        } else {
            editor.remove(KEY_TOKEN);
        }
        if (deviceId != null && !deviceId.isBlank()) {
            editor.putString(KEY_DEVICE_ID, deviceId.trim());
        }
        editor.apply();
    }

    public String getUserName(String fallback) {
        return prefs.getString(KEY_USER_NAME, fallback);
    }

    public void setUserName(String name) {
        prefs.edit().putString(KEY_USER_NAME, name.trim()).apply();
    }

    public boolean isTtsEnabled() {
        return prefs.getBoolean(KEY_TTS_ENABLED, true);
    }

    public void setTtsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TTS_ENABLED, enabled).apply();
    }

    public boolean isFullscreen() {
        return prefs.getBoolean(KEY_FULLSCREEN, false);
    }

    public void setFullscreen(boolean fullscreen) {
        prefs.edit().putBoolean(KEY_FULLSCREEN, fullscreen).apply();
    }

    public boolean isFirstLaunchDone() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, false);
    }

    public void markFirstLaunchDone() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, true).apply();
    }
}
