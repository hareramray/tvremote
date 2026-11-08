@echo off
rem Builds TvRemote.apk without Android Studio.
rem Uses the JDK + Android SDK installed in %USERPROFILE%\android-build.
setlocal
cd /d "%~dp0"

for /d %%D in ("%USERPROFILE%\android-build\jdk-17*") do set "JAVA_HOME=%%~sD"
if not defined JAVA_HOME (
  echo JDK not found in %USERPROFILE%\android-build. See README.md "Build without Android Studio".
  exit /b 1
)

call "%~dp0gradlew.bat" assembleRelease --console=plain
if errorlevel 1 (
  echo.
  echo BUILD FAILED
  exit /b 1
)

copy /y "app\build\outputs\apk\release\app-release.apk" "TvRemote.apk" >nul
echo.
echo Done: %CD%\TvRemote.apk
