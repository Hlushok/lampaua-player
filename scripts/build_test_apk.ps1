param(
    [string]$ExpectedCertificate = "749d118bc8a16a7c0464b8dd0498c53da8a86a668d8f09f551e60cf7d88ee15e",
    [string]$PythonPath = ""
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = if ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} elseif ($env:ANDROID_HOME) {
    $env:ANDROID_HOME
} else {
    Join-Path $env:LOCALAPPDATA "Android\Sdk"
}

$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdkRoot "build-tools") -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
if (-not $buildTools) {
    throw "Android build-tools not found under $sdkRoot"
}

$aapt2 = Join-Path $buildTools.FullName "aapt2.exe"
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"
$apkanalyzer = Join-Path $sdkRoot "cmdline-tools\latest\bin\apkanalyzer.bat"
if (-not $PythonPath) {
    $pythonCommand = Get-Command py -ErrorAction SilentlyContinue
    if (-not $pythonCommand) {
        $pythonCommand = Get-Command python -ErrorAction Stop
    }
    $PythonPath = $pythonCommand.Source
}

Push-Location $projectRoot
try {
    & .\gradlew.bat clean :app:assembleLatestUniversalDebug --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }

    $apk = Get-ChildItem -LiteralPath (Join-Path $projectRoot "app\build\outputs\apk\latestUniversal\debug") -Filter *.apk |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $apk) {
        throw "Latest universal debug APK was not produced"
    }

    $verifyArguments = @(
        (Join-Path $projectRoot "scripts\verify_apk.py"),
        "--apk", $apk.FullName,
        "--aapt2", $aapt2,
        "--apksigner", $apksigner,
        "--apkanalyzer", $apkanalyzer
    )
    if ($ExpectedCertificate) {
        $verifyArguments += @("--certificate", $ExpectedCertificate)
    }
    $pythonPrefix = @()
    if ([System.IO.Path]::GetFileName($PythonPath) -ieq "py.exe") {
        $pythonPrefix = @("-3")
    }
    & $PythonPath @pythonPrefix @verifyArguments
    if ($LASTEXITCODE -ne 0) {
        throw "APK verification failed with exit code $LASTEXITCODE"
    }

    $destinationDirectory = Join-Path $projectRoot "test-builds"
    New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null
    $destination = Join-Path $destinationDirectory "UA-Player-2.0.0-test.apk"
    Copy-Item -LiteralPath $apk.FullName -Destination $destination -Force
    Write-Output "Test APK: $destination"
} finally {
    Pop-Location
}
