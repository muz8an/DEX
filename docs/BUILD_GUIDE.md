# DEX Build & Packaging Guide

This document describes the compilation, packaging, and release pipelines for both the **Windows Desktop Application** and the **Android Companion Application**.

---

## 1. System Requirements & Build Prerequisites

### 1.1 Windows Desktop Packaging
- **Operating System:** Windows 10 or Windows 11 (64-bit).
- **JDK:** Java 17 or higher (tested with Oracle JDK 27 and Microsoft OpenJDK 17). Must include `jpackage.exe` in `%JAVA_HOME%\bin`.
- **Maven:** Apache Maven 3.8+ (`mvn` in `PATH`).
- **WiX Toolset (for Windows Installer):** WiX Toolset v3.14 (`candle.exe` and `light.exe`).
  - Automatically resolved if installed to `%LOCALAPPDATA%\WiX`, `%ProgramFiles(x86)%\WiX Toolset v3.14\bin`, or in system `PATH`.

### 1.2 Android APK Packaging
- **JDK:** Java 17 LTS (Microsoft OpenJDK 17 or Eclipse Adoptium Temurin 17).
  - *Note: Android Gradle Plugin 8.4 requires Java 17. The Android build script pins Java 17 automatically.*
- **Android SDK:**
  - Android API Level 34 (Android 14) platform SDK (`platforms;android-34`).
  - Android SDK Build-Tools 34.0.0 (`build-tools;34.0.0`).
  - Default SDK location: `%LOCALAPPDATA%\Android\Sdk`.

---

## 2. Automated Build Scripts

All packaging scripts reside in the `scripts/` directory:

| Script | Purpose | Output Artifacts |
| :--- | :--- | :--- |
| **`scripts/build_all.ps1`** | Master multi-platform pipeline: builds both Windows and Android, verifies non-zero sizes, and writes `checksums.txt`. | `assets/DEX.exe`, `assets/DEX-Setup.exe`, `assets/DEX-Mobile.apk`, `assets/checksums.txt` |
| **`scripts/build_windows.ps1`** | Compiles Java desktop, runs 79 unit tests, packages standalone app-image (`DEX.exe`) and WiX installer (`DEX-Setup.exe`). | `assets/DEX.exe`, `assets/DEX-Setup.exe` |
| **`scripts/build_android.ps1`** | Configures SDK, compiles Android app, runs Android unit tests, and packages `DEX-Mobile.apk`. | `assets/DEX-Mobile.apk` |
| **`scripts/generate_icons.ps1`** | Generates Windows multi-resolution `.ico` and `.png` icons. | `assets/icons/dex.ico`, `src/main/resources/dex_icon.png` |
| **`scripts/generate_android_icons.ps1`** | Generates Android mipmap launcher icons (`mdpi` through `xxxhdpi`). | `mobile/app/src/main/res/mipmap-*/` |

---

## 3. Running Builds Locally

### 3.1 All-in-One Multi-Platform Build (Recommended)
Open PowerShell in the project root:
```powershell
powershell -ExecutionPolicy Bypass -File scripts\build_all.ps1
```
*To skip tests for fast developer builds:*
```powershell
powershell -ExecutionPolicy Bypass -File scripts\build_all.ps1 -SkipTests
```

### 3.2 Windows Only
```powershell
powershell -ExecutionPolicy Bypass -File scripts\build_windows.ps1
```

### 3.3 Android Only
```powershell
powershell -ExecutionPolicy Bypass -File scripts\build_android.ps1
```

---

## 4. Packaging Pipeline Details

### 4.1 Windows Packaging Pipeline (`build_windows.ps1`)
1. **Compilation & Unit Testing:** Executes `mvn clean test` (ensuring all 79 tests pass) and builds the fat shaded JAR via `mvn package -DskipTests`.
2. **App-Image Generation:** Calls `jpackage --type app-image` bundling a self-contained Java runtime, launcher icon, and entry point `com.dex.DEXApp`.
3. **Standalone Executable:** Staged to `assets/DEX.exe` with its co-located runtime in `dist/desktop/DEX`.
4. **Windows Installer Generation:** Calls `jpackage --type exe` utilizing WiX Toolset to create a true Windows installer with Start Menu shortcuts, Desktop shortcut, and registered uninstaller in Windows Settings. Staged to `assets/DEX-Setup.exe`.
5. **Validation:** Checks that both outputs exist and have non-zero size, then computes SHA-256 hashes.

### 4.2 Android Packaging Pipeline (`build_android.ps1`)
1. **JDK Environment:** Automatically sets `JAVA_HOME` to Microsoft OpenJDK 17 LTS to satisfy AGP 8.4.
2. **SDK Configuration:** Verifies Android platform 34 and writes `local.properties`.
3. **Unit Tests:** Executes `gradlew.bat test` inside `mobile/`.
4. **APK Generation:** Executes `gradlew.bat assembleDebug` (or `assembleRelease`).
5. **Staging & Verification:** Validates APK size (approx. 5.5 MB), copies to `assets/DEX-Mobile.apk`, and generates SHA-256 hash.

---

## 5. Android Signing & Release Builds

By default, `build_android.ps1` produces a **debug APK** signed with Android's standard debug keystore. This allows testing on any Android phone without committing private production signing keys to Git.

### Producing a Production Signed APK:
1. Generate your release keystore outside the repository:
   ```cmd
   keytool -genkey -v -keystore dex-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias dex
   ```
2. Set environment variables on your build machine or CI:
   - `KEYSTORE_PATH`
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS`
   - `KEY_PASSWORD`
3. Execute:
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\build_android.ps1 -BuildType release
   ```

> [!CAUTION]
> Never commit `.jks`, `.keystore`, or signing passwords into the Git repository.

---

## 6. GitHub Actions Continuous Integration

The repository includes [`.github/workflows/build.yml`](../.github/workflows/build.yml) configured for automated builds on `push` to `main` and manual execution via `workflow_dispatch`:

- **Job 1: `build-windows` (windows-latest)**:
  - Sets up JDK 17 & Maven.
  - Runs all desktop unit tests.
  - Builds Windows standalone executable and installer via `jpackage` and WiX.
  - Uploads `DEX-Setup.exe` and `DEX.exe` as workflow artifacts.
- **Job 2: `build-android` (ubuntu-latest)**:
  - Sets up JDK 17 & Android SDK 34.
  - Runs Android unit tests.
  - Executes Gradle `assembleDebug`.
  - Uploads `DEX-Mobile.apk` as workflow artifact.
- **Job 3: `release`**:
  - Automatically bundles all artifacts and publishes a GitHub Release when a tag is pushed.
