@echo off
rem Build, install and launch the debug APK on an attached device.
rem
rem   .\run              build, install over the top, launch
rem   .\run -c           uninstall and clear data first, so it starts at onboarding again
rem   .\run -l           skip the build, just install what is already in build\outputs
rem   .\run -s SERIAL    target a specific device instead of being asked
rem
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "PKG=com.eazyverse.testtrack"
set "APK=app\build\outputs\apk\debug\app-debug.apk"
set "CLEAN=0"
set "BUILD=1"
set "SERIAL="

:args
if "%~1"=="" goto endargs
if /i "%~1"=="-c" set "CLEAN=1"
if /i "%~1"=="-l" set "BUILD=0"
if /i "%~1"=="-s" (
    set "SERIAL=%~2"
    shift
)
shift
goto args
:endargs

rem Gradle needs a JDK 17+. Android Studio ships one; only look if the shell has no JAVA_HOME.
if not defined JAVA_HOME (
    if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
        set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
    ) else if exist "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\java.exe" (
        set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android Studio\jbr"
    ) else (
        echo Could not find a JDK. Set JAVA_HOME to your Android Studio jbr folder.
        exit /b 1
    )
)

rem platform-tools is not on PATH by default on Windows.
where adb >nul 2>&1 || set "PATH=%PATH%;%LOCALAPPDATA%\Android\Sdk\platform-tools"

rem Collect only devices that are actually ready. An emulator sitting in "offline" or a handset
rem in "unauthorized" is worse than absent: adb refuses to guess a default while one is attached.
set "COUNT=0"
for /f "tokens=1,2" %%a in ('adb devices') do if "%%b"=="device" (
    set /a COUNT+=1
    set "DEV_!COUNT!=%%a"
)

if %COUNT%==0 (
    echo No device ready. Check the cable, and that USB debugging is on and authorised.
    adb devices
    exit /b 1
)
if defined SERIAL goto chosen
if %COUNT%==1 (
    set "SERIAL=!DEV_1!"
    goto chosen
)

echo %COUNT% devices attached:
for /l %%i in (1,1,%COUNT%) do echo    %%i^) !DEV_%%i!
set /p "PICK=Which one? [1-%COUNT%] "
if not defined PICK goto badpick
set "SERIAL=!DEV_%PICK%!"
if not defined SERIAL goto badpick

:chosen
echo Target: !SERIAL!
set "ADB=adb -s !SERIAL!"

if "%BUILD%"=="1" (
    call "%~dp0gradlew.bat" assembleDebug
    if errorlevel 1 exit /b 1
)

if "%CLEAN%"=="1" %ADB% uninstall %PKG% >nul 2>&1

%ADB% install -r "%APK%"
if errorlevel 1 exit /b 1

rem Uninstalling is not enough on its own: allowBackup is on, so Android restores the saved
rem session straight back and the app reopens past onboarding. Clear it after installing.
if "%CLEAN%"=="1" %ADB% shell pm clear %PKG% >nul

rem Auto-return after a capture is a background activity launch, which Android blocks without this.
%ADB% shell appops set %PKG% SYSTEM_ALERT_WINDOW allow >nul 2>&1

%ADB% shell am start -n %PKG%/.MainActivity >nul
echo Running %PKG%
exit /b 0

:badpick
echo Not one of the listed numbers.
exit /b 1
