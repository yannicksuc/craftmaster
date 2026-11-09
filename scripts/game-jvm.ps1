# Lists (or with -Kill stops) this checkout's dev game JVMs. Usage: powershell -File scripts\game-jvm.ps1 client|server [-Kill]
param([string]$Kind, [switch]$Kill)
# Lists (or kills) this checkout's dev game JVMs: $Kind = client | server. Matched on <checkout>\.gradle\loom-cache:
# the bare checkout path would also match the agent checkouts under .claude\worktrees
$p = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match "fabric.dli.env=$Kind" -and $_.CommandLine -match [regex]::Escape((Join-Path (Split-Path -Parent $PSScriptRoot) '.gradle\loom-cache')) }
foreach ($x in @($p)) { if ($x) { if ($Kill) { Stop-Process -Id $x.ProcessId -Force } ; $x.ProcessId } }
