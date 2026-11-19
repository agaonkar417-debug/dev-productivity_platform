# DevFlow AI one-command start for Windows.
# Easiest: double-click start.bat (it runs this script).
#
# What it does:
#   1. Installs Docker Desktop and Java 21 with winget if they are missing
#      (Windows asks for administrator permission; click Yes)
#   2. Starts Docker Desktop if it is not running
#   3. Creates .env with a random database password (first run only)
#   4. Builds and starts the database and app containers
#   5. Waits until the app is healthy and opens it in your browser
param([switch]$NoJava)

$ErrorActionPreference = 'Continue'   # native tools (docker, winget) write progress to stderr
Set-Location -Path $PSScriptRoot

function Say($msg)  { Write-Host "==> $msg" -ForegroundColor Cyan }
function Ok($msg)   { Write-Host " ok $msg" -ForegroundColor Green }
function Fail($msg) { Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }
function Have($cmd) { return [bool](Get-Command $cmd -ErrorAction SilentlyContinue) }
function Refresh-Path {
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' +
                [Environment]::GetEnvironmentVariable('Path', 'User')
}
function Docker-Running {
    docker info *> $null
    return ($LASTEXITCODE -eq 0)
}
function Port-Free([int]$p) {
    try {
        $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $p)
        $listener.Start()
        $listener.Stop()
        return $true
    } catch {
        return $false
    }
}
function Set-EnvValue($name, $value) {
    # Replaces "NAME=..." or a commented "# NAME=..." line in .env, or appends one
    $path = Join-Path $PSScriptRoot '.env'
    $out = New-Object System.Collections.Generic.List[string]
    $found = $false
    foreach ($line in (Get-Content $path)) {
        if (-not $found -and $line -match "^#?\s*$name=") { $out.Add("$name=$value"); $found = $true }
        else { $out.Add($line) }
    }
    if (-not $found) { $out.Add("$name=$value") }
    [System.IO.File]::WriteAllLines($path, $out.ToArray(), (New-Object System.Text.ASCIIEncoding))
}
function Winget-Install($id, $label) {
    if (-not (Have 'winget')) {
        Fail "winget is not available. Install 'App Installer' from the Microsoft Store, or install $label manually, then run start.bat again."
    }
    Say "Installing $label with winget (click Yes if Windows asks for permission)..."
    winget install -e --id $id --accept-package-agreements --accept-source-agreements --silent
    Refresh-Path
}

# ------------------------------------------------------------------ 1. Docker Desktop
$dockerExe = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
if (-not (Have 'docker') -and -not (Test-Path $dockerExe)) {
    # Docker Desktop needs WSL 2 (Windows Subsystem for Linux)
    wsl --status *> $null
    if ($LASTEXITCODE -ne 0) {
        Say 'Enabling WSL 2, which Docker Desktop needs (click Yes if Windows asks)...'
        Start-Process -FilePath 'wsl.exe' -ArgumentList '--install', '--no-distribution' -Verb RunAs -Wait
        Write-Host ''
        Write-Host 'WSL was installed. Please RESTART your computer, then double-click start.bat again.' -ForegroundColor Yellow
        exit 0
    }
    Winget-Install 'Docker.DockerDesktop' 'Docker Desktop'
}
Refresh-Path
if (-not (Have 'docker')) {
    $env:Path += ';' + (Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin')
}
if (-not (Have 'docker')) { Fail 'Docker was installed but cannot be found yet. Restart your computer, then run start.bat again.' }
Ok 'Docker installed'

# ------------------------------------------------------------------ 1b. Java 21 (for development and tests)
if (-not $NoJava) {
    $javaOk = $false
    if (Have 'java') {
        $ver = (& java -version 2>&1 | Out-String)
        if ($ver -match 'version "(\d+)') { $javaOk = ([int]$Matches[1] -ge 21) }
    }
    if ($javaOk) { Ok 'Java 21+ installed' }
    else {
        Winget-Install 'EclipseAdoptium.Temurin.21.JDK' 'Java 21 (Temurin)'
    }
}

# ------------------------------------------------------------------ 2. Start Docker Desktop
if (-not (Docker-Running)) {
    Say 'Starting Docker Desktop (the first start can take a minute or two)...'
    if (Test-Path $dockerExe) { Start-Process -FilePath $dockerExe }
    for ($i = 0; $i -lt 90 -and -not (Docker-Running); $i++) { Start-Sleep -Seconds 2 }
}
if (-not (Docker-Running)) {
    Fail 'Docker Desktop is not running yet. If this is the first time, accept its terms in the Docker window (and restart if it asks), then run start.bat again.'
}
Ok 'Docker is running'

# ------------------------------------------------------------------ 3. .env
if (-not (Test-Path '.env')) {
    Say 'Creating .env with a random database password...'
    $bytes = New-Object byte[] 24
    $rng = [System.Security.Cryptography.RNGCryptoServiceProvider]::new()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $pw = ($bytes | ForEach-Object { $_.ToString('x2') }) -join ''
    $lines = Get-Content '.env.example' | ForEach-Object {
        if ($_ -match '^DB_PASSWORD=') { "DB_PASSWORD=$pw" }
        elseif ($_ -match '^POSTGRES_PASSWORD=') { "POSTGRES_PASSWORD=$pw" }
        else { $_ }
    }
    # ASCII without BOM so both Docker Compose and the Java app read it correctly
    [System.IO.File]::WriteAllLines((Join-Path $PSScriptRoot '.env'), [string[]]$lines, (New-Object System.Text.ASCIIEncoding))
    Remove-Variable pw
    Ok '.env created (git-ignored; the password is never shown)'
} else {
    Ok '.env already exists, keeping it'
}
$port = 8080
$portLine = Select-String -Path '.env' -Pattern '^PORT=(\d+)' | Select-Object -First 1
if ($portLine) { $port = [int]$portLine.Matches[0].Groups[1].Value }

# A port can be used by another program or reserved by Windows (Hyper-V/WSL reserve ranges).
# Test by actually opening it; if it fails, pick the next free port and remember it in .env.
$appRunning = [bool](docker compose ps --status running -q app 2>$null)
if (-not $appRunning -and -not (Port-Free $port)) {
    $newPort = $null
    foreach ($p in 8081..8099) {
        if (Port-Free $p) { $newPort = $p; break }
    }
    if (-not $newPort) { Fail "Ports $port and 8081-8099 are all in use. Set a free PORT=... in .env and run start.bat again." }
    Say "Port $port is in use or reserved by Windows, so DevFlow AI will use port $newPort."
    Set-EnvValue 'PORT' $newPort
    $port = $newPort
}

# ------------------------------------------------------------------ 4. Build and start
Say 'Building and starting DevFlow AI (the first build downloads dependencies and takes a few minutes)...'
docker compose up -d --build
if ($LASTEXITCODE -ne 0) { Fail 'docker compose failed. See the messages above.' }

# ------------------------------------------------------------------ 5. Wait for health
$url = "http://localhost:$port"
Say 'Waiting for the app to become healthy...'
for ($i = 0; $i -lt 120; $i++) {
    try {
        $h = Invoke-RestMethod -Uri "$url/actuator/health" -TimeoutSec 3 -UseBasicParsing
        if ($h.status -eq 'UP') {
            Ok "DevFlow AI is running at $url"
            Start-Process $url
            Write-Host ''
            Write-Host 'Stop it with stop.bat (your kits and history are kept).'
            exit 0
        }
    } catch { }
    Start-Sleep -Seconds 2
}
docker compose logs --tail 40 app
Fail 'The app did not become healthy within 4 minutes. The log above shows why.'
