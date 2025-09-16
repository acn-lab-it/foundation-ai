param()
$ErrorActionPreference = 'Stop'

# Run in repo root
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "[setup-githooks] Configuring core.hooksPath to .githooks"
& git config core.hooksPath .githooks

# Make pre-commit executable for Git Bash users (no-op on Windows NTFS flags but helps on Unix)
if (Get-Command wsl -ErrorAction SilentlyContinue) {
  try {
    wsl bash -lc "chmod +x .githooks/pre-commit"
  } catch { }
}

Write-Host "[setup-githooks] Done. Test with: git commit --allow-empty -m 'test'"
