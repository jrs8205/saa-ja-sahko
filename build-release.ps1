$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (!(Test-Path -LiteralPath 'release-signing.properties')) { throw 'Julkaisuavain puuttuu. Palauta alkuperäinen avain ja release-signing.properties varmuuskopiosta.' }
& .\gradlew.bat :app:assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Release-käännös epäonnistui.' }
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$signer = Join-Path $sdkRoot 'build-tools/37.0.0/apksigner.bat'
$apk = Join-Path $PSScriptRoot 'app/build/outputs/apk/release/app-release.apk'
& $signer verify --verbose --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw 'APK-allekirjoituksen tarkistus epäonnistui.' }
$metadata = Get-Content 'app/build/outputs/apk/release/output-metadata.json' -Raw | ConvertFrom-Json
$version = $metadata.elements[0].versionName
$destination = Join-Path $PSScriptRoot "Saa-Sahko-$version-release.apk"
Copy-Item -LiteralPath $apk -Destination $destination -Force
Get-FileHash -Algorithm SHA256 -LiteralPath $destination | Format-List
Write-Output "Release-APK: $destination"
