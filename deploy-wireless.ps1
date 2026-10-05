<#
.SYNOPSIS
    Builds the GabAI Android app and installs it to connected wireless ADB device(s).

.DESCRIPTION
    Compiles the debug APK using Gradle and deploys it over ADB.
    Supports single device, interactive device selection, or deploying to all connected devices simultaneously.

.PARAMETER Device
    Specific ADB serial/ID, or shorthand ('a24', 'oppo', 'samsung').

.PARAMETER All
    Deploys the APK to all connected devices sequentially.

.PARAMETER Connect
    Optional IP:PORT address to connect to before building (e.g. 192.168.1.100:5555).

.PARAMETER NoLaunch
    Skip automatically launching MainActivity after installation.

.EXAMPLE
    .\deploy-wireless.ps1
    .\deploy-wireless.ps1 -Device A24
    .\deploy-wireless.ps1 -All
    .\deploy-wireless.ps1 -Connect 192.168.1.15:38821
#>

param(
    [string]$Device = "",
    [switch]$All,
    [string]$Connect = "",
    [switch]$NoLaunch
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   GabAI Wireless ADB Build & Deploy" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Connect to wireless ADB if IP:PORT provided
if ($Connect -ne "") {
    Write-Host "[*] Connecting to $Connect..." -ForegroundColor Yellow
    adb connect $Connect
}

# 2. Query attached ADB devices
$rawDevices = adb devices | Where-Object { $_ -match "\s+device$" }
if (-not $rawDevices) {
    Write-Host "[!] No devices connected via ADB." -ForegroundColor Red
    Write-Host "    Make sure Wireless Debugging is enabled on your phone and run:" -ForegroundColor Yellow
    Write-Host "    adb connect <phone-ip>:<port>" -ForegroundColor White
    exit 1
}

$deviceList = @()
foreach ($line in $rawDevices) {
    $parts = $line -split "\s+"
    $id = $parts[0]
    $model = (adb -s $id shell getprop ro.product.model 2>$null).Trim()
    if (-not $model) { $model = "Unknown Device" }
    $deviceList += [PSCustomObject]@{
        Id    = $id
        Model = $model
    }
}

# 3. Determine target device(s)
$targetDevices = @()

if ($All) {
    $targetDevices = $deviceList
    Write-Host "[*] Targeting ALL connected devices ($($deviceList.Count) device(s))." -ForegroundColor Green
} elseif ($Device -ne "") {
    # Match by partial string (e.g. 'A24', 'CPH', or full serial ID)
    $matched = $deviceList | Where-Object { $_.Id -match [regex]::Escape($Device) -or $_.Model -match [regex]::Escape($Device) }
    if ($matched) {
        $targetDevices = @($matched)
    } else {
        Write-Host "[!] Could not find connected device matching '$Device'." -ForegroundColor Red
        Write-Host "Connected devices:"
        $deviceList | ForEach-Object { Write-Host " - $($_.Model) ($($_.Id))" }
        exit 1
    }
} else {
    if ($deviceList.Count -eq 1) {
        $targetDevices = @($deviceList[0])
    } else {
        Write-Host "`nMultiple devices detected. Please select a target:" -ForegroundColor Yellow
        for ($i = 0; $i -lt $deviceList.Count; $i++) {
            Write-Host "  [$($i + 1)] $($deviceList[$i].Model) ($($deviceList[$i].Id))" -ForegroundColor White
        }
        Write-Host "  [$($deviceList.Count + 1)] Deploy to ALL devices" -ForegroundColor Cyan

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

Write-Host "`nSelected Target(s):" -ForegroundColor Green
$targetDevices | ForEach-Object { Write-Host "  - $($_.Model) ($($_.Id))" -ForegroundColor Cyan }

# 4. Build Debug APK using Gradle
Write-Host "`n[*] Building debug APK with Gradle..." -ForegroundColor Yellow
$gradleCmd = ".\gradlew.bat"
if (-not (Test-Path $gradleCmd)) {
    $gradleCmd = "./gradlew"
}

$errorLogPath = Join-Path $PSScriptRoot "build-error.log"
$rawLogPath = Join-Path $PSScriptRoot "gradle-build.log"

if (Test-Path $errorLogPath) { Remove-Item $errorLogPath -Force }
if (Test-Path $rawLogPath) { Remove-Item $rawLogPath -Force }

$prevErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"

# Stream Gradle output live to terminal while capturing for error analysis
& $gradleCmd assembleDebug 2>&1 | Tee-Object -FilePath $rawLogPath
$buildExitCode = $LASTEXITCODE

$ErrorActionPreference = $prevErrorAction

if ($buildExitCode -ne 0) {
    Write-Host "`n==========================================" -ForegroundColor Red
    Write-Host "       [!] GRADLE BUILD FAILED [!]" -ForegroundColor Red
    Write-Host "==========================================" -ForegroundColor Red

    # Extract compiler errors, unresolved references, and failure details
    $extractedErrors = @()
    if (Test-Path $rawLogPath) {
        $allLines = Get-Content $rawLogPath
        $inFailureSection = $false

        foreach ($line in $allLines) {
            $trimmed = $line.Trim()
            if ($trimmed -match "^e:\s+" -or 
                $trimmed -match "(?i)error:" -or 
                $trimmed -match "(?i)compilation error" -or 
                $trimmed -match "(?i)unresolved reference" -or 
                $trimmed -match "^\*\s+What went wrong:" -or 
                $trimmed -match "^\*\s+Try:") {
                $extractedErrors += $trimmed
            } elseif ($trimmed -match "^FAILURE:") {
                $inFailureSection = $true
                $extractedErrors += $trimmed
            } elseif ($inFailureSection) {
                $extractedErrors += $trimmed
                if ($trimmed -match "^\*\s+Get more help at") {
                    $inFailureSection = $false
                }
            }
        }
    }

    # Write clean, structured diagnostic report for AI analysis
    $report = @()
    $report += "======================================================================"
    $report += "GABAI BUILD FAILURE DIAGNOSTIC REPORT"
    $report += "Timestamp : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    $report += "Exit Code : $buildExitCode"
    $report += "Command   : $gradleCmd assembleDebug"
    $report += "======================================================================"
    $report += ""
    $report += "--- EXTRACTED COMPILER & BUILD ERRORS ---"
    if ($extractedErrors.Count -gt 0) {
        $report += $extractedErrors
    } else {
        $report += "(No specific regex matches found. See full log below.)"
    }
    $report += ""
    $report += "--- FULL BUILD LOG OUTPUT ---"
    if (Test-Path $rawLogPath) {
        $report += Get-Content $rawLogPath
    }

    $report | Out-File -FilePath $errorLogPath -Encoding utf8

    Write-Host "`n[!] Error report saved to: $errorLogPath" -ForegroundColor Yellow
    Write-Host "[!] Extracted Error Summary:" -ForegroundColor Yellow
    if ($extractedErrors.Count -gt 0) {
        $extractedErrors | Select-Object -First 15 | ForEach-Object { Write-Host "   $_" -ForegroundColor Red }
        if ($extractedErrors.Count -gt 15) {
            Write-Host "   ... and $($extractedErrors.Count - 15) more lines (see build-error.log)" -ForegroundColor DarkGray
        }
    } else {
        Write-Host "   (Check build-error.log for full output)" -ForegroundColor Red
    }

    Write-Host "`n--> TIP: Tell Antigravity: 'Build failed, please check build-error.log and fix it.'" -ForegroundColor Cyan
    Write-Host "==========================================`n" -ForegroundColor Red

    exit $buildExitCode
} else {
    # Clean up raw log on successful build
    if (Test-Path $rawLogPath) { Remove-Item $rawLogPath -Force }
}

# 5. Find generated debug APK
$apk = Get-ChildItem -Path "app\build\outputs\apk\debug" -Filter "*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) {
    Write-Host "[!] Could not locate compiled APK in app\build\outputs\apk\debug\" -ForegroundColor Red
    exit 1
}

Write-Host "[+] Found compiled APK: $($apk.Name) ($([math]::Round($apk.Length / 1MB, 2)) MB)" -ForegroundColor Green

# 6. Install to target device(s)
$package = "com.example.gabai"
$activity = "$package/.MainActivity"

foreach ($target in $targetDevices) {
    Write-Host "`n[*] Installing to $($target.Model) ($($target.Id))..." -ForegroundColor Yellow
    # -r: replace existing application
    # -d: allow version code downgrade if needed
    adb -s $target.Id install -r -d $apk.FullName

    if ($LASTEXITCODE -ne 0) {
        Write-Host "[!] Installation failed on $($target.Model)!" -ForegroundColor Red
    } else {
        Write-Host "[OK] Installed successfully on $($target.Model)!" -ForegroundColor Green

        if (-not $NoLaunch) {
            Write-Host "[*] Launching $package..." -ForegroundColor Cyan
            adb -s $target.Id shell am start -n $activity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER | Out-Null
            Write-Host "[OK] App launched!" -ForegroundColor Green
        }
    }
}

Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host "       Deployment Complete!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
