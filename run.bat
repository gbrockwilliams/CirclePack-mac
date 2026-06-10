@echo off
cd /d "%~dp0"

rem CirclePack requires Java 17 or newer.
rem Use JAVA_HOME if set, otherwise fall back to whatever 'java' is on PATH.

if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA=java"
)

rem Verify the JVM is reachable
"%JAVA%" -version >nul 2>&1
if errorlevel 1 (
    echo Error: java not found. Install Java 17+ ^(e.g. Temurin from https://adoptium.net^)
    echo and either add it to PATH or set JAVA_HOME.
    pause
    exit /b 1
)

rem Check version ^(major version: 17=53, 21=65, etc.^)
for /f "tokens=3" %%v in ('"%JAVA%" -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VER_STR=%%v"
    goto :got_ver
)
:got_ver
rem Strip quotes from version string
set "JAVA_VER_STR=%JAVA_VER_STR:"=%"
rem Extract major version (handle both "17.0.x" and "1.8.x" style)
for /f "delims=." %%m in ("%JAVA_VER_STR%") do set "MAJOR=%%m"
if "%MAJOR%"=="1" (
    echo Error: Java 8 or older detected. Java 17+ is required.
    pause
    exit /b 1
)
if %MAJOR% LSS 17 (
    echo Error: Java %MAJOR% detected. Java 17+ is required.
    echo Set JAVA_HOME to point to a Java 17+ installation.
    pause
    exit /b 1
)

"%JAVA%" -cp "out;cpcore.jar;jars/*" allMains.SplashMain %*
