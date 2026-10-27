Add-Type -AssemblyName System.Drawing
$workspaceRoot = (Resolve-Path "$PSScriptRoot\..").Path
$sourceFile = Join-Path $workspaceRoot "assets\icons\dex.png"

if (-not (Test-Path $sourceFile)) {
    Write-Error "Source icon not found: $sourceFile"
    exit 1
}

$src = [System.Drawing.Image]::FromFile($sourceFile)

$sizes = @{
    'mipmap-mdpi'    = 48
    'mipmap-hdpi'    = 72
    'mipmap-xhdpi'   = 96
    'mipmap-xxhdpi'  = 144
    'mipmap-xxxhdpi' = 192
}

foreach ($entry in $sizes.GetEnumerator()) {
    $folder = $entry.Key
    $dim = $entry.Value
    $dir = Join-Path $workspaceRoot "mobile\app\src\main\res\$folder"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    $bmp = New-Object System.Drawing.Bitmap $dim, $dim
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.DrawImage($src, 0, 0, $dim, $dim)

    $outPath = Join-Path $dir "ic_launcher.png"
    $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
}

$src.Dispose()
Write-Host "Android mipmap icons generated successfully." -ForegroundColor Green
