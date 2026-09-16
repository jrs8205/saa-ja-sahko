$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Android-tarkistus epäonnistui ($LASTEXITCODE)." }
} finally {
    Pop-Location
}
