@echo off
setlocal
rem Starts the emulator and waits until it is actually usable.
rem
rem Cold boot every time is deliberate. A quick-boot snapshot restores the phone as it was, which
rem sounds like what you want and is how an install can survive in the snapshot while being absent
rem from disk. Booting cold costs a minute and means what is on the device is what was installed.
rem Pass "warm" if you would rather have the snapshot.

set "SDK=%LOCALAPPDATA%\Android\Sdk"
set "AVD=Pixel_10"
if not "%~1"=="" if not "%~1"=="warm" set "AVD=%~1"

if /I "%~1"=="warm" (
  set "BOOTMODE="
) else (
  set "BOOTMODE=-no-snapshot-load"
)

rem usebackq with backticks, because a plain for /f cannot cope with the quoted path to adb.
for /f "usebackq tokens=1" %%D in (`"%SDK%\platform-tools\adb.exe" devices ^| findstr /B emulator-`) do (
  echo Emulator already running: %%D
  goto :ready
)

echo Starting %AVD% ...
start "" /B "%SDK%\emulator\emulator.exe" -avd %AVD% %BOOTMODE% -netdelay none -netspeed full

:ready
"%SDK%\platform-tools\adb.exe" wait-for-device
echo Waiting for boot ...
:poll
for /f "delims=" %%B in ('"%SDK%\platform-tools\adb.exe" shell getprop sys.boot_completed 2^>nul') do set "BOOT=%%B"
if not "%BOOT%"=="1" (
  timeout /t 3 /nobreak >nul
  goto :poll
)
echo Ready.
"%SDK%\platform-tools\adb.exe" devices
endlocal
