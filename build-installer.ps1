# Builds GrubEast-1.0.0.exe (a normal Windows installer) with Java's jpackage tool.
#
#   .\build-installer.ps1              -> installer (needs WiX Toolset 3.14 installed)
#   .\build-installer.ps1 -Type app-image -> a portable folder (no WiX needed)

param([string]$Type = "exe")
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage was not found. Install a full JDK (21 or newer) and make sure its bin folder is on PATH."
}

Write-Host "1/3  Building grubeast.jar ..."
.\mvnw.cmd -q clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "The Maven build failed." }

if ($Type -eq "exe" -and -not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    Write-Warning "WiX Toolset was not found, so Windows cannot build an installer (.exe)."
    Write-Warning "Building a portable app folder instead. Install WiX 3.14 and run this again for the installer."
    $Type = "app-image"
}

Write-Host "2/3  Preparing files ..."
Remove-Item -Recurse -Force build-input, installer -ErrorAction SilentlyContinue
New-Item -ItemType Directory build-input | Out-Null
Copy-Item target\grubeast.jar build-input\

Write-Host "3/3  Packaging ($Type) ..."
$jpackageArgs = @(
    "--type", $Type,
    "--name", "GrubEast",
    "--app-version", "1.0.0",
    "--vendor", "Grub East",
    "--description", "Grub East - student marketplace",
    "--input", "build-input",
    "--main-jar", "grubeast.jar",
    "--main-class", "com.grubeast.Launcher",
    "--dest", "installer",
    "--add-modules", "java.base,java.desktop,java.sql,java.naming,java.logging,java.management,java.xml,java.security.jgss,java.prefs,jdk.unsupported,jdk.crypto.ec",
    "--java-options", "--enable-native-access=ALL-UNNAMED"
)
if ($Type -eq "exe") { $jpackageArgs += @("--win-menu", "--win-shortcut", "--win-dir-chooser") }
if (Test-Path "icon.ico") { $jpackageArgs += @("--icon", "icon.ico") }

& jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage failed." }

Write-Host ""
Write-Host "Done. Look in the 'installer' folder."
