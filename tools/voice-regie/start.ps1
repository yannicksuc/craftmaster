<#
.SYNOPSIS
  Starts the CraftMaster voice control service (régie vocale) on http://127.0.0.1:25790.

.DESCRIPTION
  The Claude API key is read by the service from the ANTHROPIC_API_KEY environment variable (user variable:
  [Environment]::SetEnvironmentVariable('ANTHROPIC_API_KEY', '...', 'User'), then open a new terminal).
  This script never reads, prints or stores it. Without it, the service still transcribes but answers an error.

  Optional settings (environment variables): REGIE_PORT, REGIE_CLAUDE_MODEL (claude-opus-5-5),
  REGIE_CLAUDE_EFFORT (low), REGIE_CLAUDE_FAST (1 = fast mode), REGIE_WHISPER_MODEL / _DEVICE / _COMPUTE (auto),
  REGIE_IDLE_MINUTES (4). See README.md.

.EXAMPLE
  powershell -File tools\voice-regie\start.ps1
  powershell -File tools\voice-regie\start.ps1 -Check     # GPU / key / model check, then exit
#>
param([switch]$Check, [string]$Python = '')

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $Python) {
    $candidate = Join-Path $env:LOCALAPPDATA 'Programs\Python\Python313\python.exe'
    $Python = if (Test-Path $candidate) { $candidate } else { 'python' }
}
$env:PYTHONIOENCODING = 'utf-8'
if (-not (Test-Path Env:ANTHROPIC_API_KEY)) {
    Write-Warning "ANTHROPIC_API_KEY absente de cet environnement : la régie transcrira mais ne comprendra rien. Définis-la (variable utilisateur) puis ouvre un NOUVEAU terminal."
}
if ($Check) {
    & $Python -c "import os; from regie.stt import gpu_status; print('GPU:', gpu_status()); print('ANTHROPIC_API_KEY set:', bool(os.environ.get('ANTHROPIC_API_KEY')))"
    return
}
Write-Host 'Régie vocale CraftMaster : http://127.0.0.1:25790  (Ctrl+C pour arrêter)'
& $Python -m regie.service
