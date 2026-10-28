@echo off
title DEX - Personal AI Assistant
cd /d "%~dp0"

echo [DEX] Checking build artifacts...
if not exist "target\dex-ai-assistant-1.0.0-jar-with-dependencies.jar" (
    echo [DEX] Standalone JAR not found. Building project with Maven...
    call mvn package -DskipTests
    if errorlevel 1 (
        echo [DEX Error] Build failed. Please verify Java and Maven installation.
        pause
        exit /b 1
    )
)

echo [DEX] Launching DEX Desktop Application...
start "" javaw -jar "target\dex-ai-assistant-1.0.0-jar-with-dependencies.jar" --desktop
