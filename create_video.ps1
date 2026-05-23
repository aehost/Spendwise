# ============================================================
#  SpendWise Video Creator ? Automated Setup & Launcher
#  Double-click this file or run in PowerShell terminal
#  Output: SpendWise_Marketing_Video.mp4 (same folder)
# ============================================================
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  SpendWise Automated Video Creator" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# ?? 1. CHECK PYTHON ??????????????????????????????????????????
Write-Host "[1/5] Checking Python..." -ForegroundColor Yellow
$pythonCmd = $null
foreach ($cmd in @("python", "python3", "py")) {
    try {
        $ver = & $cmd --version 2>&1
        if ($ver -match "Python 3\.(8|9|10|11|12)") {
            $pythonCmd = $cmd
            Write-Host "      Found: $ver" -ForegroundColor Green
            break
        }
    } catch {}
}

if (!$pythonCmd) {
    Write-Host "      Python 3.8+ not found. Downloading Python 3.11..." -ForegroundColor Yellow
    $url = "https://www.python.org/ftp/python/3.11.9/python-3.11.9-amd64.exe"
    $inst = "$env:TEMP\python-installer.exe"
    Write-Host "      Downloading from python.org..." -ForegroundColor Gray
    Invoke-WebRequest -Uri $url -OutFile $inst -UseBasicParsing
    Write-Host "      Installing Python (this takes ~1 minute)..." -ForegroundColor Gray
    Start-Process -FilePath $inst -ArgumentList "/quiet InstallAllUsers=0 PrependPath=1 Include_pip=1" -Wait
    $env:PATH = "$env:LOCALAPPDATA\Programs\Python\Python311;$env:LOCALAPPDATA\Programs\Python\Python311\Scripts;$env:PATH"
    $pythonCmd = "python"
    Write-Host "      Python installed." -ForegroundColor Green
}

# ?? 2. INSTALL PIP PACKAGES ??????????????????????????????????
Write-Host ""
Write-Host "[2/5] Installing Python packages..." -ForegroundColor Yellow
Write-Host "      (playwright, edge-tts, numpy, requests - may take 2-3 minutes first run)" -ForegroundColor Gray

$packages = @("playwright", "edge-tts", "numpy", "requests")
foreach ($pkg in $packages) {
    Write-Host "      Installing $pkg..." -ForegroundColor Gray
    & $pythonCmd -m pip install $pkg -q --disable-pip-version-check 2>&1 | Out-Null
}

# Install Playwright Chromium browser
Write-Host "      Installing Playwright Chromium browser..." -ForegroundColor Gray
& $pythonCmd -m playwright install chromium 2>&1 | Out-Null
Write-Host "      Packages ready." -ForegroundColor Green

# ?? 3. GET FFMPEG ?????????????????????????????????????????????
Write-Host ""
Write-Host "[3/5] Checking ffmpeg..." -ForegroundColor Yellow

$FfmpegDir  = Join-Path $ScriptDir "ffmpeg_bin"
$FfmpegExe  = Join-Path $FfmpegDir "ffmpeg.exe"

# First check if ffmpeg is already on PATH
$sysffmpeg = (Get-Command ffmpeg -ErrorAction SilentlyContinue)
if ($sysffmpeg) {
    $FfmpegExe = $sysffmpeg.Source
    Write-Host "      Found system ffmpeg: $FfmpegExe" -ForegroundColor Green
} elseif (Test-Path $FfmpegExe) {
    Write-Host "      Found local ffmpeg." -ForegroundColor Green
} else {
    Write-Host "      ffmpeg not found. Downloading..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Force -Path $FfmpegDir | Out-Null

    $ZipUrl  = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip"
    $ZipPath = "$env:TEMP\ffmpeg_spendwise.zip"
    $ExtPath = "$env:TEMP\ffmpeg_spendwise_extract"

    Write-Host "      Downloading ffmpeg (~80MB)..." -ForegroundColor Gray
    Invoke-WebRequest -Uri $ZipUrl -OutFile $ZipPath -UseBasicParsing

    Write-Host "      Extracting..." -ForegroundColor Gray
    if (Test-Path $ExtPath) { Remove-Item $ExtPath -Recurse -Force }
    Expand-Archive -Path $ZipPath -DestinationPath $ExtPath -Force

    # Find ffmpeg.exe inside extracted folders
    $ffExe = Get-ChildItem $ExtPath -Recurse -Filter "ffmpeg.exe" | Select-Object -First 1
    if (!$ffExe) { Write-Host "ERROR: Could not find ffmpeg.exe in download." -ForegroundColor Red; exit 1 }
    Copy-Item $ffExe.FullName -Destination $FfmpegExe -Force

    $ffProbe = Get-ChildItem $ExtPath -Recurse -Filter "ffprobe.exe" | Select-Object -First 1
    if ($ffProbe) { Copy-Item $ffProbe.FullName -Destination (Join-Path $FfmpegDir "ffprobe.exe") -Force }

    Remove-Item $ZipPath -Force -ErrorAction SilentlyContinue
    Remove-Item $ExtPath -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "      ffmpeg installed." -ForegroundColor Green
}

# ?? 4. VERIFY ASSETS EXIST ????????????????????????????????????
Write-Host ""
Write-Host "[4/5] Verifying assets..." -ForegroundColor Yellow
$htmlFile = Join-Path $ScriptDir "marketing-demo.html"
if (!(Test-Path $htmlFile)) {
    Write-Host "ERROR: marketing-demo.html not found in $ScriptDir" -ForegroundColor Red
    Write-Host "       Make sure you run this script from the Spendwise folder." -ForegroundColor Red
    exit 1
}
Write-Host "      marketing-demo.html found." -ForegroundColor Green

# ?? 5. RUN VIDEO CREATOR ??????????????????????????????????????
Write-Host ""
Write-Host "[5/5] Launching video creator..." -ForegroundColor Yellow
Write-Host "      This will take 5-10 minutes. Do not close this window." -ForegroundColor Gray
Write-Host ""

$pyScript = Join-Path $ScriptDir "create_video.py"
& $pythonCmd $pyScript --ffmpeg $FfmpegExe

if ($LASTEXITCODE -eq 0) {
    $output = Join-Path $ScriptDir "SpendWise_Marketing_Video.mp4"
    Write-Host ""
    Write-Host "============================================" -ForegroundColor Green
    Write-Host "  VIDEO READY!" -ForegroundColor Green
    Write-Host "  $output" -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
    Write-Host ""
    # Open the folder in Explorer
    Start-Process explorer.exe -ArgumentList "/select,`"$output`""
} else {
    Write-Host ""
    Write-Host "Video creation failed. Check the error above." -ForegroundColor Red
}

Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
