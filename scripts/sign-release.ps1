[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Artifact,
    [Parameter(Mandatory)]
    [string]$CertificateThumbprint,
    [Parameter(Mandatory)]
    [string]$TimestampUrl,
    [string]$SignTool = "signtool.exe"
)

$ErrorActionPreference = "Stop"
$artifactPath = (Resolve-Path -LiteralPath $Artifact).Path

& $SignTool sign /sha1 $CertificateThumbprint /fd SHA256 /tr $TimestampUrl /td SHA256 $artifactPath
if ($LASTEXITCODE -ne 0) { throw "Code signing failed with exit code $LASTEXITCODE" }

& $SignTool verify /pa /v $artifactPath
if ($LASTEXITCODE -ne 0) { throw "Signature verification failed with exit code $LASTEXITCODE" }

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $artifactPath).Hash
$hashFile = Join-Path (Split-Path $artifactPath -Parent) "SHA256SUMS.txt"
Set-Content -LiteralPath $hashFile -Value "$hash  $([System.IO.Path]::GetFileName($artifactPath))" -Encoding utf8
Write-Host "Verified signed artifact: $artifactPath"
Write-Host "SHA-256: $hash"

