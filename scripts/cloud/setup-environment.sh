#!/usr/bin/env bash
# Prepares a Claude Code cloud session: the image ships Java 21 and this project uses Java 25.
# Runs from the SessionStart hook in .claude/settings.json and only acts in the cloud.
set -euo pipefail
[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0

JDK_DIR=/opt/jdk-25
URL="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz"

if [ ! -x "$JDK_DIR/bin/java" ]; then
  mkdir -p "$JDK_DIR"
  curl -fsSL "$URL" | tar -xz -C "$JDK_DIR" --strip-components=1
fi

# Gradle keeps running on the image Java and uses this JDK as the project toolchain.
mkdir -p "$HOME/.gradle"
grep -q "org.gradle.java.installations.paths" "$HOME/.gradle/gradle.properties" 2>/dev/null \
  || echo "org.gradle.java.installations.paths=$JDK_DIR" >> "$HOME/.gradle/gradle.properties"

# Docker for Testcontainers (integration tests).
if ! docker info >/dev/null 2>&1; then
  (dockerd >/tmp/dockerd.log 2>&1 &) ; sleep 3
fi
echo "Environment ready: JDK 25 at $JDK_DIR, Docker $(docker info --format '{{.ServerVersion}}' 2>/dev/null || echo 'not available')."
