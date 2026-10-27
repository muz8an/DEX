# DEX — Personal AI Assistant (Windows Desktop & Android Companion)

DEX (Desktop Executive eXperience) is an extensible, modular personal AI assistant inspired by JARVIS, engineered specifically for **Windows 10/11** and paired with a native **Android mobile companion**.

It bridges a native Windows desktop application, local Windows computer automation, multi-provider AI reasoning (Google Gemini, OpenAI GPT, Anthropic Claude, Groq, Ollama), browser intelligence, voice control, screen vision awareness, encrypted persistent memory, and a secure zero-trust mobile companion client.

---

## 📦 Downloadable Releases (`assets/`)

Ready-to-run installation packages are provided directly in the repository under [`assets/`](assets/):

| Platform | Package File | Type | Description |
| :--- | :--- | :--- | :--- |
| **Windows Installer** | [`assets/DEX-Setup.exe`](assets/DEX-Setup.exe) | Setup Installer (~52 MB) | Full Windows MSI/EXE installer. Installs to `%LOCALAPPDATA%\DEX`, creates Start Menu & Desktop shortcuts, bundles self-contained Java runtime, and registers uninstaller. |
| **Windows Standalone** | [`assets/DEX.exe`](assets/DEX.exe) | Portable App Launcher | Native 64-bit Windows executable launcher with bundled runtime support. |
| **Android Companion** | [`assets/DEX-Mobile.apk`](assets/DEX-Mobile.apk) | Android APK (~5.5 MB) | Native Android companion application (Android 8.0 to Android 14+ / API 26-34). |
| **Integrity Verification** | [`assets/checksums.txt`](assets/checksums.txt) | SHA-256 Hashes | Cryptographic checksums for all release binaries. |

---

## 🚀 Key Features

### 1. Dedicated Native Windows Desktop Application
- **True Desktop UI:** Built using Java Swing with FlatLaf Dark theme styled with a JARVIS-inspired cyan/slate aesthetic.
- **8 Comprehensive Functional Tabs:**
  1. **Chat & Agent:** Real-time conversational interface with speech button, desktop screenshot analysis, and session memory clearing.
  2. **Task Audit & Cancellation:** Displays all single-step and multi-step tasks with instant step-by-step progress and a 1-click **Cancel Selected Task** button.
  3. **Windows Control Center:** One-click instant launchers and status indicators for Notepad, Calculator, Chrome, YouTube, Google, GitHub, VS Code, File Explorer, Windows Settings, Command Prompt, PowerShell, and desktop screenshots.
  4. **Browser Intelligence:** Dedicated browser engine for navigating web URLs, extracting text/titles, and searching YouTube, GitHub, Google, or DuckDuckGo.
  5. **AI Providers:** Visual configuration interface for Google Gemini, OpenAI GPT, Anthropic Claude, Groq, OpenRouter, FreeLLMAPI, Ollama, and Custom endpoints. Displays masked keys (`sk-proj...`) and provides connection testing.
  6. **Memory & Privacy:** Manage user preferences, project context, and facts saved in `data/facts.json`, with atomic file persistence and secret redaction.
  7. **Diagnostics:** Live system hardware metrics, CPU load, memory usage, and running Windows processes via `ProcessHandle.allProcesses()`.
  8. **Mobile Companion & Pairing:** Manage paired Android devices, view host LAN IP addresses, generate 6-digit one-time PINs, and revoke mobile access instantly.
- **1-Click Launchers:** Launch DEX seamlessly using `DEX.bat` or completely silently without background terminal windows using `DEX.vbs`.

### 2. Android Mobile Companion
- **Native Android Client:** Material 3 UI engineered for Android 8.0+ (API 26-34).
- **Thin-Client Security Architecture:** **Zero** AI provider API keys are stored on the phone. All reasoning, token usage, and automation remain securely hosted on your Windows machine.
- **4 Dedicated Mobile Tabs:**
  1. **Chat:** Real-time conversation with the Windows DEX AI engine.
  2. **Windows Remote:** Remotely trigger allowlisted PC applications from your phone.
  3. **Vision AI:** Remotely capture your PC's screen and query multimodal AI for assistance.
  4. **Settings & Pairing:** PIN-based zero-trust pairing, connection latency monitor, and unpair controls.
- **Minimal Permissions:** Requires only `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE`.

