#!/usr/bin/env bash
# Starts Gujarat R&B InfraTrack on http://localhost:8080
cd "$(dirname "$0")"
command -v java >/dev/null || { echo "Java was not found. Install Java 17 or newer from https://adoptium.net"; exit 1; }
exec java -jar backend/infratrack.jar "$@"
