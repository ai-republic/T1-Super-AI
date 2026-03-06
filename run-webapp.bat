@echo off
REM Launch script for T1 Super AI - Web Application Mode
REM This script starts the web server

echo Starting T1 Super AI - Web Application Mode...

REM Find the JAR file
for %%f in (target\t1-super-ai-1.0.0-SNAPSHOT.jar) do set JAR_FILE=%%f

if not exist "%JAR_FILE%" (
    echo Error: JAR file not found. Please run 'mvn clean package' first.
    exit /b 1
)

REM Run the web application (default main class)
java --enable-native-access=ALL-UNNAMED -jar "%JAR_FILE%" %*

echo.
echo Web UI available at: http://localhost:8080
