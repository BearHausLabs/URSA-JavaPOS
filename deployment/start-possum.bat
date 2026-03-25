@echo off
setlocal enabledelayedexpansion
REM ═══════════════════════════════════════════════════════════════
REM  URSA POSSUM Launcher
REM  Auto-detects JDK, uses external config, no hardcoded paths
REM ═══════════════════════════════════════════════════════════════

set "POSSUM_HOME=C:\URSA\possum"
set "POSSUM_PORT=8080"

REM --- Profile (default: local) ---
if not defined SPRING_PROFILES_ACTIVE set "SPRING_PROFILES_ACTIVE=local"

REM --- Find Java ---
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" goto :java_found
    echo [WARN] JAVA_HOME is set but java.exe not found at %JAVA_HOME%
)

REM Auto-detect: check common JDK 21 install locations
for %%D in (
    "C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot"
    "C:\Program Files\Eclipse Adoptium\jdk-21"
    "C:\Program Files\Java\jdk-21"
) do (
    if exist "%%~D\bin\java.exe" (
        set "JAVA_HOME=%%~D"
        goto :java_found
    )
)

REM Last resort: try PATH
where java.exe >nul 2>&1
if %errorlevel% equ 0 (
    echo [WARN] Using java from PATH. Set JAVA_HOME for reliability.
    set "JAVA_EXE=java.exe"
    goto :start
)

echo [ERROR] No JDK found. Install Microsoft OpenJDK 21 or set JAVA_HOME.
echo         https://learn.microsoft.com/en-us/java/openjdk/download
exit /b 1

:java_found
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

:start
REM --- Build classpath (config BEFORE jar so external config wins) ---
set "CP=%POSSUM_HOME%\config;%POSSUM_HOME%\possum.jar;%POSSUM_HOME%\vendor-lib\*;%POSSUM_HOME%\externalLib\*"

REM --- Native library path (only include dirs that exist) ---
set "LIB_PATH=%POSSUM_HOME%\externalLib"
if exist "C:\Program Files\EPSON\JavaPOS\lib" set "LIB_PATH=!LIB_PATH!;C:\Program Files\EPSON\JavaPOS\lib"
if exist "C:\Program Files\EPSON\JavaPOS\bin" set "LIB_PATH=!LIB_PATH!;C:\Program Files\EPSON\JavaPOS\bin"
if exist "C:\Program Files\Zebra Technologies\Barcode Scanners\Scanner SDK\JPOS\bin" set "LIB_PATH=!LIB_PATH!;C:\Program Files\Zebra Technologies\Barcode Scanners\Scanner SDK\JPOS\bin"

cd /d "%POSSUM_HOME%"

echo.
echo  POSSUM - URSA Device Manager
echo  Profile:  %SPRING_PROFILES_ACTIVE%
echo  Port:     %POSSUM_PORT%
echo  Java:     %JAVA_EXE%
echo  Home:     %POSSUM_HOME%
echo.

"%JAVA_EXE%" ^
  -cp "%CP%" ^
  "-Djava.library.path=%LIB_PATH%" ^
  -Djpos.tracing=ON ^
  -Dserver.port=%POSSUM_PORT% ^
  com.target.devicemanager.DeviceMain

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] POSSUM exited with code %errorlevel%
    pause
)
endlocal
