<#
.SYNOPSIS
    Installs the built GabAI Android APK to connected phone(s) over ADB (USB or Wi-Fi).

.DESCRIPTION
    Installs the latest compiled APK from app\build\outputs\apk\debug\ without requiring a full rebuild.
    Supports single device selection, shorthand names ('a24', 'redmi'), or installing to all devices at once.

.PARAMETER Device
    Specific ADB serial/ID, or shorthand ('a24', 'redmi', 'samsung', 'xiaomi').

.PARAMETER All
    Deploys the APK to all connected devices.

.PARAMETER Connect
    Optional IP:PORT address to connect to before installing (e.g. 192.168.1.58:38363).

.PARAMETER Apk
    Custom path to an APK file. Defaults to the latest in app\build\outputs\apk\debug\.

.PARAMETER Build
    Optional switch to run './gradlew.bat assembleDebug' before installing.

.PARAMETER NoLaunch
    Skip automatically launching the app after installation.

.EXAMPLE
    .\install-app.ps1
    .\install-app.ps1 -Device a24
    .\install-app.ps1 -Device redmi
    .\install-app.ps1 -All
    .\install-app.ps1 -Connect 192.168.1.58:38363
    .\install-app.ps1 -Build
#>

param(
    [string]$Device = "",
    [switch]$All,
    [string]$Connect = "",
    [string]$Apk = "",
    [switch]$Build,
    [switch]$NoLaunch
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "       GabAI Phone Installer (ADB)        " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Locate ADB executable
$adbPath = $null
if (Get-Command adb -ErrorAction SilentlyContinue) {
    $adbPath = (Get-Command adb).Source
} else {
    # Check WinGet installation path
    $wingetAdb = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter "adb.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($wingetAdb) {
        $adbPath = $wingetAdb.FullName
    } else {
        # Check local brain scratch fallback
        $scratchAdb = Get-ChildItem "C:\Users\plaza\.gemini\antigravity\brain" -Recurse -Filter "adb.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($scratchAdb) {
            $adbPath = $scratchAdb.FullName
        }
    }
}

if (-not $adbPath) {
    Write-Host "[!] ADB was not found on your system." -ForegroundColor Red
    Write-Host "    Please ensure Android SDK Platform-Tools is installed." -ForegroundColor Yellow
    exit 1
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments=$true)]$ArgsList)
    & $adbPath @ArgsList
}

# 2. Optional: Rebuild APK if -Build switch provided
if ($Build) {
    Write-Host "`n[*] Building debug APK with Gradle..." -ForegroundColor Yellow
    $gradleCmd = ".\gradlew.bat"
    if (-not (Test-Path $gradleCmd)) { $gradleCmd = "./gradlew" }
    & $gradleCmd assembleDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[!] Gradle build failed!" -ForegroundColor Red
        exit 1
    }
}

