@echo off
setlocal
rem Builds the debug variant, puts it over the top of whatever is there, and opens it.
rem
rem Three rules are baked in rather than remembered:
rem
rem   The emulator, never the phone. It targets an emulator serial explicitly and stops if there
rem   isn't one, so a plugged-in handset can never be the thing that gets written to.
rem
rem   install -r, never uninstall. Reinstalling over the top keeps the signed-in session and every
rem   granted permission, which is most of what makes a test run quick.
rem
rem   Debug variant. The release build is for Play and takes a minute to shrink; nothing about
rem   looking at a screen needs it.

set "SDK=%LOCALAPPDATA%\Android\Sdk"
set "ADB=%SDK%\platform-tools\adb.exe"
set "PKG=com.eazyverse.testtrack"
set "ACT=%PKG%/.MainActivity"
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

rem usebackq with backticks, because a plain for /f cannot cope with the quoted path to adb.
set "SERIAL="
for /f "usebackq tokens=1" %%S in (`"%ADB%" devices ^| findstr /B emulator-`) do set "SERIAL=%%S"

if not defined SERIAL (
  echo No emulator is running. Start one with:  emu
  echo.
  echo Refusing to fall back to an attached phone on purpose: this app writes to a live database
  echo and signs in as a real tester, and neither belongs on a handset by accident.
  exit /b 1
)

echo Building the debug apk ...
call "%~dp0gradlew.bat" :app:assembleDebug || exit /b 1

echo Installing on %SERIAL% ...
"%ADB%" -s %SERIAL% install -r "%~dp0app\build\outputs\apk\debug\app-debug.apk" || exit /b 1

"%ADB%" -s %SERIAL% shell am force-stop %PKG%
"%ADB%" -s %SERIAL% shell am start -n %ACT% >nul
echo Running.
endlocal
