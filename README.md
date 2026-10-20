# DEX — Personal AI Assistant for Windows

DEX is a functional, modular, extensible personal AI assistant engineered specifically for **Windows 11**. It bridges local Windows desktop automation, safe file workspace operations, and AI intelligence via an OpenAI-compatible local LLM endpoint (FreeLLMAPI).

---

## 🚀 Key Features

### 1. Unified Core Architecture & Startup
- **Dual Interface:** Run interactively via the Command-Line Interface (CLI) or the embedded real-time Web Dashboard simultaneously.
- **Single Maven Command Startup:** Launch with `mvn exec:java` or run the standalone fat JAR with `java -jar`.
- **Graceful Lifecycle Management:** Automatic JVM shutdown hooks guarantee session memory is flushed atomically to disk.
- **Fail-Safe Startup:** Local Windows automation tools remain 100% operational even when the LLM service is offline or unconfigured.

### 2. FreeLLMAPI Integration
- **OpenAI-Compatible Chat Completions:** Connects to FreeLLMAPI at `http://127.0.0.1:31415/v1/chat/completions`.
- **Zero Hardcoded Secrets:** Reads keys strictly from the `DEX_API_KEY` environment variable or prompts interactively via masked console input without persisting keys to disk.
- **Configurable Models & Endpoints:** Customize with `DEX_API_MODEL` (default: `auto`) and `DEX_API_BASE_URL`.
- **Resilient Network Handling:** 15-second connect timeout, 60-second request timeout, descriptive HTTP error diagnostics (401, 403, 429, 500+), and automatic fallback when the local API server is unreachable.

### 3. Persistent Conversation Memory
- **Disk Persistence:** Safe, atomic writes using `.tmp` swap files and atomic file replacement in `data/memory.json`.
- **Privacy Preservation:** Existing conversation history is strictly preserved and automatically sanitized to redact API keys and bearer tokens.
- **Sliding Context Window:** Dynamically prunes long histories to fit model token budgets while keeping system instructions and recent conversation context intact.
- **Session Reset vs. Disk Wipe:**
  - `clear chat`: Wipes in-memory session messages without altering the saved history on disk.
  - `reset memory`: Performs a confirmed, irreversible disk deletion only upon explicit user confirmation (`yes`).

### 4. Natural-Language Tool Calling
- **Autonomous Intent Detection:** Recognizes natural language requests (e.g., *"Can you open Notepad for me?"*, *"What time is it?"*, *"List tools"*).
- **Two-Way Tool Protocol:** Safely parses model-generated JSON tool requests (`{"tool": "...", "argument": "..."}`), executes them against a strict allowlist, and feeds results back to the model for summarization.
- **Deterministic Local Interception:** Common commands execute instantly via the local engine without network round-trips.

### 5. Windows Desktop & Application Control
- **Approved Application Registry:** Controls Windows Notepad, Calculator, Google Chrome, YouTube (in default browser), Visual Studio Code, Windows File Explorer, and standard user directories (`Downloads`, `Documents`, `Desktop`).
- **Fuzzy Matching & Typo Tolerance:** Employs Levenshtein distance matching and aliases (e.g., `"open calculater"`, `"launch vs code"`, `"my downloads"`).
- **Execution Safety:** Verifies process startup via `ProcessBuilder` without command-string concatenation.

### 6. Workspace File Management
- **Strict Boundary Confinement:** All file operations are confined to the `workspace/` directory. Path traversal outside this directory (`../`, absolute paths) throws immediate `SecurityException`.
- **Sensitive File Shield:** Access to private files (`.key`, `.pem`, `.env`, `id_rsa`, `credentials.json`) is blocked.
- **File Capabilities:** List directory contents, read files (up to 500 KB), search filenames, create files, and save AI-generated code.
- **Overwrite Protection:** Requires explicit permission before overwriting existing workspace files.

