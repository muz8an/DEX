@echo off
title DEX - Personal AI Assistant
cd /d "%~dp0"

set "JAR=target\dex-ai-assistant-1.0.0-jar-with-dependencies.jar"
if not exist "%JAR%" (
    echo [DEX] Standalone JAR not found. Building with Maven...
    call mvn clean package -DskipTests
    if errorlevel 1 (
        echo [DEX Error] Build failed. Verify Java 17+ and Maven installation.
        pause
        exit /b 1
    )
)

if "%~1"=="" (
    echo [DEX] Launching DEX Desktop Application...
    start "" javaw -jar "%JAR%" --desktop
) else (
    java -jar "%JAR%" %*
)
