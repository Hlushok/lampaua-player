param()

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$adaptivePath = Join-Path $repoRoot "app\src\main\res\mipmap-anydpi-v26\ic_launcher.xml"
$layoutPath = Join-Path $repoRoot "app\src\main\res\layout\activity_player.xml"

$adaptive = Get-Content -LiteralPath $adaptivePath -Raw
if (-not $adaptive.Contains('@android:color/transparent')) {
    throw "Adaptive launcher background is not transparent"
}
if (-not $adaptive.Contains('@drawable/ua_player_launcher_icon')) {
    throw "Adaptive icon does not use the launcher-only logo"
}

$layout = Get-Content -LiteralPath $layoutPath -Raw
if (-not $layout.Contains('android:src="@drawable/ua_player_icon"')) {
    throw "The in-player logo no longer uses the untouched canonical asset"
}

Add-Type -AssemblyName System.Drawing

function Assert-TransparentOutsideCircle {
    param([Parameter(Mandatory = $true)][string]$RelativePath)

    $fullPath = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw "Launcher image is missing: $RelativePath"
    }

    $bitmap = [System.Drawing.Bitmap]::FromFile($fullPath)
    try {
        $width = $bitmap.Width
        $height = $bitmap.Height
        $rectangle = [System.Drawing.Rectangle]::new(0, 0, $width, $height)
        $pixelFormat = [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
        $data = $bitmap.LockBits(
            $rectangle,
            [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
            $pixelFormat
        )
        try {
            $stride = [Math]::Abs($data.Stride)
            $pixels = [byte[]]::new($stride * $height)
            [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $pixels, 0, $pixels.Length)
            $centerX = ($width - 1) / 2.0
            $centerY = ($height - 1) / 2.0
            $transparentFrom = [Math]::Min($width, $height) * 0.47
            $transparentFromSquared = $transparentFrom * $transparentFrom

            for ($y = 0; $y -lt $height; $y++) {
                for ($x = 0; $x -lt $width; $x++) {
                    $dx = $x - $centerX
                    $dy = $y - $centerY
                    if (($dx * $dx + $dy * $dy) -ge $transparentFromSquared) {
                        $alpha = $pixels[$y * $stride + $x * 4 + 3]
                        if ($alpha -ne 0) {
                            throw "$RelativePath has a visible square underlay at $x,$y (alpha $alpha)"
                        }
                    }
                }
            }
        } finally {
            $bitmap.UnlockBits($data)
        }
    } finally {
        $bitmap.Dispose()
    }
}

$launcherImages = @(
    "app\src\main\res\drawable-nodpi\ua_player_launcher_icon.png",
    "app\src\main\res\mipmap-mdpi\ic_launcher.png",
    "app\src\main\res\mipmap-hdpi\ic_launcher.png",
    "app\src\main\res\mipmap-xhdpi\ic_launcher.png",
    "app\src\main\res\mipmap-xxhdpi\ic_launcher.png",
    "app\src\main\res\mipmap-xxxhdpi\ic_launcher.png"
)

foreach ($launcherImage in $launcherImages) {
    Assert-TransparentOutsideCircle -RelativePath $launcherImage
}

Write-Output "Launcher icon transparency verified"