### 7. Coding Assistant & Restricted Command Runner
- **Code Assistance:** Explain, review, debug, and generate code across Java, C/C++, Python, HTML/CSS, JavaScript, and SQL.
- **Direct Workspace Code Export:** Automatically save generated code into workspace files using the `save_code` tool.
- **Restricted Command Runner:** Executes allowlisted development tools (`mvn`, `javac`, `java`, `git`, `python`, `node`) with strict argument checking, timeouts (45s), and stdout/stderr capture. Shell chaining (`&&`, `|`, `;`) and destructive commands (`del`, `rmdir`, `format`) are blocked.

### 8. Task Execution Engine & Multi-Step Workflows
- **Compound Intent Deconstruction:** Breaks composite requests (e.g., *"Open Notepad and tell me the current time"*) into discrete steps.
- **Execution Safety:** Limits multi-step workflows to 5 sequential steps, stops immediately if a prerequisite step fails, and supports task cancellation.
- **Task History Audit:** Retains session execution history viewable via `task history` or the dashboard.

### 9. Modern Glassmorphism Web Dashboard
- **Zero-Dependency HTTP Server:** Powered entirely by standard library `com.sun.net.httpserver.HttpServer` on port `31416`.
- **Aesthetic Dark Theme:** Real-time glassmorphism UI with status pills, model indicator, live chat interface, quick-action chips, registered tools list, and task audit trail.
- **Protected Endpoints:** REST endpoints (`/api/chat`, `/api/status`, `/api/tools`, `/api/tasks`, `/api/screenshot`) bound to `127.0.0.1` by default.

### 10. Phone-to-Laptop Remote Access Foundation
- **Private Network Ready:** Designed for secure access over Tailscale or local Wi-Fi.
- **Token Authentication:** Secure randomly-generated Bearer token generated on startup (`DEXConfig.getRemoteAuthToken()`) or customized via `DEX_REMOTE_TOKEN`.
- **Security Guardrails:** Remote requests require `Authorization: Bearer <token>` or `?token=<token>`.

### 11. Desktop Screenshots
- Captures full-desktop screenshots via `java.awt.Robot` to `data/screenshots/`.
- Accessible locally or via authenticated HTTP endpoint (`/api/screenshots/{filename}`).

### 12. Voice Support
- Optional Text-to-Speech synthesis using Windows PowerShell (`System.Speech.Synthesis`).
- Speech-to-text dictation supported natively via Windows `Win + H` shortcut into any DEX input.

---

## 📋 System Requirements
- **Operating System:** Windows 10 / Windows 11 (64-bit)
- **Java Development Kit (JDK):** Java 17 or higher
- **Build Tool:** Apache Maven 3.8+
- **AI Backend (Optional for local tools):** FreeLLMAPI running on `http://127.0.0.1:31415`

---

## ⚙️ Setup & Configuration

### 1. Configure Environment Variables (PowerShell)
```powershell
# Required only for AI Chat (local tools work without it)
$env:DEX_API_KEY = "your_freellmapi_key_here"

# Optional overrides:
$env:DEX_API_BASE_URL = "http://127.0.0.1:31415/v1"
$env:DEX_API_MODEL = "auto"
$env:DEX_PORT = "31416"
$env:DEX_HOST = "127.0.0.1" # Set to 0.0.0.0 for LAN/Tailscale access
$env:DEX_REMOTE_TOKEN = "your_custom_secure_token"
```

---

## 🏃 Running DEX

### Option A: Using Maven (Recommended Development Mode)
```powershell
# Compile, start background dashboard, and launch interactive CLI
mvn exec:java
```

### Option B: Running the Standalone Executable JAR
```powershell
# Build the fat JAR
mvn package -DskipTests

# Run the application
java -jar target/dex-ai-assistant-1.0.0-jar-with-dependencies.jar
```

### Option C: Server-Only / Background Mode
```powershell
# Run without opening the CLI loop (ideal for dashboard-only use)
java -jar target/dex-ai-assistant-1.0.0-jar-with-dependencies.jar --server-only
```

