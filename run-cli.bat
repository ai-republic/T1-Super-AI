@echo off
REM Launch script for T1 Super AI - CLI Mode
REM This script starts the standalone CLI application

echo Starting T1 Super AI - CLI Mode...

REM Find the JAR file (exclude .original files)
for %%f in (target\t1-super-ai-1.0.0-SNAPSHOT.jar) do set JAR_FILE=%%f

if not exist "%JAR_FILE%" (
    echo Error: JAR file not found. Please run 'mvn clean package' first.
    exit /b 1
)

REM Run the CLI application using PropertiesLauncher with alternate main class
java --enable-native-access=ALL-UNNAMED ^
    -Dloader.main=com.airepublic.t1.T1SuperAiCLI ^
    -jar "%JAR_FILE%" %*