# 3. Locate the compiled APK
$targetApk = $null
if ($Apk -ne "") {
    if (Test-Path $Apk) {
        $targetApk = Get-Item $Apk
    } else {
        Write-Host "[!] Specified APK path does not exist: $Apk" -ForegroundColor Red
        exit 1
    }
} else {
    $searchDir = Join-Path $PSScriptRoot "app\build\outputs\apk\debug"
    if (Test-Path $searchDir) {
        $targetApk = Get-ChildItem -Path $searchDir -Filter "*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    }
    if (-not $targetApk) {
        # Check repository root fallback
        $targetApk = Get-ChildItem -Path $PSScriptRoot -Filter "GabAI-*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    }
}

if (-not $targetApk) {
    Write-Host "[!] No compiled APK found in app\build\outputs\apk\debug\" -ForegroundColor Red
    Write-Host "    Run '.\install-app.ps1 -Build' to compile the app first." -ForegroundColor Yellow
    exit 1
}

$apkMb = [math]::Round($targetApk.Length / 1MB, 2)
Write-Host "[+] Found APK: $($targetApk.Name) ($apkMb MB)" -ForegroundColor Green
Write-Host "    Modified:  $($targetApk.LastWriteTime)" -ForegroundColor DarkGray

# 4. Connect to wireless ADB if specified
if ($Connect -ne "") {
    Write-Host "`n[*] Connecting to $Connect..." -ForegroundColor Yellow
    Invoke-Adb connect $Connect
}

# 5. Query attached devices
$rawLines = (Invoke-Adb devices) | Where-Object { $_ -match "\s+device$" }
if (-not $rawLines) {
    Write-Host "`n[!] No devices currently connected via ADB." -ForegroundColor Yellow
    Write-Host "    Select a connection shortcut below or enter IP:PORT:" -ForegroundColor Cyan
    Write-Host "    [1] Galaxy A24    (192.168.1.42)" -ForegroundColor White
    Write-Host "    [2] Redmi Note 8  (192.168.1.58)" -ForegroundColor White
    Write-Host "    [3] Enter custom IP:PORT" -ForegroundColor White
    Write-Host "    [4] Exit" -ForegroundColor DarkGray

    $connChoice = Read-Host "`nEnter selection (1-4)"
    switch ($connChoice) {
        "1" {
            $port = Read-Host "Enter port for Galaxy A24 (or press Enter for default 5555)"
            if (-not $port) { $port = "5555" }
            Invoke-Adb connect "192.168.1.42:$port"
        }
        "2" {
            $port = Read-Host "Enter port for Redmi Note 8 (or press Enter for default 5555)"
            if (-not $port) { $port = "5555" }
            Invoke-Adb connect "192.168.1.58:$port"
        }
        "3" {
            $customIp = Read-Host "Enter <IP:PORT>"
            if ($customIp) { Invoke-Adb connect $customIp }
        }
        default {
            Write-Host "Exiting." -ForegroundColor Yellow
            exit 0
        }
    }
    $rawLines = (Invoke-Adb devices) | Where-Object { $_ -match "\s+device$" }
    if (-not $rawLines) {
        Write-Host "[!] Could not establish device connection. Please verify Wireless Debugging is on." -ForegroundColor Red
        exit 1
    }
}

$deviceList = @()
foreach ($line in $rawLines) {
    $parts = $line -split "\s+"
    $id = $parts[0]
    $model = (Invoke-Adb -s $id shell getprop ro.product.model 2>$null)
    if ($model) { $model = $model.Trim() } else { $model = "Unknown Device" }
    $deviceList += [PSCustomObject]@{
        Id    = $id
        Model = $model
    }
}

# 6. Determine target device(s)
$targetDevices = @()

if ($All) {
    $targetDevices = $deviceList
    Write-Host "`n[*] Targeting ALL connected devices ($($deviceList.Count) device(s))." -ForegroundColor Green
} elseif ($Device -ne "") {
    # Match shorthands: 'a24', 'redmi', 'samsung', or partial string
    $devLower = $Device.ToLower()
    $matched = $deviceList | Where-Object {
        $_.Id.ToLower().Contains($devLower) -or
        $_.Model.ToLower().Contains($devLower) -or
        ($devLower -eq "a24" -and ($_.Model -match "A24" -or $_.Id -match "192.168.1.42")) -or
        ($devLower -eq "redmi" -and ($_.Model -match "Redmi" -or $_.Id -match "192.168.1.58"))
    }
    if ($matched) {
        $targetDevices = @($matched)
    } else {
        Write-Host "[!] No connected device matched '$Device'." -ForegroundColor Red
        Write-Host "Connected devices:"
        $deviceList | ForEach-Object { Write-Host " - $($_.Model) ($($_.Id))" }
        exit 1
    }
} else {
    if ($deviceList.Count -eq 1) {
        $targetDevices = @($deviceList[0])
    } else {
        Write-Host "`nMultiple devices detected. Select a target:" -ForegroundColor Yellow
        for ($i = 0; $i -lt $deviceList.Count; $i++) {
            Write-Host "  [$($i + 1)] $($deviceList[$i].Model) ($($deviceList[$i].Id))" -ForegroundColor White
        }
        Write-Host "  [$($deviceList.Count + 1)] Install to ALL devices" -ForegroundColor Cyan

        $choice = Read-Host "`nEnter selection (default: 1)"
        if (-not $choice) { $choice = "1" }
        $choiceInt = [int]$choice

        if ($choiceInt -eq ($deviceList.Count + 1)) {
            $targetDevices = $deviceList
        } elseif ($choiceInt -ge 1 -and $choiceInt -le $deviceList.Count) {
            $targetDevices = @($deviceList[$choiceInt - 1])
        } else {
            Write-Host "[!] Invalid selection." -ForegroundColor Red
            exit 1
        }
    }
}

Write-Host "`nTarget Device(s):" -ForegroundColor Green
$targetDevices | ForEach-Object { Write-Host "  • $($_.Model) ($($_.Id))" -ForegroundColor Cyan }

# 7. Install and launch on target devices
$package = "com.example.gabai"
$launcherActivity = "$package/.SplashActivity"

foreach ($target in $targetDevices) {
    Write-Host "`n[*] Installing to $($target.Model) ($($target.Id))..." -ForegroundColor Yellow

    # -r: replace existing application
    # -d: allow version code downgrade if needed
    $installOutput = Invoke-Adb -s $target.Id install -r -d $targetApk.FullName 2>&1
    $installSuccess = ($installOutput -match "Success")

    if ($installSuccess) {
        Write-Host "[OK] Successfully installed on $($target.Model)!" -ForegroundColor Green

        if (-not $NoLaunch) {
            Write-Host "[*] Launching GabAI..." -ForegroundColor Cyan
            # Launch via SplashActivity (the declared exported launcher in AndroidManifest)
            Invoke-Adb -s $target.Id shell am start -n $launcherActivity | Out-Null
            if ($LASTEXITCODE -ne 0) {
                # Fallback via monkey launcher intent
                Invoke-Adb -s $target.Id shell monkey -p $package -c android.intent.category.LAUNCHER 1 | Out-Null
            }
            Write-Host "[OK] App launched!" -ForegroundColor Green
        }
    } else {
        Write-Host "[!] Installation failed on $($target.Model):" -ForegroundColor Red
        $installOutput | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }

        if ($installOutput -match "INSTALL_FAILED_USER_RESTRICTED") {
            Write-Host "`n[TIP] Xiaomi/MIUI security blocked the silent install:" -ForegroundColor Yellow
            Write-Host "      1. In Developer Options, enable 'Install via USB'" -ForegroundColor Yellow
            Write-Host "      2. Or tap 'Install' on the phone screen popup when it appears." -ForegroundColor Yellow
        }
    }
}

Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host "           Done!                          " -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
