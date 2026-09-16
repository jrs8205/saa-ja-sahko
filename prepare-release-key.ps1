$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$keyDirectory = Join-Path $PSScriptRoot '.signing'
$keyFile = Join-Path $keyDirectory 'saa-sahko-release.p12'
$propertiesFile = Join-Path $PSScriptRoot 'release-signing.properties'
if ((Test-Path -LiteralPath $keyFile) -or (Test-Path -LiteralPath $propertiesFile)) {
    throw 'Allekirjoitus on jo luotu. Säilytä nykyinen avain; älä luo päivityksille uutta.'
}
New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $keyDirectory /inheritance:r /grant:r "${identity}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Avaimen hakemiston käyttöoikeuksien rajaaminen epäonnistui.' }
$password = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
try {
    $env:SAA_RELEASE_PASSWORD = $password
    & keytool.exe -genkeypair -keystore $keyFile -storetype PKCS12 -storepass:env SAA_RELEASE_PASSWORD -keypass:env SAA_RELEASE_PASSWORD -alias saa-sahko -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=Saa ja Sahko, OU=Personal, O=Jarsi, C=FI'
    if ($LASTEXITCODE -ne 0) { throw 'Allekirjoitusavaimen luominen epäonnistui.' }
    $properties = "storeFile=.signing/saa-sahko-release.p12`nstorePassword=$password`nkeyAlias=saa-sahko`nkeyPassword=$password`n"
    [IO.File]::WriteAllText($propertiesFile, $properties, [Text.UTF8Encoding]::new($false))
    & icacls.exe $propertiesFile /inheritance:r /grant:r "${identity}:F" 'SYSTEM:F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Salasanatiedoston käyttöoikeuksien rajaaminen epäonnistui.' }
    & keytool.exe -exportcert -rfc -keystore $keyFile -storepass:env SAA_RELEASE_PASSWORD -alias saa-sahko -file (Join-Path $PSScriptRoot 'docs/release-certificate.pem')
    if ($LASTEXITCODE -ne 0) { throw 'Julkisen varmenteen vienti epäonnistui.' }
    Write-Output 'Pysyvä julkaisuavain luotu. Varmuuskopioi .signing ja release-signing.properties turvalliseen paikkaan. Niitä ei tallenneta Gitiin.'
} finally {
    Remove-Item Env:SAA_RELEASE_PASSWORD -ErrorAction SilentlyContinue
    $password = $null
    $properties = $null
}
