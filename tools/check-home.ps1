param(
    [string]$Classes = 'build/classes',
    [string]$Device = '',
    [switch]$PerformanceOnly,
    [switch]$Screenshots,
    [switch]$Presentation,
    [switch]$Previews,
    [switch]$Navigation,
    [switch]$SettingsLayout
)

# Build a separate, same-signature instrumentation APK; never change launcher preferences.
$ErrorActionPreference = 'Stop'
if (@($PerformanceOnly, $Screenshots, $Presentation, $Previews, $Navigation, $SettingsLayout).Where({ $_ }).Count -gt 1) { throw 'Choose one check mode' }
Set-Location (Split-Path $PSScriptRoot -Parent)
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME/bin;$env:PATH" }
$checkSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA/Android/Sdk" }
$checkTools = "$checkSdk/build-tools/34.0.0"
if (-not (Test-Path "$checkTools/aapt2.exe")) {
    $checkTools = (Get-ChildItem "$checkSdk/build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
}
$checkPlatform = "$checkSdk/platforms/android-34/android.jar"
if (-not (Test-Path $checkPlatform)) {
    $checkPlatform = Join-Path (Get-ChildItem "$checkSdk/platforms" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName 'android.jar'
}
$checkOutput = 'build/home-checks'
New-Item -ItemType Directory -Force "$checkOutput/classes", "$checkOutput/dex" | Out-Null
& javac -encoding UTF-8 --release 11 -cp "$Classes;$checkPlatform" -d "$checkOutput/classes" tests/HomeScreenChecks.java tests/HomeScreenScreenshots.java tests/PresentationImageChecks.java tests/PreviewLoadingChecks.java tests/HomeNavigationChecks.java tests/SettingsLayoutChecks.java
if ($LASTEXITCODE -ne 0) { throw 'Home checks compilation failed' }
& jar cf "$checkOutput/tests.jar" -C "$checkOutput/classes" .
& jar cf "$checkOutput/target.jar" -C $Classes .
& "$checkTools/d8.bat" --min-api 24 --lib $checkPlatform --classpath "$checkOutput/target.jar" --output "$checkOutput/dex" "$checkOutput/tests.jar"
if ($LASTEXITCODE -ne 0) { throw 'Home checks dex conversion failed' }
& "$checkTools/aapt2.exe" link -I $checkPlatform --manifest tests/home-screen-manifest.xml -o "$checkOutput/unsigned.apk"
if ($LASTEXITCODE -ne 0) { throw 'Home checks resource linking failed' }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$checkArchive = [System.IO.Compression.ZipFile]::Open((Resolve-Path "$checkOutput/unsigned.apk").Path, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($checkArchive, (Resolve-Path "$checkOutput/dex/classes.dex").Path, 'classes.dex') | Out-Null
} finally { $checkArchive.Dispose() }
& "$checkTools/zipalign.exe" -f 4 "$checkOutput/unsigned.apk" "$checkOutput/aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'Home checks alignment failed' }
& "$checkTools/apksigner.bat" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out "$checkOutput/checks.apk" "$checkOutput/aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'Home checks signing failed' }

$checkAdb = "$checkSdk/platform-tools/adb.exe"
$checkDeviceArgs = if ($Device) { @('-s', $Device) } else { @() }
& $checkAdb @checkDeviceArgs install -r -t "$checkOutput/checks.apk"
if ($LASTEXITCODE -ne 0) { throw 'Home checks installation failed' }
try {
    $checkRunArgs = @('shell', 'am', 'instrument', '-w')
    if ($PerformanceOnly) { $checkRunArgs += @('-e', 'only', 'performance') }
    $checkRunner = if ($SettingsLayout) { 'SettingsLayoutChecks' } elseif ($Navigation) { 'HomeNavigationChecks' } elseif ($Previews) { 'PreviewLoadingChecks' } elseif ($Presentation) { 'present.PresentationImageChecks' } elseif ($Screenshots) { 'HomeScreenScreenshots' } else { 'HomeScreenChecks' }
    $checkRunArgs += "com.techgenius.tvoslauncher.homechecks/com.techgenius.tvoslauncher.$checkRunner"
    $checkResult = (& $checkAdb @checkDeviceArgs @checkRunArgs) -join "`n"
    Write-Output $checkResult
    if ($LASTEXITCODE -ne 0 -or $checkResult -match 'FAIL:|Process crashed|INSTRUMENTATION_FAILED') {
        throw 'Home device checks failed'
    }
    if (-not $PerformanceOnly -and $checkResult -notmatch 'PASS:') { throw 'No Home checks result received' }
    if ($Screenshots) {
        if ($checkResult -notmatch 'SCREENSHOTS_DIR=([^\r\n]+)') { throw 'Screenshot directory not reported' }
        $checkRemoteScreens = $Matches[1]
        New-Item -ItemType Directory -Force docs/screenshots | Out-Null
        & $checkAdb @checkDeviceArgs pull "$checkRemoteScreens/." docs/screenshots
        if ($LASTEXITCODE -ne 0) { throw 'Screenshot download failed' }
    }
} finally {
    & $checkAdb @checkDeviceArgs uninstall com.techgenius.tvoslauncher.homechecks | Out-Null
}
