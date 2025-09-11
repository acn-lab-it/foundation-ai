param(
  [string]$MongoUri = $env:MONGO_URI,
  [string]$DbName = $env:MONGO_DB
)

# Wrapper: call the Bash script via WSL
$wsl = (Get-Command wsl -ErrorAction SilentlyContinue)
if (-not $wsl) {
  Write-Error "WSL not found. Please install Windows Subsystem for Linux and ensure 'wsl' is in PATH. Alternatively run db/scripts/seed.sh directly in a Linux shell."
  exit 1
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$bashScriptWin = Join-Path $scriptDir "seed.sh"
if (-not (Test-Path $bashScriptWin)) {
  Write-Error "Bash script not found: $bashScriptWin"
  exit 1
}
$bashScriptWinEsc = $bashScriptWin -replace "\\","/"
$bashScriptWsl = (wsl wslpath -a "$bashScriptWinEsc").Trim()

$envArgs = @()
if ($MongoUri) { $envArgs += ("MONGO_URI='" + $MongoUri + "'") }
if ($DbName)   { $envArgs += ("MONGO_DB='"  + $DbName   + "'") }

$joinedEnv = ($envArgs -join ' ')
if ($joinedEnv) { $joinedEnv = "env $joinedEnv " }
$bashCmd = @"
$joinedEnv'$bashScriptWsl'
"@

Write-Host ("[INFO] PowerShell version: {0}" -f $PSVersionTable.PSVersion)

$bashCmd = $bashCmd -replace "`r", ""
wsl bash -lc "$bashCmd"
