# ==============================================================================
# DEX - Master Multi-Platform Packaging Pipeline (Windows + Android)
# Compiles, tests, packages, and verifies artifacts for both desktop and mobile.
# ==============================================================================

param(
    [switch]$SkipTests = $false
)

$ErrorActionPreference = "Stop"
$workspaceRoot = (Resolve-Path "$PSScriptRoot\..").Path
Set-Location $workspaceRoot

$startTime = Get-Date

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "             DEX COMPLETE BUILD PIPELINE (WIN + ANDROID)         " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Started at: $($startTime.ToString('yyyy-MM-dd HH:mm:ss K'))`n" -ForegroundColor Gray

# 1. Build Windows Desktop App & Installer
Write-Host "-----------------------------------------------------------------" -ForegroundColor Magenta
Write-Host ">>> PHASE 1: WINDOWS DESKTOP APPLICATION & INSTALLER" -ForegroundColor Magenta
Write-Host "-----------------------------------------------------------------" -ForegroundColor Magenta
$winScript = "$workspaceRoot\scripts\build_windows.ps1"
$winArgs = @()
if ($SkipTests) { $winArgs += "-SkipTests" }
& powershell -ExecutionPolicy Bypass -File $winScript @winArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "Windows build pipeline failed."
    exit 1
}

# 2. Build Android Companion APK
Write-Host "`n-----------------------------------------------------------------" -ForegroundColor Magenta
Write-Host ">>> PHASE 2: ANDROID COMPANION APPLICATION (APK)" -ForegroundColor Magenta
Write-Host "-----------------------------------------------------------------" -ForegroundColor Magenta
$androidScript = "$workspaceRoot\scripts\build_android.ps1"
$androidArgs = @()
if ($SkipTests) { $androidArgs += "-SkipTests" }
& powershell -ExecutionPolicy Bypass -File $androidScript @androidArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "Android build pipeline failed."
    exit 1
}

# 3. Master Verification of Staged Artifacts
Write-Host "`n-----------------------------------------------------------------" -ForegroundColor Magenta
Write-Host ">>> PHASE 3: ARTIFACT VALIDATION & VERIFICATION" -ForegroundColor Magenta
Write-Host "-----------------------------------------------------------------" -ForegroundColor Magenta

$assetsDir = "$workspaceRoot\assets"
$expectedFiles = @("DEX.exe", "DEX-Setup.exe", "DEX-Mobile.apk")
$allValid = $true

foreach ($f in $expectedFiles) {
    $fullPath = Join-Path $assetsDir $f
    if (-not (Test-Path $fullPath)) {
        Write-Host "[-] Missing: assets\$f" -ForegroundColor Red
        $allValid = $false
    } else {
        $item = Get-Item $fullPath
        $sizeMB = [Math]::Round($item.Length / 1MB, 2)
        if ($item.Length -le 0) {
            Write-Host "[-] Zero-byte artifact: assets\$f" -ForegroundColor Red
            $allValid = $false
        } else {
            $hash = (Get-FileHash -Path $fullPath -Algorithm SHA256).Hash
            Write-Host "[+] Verified: assets\$f | Size: $sizeMB MB | SHA256: $hash" -ForegroundColor Green
        }
    }
}

if (-not $allValid) {
    Write-Error "Verification failed: Not all required artifacts exist with non-zero size."
    exit 1
}

# Update master checksums file
$checksumFile = "$assetsDir\checksums.txt"
$checksums = @()
$checksums += "SHA256 Checksums - DEX Distribution Artifacts"
$checksums += "Generated on: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')"
$checksums += "================================================================================"
foreach ($f in $expectedFiles) {
    $item = Get-Item (Join-Path $assetsDir $f)
    $fHash = (Get-FileHash -Path $item.FullName -Algorithm SHA256).Hash
    $fSize = [Math]::Round($item.Length / 1MB, 2)
    $checksums += "$fHash  $($item.Name)  ($fSize MB)"
}
$checksums | Out-File -FilePath $checksumFile -Encoding utf8


$duration = (Get-Date) - $startTime
Write-Host "`n=================================================================" -ForegroundColor Green
Write-Host "   ALL BUILDS SUCCESSFUL - DURATION: $([Math]::Round($duration.TotalSeconds, 1))s" -ForegroundColor Green
Write-Host "   Windows Desktop Executable : assets\DEX.exe                   " -ForegroundColor Green
Write-Host "   Windows Genuine Installer  : assets\DEX-Setup.exe             " -ForegroundColor Green
Write-Host "   Android Companion APK      : assets\DEX-Mobile.apk            " -ForegroundColor Green
Write-Host "   Cryptographic Checksums    : assets\checksums.txt             " -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Green
