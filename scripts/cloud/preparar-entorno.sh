#!/usr/bin/env bash
# Prepara una sesión de Claude Code en la nube: la imagen trae Java 21 y el proyecto usa Java 25.
# Se ejecuta desde el hook SessionStart de .claude/settings.json y solo actúa en la nube.
set -euo pipefail
[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0

JDK_DIR=/opt/jdk-25
URL="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz"

if [ ! -x "$JDK_DIR/bin/java" ]; then
  mkdir -p "$JDK_DIR"
  curl -fsSL "$URL" | tar -xz -C "$JDK_DIR" --strip-components=1
fi

# Gradle sigue corriendo con el Java de la imagen y usa este JDK como toolchain del proyecto.
mkdir -p "$HOME/.gradle"
grep -q "org.gradle.java.installations.paths" "$HOME/.gradle/gradle.properties" 2>/dev/null \
  || echo "org.gradle.java.installations.paths=$JDK_DIR" >> "$HOME/.gradle/gradle.properties"

# Docker para Testcontainers (las pruebas de integración).
if ! docker info >/dev/null 2>&1; then
  (dockerd >/tmp/dockerd.log 2>&1 &) ; sleep 3
fi
echo "Entorno listo: JDK 25 en $JDK_DIR, Docker $(docker info --format '{{.ServerVersion}}' 2>/dev/null || echo 'no disponible')."
