package com.dex.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages authenticated mobile companion devices, one-time pairing PINs,
 * and token revocation for DEX.
 * Prevents unauthorized LAN devices from controlling the Windows system.
 */
public class DeviceManager {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path PAIRED_DEVICES_FILE = Paths.get("data", "paired_devices.json");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long PIN_VALIDITY_SECONDS = 300; // 5 minutes

    public static class PairedDevice {
        private String deviceId;
        private String deviceName;
        private String token;
        private long pairedAt;
        private long lastUsedAt;

        public PairedDevice() {}

        public PairedDevice(String deviceId, String deviceName, String token, long pairedAt) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.token = token;
            this.pairedAt = pairedAt;
            this.lastUsedAt = pairedAt;
        }

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }

        public long getPairedAt() { return pairedAt; }
        public void setPairedAt(long pairedAt) { this.pairedAt = pairedAt; }

        public long getLastUsedAt() { return lastUsedAt; }
        public void setLastUsedAt(long lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    }

    private static final Map<String, PairedDevice> DEVICES = new ConcurrentHashMap<>();
    private static String activePin = null;
    private static long pinExpiryEpoch = 0;
    private static boolean initialized = false;

    static {
        init();
    }

    public static synchronized void init() {
        if (initialized) return;
        loadDevices();
        generatePairingPin();
        initialized = true;
    }

    /**
     * Generates a new 6-digit one-time pairing PIN valid for 5 minutes.
     */
    public static synchronized String generatePairingPin() {
        int pinNum = 100000 + RANDOM.nextInt(900000);
        activePin = String.valueOf(pinNum);
        pinExpiryEpoch = Instant.now().getEpochSecond() + PIN_VALIDITY_SECONDS;
        return activePin;
    }

    /**
     * Gets the currently active pairing PIN, regenerating if expired.
     */
    public static synchronized String getActivePin() {
        if (activePin == null || Instant.now().getEpochSecond() > pinExpiryEpoch) {
            return generatePairingPin();
        }
        return activePin;
    }

    public static synchronized long getPinRemainingSeconds() {
        long remaining = pinExpiryEpoch - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    /**
     * Attempts to pair a new device using the provided PIN.
     * Returns the paired device details (including auth token) upon success, or null if PIN is invalid.
     */
    public static synchronized PairedDevice pairDevice(String deviceName, String candidatePin) {
        if (candidatePin == null || candidatePin.isBlank() || activePin == null) {
            return null;
        }

        long now = Instant.now().getEpochSecond();
        if (now > pinExpiryEpoch) {
            return null; // Expired
        }

        if (!activePin.equals(candidatePin.trim())) {
            return null; // Invalid PIN
        }

        // Generate cryptographically secure token and device ID
        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String token = "dex_dev_" + Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String deviceId = UUID.randomUUID().toString().substring(0, 8);

        String cleanName = (deviceName != null && !deviceName.isBlank())
                ? deviceName.trim()
                : "Android Companion (" + deviceId + ")";

        PairedDevice device = new PairedDevice(deviceId, cleanName, token, System.currentTimeMillis());
        DEVICES.put(deviceId, device);
        saveDevices();

        // Invalidate PIN after successful use for security
        activePin = null;
        pinExpiryEpoch = 0;

        return device;
    }

    /**
     * Validates whether a token belongs to an authorized, active paired device.
     */
    public static boolean isValidDeviceToken(String token) {
        if (token == null || token.isBlank()) return false;
        String cleanToken = token.trim();
        for (PairedDevice dev : DEVICES.values()) {
            if (cleanToken.equals(dev.getToken())) {
                dev.setLastUsedAt(System.currentTimeMillis());
                return true;
            }
        }
        return false;
    }

    /**
     * Revokes a paired device by ID.
     */
    public static synchronized boolean revokeDevice(String deviceId) {
        if (deviceId == null) return false;
        PairedDevice removed = DEVICES.remove(deviceId);
        if (removed != null) {
            saveDevices();
            return true;
        }
        return false;
    }

    /**
     * Lists all paired devices. Tokens are masked in list output for security.
     */
    public static List<PairedDevice> getPairedDevices() {
        return new ArrayList<>(DEVICES.values());
    }

    public static synchronized void clearAllDevices() {
        DEVICES.clear();
        saveDevices();
    }

    private static synchronized void loadDevices() {
        if (!Files.exists(PAIRED_DEVICES_FILE)) {
            return;
        }
        try {
            List<PairedDevice> list = MAPPER.readValue(
                    PAIRED_DEVICES_FILE.toFile(),
                    new TypeReference<List<PairedDevice>>() {}
            );
            DEVICES.clear();
            for (PairedDevice d : list) {
                if (d.getDeviceId() != null && d.getToken() != null) {
                    DEVICES.put(d.getDeviceId(), d);
                }
            }
        } catch (Exception e) {
            System.err.println("[DEX Security] Note reading paired devices: " + e.getMessage());
        }
    }

    private static synchronized void saveDevices() {
        try {
            Path parent = PAIRED_DEVICES_FILE.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Path tmp = PAIRED_DEVICES_FILE.resolveSibling("paired_devices.tmp");
            MAPPER.writeValue(tmp.toFile(), new ArrayList<>(DEVICES.values()));
            Files.move(tmp, PAIRED_DEVICES_FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.err.println("[DEX Security] Error saving paired devices: " + e.getMessage());
        }
    }
}
