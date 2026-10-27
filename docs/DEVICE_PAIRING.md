# DEX Mobile Companion Device Pairing Guide

DEX enforces an authenticated, zero-trust device pairing protocol between the **Windows Desktop AI Assistant** and the **Android Mobile Companion**.

This guide covers how to pair an Android device, how authentication tokens are managed, and how access can be audited or revoked.

---

## 1. Prerequisites

1. **DEX Desktop Application** running on your Windows 10/11 PC.
2. **DEX Mobile APK** installed on your Android device (Android 8.0+ / API 26-34).
3. **Network Connectivity**:
   - Both devices connected to the same Wi-Fi / Local Area Network (LAN), OR
   - Both devices connected to a private secure VPN mesh (such as **Tailscale** or **WireGuard**).

> [!NOTE]
> Android devices cannot use `127.0.0.1` or `localhost` to connect to your PC. The Android app connects to your PC's LAN IP (e.g. `192.168.1.50`) or VPN IP (e.g. `100.x.y.z`).

---

## 2. Pairing Workflow (Step-by-Step)

### Step 1: Open the Mobile Companion Tab on PC
1. Launch DEX on your Windows desktop (via `DEX.exe`, `DEX-Setup.exe`, `DEX.bat`, or `DEX.vbs`).
2. Navigate to the **Mobile Companion** tab (the 8th tab in the main desktop window).
3. You will see:
   - Your PC's detected **Local IP Addresses** (e.g., `192.168.1.45`, `100.85.20.12`).
   - The DEX Server Port (default: `8765`).
   - The **Active Paired Devices** list.

### Step 2: Generate a 6-Digit One-Time Pairing PIN
1. In the **Mobile Companion** tab on PC, click the button: **`Generate New Pairing PIN`**.
2. DEX will generate and display a large cyan 6-digit numeric PIN (e.g., `492018`).
3. **Security TTL**: The PIN is valid for exactly **5 minutes (300 seconds)**. It can only be used once; upon successful pairing, it is instantly invalidated.

### Step 3: Enter Connection Details on Android
1. Open the **DEX Mobile Companion** app on your Android phone.
2. In the top bar or under the **Settings / Pairing** tab:
   - Enter your **Host IP** (the IP displayed on your PC, e.g. `192.168.1.45`).
   - Enter the **Port** (`8765`).
   - Enter the **6-digit PIN** displayed on your PC.
3. Tap **`Pair with Desktop`**.

### Step 4: Pairing Confirmation
1. The Android app sends an authenticated pairing request: `POST /api/pair { "pin": "...", "deviceName": "..." }`.
2. The Windows backend verifies the PIN against the active `DeviceManager` registration table.
3. Upon validation:
   - The backend generates a **256-bit cryptographically secure token** (64 hex characters) using `SecureRandom`.
   - The token is transmitted back over the connection and securely saved in Android's private sandboxed `SharedPreferences`.
   - The mobile status indicator turns **Green (Connected & Paired)**.
   - On the PC, the **Active Paired Devices** table immediately updates with the device's name, unique ID, and registration timestamp.

---

## 3. Remote Operations & Capabilities

Once paired, your mobile companion can perform operations securely:

| Tab | Feature | Backend Behavior |
| :--- | :--- | :--- |
| **Chat** | Conversational AI | Queries the Windows AI Provider Manager (Gemini, Claude, GPT-4o, Groq, Ollama) using the API keys stored **exclusively** on your PC. No keys are ever sent to Android. |
| **Windows Remote** | Launch Desktop Apps | Remotely trigger allowlisted PC apps (`Notepad`, `Calculator`, `Chrome`, `VS Code`, `Explorer`, `Settings`). Unauthorized arbitrary commands are strictly blocked. |
| **Vision AI** | Screen Analysis | Instructs your PC to capture a desktop screenshot, feeds it into your multimodal AI provider on PC, and returns the analysis to your phone. |
| **Settings** | Device Management | View connection latency, refresh token, or manually unpair and disconnect. |

---

## 4. Revoking a Paired Device

If a device is lost, stolen, or no longer needed:
1. On your Windows PC, go to the **Mobile Companion** tab in DEX.
2. Select the device in the **Active Paired Devices** table.
3. Click **`Revoke Selected Device`**.
4. Confirmation dialog: Click **Yes**.
5. The device token is permanently purged from memory and `data/paired_devices.json`.
6. Any subsequent requests from that phone will immediately receive `401 Unauthorized: Device revoked or unauthorized`.

---

## 5. Security & Threat Mitigation

- **No Remote Shell**: DEX does not expose an unrestricted command line or arbitrary process execution to mobile clients. Only verified applications registered in `WindowsController` are permitted.
- **Path Traversal Guards**: Remote file and directory operations are confined to approved workspace directories.
- **Zero API Keys on Phone**: Because all AI API keys remain encrypted inside `data/secrets.enc` on the Windows machine, inspecting the Android APK or phone memory yields zero provider secrets.
- **Protected Network Metadata**: Endpoints like `/api/status` and `/api/network-info` require authentication before disclosing internal tokens or network configurations.
