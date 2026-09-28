@echo off
REM Starts Gujarat R&B InfraTrack on http://localhost:8080
cd /d %~dp0
where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install Java 17 or newer from https://adoptium.net and run this again.
  pause
  exit /b 1
)
java -jar backend\infratrack.jar %*
pause
