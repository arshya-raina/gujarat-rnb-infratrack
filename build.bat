@echo off
REM Recompiles the Java backend into backend\infratrack.jar (needs a JDK 17+)
cd /d %~dp0backend
if exist build rmdir /s /q build
mkdir build\classes
dir /s /b src\*.java > build\sources.txt
javac -encoding UTF-8 --release 17 -d build\classes -cp lib\h2-2.2.224.jar @build\sources.txt
if errorlevel 1 ( echo Build failed. & pause & exit /b 1 )
(echo Main-Class: gov.gujarat.rnb.infratrack.App& echo Class-Path: lib/h2-2.2.224.jar) > build\manifest.txt
jar cfm infratrack.jar build\manifest.txt -C build\classes .
echo Built backend\infratrack.jar
pause
