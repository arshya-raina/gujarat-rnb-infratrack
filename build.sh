#!/usr/bin/env bash
# Recompiles the Java backend into backend/infratrack.jar (needs a JDK 17+)
set -e
cd "$(dirname "$0")/backend"
rm -rf build && mkdir -p build/classes
javac -encoding UTF-8 --release 17 -d build/classes -cp lib/h2-2.2.224.jar $(find src -name "*.java")
printf "Main-Class: gov.gujarat.rnb.infratrack.App\nClass-Path: lib/h2-2.2.224.jar\n" > build/manifest.txt
jar cfm infratrack.jar build/manifest.txt -C build/classes .
echo "Built backend/infratrack.jar"
