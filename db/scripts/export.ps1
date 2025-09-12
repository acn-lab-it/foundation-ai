param(
  [string]$MongoUri = $env:MONGO_URI,
  [string]$DbName = $env:MONGO_DB,
  [string[]]$Collections
)

# Wrapper: call the Bash script via WSL
# Requirements: WSL installed, bash available, and mongosh installed inside the WSL environment.

# Check WSL availability
$wsl = (Get-Command wsl -ErrorAction SilentlyContinue)
if (-not $wsl) {
  Write-Error "WSL not found. Please install Windows Subsystem for Linux and ensure 'wsl' is in PATH. Alternatively run db/scripts/export.sh directly in a Linux shell."
  exit 1
}

# Resolve repo root path and convert to WSL path
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$bashScriptWin = Join-Path $scriptDir "export.sh"
if (-not (Test-Path $bashScriptWin)) {
  Write-Error "Bash script not found: $bashScriptWin"
  exit 1
}
# Convert Windows path to WSL path. Need to escape backslashes before sending to wslpath.
$bashScriptWinEsc = $bashScriptWin -replace "\\","/"
$bashScriptWsl = (wsl wslpath -a "$bashScriptWinEsc").Trim()

# Build argument list for collections
$collArgs = @()
if ($Collections -and $Collections.Count -gt 0) {
  $collArgs = $Collections
  if ($collArgs.Count -eq 1 -and $collArgs[0] -like "*,*") {
    $collArgs = $collArgs[0].Split(',')
  }
}

# Pass environment variables into WSL for Mongo URI and DB
$envArgs = @()
if ($MongoUri) { $envArgs += ("MONGO_URI='" + $MongoUri + "'") }
if ($DbName)   { $envArgs += ("MONGO_DB='"  + $DbName   + "'") }

# Compose bash -lc argument
$joinedEnv = ($envArgs -join ' ')
$joinedCols = ($collArgs -join ' ')
if ($joinedEnv) { $joinedEnv = "env $joinedEnv " }
# Build the bash command as a here-string to avoid PS parsing issues
$bashCmd = @"
$joinedEnv'$bashScriptWsl' $joinedCols
"@

# PowerShell version note
Write-Host ("[INFO] PowerShell version: {0}" -f $PSVersionTable.PSVersion)

# Normalize line endings and execute
$bashCmd = $bashCmd -replace "`r", ""
wsl bash -lc "$bashCmd"
