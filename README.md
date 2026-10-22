# DEX — Advanced Personal AI Assistant for Windows

DEX is a functional, modular, extensible personal AI assistant inspired by JARVIS, engineered specifically for **Windows 10** and **Windows 11**. It bridges a dedicated native Windows desktop application, local Windows computer automation, multi-provider AI reasoning (Gemini, OpenAI, Claude, Groq, Ollama), browser automation, voice control, screen vision awareness, and encrypted persistent memory.

---

## 🚀 Key Features

### 1. Dedicated Native Windows Desktop Application
- **True Desktop UI:** Built using Java Swing with FlatLaf Dark theme styled with a JARVIS-inspired cyan/slate aesthetic.
- **7 Comprehensive Functional Tabs:**
  1. **Chat & Agent:** Real-time conversational interface with speech button, desktop screenshot analysis, and session memory clearing.
  2. **Task Audit & Cancellation:** Displays all single-step and multi-step tasks with instant step-by-step progress and a 1-click **Cancel Selected Task** button.
  3. **Windows Control Center:** One-click instant launchers and status indicators for Notepad, Calculator, Chrome, YouTube, Google, GitHub, VS Code, File Explorer, Windows Settings, Command Prompt, PowerShell, and desktop screenshots.
  4. **Browser Intelligence:** Dedicated browser engine for navigating web URLs, extracting text/titles, and searching YouTube, GitHub, Google, or DuckDuckGo.
  5. **AI Providers:** Visual configuration interface for Google Gemini, OpenAI GPT, Anthropic Claude, Groq, OpenRouter, FreeLLMAPI, Ollama, and Custom endpoints. Displays masked keys (`sk-proj...`) and provides connection testing.
  6. **Memory & Privacy:** Manage user preferences, project context, and facts saved in `data/facts.json`, with atomic file persistence and secret redaction.
  7. **Diagnostics:** Live system hardware metrics, CPU load, memory usage, and running Windows processes via `ProcessHandle.allProcesses()`.
- **1-Click Launchers:** Launch DEX seamlessly using `DEX.bat` or completely silently without background terminal windows using `DEX.vbs`.

### 2. Multi-Provider AI Architecture
DEX does not lock you into any single provider. It supports:
- **Google Gemini:** Direct native REST integration (`v1beta/models/...:generateContent`) with `system_instruction` and inline base64 vision blocks.
- **OpenAI GPT:** Chat completions (`/v1/chat/completions`) with tool calling and vision.
- **Anthropic Claude:** Direct Messages API (`/v1/messages`) with `x-api-key`, `anthropic-version: 2023-06-01`, and base64 image blocks.
- **Groq:** Ultra-fast inference with Llama 3 models via OpenAI-compatible endpoints.
- **OpenRouter:** Universal gateway for open and proprietary models.
- **FreeLLMAPI:** Local/remote OpenAI-compatible free LLM endpoint (`http://127.0.0.1:31415/v1`).
- **Ollama / Local LLM:** Local inference via `http://localhost:11434/v1`.
- **Custom Endpoints:** Connect to any self-hosted or proxy endpoint.
- **Automatic Fallback:** Configure a secondary fallback provider that activates automatically if the primary provider hits rate limits or network errors.

### 3. Bank-Grade Secret Storage (AES-256-GCM)
- **Local-First Security:** All API keys are encrypted on disk at `data/secrets.enc` using **AES-256-GCM** with a PBKDF2-derived key (65,536 iterations) based on the user identity and machine salt (`data/.dex_salt`).
- **Zero Secret Leaks:** Plaintext keys are never stored on disk, never output to logs, never sent to the browser, and never committed to Git.
- **Key Masking:** The UI displays only masked previews (e.g., `sk-proj-abc...f456`).

### 4. Real Windows Automation & Process Verification
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
- **Real Process Creation:** Uses `ProcessBuilder` with verified PID extraction and structured `LaunchResult` diagnostics instead of blind command firing.

### 5. Genuine Browser Automation
- **Web Navigation:** Safely fetches and parses web pages using Java HTTP Client, extracting readable clean text without `<script>` or `<style>` clutter.
- **Structured Search:** Automatically builds and launches queries for Google, YouTube, GitHub, or DuckDuckGo in both the internal text reader and your Windows desktop browser.
- **Element & Content Discovery:** Extracts page title, meta description, links, and readable text for AI context.

### 6. Voice Assistant
- **Microphone & Speech-to-Text:** Instant 1-click trigger for Windows Voice Typing (`Win + H`) allowing hands-free speech input into any chat box.
- **Text-to-Speech:** Real-time speech synthesis using Windows PowerShell `System.Speech.Synthesis`.
- **Interruptible Speech:** Stop speaking at any time via `VoiceController.stopSpeaking()` without freezing the application.

### 7. Screen Awareness (Vision AI)
- **Desktop Screenshots:** Controlled screenshot capture via `java.awt.Robot` to `data/screenshots/`.
- **AI Screen Analysis:** Send desktop screenshots directly to vision-capable models (Gemini, GPT-4o, Claude 3.5 Sonnet) with custom prompts for instant GUI assistance, error diagnosis, and debugging.

