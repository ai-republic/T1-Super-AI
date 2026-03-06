#!/bin/bash
# Launch script for T1 Super AI - CLI Mode
# This script starts the standalone CLI application

echo "Starting T1 Super AI - CLI Mode..."

# Find the JAR file
JAR_FILE=$(ls target/t1-super-ai-*.jar 2>/dev/null | grep -v "\.original$" | head -n 1)

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found. Please run 'mvn clean package' first."
    exit 1
fi

# Run the CLI application using PropertiesLauncher with alternate main class
java --enable-native-access=ALL-UNNAMED \
    -Dloader.main=com.airepublic.t1.T1SuperAiCLI \
    -jar "$JAR_FILE" "$@"
