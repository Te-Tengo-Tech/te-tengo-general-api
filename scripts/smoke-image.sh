#!/usr/bin/env bash
# Smoke test of an API container image: it must start against an empty PostgreSQL 18, run the Flyway
# migrations, report healthy (the image HEALTHCHECK, then /actuator/health/readiness) and not run as
# root. Clips, e-mail and push use their in-memory and logging adapters, so no AWS emulator is needed.
# Used by the Container image workflow (the image built on a pull request) and by the Release
# workflow's staging job (the image pulled from GHCR by digest). Everything is removed on exit.
#
# Usage: scripts/smoke-image.sh <image>      e.g. te-tengo-general-api:smoke or ghcr.io/...@sha256:...
# Requires: Docker, curl and openssl.
#
# Environment (all optional):
#   TT_SMOKE_PUERTO   host port of the API          (default: 8080)
#   TT_SMOKE_ESPERA   seconds to wait for healthy    (default: 180)
set -euo pipefail

IMAGEN="${1:?Usage: scripts/smoke-image.sh <image>}"
PUERTO="${TT_SMOKE_PUERTO:-8080}"
ESPERA="${TT_SMOKE_ESPERA:-180}"
PREFIJO="tt-smoke-$$"
RED="$PREFIJO-red"
PG="$PREFIJO-pg"
API="$PREFIJO-api"
CLAVES="$(mktemp -d -t tt-smoke.XXXXXX)"

limpiar() {
  local codigo=$?
  if [ "$codigo" != 0 ]; then
    echo "---- API log ----"
    docker logs "$API" 2>&1 | tail -n 80 || true
  fi
  docker rm -f "$API" "$PG" >/dev/null 2>&1 || true
  docker network rm "$RED" >/dev/null 2>&1 || true
  rm -rf "$CLAVES"
  exit "$codigo"
}
trap limpiar EXIT

# Throwaway JWT key pair, readable by the image's non-root user (uid 10001).
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$CLAVES/privada.pem" 2>/dev/null
openssl rsa -in "$CLAVES/privada.pem" -pubout -out "$CLAVES/publica.pem" 2>/dev/null
chmod 755 "$CLAVES"
chmod 644 "$CLAVES"/*.pem

docker network create "$RED" >/dev/null
docker run -d --name "$PG" --network "$RED" --network-alias pg \
  -e POSTGRES_DB=tetengo -e POSTGRES_USER=tetengo -e POSTGRES_PASSWORD=tetengo postgres:18 >/dev/null
docker run -d --name "$API" --network "$RED" -p "$PUERTO:8080" \
  -v "$CLAVES:/run/secrets/tetengo:ro" \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://pg:5432/tetengo \
  -e SPRING_DATASOURCE_USERNAME=tetengo -e SPRING_DATASOURCE_PASSWORD=tetengo \
  -e TT_JWT_CLAVE_PUBLICA=file:/run/secrets/tetengo/publica.pem \
  -e TT_JWT_CLAVE_PRIVADA=file:/run/secrets/tetengo/privada.pem \
  "$IMAGEN" >/dev/null
echo "Started $IMAGEN with PostgreSQL 18; waiting up to ${ESPERA}s for it to report healthy"

estado=starting
limite=$((SECONDS + ESPERA))
while [ $SECONDS -lt $limite ]; do
  [ "$(docker inspect -f '{{.State.Running}}' "$API")" = true ] || { echo "::error::The API container exited"; exit 1; }
  estado=$(docker inspect -f '{{.State.Health.Status}}' "$API")
  [ "$estado" = healthy ] && break
  sleep 3
done
[ "$estado" = healthy ] || { echo "::error::The API did not report healthy within ${ESPERA}s (last status: $estado)"; exit 1; }

curl -fsS "localhost:$PUERTO/actuator/health/readiness"
echo
[ "$(docker exec "$API" id -u)" != 0 ] || { echo "::error::The image runs as root"; exit 1; }
echo "PASS: $IMAGEN migrated an empty PostgreSQL 18, reported healthy and ready, and runs as uid $(docker exec "$API" id -u)"
