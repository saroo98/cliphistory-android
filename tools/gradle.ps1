# Text-only Gradle bootstrap. The official wrapper JAR is not bundled.
# Downloads only the pinned distribution and validates its publisher checksum.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$GradleArgs = $args
$Root = Split-Path $PSScriptRoot -Parent
$Version = '9.3.1'
$Tools = Join-Path $Root '.tools'
$Download = Join-Path $Tools 'downloads'
$Gradle = Join-Path $Tools "gradle-$Version\bin\gradle.bat"
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
try {
    if (!(Test-Path $Gradle)) {
        New-Item -ItemType Directory -Force $Download | Out-Null
        $Zip = Join-Path $Download "gradle-$Version-bin.zip"
        $Sum = "$Zip.sha256"
        $Url = "https://services.gradle.org/distributions/gradle-$Version-bin.zip"
        Write-Host "Downloading Gradle $Version from gradle.org..."
        Invoke-WebRequest -UseBasicParsing -Uri "$Url.sha256" -OutFile $Sum -TimeoutSec 120
        $Expected = (Get-Content -Raw $Sum).Trim()
        if ($Expected -notmatch '^[a-fA-F0-9]{64}$') { throw 'Invalid Gradle checksum response.' }
        if (!(Test-Path $Zip) -or ((Get-FileHash $Zip -Algorithm SHA256).Hash -ne $Expected)) {
            Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile "$Zip.part" -TimeoutSec 900
            if ((Get-FileHash "$Zip.part" -Algorithm SHA256).Hash -ne $Expected) { throw 'Gradle checksum did not match. Archive not executed.' }
            Move-Item -Force "$Zip.part" $Zip
        }
        Expand-Archive -LiteralPath $Zip -DestinationPath $Tools -Force
        if (!(Test-Path $Gradle)) { throw 'Gradle executable is missing after extraction.' }
    }
    Push-Location $Root
    try { & $Gradle @GradleArgs; $Code = $LASTEXITCODE } finally { Pop-Location }
    exit $Code
} catch {
    Write-Host "Gradle bootstrap failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
