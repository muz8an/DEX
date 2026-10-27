# ==============================================================================
# DEX - Android Packaging & Build Script
# Compiles, tests, and builds DEX-Mobile.apk for mobile companion deployment.
# ==============================================================================

param(
    [switch]$SkipTests = $false,
    [string]$BuildType = "debug"
)

$ErrorActionPreference = "Stop"
$workspaceRoot = (Resolve-Path "$PSScriptRoot\..").Path
$mobileDir = "$workspaceRoot\mobile"
Set-Location $mobileDir

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "             DEX ANDROID PACKAGING & BUILD PIPELINE              " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# 1. Locate Java 17 LTS (Required for Android Gradle Plugin)
Write-Host "`n[Stage 1/5] Configuring Java 17 Environment..." -ForegroundColor Yellow
$jdk17Candidates = @(
    "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot",
    "$env:LOCALAPPDATA\Java\jdk-17",
    "C:\Program Files\Eclipse Adoptium\jdk-17*",
    "C:\Program Files\Java\jdk-17*"
)

$selectedJdk = $null
foreach ($cand in $jdk17Candidates) {
    $resolved = Resolve-Path $cand -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($resolved -and (Test-Path "$($resolved.Path)\bin\javac.exe")) {
        $selectedJdk = $resolved.Path
        break
    }
}

if ($selectedJdk) {
    $env:JAVA_HOME = $selectedJdk
    $env:PATH = "$selectedJdk\bin;$env:PATH"
    Write-Host "[Stage 1/5] Using Java 17 at $selectedJdk" -ForegroundColor Green
} else {
    Write-Host "[Stage 1/5] Java 17 path not detected explicitly. Using current JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Yellow
}

# 2. Ensure Android SDK Configuration
Write-Host "`n[Stage 2/5] Configuring Android SDK..." -ForegroundColor Yellow
$sdkDir = "$env:LOCALAPPDATA\Android\Sdk"
if (-not (Test-Path $sdkDir)) {
    if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
        $sdkDir = $env:ANDROID_HOME
    }
}
$env:ANDROID_HOME = $sdkDir
$localProps = "$mobileDir\local.properties"
$escapedSdk = $sdkDir.Replace("\", "\\")
"sdk.dir=$escapedSdk" | Out-File -FilePath $localProps -Encoding ascii
Write-Host "[Stage 2/5] Configured SDK directory: $sdkDir" -ForegroundColor Green

# 3. Ensure Android Icons
Write-Host "`n[Stage 3/5] Validating Launcher Icons..." -ForegroundColor Yellow
if (-not (Test-Path "$mobileDir\app\src\main\res\mipmap-xxhdpi\ic_launcher.png")) {
    & powershell -ExecutionPolicy Bypass -File "$workspaceRoot\scripts\generate_android_icons.ps1"
}
Write-Host "[Stage 3/5] Launcher icons validated." -ForegroundColor Green

# 4. Run Unit Tests & Build APK
Write-Host "`n[Stage 4/5] Building Android Package ($BuildType)..." -ForegroundColor Yellow
$gradleCmd = "$mobileDir\gradlew.bat"

if (-not $SkipTests) {
    Write-Host "Running Android unit tests..." -ForegroundColor Gray
    & ".\gradlew.bat" test
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Android unit tests failed with code $LASTEXITCODE"
        exit $LASTEXITCODE
    }
}

$taskName = if ($BuildType -eq "release") { "assembleRelease" } else { "assembleDebug" }
Write-Host "Executing Gradle task: $taskName..." -ForegroundColor Gray
& ".\gradlew.bat" $taskName
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed with code $LASTEXITCODE"
    exit $LASTEXITCODE
}

# Find built APK
$apkOutputDir = if ($BuildType -eq "release") {
    "$mobileDir\app\build\outputs\apk\release"
} else {
    "$mobileDir\app\build\outputs\apk\debug"
}

$apkFile = Get-ChildItem -Path $apkOutputDir -Filter "*.apk" -File -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $apkFile -or $apkFile.Length -le 0) {
    Write-Error "Failed to locate generated APK in $apkOutputDir."
    exit 1
}
Write-Host "[Stage 4/5] APK generated: $($apkFile.FullName) ($([Math]::Round($apkFile.Length / 1MB, 2)) MB)" -ForegroundColor Green

# 5. Stage to assets/
Write-Host "`n[Stage 5/5] Staging APK to assets/..." -ForegroundColor Yellow
$assetsDir = "$workspaceRoot\assets"
New-Item -ItemType Directory -Force -Path $assetsDir | Out-Null
$targetApk = "$assetsDir\DEX-Mobile.apk"

Copy-Item $apkFile.FullName $targetApk -Force

if (-not (Test-Path $targetApk) -or (Get-Item $targetApk).Length -le 0) {
    Write-Error "Staging failed: DEX-Mobile.apk is missing or 0 bytes."
    exit 1
}

# Update Checksum
$hash = (Get-FileHash -Path $targetApk -Algorithm SHA256).Hash
$sizeMB = [Math]::Round((Get-Item $targetApk).Length / 1MB, 2)
Write-Host "Staged: assets\DEX-Mobile.apk | Size: $sizeMB MB | SHA256: $hash" -ForegroundColor Cyan

# Update Checksum file
$checksumFile = "$assetsDir\checksums.txt"
$checksums = @()
$checksums += "SHA256 Checksums - DEX Distribution Artifacts"
$checksums += "Generated on: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')"
$checksums += "================================================================================"
foreach ($file in (Get-ChildItem -Path "$assetsDir\*" -Include "*.exe", "*.apk" -File)) {
    $fHash = (Get-FileHash -Path $file.FullName -Algorithm SHA256).Hash
    $fSize = [Math]::Round($file.Length / 1MB, 2)
    $checksums += "$fHash  $($file.Name)  ($fSize MB)"
}
$checksums | Out-File -FilePath $checksumFile -Encoding utf8

Write-Host "`n=================================================================" -ForegroundColor Green
Write-Host "       ANDROID BUILD SUCCESSFUL - ARTIFACT IN ASSETS/            " -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Green
