param(
    [switch]$Android = $false
)

Add-Type -AssemblyName System.Drawing

$workspaceRoot = (Resolve-Path "$PSScriptRoot\..").Path
$targetIconsDir = Join-Path $workspaceRoot "assets\icons"
$targetResourceDir = Join-Path $workspaceRoot "src\main\resources"

New-Item -ItemType Directory -Force -Path $targetIconsDir | Out-Null
New-Item -ItemType Directory -Force -Path $targetResourceDir | Out-Null

$sourceImage = Join-Path $targetResourceDir "dex_icon.png"
if (-not (Test-Path $sourceImage)) {
    $sourceImage = Join-Path $targetResourceDir "assistant_orb.jpg"
}
if (-not (Test-Path $sourceImage)) {
    $sourceImage = Join-Path $targetIconsDir "dex.png"
}
if (-not (Test-Path $sourceImage)) {
    Write-Error "Source image not found in assets\icons or src\main\resources."
    exit 1
}

$fileBytes = [System.IO.File]::ReadAllBytes($sourceImage)
$msIn = New-Object System.IO.MemoryStream(,$fileBytes)
$img = [System.Drawing.Image]::FromStream($msIn)

function Save-ResizedPng($srcImg, [int]$dim, [string]$outPath) {
    $bmp = New-Object System.Drawing.Bitmap $dim, $dim
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.DrawImage($srcImg, 0, 0, $dim, $dim)
    $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
}

# 1. Desktop 512x512 PNG
$pngAssetPath = Join-Path $targetIconsDir "dex.png"
$pngResourcePath = Join-Path $targetResourceDir "dex_icon.png"
Save-ResizedPng $img 512 $pngAssetPath
if ($sourceImage -ne $pngResourcePath) {
    Save-ResizedPng $img 512 $pngResourcePath
}

# 2. Windows 256x256 ICO
$b256 = New-Object System.Drawing.Bitmap 256, 256
$g256 = [System.Drawing.Graphics]::FromImage($b256)
$g256.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g256.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g256.DrawImage($img, 0, 0, 256, 256)

$ms = New-Object System.IO.MemoryStream
$b256.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
$pngBytes = $ms.ToArray()
$ms.Dispose()
$g256.Dispose()
$b256.Dispose()

$icoAssetPath = Join-Path $targetIconsDir "dex.ico"
$fs = [System.IO.File]::Create($icoAssetPath)
$bw = New-Object System.IO.BinaryWriter($fs)
$bw.Write([UInt16]0)
$bw.Write([UInt16]1)
$bw.Write([UInt16]1)
$bw.Write([Byte]0)
$bw.Write([Byte]0)
$bw.Write([Byte]0)
$bw.Write([Byte]0)
$bw.Write([UInt16]1)
$bw.Write([UInt16]32)
$bw.Write([UInt32]$pngBytes.Length)
$bw.Write([UInt32]22)
$bw.Write($pngBytes)
$bw.Flush()
$fs.Close()

# 3. Android Mipmaps (when -Android is specified)
if ($Android) {
    $sizes = @{
        'mipmap-mdpi'    = 48
        'mipmap-hdpi'    = 72
        'mipmap-xhdpi'   = 96
        'mipmap-xxhdpi'  = 144
        'mipmap-xxxhdpi' = 192
    }
    foreach ($entry in $sizes.GetEnumerator()) {
        $dir = Join-Path $workspaceRoot "mobile\app\src\main\res\$($entry.Key)"
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        Save-ResizedPng $img $entry.Value (Join-Path $dir "ic_launcher.png")
    }
    Write-Output "Generated Android mipmap icons successfully."
}

$img.Dispose()
$msIn.Dispose()

Write-Output "Generated desktop icons successfully: $pngAssetPath, $icoAssetPath, $pngResourcePath"
