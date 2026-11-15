# Installs ffmpeg with Vorbis support (libvorbis) on Windows, which PackObfuscator needs for
# junk sounds and for removing Vorbis tags.
#
#   powershell -ExecutionPolicy Bypass -File scripts\install-ffmpeg.ps1          install if missing
#   powershell -ExecutionPolicy Bypass -File scripts\install-ffmpeg.ps1 -Check   only check
param([switch]$Check)

$ErrorActionPreference = 'Stop'

function Test-Vorbis {
    $ff = Get-Command ffmpeg -ErrorAction SilentlyContinue
    if (-not $ff) { return $false }
    $encoders = & ffmpeg -hide_banner -encoders 2>$null | Out-String
    return $encoders -match 'libvorbis'
}

function Show-Result {
    if (Test-Vorbis) {
        Write-Host "OK: $((Get-Command ffmpeg).Source) supports libvorbis."
        & ffmpeg -hide_banner -version | Select-Object -First 1
        return $true
    }
    Write-Host "ffmpeg with libvorbis was not found on the PATH." -ForegroundColor Red
    return $false
}

if (Test-Vorbis) { Show-Result | Out-Null; exit 0 }
if ($Check) { if (Show-Result) { exit 0 } else { exit 1 } }

$installed = $false
if (Get-Command winget -ErrorAction SilentlyContinue) {
    winget install --id Gyan.FFmpeg --exact --accept-source-agreements --accept-package-agreements
    $installed = ($LASTEXITCODE -eq 0)
}
if (-not $installed -and (Get-Command choco -ErrorAction SilentlyContinue)) {
    choco install ffmpeg -y
    $installed = ($LASTEXITCODE -eq 0)
}
if (-not $installed -and (Get-Command scoop -ErrorAction SilentlyContinue)) {
    scoop install ffmpeg
    $installed = ($LASTEXITCODE -eq 0)
}
if (-not $installed) {
    Write-Host "Could not install ffmpeg. Install winget, Chocolatey or Scoop, or get a build from https://www.gyan.dev/ffmpeg/builds/ and add its bin folder to PATH." -ForegroundColor Red
    exit 1
}

# The PATH is not refreshed in this window, so pick up the new install from the registry.
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
if (Show-Result) {
    Write-Host "Open a new terminal before running PackObfuscator, so it sees the updated PATH."
    exit 0
}
exit 1
