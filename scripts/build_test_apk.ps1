param(
    [string]$ExpectedCertificate = "749d118bc8a16a7c0464b8dd0498c53da8a86a668d8f09f551e60cf7d88ee15e",
    [string]$PythonPath = "",
    [string]$SigningKeystore = "",
    [string]$KeystorePassword = ""
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
$zipalign = Join-Path $buildTools.FullName "zipalign.exe"
$apkanalyzer = Join-Path $sdkRoot "cmdline-tools\latest\bin\apkanalyzer.bat"
if (-not $PythonPath) {
    $pythonCommand = Get-Command py -ErrorAction SilentlyContinue
    if (-not $pythonCommand) {
        $pythonCommand = Get-Command python -ErrorAction Stop
    }
    $PythonPath = $pythonCommand.Source
}
if (-not $SigningKeystore) {
    $SigningKeystore = Join-Path $env:USERPROFILE ".android\debug.keystore"
}
if (-not (Test-Path -LiteralPath $SigningKeystore -PathType Leaf)) {
    throw "Compatible signing keystore not found: $SigningKeystore"
}
if (-not $KeystorePassword) {
    $KeystorePassword = if ($env:UA_PLAYER_KEYSTORE_PASSWORD) {
        $env:UA_PLAYER_KEYSTORE_PASSWORD
    } else {
        "android"
    }
}

Push-Location $projectRoot
try {
    $gradleArguments = @(
        "clean",
        ":app:assembleLatestUniversalRelease",
        "--console=plain",
        "-Pandroid.injected.signing.store.file=$SigningKeystore",
        "-Pandroid.injected.signing.store.password=$KeystorePassword",
        "-Pandroid.injected.signing.key.alias=androiddebugkey",
        "-Pandroid.injected.signing.key.password=$KeystorePassword"
    )
    & .\gradlew.bat @gradleArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }

    $apk = Get-ChildItem -LiteralPath (Join-Path $projectRoot "app\build\outputs\apk\latestUniversal\release") -Filter *.apk |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $apk) {
        throw "Latest universal release APK was not produced"
    }

    # Re-sign explicitly so every local test build has the same v1/v2/v3 scheme set as CI.
    $env:UA_PLAYER_LOCAL_KEYSTORE_PASSWORD = $KeystorePassword
    & $apksigner sign `
        --ks $SigningKeystore `
        --ks-key-alias androiddebugkey `
        --ks-pass env:UA_PLAYER_LOCAL_KEYSTORE_PASSWORD `
        --key-pass env:UA_PLAYER_LOCAL_KEYSTORE_PASSWORD `
        --v1-signing-enabled true `
        --v2-signing-enabled true `
        --v3-signing-enabled true `
        --v4-signing-enabled false `
        $apk.FullName
    if ($LASTEXITCODE -ne 0) {
        throw "APK signing failed with exit code $LASTEXITCODE"
    }

    $versionName = (& $apkanalyzer manifest version-name $apk.FullName).Trim()
    $versionCode = [int]((& $apkanalyzer manifest version-code $apk.FullName).Trim())

    $verifyArguments = @(
        (Join-Path $projectRoot "scripts\verify_apk.py"),
        "--apk", $apk.FullName,
        "--aapt2", $aapt2,
        "--apksigner", $apksigner,
        "--apkanalyzer", $apkanalyzer,
        "--zipalign", $zipalign,
        "--package", "com.lampaua.player",
        "--version-name", $versionName,
        "--version-code", $versionCode,
        "--abi", "arm64-v8a",
        "--abi", "armeabi-v7a",
        "--release"
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
    $destination = Join-Path $destinationDirectory "UA-Player-$versionName-private.apk"
    Copy-Item -LiteralPath $apk.FullName -Destination $destination -Force
    Write-Output "Test APK: $destination"
    Write-Output "SHA-256: $((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant())"
} finally {
    Remove-Item Env:UA_PLAYER_LOCAL_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    Pop-Location
}