Once started, open your web browser to:
👉 **[http://127.0.0.1:31416](http://127.0.0.1:31416)**

---

## 💬 Usage Examples

### Application & Folder Control
- `open calculator` (or `open calc`, `open calculater`)
- `launch vs code` (or `open vscode`, `code`)
- `start notepad`
- `open chrome`
- `open youtube`
- `open my downloads`
- `open documents`
- `open desktop`

### System Diagnostics & Information
- `what time is it?`
- `show system info` (or `system diagnostics`)
- `take a screenshot`
- `list tools`

### Multi-Step Workflows
- `open notepad and tell me the current time`
- `what time is it and show system info`
- `task history`

### Workspace File Operations
- `list files`
- `read file example.txt`
- `run command mvn test`
- `run command git status`

### Chat & Memory Management
- `clear chat` — Clears active conversation window without deleting permanent history.
- `reset memory` — Prompts for confirmation (`yes/no`) before wiping disk memory.
- `exit` or `quit` — Gracefully stops server, saves state, and terminates.

---

## 📱 Phone-to-Laptop Access (Tailscale Guide)

1. **Install Tailscale** on both your Windows laptop and your phone.
2. Sign in with the same Tailscale account. Both devices will join your secure, encrypted Mesh overlay network.
3. Note your laptop's Tailscale IP (e.g., `100.x.y.z`).
4. Start DEX with host binding to all interfaces:
   ```powershell
   $env:DEX_HOST = "0.0.0.0"
   mvn exec:java
   ```
5. On your phone's browser, navigate to:
   ```
   http://100.x.y.z:31416/?token=YOUR_DEX_REMOTE_TOKEN
   ```
   *(Find your generated Remote Token in the console startup banner or set your own with `$env:DEX_REMOTE_TOKEN`).*
6. You can now chat with DEX, trigger applications, run builds, and request screenshots securely from your phone.

---

## 🔒 Security & Privacy Architecture

- **Path Traversal Protection:** Absolute paths and relative traversals (`../`) outside `workspace/` are strictly blocked.
- **Sensitive File Shielding:** Private keys, credentials, and `.env` files cannot be read or overwritten.
- **Safe Command Execution:** Shell injection characters (`&`, `|`, `;`, `>`, `<`, backticks) are rejected by the Command Runner.
- **Localhost Default:** The Web Dashboard binds only to `127.0.0.1` unless explicitly configured for remote access.
- **Credential Protection:** API keys and Bearer tokens are redacted automatically before writing to logs or `data/memory.json`.
- **Git Shielding:** `.gitignore` excludes `data/memory.json`, `data/screenshots/`, `workspace/`, `.env`, and build targets.

---

## 🧪 Testing

Run the automated JUnit 5 test suite:
```powershell
# Run clean compilation and all 48 unit tests
mvn clean test
```

All 48 tests validate:
- Configuration defaults and runtime key injection.
- Application aliases, typo tolerance, and unknown app rejection.
- Tool registration, tool execution, and JSON action protocols.
- Memory persistence, secret sanitization, and sliding window pruning.
- Path traversal guards, file overwrite protection, and workspace sandboxing.
- Natural-language intent parsing and multi-step task handling.
- Restricted build command runner allowlists and malicious token blocking.
- Web server REST endpoints, lifecycle, and authentication.

---

## 🛡️ Safe GitHub Publishing Instructions

To connect this project to your own private GitHub repository safely:

```powershell
# 1. Ensure you are in the DEX project directory
cd "C:\Users\MUHAMMED MUZAIN\Desktop\DEX"

# 2. Initialize a dedicated git repository for DEX (if not already done)
git init -b main

# 3. Verify .gitignore is protecting your memory and secrets
git status
# Notice that data/memory.json, workspace/, target/, and credentials are NOT staged!

# 4. Stage project source files
git add pom.xml .gitignore README.md src/ data/memory.example.json

# 5. Commit project files
git commit -m "Initial commit: Complete DEX Personal AI Assistant for Windows"

# 6. Add your private GitHub repository remote and push
git remote add origin https://github.com/<your-username>/DEX.git
git push -u origin main
```
