# mkit launcher for PowerShell: ./mkit run
if (-not (Get-Command mkit.exe -ErrorAction SilentlyContinue)) {
    Write-Error 'mkit is not installed. Install it with: uv tool install -e PATH_TO_MKIT_REPO'
    exit 9009
}
& mkit.exe --project $PSScriptRoot @args
exit $LASTEXITCODE
