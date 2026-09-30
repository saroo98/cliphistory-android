# Windows 10/11, PowerShell 5.1+. Everything is built locally; no source is uploaded.
# SDK/JDK licences are presented to the person running the script, not silently accepted.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$Root = Split-Path $PSScriptRoot -Parent
$Tools = Join-Path $Root '.tools'
$Downloads = Join-Path $Tools 'downloads'
$Log = Join-Path $Root 'build-windows.log'
$TranscriptStarted = $false
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

function Download-File([string]$Url, [string]$Destination) {
    $Uri = [Uri]$Url
    if ($Uri.Scheme -ne 'https') { throw 'Refusing a non-HTTPS download.' }
    Write-Host "Downloading from $($Uri.Host)..."
    Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile "$Destination.part" -TimeoutSec 900
    Move-Item -Force "$Destination.part" $Destination
}
function Confirm-Step([string]$Message) {
    $Answer = Read-Host "$Message [y/N]"
    if ($Answer -notmatch '^(y|yes)$') { throw 'Stopped at your request. No APK was built.' }
}
function Java-Is-Supported([string]$HomePath) {
    if (!$HomePath -or !(Test-Path (Join-Path $HomePath 'bin\javac.exe'))) { return $false }
    $Text = (& (Join-Path $HomePath 'bin\javac.exe') -version 2>&1 | Out-String)
    return ($LASTEXITCODE -eq 0 -and $Text -match 'javac\s+(\d+)' -and [int]$Matches[1] -ge 17)
}
function Find-Java {
    $Candidates = @()
    if ($env:JAVA_HOME) { $Candidates += $env:JAVA_HOME.Trim('"') }
    $Candidates += (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr')
    $Cached = Join-Path $Tools 'jdk'
    if (Test-Path $Cached) { $Candidates += @(Get-ChildItem $Cached -Directory | ForEach-Object { $_.FullName }) }
    $Compiler = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($Compiler) { $Candidates += (Split-Path (Split-Path $Compiler.Source -Parent) -Parent) }
    foreach ($Candidate in $Candidates) { if (Java-Is-Supported $Candidate) { return $Candidate } }
    return $null
}
function Install-Java {
    Confirm-Step 'No suitable JDK found. Download a local Eclipse Temurin JDK 17 (GPLv2 with Classpath Exception)?'
    $Metadata = 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=windows&vendor=eclipse'
    $Assets = @(Invoke-RestMethod -Uri $Metadata -TimeoutSec 120)
    if ($Assets.Count -lt 1) { throw 'Adoptium returned no JDK 17 package.' }
    $Package = $Assets[0].binary.package
    $Expected = [string]$Package.checksum
    if ($Expected -notmatch '^[a-fA-F0-9]{64}$') { throw 'Invalid JDK checksum metadata.' }
    $Zip = Join-Path $Downloads 'temurin-jdk17-windows-x64.zip'
    Download-File ([string]$Package.link) $Zip
    if ((Get-FileHash $Zip -Algorithm SHA256).Hash -ne $Expected) { throw 'JDK checksum mismatch; not extracting.' }
    $Directory = Join-Path $Tools 'jdk'
    New-Item -ItemType Directory -Force $Directory | Out-Null
    Expand-Archive -LiteralPath $Zip -DestinationPath $Directory -Force
    foreach ($Candidate in (Get-ChildItem $Directory -Directory)) {
        if (Java-Is-Supported $Candidate.FullName) { return $Candidate.FullName }
    }
    throw 'Downloaded JDK did not contain a usable Java compiler.'
}
function Install-CommandLineTools([string]$Sdk) {
    $Metadata = Join-Path $Downloads 'google-repository.xml'
    Download-File 'https://dl.google.com/android/repository/repository2-3.xml' $Metadata
    [xml]$Repository = Get-Content -LiteralPath $Metadata -Raw
    $Package = $Repository.SelectNodes("//*[local-name()='remotePackage']") |
        Where-Object { $_.GetAttribute('path') -eq 'cmdline-tools;latest' } |
        Select-Object -First 1
    if (!$Package) { throw 'Google SDK metadata has no cmdline-tools;latest package. Install Command-line Tools in Android Studio, then rerun.' }
    $Archive = $Package.SelectNodes("./*[local-name()='archives']/*[local-name()='archive']") |
        Where-Object { $_.SelectSingleNode("./*[local-name()='host-os']").InnerText -eq 'windows' } |
        Select-Object -First 1
    if (!$Archive) { throw 'No Windows command-line tools archive was listed by Google.' }
    $Complete = $Archive.SelectSingleNode("./*[local-name()='complete']")
    $Relative = $Complete.SelectSingleNode("./*[local-name()='url']").InnerText
    if ($Relative -match '(^https?:|\.\.|^/)') { throw 'Unexpected SDK archive path; refusing download.' }
    $Checksum = $Complete.SelectSingleNode("./*[local-name()='checksum']")
    $Algorithm = $Checksum.GetAttribute('type').ToUpperInvariant().Replace('-','')
    if (!$Algorithm) { $Algorithm = 'SHA1' }
    if ($Algorithm -notin @('SHA1','SHA256')) { throw 'Unsupported SDK metadata checksum type.' }
    $ExpectedSize = [long]$Complete.SelectSingleNode("./*[local-name()='size']").InnerText
    $Zip = Join-Path $Downloads 'android-commandline-tools-windows.zip'
    Download-File "https://dl.google.com/android/repository/$Relative" $Zip
    if ((Get-Item $Zip).Length -ne $ExpectedSize -or (Get-FileHash $Zip -Algorithm $Algorithm).Hash -ne $Checksum.InnerText.Trim()) {
        throw 'Google SDK archive size/checksum mismatch; not extracting.'
    }
    $Temporary = Join-Path $Tools ('sdk-extract-' + [Guid]::NewGuid().ToString('N'))
    $Destination = Join-Path $Sdk 'cmdline-tools\latest'
    if (Test-Path $Destination) { throw "Existing incomplete tools folder at $Destination. Repair it using Android Studio; this script will not delete it." }
    New-Item -ItemType Directory -Force (Split-Path $Destination -Parent) | Out-Null
    try {
        Expand-Archive -LiteralPath $Zip -DestinationPath $Temporary
        $Extracted = Join-Path $Temporary 'cmdline-tools'
        if (!(Test-Path (Join-Path $Extracted 'bin\sdkmanager.bat'))) { throw 'SDK archive structure is not recognised.' }
        Move-Item -LiteralPath $Extracted -Destination $Destination
    } finally { if (Test-Path $Temporary) { Remove-Item -LiteralPath $Temporary -Recurse -Force } }
    return (Join-Path $Destination 'bin\sdkmanager.bat')
}
function Prepare-Signing([string]$JavaHome) {
    $Directory = Join-Path $Root '.signing'
    $Keystore = Join-Path $Directory 'cliphistory.jks'
    $PasswordFile = Join-Path $Directory 'password.txt'
    if ((Test-Path $Keystore) -and !(Test-Path $PasswordFile)) { throw 'Signing key exists but password.txt is missing. Restore the password file rather than replacing the key.' }
    if (!(Test-Path $Keystore)) {
        New-Item -ItemType Directory -Force $Directory | Out-Null
        $Bytes = New-Object byte[] 32
        $Random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $Random.GetBytes($Bytes) } finally { $Random.Dispose() }
        $Password = ([BitConverter]::ToString($Bytes)).Replace('-','')
        [IO.File]::WriteAllText($PasswordFile, $Password, [Text.Encoding]::ASCII)
        # Restrict the local signing directory to the current Windows account.
        $Identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
        $Acl = New-Object Security.AccessControl.DirectorySecurity
        $Acl.SetAccessRuleProtection($true, $false)
        $Rule = New-Object Security.AccessControl.FileSystemAccessRule($Identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $Acl.AddAccessRule($Rule)
        Set-Acl -LiteralPath $Directory -AclObject $Acl
        $env:CLIPHISTORY_SIGN_PASS = $Password
        try {
            & (Join-Path $JavaHome 'bin\keytool.exe') -genkeypair -noprompt -keystore $Keystore -storetype JKS `
                -alias cliphistory -keyalg RSA -keysize 3072 -validity 10000 `
                -dname 'CN=ClipHistory Personal Build' '-storepass:env' CLIPHISTORY_SIGN_PASS '-keypass:env' CLIPHISTORY_SIGN_PASS
            if ($LASTEXITCODE -ne 0) { throw 'Personal signing key generation failed.' }
        } finally { Remove-Item Env:\CLIPHISTORY_SIGN_PASS -ErrorAction SilentlyContinue; $Password = $null }
    }
    Write-Host 'Keep .signing/ safe: the same key is needed to install future upgrades without uninstalling.'
}

try {
    if (![Environment]::Is64BitOperatingSystem) { throw 'A 64-bit Windows computer is required.' }
    Set-Location $Root
    New-Item -ItemType Directory -Force $Downloads | Out-Null
    Start-Transcript -LiteralPath $Log -Force | Out-Null
    $TranscriptStarted = $true
    Write-Host 'ClipHistory - local Android build' -ForegroundColor Cyan
    Write-Host 'Building and verifying this source project on this PC.'
    Write-Host 'This script will run the actual compilation, tests, lint and APK signing on this PC.'
    Confirm-Step 'Download missing build tools/dependencies and build here? Downloads may be several hundred megabytes'
    $JavaHome = Find-Java
    if (!$JavaHome) { $JavaHome = Install-Java }
    $env:JAVA_HOME = $JavaHome
    $env:PATH = "$JavaHome\bin;$env:PATH"
    Write-Host "Java: $JavaHome"
    $Sdk = $env:ANDROID_HOME
    if (!$Sdk) { $Sdk = $env:ANDROID_SDK_ROOT }
    $StudioSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (!$Sdk -and (Test-Path $StudioSdk)) { $Sdk = $StudioSdk }
    if (!$Sdk) { $Sdk = Join-Path $Tools 'android-sdk' }
    $Sdk = [IO.Path]::GetFullPath($Sdk)
    New-Item -ItemType Directory -Force $Sdk | Out-Null
    $Manager = Join-Path $Sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
    if (!(Test-Path $Manager)) {
        $Other = @(Get-ChildItem (Join-Path $Sdk 'cmdline-tools') -Filter sdkmanager.bat -Recurse -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending)
        if ($Other.Count -gt 0) { $Manager = $Other[0].FullName } else { $Manager = Install-CommandLineTools $Sdk }
    }
    $env:ANDROID_HOME = $Sdk
    $env:ANDROID_SDK_ROOT = $Sdk
    Write-Host 'Review the Android SDK licence prompts below. Enter y only for terms you accept.' -ForegroundColor Yellow
    & $Manager "--sdk_root=$Sdk" --licenses
    if ($LASTEXITCODE -ne 0) { throw 'Android SDK licence step did not complete.' }
    & $Manager "--sdk_root=$Sdk" 'platform-tools' 'platforms;android-37.0' 'build-tools;36.0.0'
    if ($LASTEXITCODE -ne 0) { throw 'SDK installation failed. API 37 and Build Tools 36.0.0 are required.' }
    if (!(Test-Path (Join-Path $Sdk 'platforms\android-37.0\android.jar'))) { throw 'Android API 37 SDK was not installed.' }
    # Forward slashes avoid Java-properties backslash escapes. Escape non-ASCII paths as Unicode.
    $SdkProperty = $Sdk.Replace('\','/').Replace(':','\:')
    $Escaped = New-Object Text.StringBuilder
    foreach ($Character in $SdkProperty.ToCharArray()) {
        if ([int]$Character -gt 127) { [void]$Escaped.Append(('\u{0:x4}' -f [int]$Character)) }
        else { [void]$Escaped.Append($Character) }
    }
    [IO.File]::WriteAllText((Join-Path $Root 'local.properties'), "sdk.dir=$Escaped`n", [Text.Encoding]::ASCII)
    Prepare-Signing $JavaHome
    & (Join-Path $Root 'gradlew.bat') `
        --no-daemon :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Compilation, tests or lint failed. No success is claimed. Read build-windows.log.' }
    $Apk = Join-Path $Root 'app\build\outputs\apk\release\app-release.apk'
    if (!(Test-Path $Apk)) { throw 'Signed release APK was not produced.' }
    $BuildTools = Join-Path $Sdk 'build-tools\36.0.0'
    & (Join-Path $BuildTools 'apksigner.bat') verify --verbose --print-certs $Apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $Aapt = Join-Path $BuildTools 'aapt2.exe'
    $Permissions = (& $Aapt dump permissions $Apk 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect APK permissions.' }
    [IO.File]::WriteAllText((Join-Path $Root 'apk-permissions.txt'),$Permissions)
    $DeclaredPermissions = [regex]::Matches($Permissions, "uses-permission(?:-sdk-\d+)?: name='([^']+)'")
    foreach ($Permission in $DeclaredPermissions) {
        if ($Permission.Groups[1].Value -ne 'moe.shizuku.manager.permission.API_V23') {
            throw "Unexpected permission in built APK: $($Permission.Groups[1].Value)"
        }
    }
    if ($DeclaredPermissions.Count -lt 1) { throw 'Expected Shizuku permission was not found in APK permission report.' }
    $Badging = (& $Aapt dump badging $Apk 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $Badging -notmatch "targetSdkVersion:'37'" -or $Badging -match 'application-debuggable') {
        throw 'APK target/debuggable audit failed.'
    }
    $Version = [regex]::Match([IO.File]::ReadAllText((Join-Path $Root 'app/build.gradle.kts')), 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
    if (!$Version) { throw 'Release version is missing.' }
    $Result = Join-Path $Root "ClipHistory-$Version.apk"
    Copy-Item -Force $Apk $Result
    $Digest = (Get-FileHash $Result -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText("$Result.sha256", "$Digest  ClipHistory-$Version.apk`n", [Text.Encoding]::ASCII)
    Write-Host ''
    Write-Host 'APK built, tested at the JVM level, linted and signature/permission-checked.' -ForegroundColor Green
    Write-Host $Result
    Write-Host 'Physical Pixel testing has NOT happened automatically. Follow DEVICE_TESTS.md after installing.'
    Write-Host 'No phone changes, installation or root operations were performed by this script.'
    if ($TranscriptStarted) { Stop-Transcript | Out-Null; $TranscriptStarted = $false }
    exit 0
} catch {
    Write-Host ''
    Write-Host "BUILD STOPPED: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'Keep build-windows.log for troubleshooting. It should not contain clipboard text or signing passwords.'
    if ($TranscriptStarted) { Stop-Transcript | Out-Null }
    exit 1
}