### 3. Multi-Provider AI Architecture
DEX does not lock you into any single provider. It supports:
- **Google Gemini:** Direct native REST integration (`v1beta/models/...:generateContent`) with `system_instruction` and inline base64 vision blocks.
- **OpenAI GPT:** Chat completions (`/v1/chat/completions`) with tool calling and vision.
- **Anthropic Claude:** Direct Messages API (`/v1/messages`) with `x-api-key`, `anthropic-version: 2023-06-01`, and base64 image blocks.
- **Groq:** Ultra-fast inference with Llama 3 models via OpenAI-compatible endpoints.
- **OpenRouter:** Universal gateway for open and proprietary models.
- **FreeLLMAPI:** Local/remote OpenAI-compatible free LLM endpoint (`http://127.0.0.1:31415/v1`).
- **Ollama / Local LLM:** Local inference via `http://localhost:11434/v1`.
- **Automatic Fallback:** Configure a secondary fallback provider that activates automatically if the primary provider hits rate limits or network errors.

### 4. Bank-Grade Secret Storage (AES-256-GCM)
- **Local-First Security:** All API keys are encrypted on disk at `data/secrets.enc` using **AES-256-GCM** with a PBKDF2-derived key (65,536 iterations) based on user identity and machine salt (`data/.dex_salt`).
- **Zero Secret Leaks:** Plaintext keys are never stored on disk, never output to logs, never sent to the browser/mobile client, and never committed to Git.
- **Key Masking:** The UI displays only masked previews (e.g., `sk-proj-abc...f456`).

### 5. Real Windows Automation & Process Verification
- **Allowlisted Applications & Commands:**
  - `notepad.exe` (Notepad)
  - `calculator:` (Windows Modern Calculator)
  - `chrome.exe` (Google Chrome)
  - `https://www.youtube.com` (YouTube in default browser)
  - `https://www.google.com` (Google Search)
  - `https://github.com` (GitHub)
  - `code.cmd` (Visual Studio Code)
  - `explorer.exe` (File Explorer & directories: `Downloads`, `Documents`, `Desktop`)
  - `ms-settings:` (Windows Settings)
  - `cmd.exe` (Command Prompt)
  - `powershell.exe` (PowerShell)
- **Real Process Creation:** Uses `ProcessBuilder` with verified PID extraction and structured `LaunchResult` diagnostics instead of blind shell firing.

---

## 📁 Repository Structure

```
DEX/
├── .github/
│   └── workflows/
│       └── build.yml               # Automated multi-platform CI/CD (Windows + Android)
├── assets/                         # Distribution binaries and release checksums
│   ├── DEX-Setup.exe               # Windows MSI/EXE installer (~52 MB)
│   ├── DEX.exe                     # Windows standalone executable launcher
│   ├── DEX-Mobile.apk              # Android mobile companion package (~5.5 MB)
│   ├── checksums.txt               # SHA-256 verification hashes
│   └── icons/                      # Application icons (.ico and .png)
├── data/                           # Local encrypted storage (git-ignored)
│   ├── secrets.enc                 # AES-256-GCM encrypted API keys
│   ├── memory.json                 # Conversation history (sanitized)
│   ├── facts.json                  # Persistent user context
│   └── paired_devices.json         # Paired mobile device registry
├── docs/                           # Architecture, pairing, and build documentation
│   ├── ARCHITECTURE.md             # System topology, threat model, and security controls
│   ├── DEVICE_PAIRING.md           # Step-by-step pairing protocol and token management
│   └── BUILD_GUIDE.md              # Complete packaging guide for Windows & Android
├── mobile/                         # Native Android Companion Application
│   ├── app/
│   │   ├── src/main/java/          # MainActivity, DexClient, UI components
│   │   ├── src/main/res/           # Layouts, themes, launcher mipmaps
│   │   └── build.gradle            # Android app Gradle configuration (API 26-34)
│   ├── gradlew.bat / gradlew       # Gradle 8.7 wrapper
│   └── build.gradle                # Root Android build configuration
├── scripts/                        # Repeatable build and packaging automation
│   ├── build_all.ps1               # Master multi-platform build pipeline
│   ├── build_windows.ps1           # Windows JAR compilation + jpackage installer
│   ├── build_android.ps1           # Android SDK setup + Gradle APK packaging
│   ├── generate_icons.ps1          # Windows icon generator
│   └── generate_android_icons.ps1  # Android launcher mipmap icon generator
├── src/                            # Windows Desktop Application & Core Engine
│   ├── main/java/com/dex/
│   │   ├── ai/                     # AI Providers (Gemini, Claude, GPT, Groq, Ollama)
│   │   ├── automation/             # WindowsController & ProcessManager
│   │   ├── browser/                # Browser automation engine
│   │   ├── memory/                 # MemoryManager & FactManager
│   │   ├── security/               # SecretStorage & DeviceManager (PINs & tokens)
│   │   ├── server/                 # DEXWebServer & REST API
│   │   ├── ui/                     # Java Swing Desktop App (FlatLaf Dark theme)
│   │   ├── vision/                 # VisionController (screen capture)
│   │   └── voice/                  # VoiceController (speech synthesis & typing)
│   └── test/java/com/dex/          # Full unit test suite (79 passing tests)
├── DEX.bat                         # 1-Click Windows desktop launcher
├── DEX.vbs                         # 1-Click silent Windows background launcher
├── pom.xml                         # Maven project configuration
└── README.md                       # Project overview and instructions
```

