# ==============================================================================
# DEX - Windows Packaging & Build Script
# Compiles, tests, packages standalone executable and Windows installer.
# ==============================================================================

param(
    [switch]$SkipTests = $false
)

$ErrorActionPreference = "Stop"
$workspaceRoot = (Resolve-Path "$PSScriptRoot\..").Path
Set-Location $workspaceRoot

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "             DEX WINDOWS PACKAGING & BUILD PIPELINE              " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# 1. Compile and Test Maven Project
Write-Host "`n[Stage 1/5] Compiling Desktop Application & Running Tests..." -ForegroundColor Yellow
Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like "*dex-ai-assistant*" } |
    ForEach-Object {
        Write-Host "Stopping running DEX process (PID: $($_.ProcessId)) to release file locks..." -ForegroundColor Yellow
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
$mvnArgs = @("clean", "package")
if ($SkipTests) {
    $mvnArgs += "-DskipTests"
}
& mvn @mvnArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "Maven build/test failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}
Write-Host "[Stage 1/5] Maven build completed successfully." -ForegroundColor Green

# 2. Generate Application Icons
Write-Host "`n[Stage 2/5] Ensuring Application Icons Exist..." -ForegroundColor Yellow
if (-not (Test-Path "$workspaceRoot\assets\icons\dex.ico")) {
    & powershell -ExecutionPolicy Bypass -File "$workspaceRoot\scripts\generate_icons.ps1"
}
Write-Host "[Stage 2/5] Icons validated." -ForegroundColor Green

# 3. Create Standalone Executable (App-Image) via jpackage
Write-Host "`n[Stage 3/5] Packaging Standalone Application Image via jpackage..." -ForegroundColor Yellow
$jpackageExe = "C:\Program Files\Java\jdk-27\bin\jpackage.exe"
if (-not (Test-Path $jpackageExe)) {
    $found = (Get-Command jpackage -ErrorAction SilentlyContinue)
    if ($found) { $jpackageExe = $found.Source }
    else {
        Write-Error "JDK jpackage tool not found. Please install JDK 17+ or set JAVA_HOME."
        exit 1
    }
}

$inputDir = "$workspaceRoot\target\package-input"
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
Copy-Item "$workspaceRoot\target\dex-ai-assistant-1.0.0-jar-with-dependencies.jar" "$inputDir\DEX.jar" -Force

$distDesktop = "$workspaceRoot\dist\desktop"
if (Test-Path "$distDesktop\DEX") {
    Remove-Item -Recurse -Force "$distDesktop\DEX" -ErrorAction SilentlyContinue
}

& $jpackageExe `
    --name "DEX" `
    --app-version "1.0.0" `
    --vendor "DEX AI Assistant" `
    --description "DEX - Personal AI Assistant for Windows" `
    --input $inputDir `
    --main-jar "DEX.jar" `
    --main-class "com.dex.Main" `
    --icon "$workspaceRoot\assets\icons\dex.ico" `
    --type app-image `
    --dest $distDesktop

if ($LASTEXITCODE -ne 0 -or -not (Test-Path "$distDesktop\DEX\DEX.exe")) {
    Write-Error "jpackage app-image creation failed."
    exit 1
}
Write-Host "[Stage 3/5] Standalone executable created at $distDesktop\DEX\DEX.exe" -ForegroundColor Green

# 4. Create Windows EXE Installer via jpackage and WiX
Write-Host "`n[Stage 4/5] Packaging Windows Installer (DEX-Setup.exe)..." -ForegroundColor Yellow
$wixDir = "$env:LOCALAPPDATA\WiX"
if (Test-Path "$wixDir\candle.exe") {
    $env:PATH = "$wixDir;$env:PATH"
}

$distInstaller = "$workspaceRoot\dist\installer"
New-Item -ItemType Directory -Force -Path $distInstaller | Out-Null

& $jpackageExe `
    --type exe `
    --app-image "$distDesktop\DEX" `
    --name "DEX-Setup" `
    --app-version "1.0.0" `
    --vendor "DEX AI Assistant" `
    --description "DEX - Personal AI Assistant Installer" `
    --dest $distInstaller `
    --win-dir-chooser `
    --win-menu `
    --win-shortcut `
    --icon "$workspaceRoot\assets\icons\dex.ico"

$installerOutput = "$distInstaller\DEX-Setup-1.0.0.exe"
if (-not (Test-Path $installerOutput)) {
    # Check alternate naming
    $installerAlt = Get-ChildItem -Path $distInstaller -Filter "*.exe" | Select-Object -First 1
    if ($installerAlt) { $installerOutput = $installerAlt.FullName }
}

if (-not (Test-Path $installerOutput)) {
    Write-Error "Windows installer generation failed."
    exit 1
}
Write-Host "[Stage 4/5] Windows installer created at $installerOutput" -ForegroundColor Green

# 5. Stage Artifacts to assets/
Write-Host "`n[Stage 5/5] Staging Release Artifacts to assets/..." -ForegroundColor Yellow
$assetsDir = "$workspaceRoot\assets"
New-Item -ItemType Directory -Force -Path $assetsDir | Out-Null

# Compile standalone GUI launcher for assets\DEX.exe
$cscExe = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if (Test-Path $cscExe) {
    & $cscExe /target:winexe /win32icon:"$workspaceRoot\assets\icons\dex.ico" /out:"$assetsDir\DEX.exe" "$workspaceRoot\scripts\Launcher.cs"
} else {
    Copy-Item "$distDesktop\DEX\DEX.exe" "$assetsDir\DEX.exe" -Force
}
Copy-Item $installerOutput "$assetsDir\DEX-Setup.exe" -Force

# Verify non-zero size
$dexExeItem = Get-Item "$assetsDir\DEX.exe"
$dexSetupItem = Get-Item "$assetsDir\DEX-Setup.exe"

if ($dexExeItem.Length -le 0 -or $dexSetupItem.Length -le 0) {
    Write-Error "Artifact validation failed: Zero-byte files detected in assets/."
    exit 1
}

# Generate SHA256 Checksums
$checksums = @()
$checksums += "SHA256 Checksums - DEX Distribution Artifacts"
$checksums += "Generated on: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')"
$checksums += "================================================================================"

foreach ($file in (Get-ChildItem -Path "$assetsDir\*" -Include "*.exe", "*.apk" -File)) {
    $hash = (Get-FileHash -Path $file.FullName -Algorithm SHA256).Hash
    $sizeMB = [Math]::Round($file.Length / 1MB, 2)
    $checksums += "$($hash)  $($file.Name)  ($sizeMB MB)"
    Write-Host "Artifact: $($file.Name) | Size: $sizeMB MB | SHA256: $hash" -ForegroundColor Cyan
}

$checksums | Out-File -FilePath "$assetsDir\checksums.txt" -Encoding utf8

Write-Host "`n=================================================================" -ForegroundColor Green
Write-Host "       WINDOWS PACKAGING SUCCESSFUL - ARTIFACTS IN ASSETS/       " -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Green
