@echo off
where mkit.exe >nul 2>nul || (echo mkit is not installed. Install it with: uv tool install -e PATH_TO_MKIT_REPO 1>&2 & exit /b 9009)
mkit.exe --project "%~dp0." %*