### 8. Persistent Memory & Long-Term User Facts
- **Conversation Memory:** Preserved in `data/memory.json` with sliding context window and automatic secret sanitization.
- **User Facts & Preferences:** Stored separately in `data/facts.json` (e.g., name, preferred coding language, workspace directory) and automatically injected into LLM system prompts for personalized responses.
- **Atomic Disk Writes:** Safe `.tmp` file swapping prevents file corruption on sudden reboots.

---

## 📋 System Requirements
- **Operating System:** Windows 10 or Windows 11 (64-bit)
- **Java Runtime / JDK:** Java 17 or higher (tested on Java 17 and Java 27)
- **Build Tool:** Apache Maven 3.8+

---

## 🏃 Launching DEX

### Option 1: 1-Click Desktop Launch (Recommended)
Double-click `DEX.bat` in the project root:
```cmd
DEX.bat
```
Or double-click `DEX.vbs` to launch DEX completely silently in the background without any CMD window!

### Option 2: Using Maven
```powershell
mvn exec:java
```

### Option 3: Standalone Executable Fat JAR
```powershell
# Build the standalone fat JAR
mvn package -DskipTests

# Run the desktop application
java -jar target/dex-ai-assistant-1.0.0-jar-with-dependencies.jar
```

### Option 4: Server-Only / Headless Mode
If running on a headless machine or accessing exclusively via your phone:
```powershell
java -jar target/dex-ai-assistant-1.0.0-jar-with-dependencies.jar --server-only
```
Access the web dashboard at: 👉 **http://127.0.0.1:31416**

---

## ⚙️ AI Provider Setup & Configuration

You can configure providers directly inside the **Desktop Application** under the **AI Providers** tab, or via environment variables in PowerShell:

### Google Gemini
```powershell
$env:GEMINI_API_KEY = "AIzaSy..."
# Default Model: gemini-1.5-flash (or gemini-1.5-pro)
```

### OpenAI
```powershell
$env:OPENAI_API_KEY = "sk-proj-..."
# Default Model: gpt-4o-mini (or gpt-4o)
```

### Anthropic Claude
```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-..."
# Default Model: claude-3-5-sonnet-20241022
```

### Groq
```powershell
$env:GROQ_API_KEY = "gsk_..."
# Default Model: llama-3.3-70b-versatile
```

### FreeLLMAPI / Local Endpoints
```powershell
$env:DEX_API_KEY = "your_key"
$env:DEX_API_BASE_URL = "http://127.0.0.1:31415/v1"
```

---

## 💬 Usage Examples

### Natural Language Windows Commands
- `"Open Notepad"`
- `"Open Calculator"`
- `"Open Google Chrome"`
- `"Open YouTube"`
- `"Open Google"`
- `"Open GitHub"`
- `"Open VS Code"`
- `"Open File Explorer"`
- `"Open Windows Settings"`
- `"Open Command Prompt"`
- `"Open PowerShell"`
- `"Take a screenshot"`
- `"What time is it?"`
- `"Show system information"`
- `"List running processes"`

### Multi-Step Workflows
- `"Open Notepad and tell me the current time"`
- `"Take a screenshot and show system diagnostics"`
- `"Search YouTube for Java Spring Boot tutorial"`
- `"Search GitHub for Iron Man Jarvis assistant"`

### Agent & Workspace Tasks
- `"List workspace files"`
- `"Read file hello.txt"`
- `"Run command mvn test"`
- `"Task history"`

---

## 🧪 Testing & Verification

Run the full automated test suite:
```powershell
mvn test
```

### Test Suite Summary:
- **Total Tests:** 73 passed (0 failures, 0 errors, 0 skipped)
- **Coverage Areas:**
  - `ProviderManagerTest`: Provider switching, fallback execution, and key masking.
  - `SecretStorageTest`: AES-256-GCM encryption on disk, decryption, and key masking.
  - `BrowserAutomationTest`: URL normalization, HTML entity cleanup, search query construction.
  - `WindowsControllerTest`: ProcessBuilder execution, application allowlist (Notepad, Calc, Chrome, YouTube, Settings, CMD, PowerShell), and exception handling.
  - `TaskManagerTest`: Multi-step parsing, tool routing, error capture, and task cancellation.
  - `MemoryManagerTest`: JSON serialization, atomic swapping, secret redaction, and corruption recovery.
  - `DEXWebServerTest`: HTTP routes, remote authentication tokens, and CORS.
  - `FileToolsTest`: Path traversal blocking, file size limits, and workspace sandboxing.
  - `CommandRunnerTest`: Restricted commands allowlist, malicious syntax blocking (`&&`, `;`, `|`).
  - `AIClientTest`: Offline diagnostic responses and tool delegation without an API key.

---

## 🔒 Security Architecture

1. **Loopback Default:** Local REST server and Web Dashboard bind exclusively to `127.0.0.1:31416`.
2. **Encrypted Secrets:** Zero plaintext secrets on disk. Secrets stored in AES-256-GCM encrypted format in `data/secrets.enc`.
3. **Restricted Command Runner:** Only allows allowlisted developer tools (`mvn`, `git`, `javac`, `java`, `python`, `node`) within timeouts.
4. **Sandboxed File Operations:** Confined strictly to `workspace/`. Path traversal (`../`) and sensitive file access (`.env`, `.key`) are strictly forbidden.
5. **Git Shielding:** `.gitignore` excludes `data/secrets.enc`, `data/memory.json`, `data/screenshots/`, and build artifacts.
