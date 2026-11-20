@echo off
setlocal
set ROOT=%~dp0
if "%JAVA_HOME%"=="" set JAVA_HOME=%~dp0..\tools\jdk\jdk-17.0.13+11
if "%ANDROID_HOME%"=="" set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
call "%~dp0..\tools\gradle-8.7\bin\gradle.bat" -p "%ROOT%" assembleRelease
echo.
echo APK: %ROOT%app\build\outputs\apk\release\app-release.apk
endlocal