---

## 🔒 Security Architecture

DEX is designed under a **defense-in-depth, zero-trust** security model:

1. **PIN-Based Device Pairing:**
   - Android companions connect using a 6-digit one-time PIN generated on the PC.
   - PINs expire in 5 minutes (300-second TTL) and are strictly single-use.
   - Successful pairing grants a 256-bit cryptographically secure device token.
2. **Instant Device Revocation:**
   - The PC user can view all paired mobile devices in the desktop UI and revoke any device with a single click.
3. **No Unrestricted Remote Shell:**
   - Remote clients cannot execute arbitrary shell commands. Only allowlisted applications registered in `WindowsController` may be invoked.
4. **Sandboxed File Operations & Path Traversal Guards:**
   - File actions are strictly constrained to the approved `workspace/` directory. Path traversal sequences (`../`) and sensitive configuration files (`.env`, `.key`, `secrets.enc`) are rejected.
5. **Zero Secrets on Mobile:**
   - AI provider API keys never leave the Windows host machine. The Android APK contains zero secrets.
6. **No Indiscriminate Exposure:**
   - Informational endpoints (`/api/status`) mask internal tokens. Unauthenticated users cannot view master remote tokens or network metadata.

---

## 📋 System Requirements

### For Windows Desktop:
- **Operating System:** Windows 10 or Windows 11 (64-bit).
- **Installed Package:** `assets/DEX-Setup.exe` includes a bundled Java runtime; no pre-installed Java is required for the installer!
- **Building from Source:** Java 17+ and Apache Maven 3.8+.

### For Android Companion:
- **Operating System:** Android 8.0 (Oreo / API level 26) through Android 14+ (API level 34).
- **Network:** Wi-Fi connection on the same Local Area Network (LAN) as the Windows PC, or an active private VPN (Tailscale/WireGuard).

---

## 📥 Installation Instructions

### Option A: Install on Windows (Recommended for Users)
1. Download or locate [`assets/DEX-Setup.exe`](assets/DEX-Setup.exe).
2. Double-click `DEX-Setup.exe` to run the setup wizard.
3. Follow the on-screen installer prompts:
   - Default install path: `%LOCALAPPDATA%\DEX`
   - Automatically creates Start Menu and Desktop shortcuts.
   - Bundles its own self-contained Java runtime (no separate Java installation needed).
4. Launch DEX directly from your Start Menu or Desktop.
5. To uninstall at any time: open **Windows Settings > Apps > Installed apps > DEX > Uninstall**.

### Option B: Install on Android
1. Transfer [`assets/DEX-Mobile.apk`](assets/DEX-Mobile.apk) to your Android device (via USB cable, Google Drive, local network share, or direct download).
2. Open your device's **Files** or **Downloads** app and tap `DEX-Mobile.apk`.
3. If Android prompts that installation from unknown sources is blocked, tap **Settings** and toggle **Allow from this source**.
4. Tap **Install**, then tap **Open**.

---

## 🔗 Connecting Android Companion to Windows Desktop

To pair your Android phone with your PC:

1. **On your Windows PC**:
   - Open DEX.
   - Click the **Mobile Companion** tab (8th tab on the right).
   - Note the **Host IP Address** displayed in the table (e.g., `192.168.1.50` or your Tailscale IP).
   - Click **`Generate New Pairing PIN`**. A 6-digit code will appear in cyan (e.g., `492018`).
2. **On your Android Phone**:
   - Open the **DEX Companion** app.
   - Go to the **Settings / Pairing** tab.
   - Enter the **Host IP**, keep Port as `8765`, and enter the **6-digit PIN**.
   - Tap **`Pair with Desktop`**.
3. **Success**:
   - The indicator turns green (**Connected & Paired**).
   - The phone appears in the PC's **Active Paired Devices** table.
   - You can now chat, remotely launch apps, and analyze your PC screen from your phone.

