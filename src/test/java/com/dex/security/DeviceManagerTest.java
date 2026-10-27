package com.dex.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DeviceManagerTest {

    @BeforeEach
    public void setup() {
        DeviceManager.clearAllDevices();
        DeviceManager.generatePairingPin();
    }

    @Test
    public void testPinGenerationAndValidity() {
        String pin = DeviceManager.getActivePin();
        assertNotNull(pin);
        assertEquals(6, pin.length());
        assertTrue(DeviceManager.getPinRemainingSeconds() > 0);
    }

    @Test
    public void testPairDeviceSuccessAndTokenValidation() {
        String pin = DeviceManager.getActivePin();
        DeviceManager.PairedDevice dev = DeviceManager.pairDevice("Pixel 8 Pro", pin);

        assertNotNull(dev);
        assertEquals("Pixel 8 Pro", dev.getDeviceName());
        assertNotNull(dev.getToken());
        assertTrue(dev.getToken().startsWith("dex_dev_"));

        // Token must be valid
        assertTrue(DeviceManager.isValidDeviceToken(dev.getToken()));

        // Invalid token check
        assertFalse(DeviceManager.isValidDeviceToken("random_fake_token"));

        // The PIN should now be consumed / invalidated
        assertNull(DeviceManager.pairDevice("Another Phone", pin));
    }

    @Test
    public void testPairDeviceInvalidPin() {
        DeviceManager.PairedDevice dev = DeviceManager.pairDevice("Attacker Phone", "000000");
        assertNull(dev);
    }

    @Test
    public void testRevokeDevice() {
        String pin = DeviceManager.getActivePin();
        DeviceManager.PairedDevice dev = DeviceManager.pairDevice("Tablet", pin);
        assertNotNull(dev);
        assertTrue(DeviceManager.isValidDeviceToken(dev.getToken()));

        boolean revoked = DeviceManager.revokeDevice(dev.getDeviceId());
        assertTrue(revoked);

        // Token should now be rejected
        assertFalse(DeviceManager.isValidDeviceToken(dev.getToken()));
    }

    @Test
    public void testListPairedDevices() {
        String pin1 = DeviceManager.getActivePin();
        DeviceManager.pairDevice("Phone 1", pin1);

        String pin2 = DeviceManager.generatePairingPin();
        DeviceManager.pairDevice("Phone 2", pin2);

        List<DeviceManager.PairedDevice> list = DeviceManager.getPairedDevices();
        assertEquals(2, list.size());
    }
}
