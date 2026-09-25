[CmdletBinding()]
param(
    [ValidateSet("app-image", "exe", "msi")]
    [string]$PackageType = "exe",
    [string]$MavenCommand = "mvn",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$targetRoot = Join-Path $projectRoot "target"
$releaseRoot = Join-Path $projectRoot "release"
$toolsRoot = Join-Path $projectRoot ".release-tools"
$stagingRoot = Join-Path $targetRoot "release-staging"
$inputRoot = Join-Path $stagingRoot "input"
$runtimeRoot = Join-Path $stagingRoot "runtime"
$iconPath = Join-Path $stagingRoot "Hestia.ico"

$jdkUrl = "https://aka.ms/download-jdk/microsoft-jdk-21.0.12.1-windows-x64.zip"
$jdkSha256 = "192441A9D27DA813BADA974BB88B4CF64D37A9589ED37F204374D411CA5CE07F"
$wixUrl = "https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip"
$wixSha256 = "6AC824E1642D6F7277D0ED7EA09411A508F6116BA6FAE0AA5F2C7DAA2FF43D31"
$upgradeUuid = "C72C0407-5FFD-432C-A192-381527812B2D"

function Assert-SafeBuildPath([string]$Path) {
    $resolvedTarget = [System.IO.Path]::GetFullPath($targetRoot).TrimEnd('\') + '\'
    $resolvedRelease = [System.IO.Path]::GetFullPath($releaseRoot).TrimEnd('\') + '\'
    $resolvedTools = [System.IO.Path]::GetFullPath($toolsRoot).TrimEnd('\') + '\'
    $resolvedPath = [System.IO.Path]::GetFullPath($Path).TrimEnd('\') + '\'
    if (-not ($resolvedPath.StartsWith($resolvedTarget, [StringComparison]::OrdinalIgnoreCase) -or
            $resolvedPath.StartsWith($resolvedRelease, [StringComparison]::OrdinalIgnoreCase) -or
            $resolvedPath.StartsWith($resolvedTools, [StringComparison]::OrdinalIgnoreCase))) {
        throw "Refusing to modify a path outside the release build directories: $Path"
    }
}

function Reset-BuildDirectory([string]$Path) {
    Assert-SafeBuildPath $Path
    if (Test-Path -LiteralPath $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
}

function Get-CheckedArchive(
    [string]$Uri,
    [string]$Destination,
    [string]$ExpectedHash
) {
    if (-not (Test-Path -LiteralPath $Destination)) {
        Write-Host "Downloading $Uri"
        Invoke-WebRequest -Uri $Uri -OutFile $Destination
    }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Destination).Hash
    if ($actualHash -ne $ExpectedHash) {
        throw "Checksum mismatch for $Destination. Expected $ExpectedHash, found $actualHash."
    }
}

function Expand-ToolArchive([string]$Archive, [string]$Destination, [string]$ExpectedExecutable) {
    if (-not (Test-Path -LiteralPath $ExpectedExecutable)) {
        if (Test-Path -LiteralPath $Destination) {
            Assert-SafeBuildPath $Destination
            Remove-Item -LiteralPath $Destination -Recurse -Force
        }
        Expand-Archive -LiteralPath $Archive -DestinationPath $Destination -Force
    }
}

function New-MultiResolutionIcon([string]$Destination) {
    $sizes = @(16, 32, 48, 64, 128, 256)
    $images = [System.Collections.Generic.List[byte[]]]::new()
    foreach ($size in $sizes) {
        $path = Join-Path $projectRoot "src\main\resources\images\branding\hestia-symbol-$size.png"
        if (-not (Test-Path -LiteralPath $path)) { throw "Missing icon source: $path" }
        $images.Add([System.IO.File]::ReadAllBytes($path))
    }

    $stream = [System.IO.File]::Open($Destination, [System.IO.FileMode]::Create)
    $writer = [System.IO.BinaryWriter]::new($stream)
    try {
        $writer.Write([UInt16]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]$images.Count)
        $offset = 6 + (16 * $images.Count)
        for ($index = 0; $index -lt $images.Count; $index++) {
            $size = $sizes[$index]
            $writer.Write([Byte]$(if ($size -eq 256) { 0 } else { $size }))
            $writer.Write([Byte]$(if ($size -eq 256) { 0 } else { $size }))
            $writer.Write([Byte]0)
            $writer.Write([Byte]0)
            $writer.Write([UInt16]1)
            $writer.Write([UInt16]32)
            $writer.Write([UInt32]$images[$index].Length)
            $writer.Write([UInt32]$offset)
            $offset += $images[$index].Length
        }
        foreach ($image in $images) { $writer.Write($image) }
    } finally {
        $writer.Dispose()
        $stream.Dispose()
    }
}

Set-Location $projectRoot
[xml]$pom = Get-Content -LiteralPath (Join-Path $projectRoot "pom.xml")
$version = [string]$pom.project.version
if ($version -notmatch '^\d+\.\d+\.\d+$') {
    throw "Release version must use MAJOR.MINOR.PATCH without SNAPSHOT: $version"
}

$mavenArguments = @("-Dmaven.repo.local=.m2/repository", "clean", "package")
if ($SkipTests) { $mavenArguments += "-DskipTests" }
& $MavenCommand @mavenArguments
if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE" }

New-Item -ItemType Directory -Path $toolsRoot -Force | Out-Null
$jdkArchive = Join-Path $toolsRoot "microsoft-jdk-21.0.12.1-windows-x64.zip"
$jdkExtract = Join-Path $toolsRoot "jdk-21"
$jdkHome = Join-Path $jdkExtract "jdk-21.0.12.1+1"
$jlink = Join-Path $jdkHome "bin\jlink.exe"
$jpackage = Join-Path $jdkHome "bin\jpackage.exe"
Get-CheckedArchive $jdkUrl $jdkArchive $jdkSha256
Expand-ToolArchive $jdkArchive $jdkExtract $jlink

if ($PackageType -ne "app-image") {
    $wixArchive = Join-Path $toolsRoot "wix314-binaries.zip"
    $wixHome = Join-Path $toolsRoot "wix314"
    $candle = Join-Path $wixHome "candle.exe"
    Get-CheckedArchive $wixUrl $wixArchive $wixSha256
    Expand-ToolArchive $wixArchive $wixHome $candle
    $env:PATH = "$wixHome;$env:PATH"
}

Reset-BuildDirectory $stagingRoot
New-Item -ItemType Directory -Path $inputRoot -Force | Out-Null
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null

$jarName = "hestia-$version-executable.jar"
$jarSource = Join-Path $targetRoot $jarName
if (-not (Test-Path -LiteralPath $jarSource)) { throw "Missing executable JAR: $jarSource" }
Copy-Item -LiteralPath $jarSource -Destination $inputRoot
Copy-Item -LiteralPath (Join-Path $projectRoot "LICENSE") -Destination $inputRoot
Copy-Item -LiteralPath (Join-Path $projectRoot "THIRD-PARTY-NOTICES.md") -Destination $inputRoot
New-MultiResolutionIcon $iconPath

$modules = @(
    "java.base", "java.desktop", "java.logging", "java.management", "java.naming",
    "java.scripting", "java.sql", "java.xml", "jdk.charsets", "jdk.crypto.ec",
    "jdk.jfr", "jdk.unsupported", "jdk.unsupported.desktop"
) -join ','

& $jlink --add-modules $modules --bind-services --strip-debug --no-header-files --no-man-pages `
    --compress zip-6 --output $runtimeRoot
if ($LASTEXITCODE -ne 0) { throw "jlink failed with exit code $LASTEXITCODE" }

$commonArguments = @(
    "--type", $PackageType,
    "--dest", $releaseRoot,
    "--name", "Hestia",
    "--app-version", $version,
    "--vendor", "Ryan",
    "--description", "Gestão financeira pessoal e familiar",
    "--copyright", "Copyright (c) 2026 Ryan",
    "--input", $inputRoot,
    "--main-jar", $jarName,
    "--main-class", "io.github.ryanoviski.hestia.HestiaLauncher",
    "--runtime-image", $runtimeRoot,
    "--icon", $iconPath,
    "--java-options", "--enable-native-access=ALL-UNNAMED",
    "--java-options", "-Dfile.encoding=UTF-8"
)

if ($PackageType -ne "app-image") {
    $commonArguments += @(
        "--license-file", (Join-Path $projectRoot "LICENSE"),
        "--install-dir", "Hestia Application",
        "--win-dir-chooser",
        "--win-menu",
        "--win-menu-group", "Hestia",
        "--win-per-user-install",
        "--win-shortcut",
        "--win-upgrade-uuid", $upgradeUuid
    )
}

if ($PackageType -eq "app-image") {
    $existingOutput = Join-Path $releaseRoot "Hestia"
    if (Test-Path -LiteralPath $existingOutput) {
        Assert-SafeBuildPath $existingOutput
        Remove-Item -LiteralPath $existingOutput -Recurse -Force
    }
} else {
    foreach ($name in @("Hestia-$version.$PackageType", "Hestia-$version-Setup.exe")) {
        $existingOutput = Join-Path $releaseRoot $name
        if (Test-Path -LiteralPath $existingOutput) {
            Assert-SafeBuildPath $existingOutput
            Remove-Item -LiteralPath $existingOutput -Force
        }
    }
}

& $jpackage @commonArguments
if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE" }

if ($PackageType -eq "exe") {
    $generated = Join-Path $releaseRoot "Hestia-$version.exe"
    $final = Join-Path $releaseRoot "Hestia-$version-Setup.exe"
    if (-not (Test-Path -LiteralPath $generated)) { throw "Expected installer not found: $generated" }
    Move-Item -LiteralPath $generated -Destination $final -Force
} elseif ($PackageType -eq "msi") {
    $generated = Join-Path $releaseRoot "Hestia-$version.msi"
    $final = Join-Path $releaseRoot "Hestia-$version.msi"
} else {
    $final = Join-Path $releaseRoot "Hestia"
}

if ($PackageType -ne "app-image") {
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $final).Hash
    $hashLine = "$hash  $([System.IO.Path]::GetFileName($final))"
    Set-Content -LiteralPath (Join-Path $releaseRoot "SHA256SUMS.txt") -Value $hashLine -Encoding utf8
    Copy-Item -LiteralPath (Join-Path $projectRoot "THIRD-PARTY-NOTICES.md") -Destination $releaseRoot -Force
    Copy-Item -LiteralPath (Join-Path $projectRoot "LICENSE") -Destination $releaseRoot -Force
    $securityReport = Join-Path $projectRoot "docs\release-security-report.md"
    if (Test-Path -LiteralPath $securityReport) {
        Copy-Item -LiteralPath $securityReport -Destination (Join-Path $releaseRoot "SECURITY-SCAN.md") -Force
    }
    Write-Host "Release artifact: $final"
    Write-Host "SHA-256: $hash"
} else {
    Write-Host "Application image: $final"
}