For complete details, see [`docs/DEVICE_PAIRING.md`](docs/DEVICE_PAIRING.md).

---

## 🌐 Secure Remote Access (Outside Home Wi-Fi)

To access your DEX assistant from your phone when you are away from home:
- **Do not expose port 8765 directly to the public internet** via router port forwarding without encryption.
- **Recommended Free Solution: Tailscale or WireGuard**:
  1. Install [Tailscale](https://tailscale.com) on your Windows PC and your Android phone.
  2. Sign in to both devices with the same account.
  3. In the DEX Mobile app, enter your PC's 100.x.y.z Tailscale IP address as the Host IP.
  4. All traffic travels over an end-to-end encrypted WireGuard tunnel automatically.

---

## ⚙️ AI Provider Setup

You can configure your AI providers directly inside the **Desktop Application** under the **AI Providers** tab, or via environment variables:

- **Google Gemini:** Set your Gemini API key (`AIzaSy...`) in the UI or `$env:GEMINI_API_KEY`.
- **OpenAI:** Set your OpenAI key (`sk-proj-...`) in the UI or `$env:OPENAI_API_KEY`.
- **Anthropic Claude:** Set your Claude key (`sk-ant-...`) in the UI or `$env:ANTHROPIC_API_KEY`.
- **Groq:** Set your Groq key (`gsk_...`) in the UI or `$env:GROQ_API_KEY`.
- **Local Ollama:** Run Ollama locally; DEX connects automatically to `http://localhost:11434/v1`.

All keys are encrypted on your local PC using **AES-256-GCM** in `data/secrets.enc`. Keys are **never** shared with the Android phone.

---

## 🛠️ Building from Source

To compile and package both applications from source:

### Clone the Repository
```bash
git clone https://github.com/<your-username>/DEX.git
cd DEX
```

### Build Everything (Windows Installer + Standalone Executable + Android APK)
```powershell
powershell -ExecutionPolicy Bypass -File scripts\build_all.ps1
```

### Build Windows Only
```powershell
powershell -ExecutionPolicy Bypass -File scripts\build_windows.ps1
```

### Build Android Only
```powershell
powershell -ExecutionPolicy Bypass -File scripts\build_android.ps1
```

### Run Tests
```powershell
# Desktop tests (79 unit tests)
mvn test

# Android tests
cd mobile
.\gradlew.bat test
```

For toolset prerequisites (WiX Toolset 3.14, Android SDK Platform 34, JDK 17), see [`docs/BUILD_GUIDE.md`](docs/BUILD_GUIDE.md).

---

## 🔄 Updating Applications

To update to a new version:
1. Re-run `scripts/build_all.ps1` to produce fresh packages.
2. Run the newly generated `assets/DEX-Setup.exe` on Windows (your encrypted keys, settings, and memory are preserved).
3. Install the updated `assets/DEX-Mobile.apk` on Android over the existing installation.

---

## ❓ Troubleshooting

| Issue | Cause | Solution |
| :--- | :--- | :--- |
| **Android shows "Connection Refused"** | PC and phone are not on the same network or wrong IP entered. | Ensure both devices are on the same Wi-Fi. Verify the PC IP shown in the Mobile Companion tab. Check that Windows Defender Firewall allows Java on private networks. |
| **Pairing fails ("Invalid or expired PIN")** | The 5-minute PIN timer expired or PIN was mistyped. | Click **`Generate New Pairing PIN`** on the PC for a new code and enter it on Android. |
| **Windows app launch fails** | App is not in the security allowlist. | For safety, only allowlisted apps (`notepad.exe`, `calculator:`, `chrome.exe`, `code.cmd`, etc.) can be launched. Add custom apps in `WindowsController.java` if needed. |
| **AI replies "No active provider"** | No API keys configured. | Open the **AI Providers** tab in DEX Desktop and enter an API key for Gemini, Claude, OpenAI, Groq, or start Ollama. |
| **Android installation blocked** | Sideloading restriction. | Enable "Install unknown apps" for your file manager in Android Settings. |

---

## ⚠️ Known Limitations

1. **Windows Voice Typing:** Speech-to-text uses the native Windows Voice Typing shortcut (`Win + H`), which requires an active Windows microphone input.
2. **Public Networks:** For communication outside your home Wi-Fi, always use a private VPN (Tailscale/WireGuard) to secure the connection.
3. **Development Signing:** Release packages are self-signed for personal deployment. Windows SmartScreen or Android Play Protect may show a standard initial prompt when first installing.
