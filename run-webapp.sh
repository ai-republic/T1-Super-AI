#!/bin/bash
# Launch script for T1 Super AI - Web Application Mode
# This script starts the web server

echo "Starting T1 Super AI - Web Application Mode..."

# Find the JAR file
JAR_FILE=$(ls target/t1-super-ai-*.jar 2>/dev/null | grep -v "\.original$" | head -n 1)

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found. Please run 'mvn clean package' first."
    exit 1
fi

# Run the web application (default main class)
java --enable-native-access=ALL-UNNAMED -jar "$JAR_FILE" "$@"

echo ""
echo "Web UI available at: http://localhost:8080"
