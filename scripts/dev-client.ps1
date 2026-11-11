<#
.SYNOPSIS
  Starts the dev client in the background, joined to the dev server, without disturbing whoever uses the PC.

.DESCRIPTION
  - windowed, sound muted (run/options.txt);
  - the window opens without the focus (SDL hint, see build.gradle.kts) and is pushed behind the other windows;
  - joins localhost:25691 (start the server first: .\gradlew.bat runServer);
  - -Second starts the second player (Joueur2, run2/) instead of LordFinn (run/);
  - -Version 26.2 starts the Minecraft 26.2 client (run-26.2/, run2-26.2/) on the 26.2 dev server (localhost:25901);
  - -Size 1920x1080 opens the second player's window at that size (the broadcast picture at Twitch's size).
  The game is then seen and driven through MCP Fabric (docs/wiki/Tests.md).

.EXAMPLE
  powershell -File scripts\dev-client.ps1
#>
param([string]$Server = '', [switch]$Second, [switch]$Foreground, [string]$Size = '', [string]$Version = '26.3')

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$suffix = if ($Version -eq '26.3') { '' } else { "-$Version" }
if (-not $Server) { $Server = if ($Version -eq '26.3') { 'localhost:25691' } else { 'localhost:25901' } }
$runDir = Join-Path $root $(if ($Second) { "run2$suffix" } else { "run$suffix" })
New-Item -ItemType Directory -Force $runDir | Out-Null

# Windowed and silent, whatever the last session left in options.txt
$options = Join-Path $runDir 'options.txt'
$lines = if (Test-Path $options) { Get-Content $options } else { @() }
$wanted = @{ 'fullscreen' = 'false'; 'soundCategory_master' = '0.0'; 'pauseOnLostFocus' = 'false'; 'onboardAccessibility' = 'false' }
foreach ($k in $wanted.Keys) {
    $lines = @($lines | Where-Object { $_ -notmatch "^$k`:" }) + "${k}:$($wanted[$k])"
}
Set-Content -Path $options -Value $lines -Encoding ASCII

Add-Type @'
using System; using System.Runtime.InteropServices;
namespace DevLaunch { public static class Win32 {
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint flags);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
} }
'@ -ErrorAction SilentlyContinue

$previous = [DevLaunch.Win32]::GetForegroundWindow()
$started = Get-Date
$log = Join-Path $runDir 'client-launch.log'
$gradleArgs = @(":${Version}:$(if ($Second) { 'runClient2' } else { 'runClient' })", '--console=plain', "-PquickPlayMultiplayer=$Server")
if (-not $Foreground) { $gradleArgs += '-PbackgroundClient' }
if ($Size) { $gradleArgs += "-PwindowSize=$Size" }
Start-Process -FilePath (Join-Path $root 'gradlew.bat') -ArgumentList $gradleArgs -WorkingDirectory $root `
    -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError "$log.err"
Write-Host "Client starting (log: $log)"
if ($Foreground) { return }

# Push the game window behind the others as soon as it exists
$deadline = (Get-Date).AddMinutes(5)
while ((Get-Date) -lt $deadline) {
    $client = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object { $_.CommandLine -match 'fabric\.dli\.env=client' -and $_.CommandLine -match [regex]::Escape($root) -and $_.CommandLine -match 'loom-cache' } |
        Where-Object { (Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue).StartTime -gt $started }
    foreach ($p in @($client)) {
        $handle = (Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue).MainWindowHandle
        if ($handle -and $handle -ne [IntPtr]::Zero) {
            [DevLaunch.Win32]::SetWindowPos($handle, [IntPtr]1, 0, 0, 0, 0, 0x13) | Out-Null   # HWND_BOTTOM, no move/size/activate
            if ($previous -ne [IntPtr]::Zero -and [DevLaunch.Win32]::GetForegroundWindow() -eq $handle) {
                [DevLaunch.Win32]::SetForegroundWindow($previous) | Out-Null
            }
            Write-Host "Client window sent to the back."
            return
        }
    }
    Start-Sleep -Milliseconds 500
}
Write-Warning "The client window did not show up within 5 minutes."
