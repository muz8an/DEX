Add-Type -AssemblyName System.Drawing

$workspaceRoot = $PSScriptRoot + "\.."
$targetIconsDir = Join-Path $workspaceRoot "assets\icons"
$targetResourceDir = Join-Path $workspaceRoot "src\main\resources"

if (-not (Test-Path $targetIconsDir)) {
    New-Item -ItemType Directory -Force -Path $targetIconsDir | Out-Null
}
if (-not (Test-Path $targetResourceDir)) {
    New-Item -ItemType Directory -Force -Path $targetResourceDir | Out-Null
}

$sourceImage = Join-Path $targetResourceDir "dex_icon.png"
if (-not (Test-Path $sourceImage)) {
    $sourceImage = Join-Path $targetResourceDir "assistant_orb.jpg"
}
if (-not (Test-Path $sourceImage)) {
    $fallbackPath = Join-Path $workspaceRoot "assets\icons\dex.png"
    if (Test-Path $fallbackPath) { $sourceImage = $fallbackPath }
}

if (-not (Test-Path $sourceImage)) {
    Write-Error "Source image not found in assets or resources."
    exit 1
}

$img = [System.Drawing.Image]::FromFile($sourceImage)

# 1. 512x512 PNG
$b512 = New-Object System.Drawing.Bitmap 512, 512
$g512 = [System.Drawing.Graphics]::FromImage($b512)
$g512.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g512.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g512.DrawImage($img, 0, 0, 512, 512)
$pngAssetPath = Join-Path $targetIconsDir "dex.png"
$pngResourcePath = Join-Path $targetResourceDir "dex_icon.png"
$b512.Save($pngAssetPath, [System.Drawing.Imaging.ImageFormat]::Png)
$b512.Save($pngResourcePath, [System.Drawing.Imaging.ImageFormat]::Png)
$g512.Dispose()
$b512.Dispose()

# 2. Multi-size Windows ICO (256x256)
$b256 = New-Object System.Drawing.Bitmap 256, 256
$g256 = [System.Drawing.Graphics]::FromImage($b256)
$g256.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g256.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g256.DrawImage($img, 0, 0, 256, 256)

$ms = New-Object System.IO.MemoryStream
$b256.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
$pngBytes = $ms.ToArray()
$ms.Dispose()

$icoAssetPath = Join-Path $targetIconsDir "dex.ico"
$fs = [System.IO.File]::Create($icoAssetPath)
$bw = New-Object System.IO.BinaryWriter($fs)
$bw.Write([UInt16]0)    # Reserved
$bw.Write([UInt16]1)    # Type (1=Icon)
$bw.Write([UInt16]1)    # 1 Image
$bw.Write([Byte]0)      # Width (0=256)
$bw.Write([Byte]0)      # Height (0=256)
$bw.Write([Byte]0)      # Color count
$bw.Write([Byte]0)      # Reserved
$bw.Write([UInt16]1)    # Color planes
$bw.Write([UInt16]32)   # Bits per pixel
$bw.Write([UInt32]$pngBytes.Length) # Image size
$bw.Write([UInt32]22)   # Offset (6 header + 16 dir entry)
$bw.Write($pngBytes)
$bw.Flush()
$fs.Close()

$g256.Dispose()
$b256.Dispose()
$img.Dispose()

Write-Output "Generated icons successfully: $pngAssetPath, $icoAssetPath, $pngResourcePath"
