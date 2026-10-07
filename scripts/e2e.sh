#!/usr/bin/env bash
# End-to-end smoke test of the agent ↔ API contract: agent → API → alert.
#
# It starts an isolated stack (own compose project and host ports, so it runs next to the
# developer's stack), runs the API on it, seeds the demo household, plays a URFD fall clip through
# the real desktop agent without its UI (`--sin-interfaz`) and asserts through the API, as the demo
# family account, that:
#   1. a CAIDA alert appears and the push provider was invoked for it;
#   2. it becomes `confirmada: true` (the agent's `caida_confirmada`, 30 s on the floor);
#   3. its clip becomes DISPONIBLE and the pre-signed URL serves the MP4 from Floci's S3.
# Everything is torn down on exit. It prints a PASS/FAIL summary and the detection → alert latency.
#
# Usage: scripts/e2e.sh
# Requires: Docker, JDK 25, jq, curl, openssl, python3, uv, and te-tengo-desktop-pywebview checked out.
#
# Environment (all optional):
#   TT_E2E_ESCRITORIO   desktop agent repository           (default: ../te-tengo-desktop-pywebview)
#   TT_E2E_VIDEO        URFD fall video, full depth+RGB frame (default: <desktop>/datos/urfd/fall-03-cam0.mp4,
#                       downloaded from the URFD site when missing). fall-03: MediaPipe finds the
#                       person in every frame, so `caida` comes on the first pass.
#   TT_E2E_PUERTO_API   API port                           (default: 18080)
#   TT_POSTGRES_PUERTO  PostgreSQL host port               (default: 15432)
#   TT_FLOCI_PUERTO     Floci host port                    (default: 14566)
#   TT_E2E_PROYECTO     compose project name               (default: tt-e2e)
#   TT_E2E_DIR          work directory for logs and files  (default: a new temporary directory)
#   TT_E2E_ESPERA       seconds each assertion may wait    (default: 120)
#   TT_E2E_SIN_BUILD=1  reuse build/libs/*.jar instead of running `./gradlew bootJar`
set -euo pipefail
cd "$(dirname "$0")/.."
API_REPO="$PWD"

ESCRITORIO="$(cd "${TT_E2E_ESCRITORIO:-../te-tengo-desktop-pywebview}" && pwd)"
PUERTO_API="${TT_E2E_PUERTO_API:-18080}"
export TT_POSTGRES_PUERTO="${TT_POSTGRES_PUERTO:-15432}"
export TT_FLOCI_PUERTO="${TT_FLOCI_PUERTO:-14566}"
export COMPOSE_PROJECT_NAME="${TT_E2E_PROYECTO:-tt-e2e}"
ESPERA="${TT_E2E_ESPERA:-120}"
WORK="${TT_E2E_DIR:-$(mktemp -d -t tt-e2e.XXXXXX)}"
mkdir -p "$WORK"
WORK="$(cd "$WORK" && pwd)"
API="http://localhost:$PUERTO_API"
URFD_URL="https://fenix.ur.edu.pl/~mkepski/ds/data/fall-03-cam0.mp4"

# Test values for the throwaway demo account (seed-demo.sh reads them).
export DEMO_CORREO="e2e@tetengo.test"
DEMO_CONTRASENA="E2e-$(openssl rand -hex 8)"
export DEMO_CONTRASENA

API_PID="" AGENTE_PID=""
INICIO=$SECONDS
declare -a RESULTADOS=()
LATENCIA="n/a" LATENCIA_API="n/a"

log() { printf '[e2e %3ds] %s\n' $((SECONDS - INICIO)) "$*"; }
ok() { RESULTADOS+=("PASS  $1"); log "PASS $1"; }
falla() {
  RESULTADOS+=("FAIL  $1")
  log "FAIL $1"
  exit 1
}

resumen() {
  local codigo=$1
  echo
  {
    echo "================ Te Tengo end-to-end smoke test ================"
    for r in ${RESULTADOS[@]+"${RESULTADOS[@]}"}; do echo "  $r"; done
    echo "  Latency, agent detection → push sent (agent and API logs): $LATENCIA"
    echo "  Latency in the API, alert ocurridaEn → notificadaEn: $LATENCIA_API (requirement: < 10 s)"
    echo "  Total time: $((SECONDS - INICIO)) s · logs in $WORK"
    if [ "$codigo" = 0 ]; then echo "  RESULT: PASS"; else echo "  RESULT: FAIL (exit $codigo)"; fi
    echo "================================================================"
  } | tee "$WORK/resumen.txt"
}

limpiar() {
  local codigo=$?
  trap - EXIT INT TERM
  if [ -n "$AGENTE_PID" ] && kill -0 "$AGENTE_PID" 2>/dev/null; then
    kill -TERM "$AGENTE_PID" 2>/dev/null || true
    for _ in $(seq 1 20); do kill -0 "$AGENTE_PID" 2>/dev/null || break; sleep 0.5; done
    kill -KILL "$AGENTE_PID" 2>/dev/null || true
  fi
  if [ -n "$API_PID" ] && kill -0 "$API_PID" 2>/dev/null; then
    kill -TERM "$API_PID" 2>/dev/null || true
    for _ in $(seq 1 30); do kill -0 "$API_PID" 2>/dev/null || break; sleep 0.5; done
    kill -KILL "$API_PID" 2>/dev/null || true
  fi
  docker compose down -v --remove-orphans >"$WORK/compose-down.log" 2>&1 || true
  if [ "$codigo" != 0 ]; then
    echo
    echo "---- last lines of the API log ($WORK/api.log) ----"
    tail -n 40 "$WORK/api.log" 2>/dev/null || true
    echo "---- last lines of the agent log ($WORK/agente.log) ----"
    tail -n 40 "$WORK/agente.log" 2>/dev/null || true
  fi
  resumen "$codigo"
  exit "$codigo"
}
trap limpiar EXIT
trap 'exit 130' INT TERM

# Waits up to $1 seconds for the command in the remaining arguments to succeed.
esperar() {
  local limite=$((SECONDS + $1))
  shift
  until "$@"; do
    [ $SECONDS -lt $limite ] || return 1
    sleep 1
  done
}

llamar() { # method path [json [extra curl arguments...]]
  local metodo=$1 ruta=$2 cuerpo=${3:-}
  shift $(($# < 3 ? $# : 3))
  curl -sS -X "$metodo" "$API$ruta" -H 'Api-Version: 1' -H 'Content-Type: application/json' \
    ${TOKEN:+-H "Authorization: Bearer $TOKEN"} ${cuerpo:+--data "$cuerpo"} "$@"
}

# ---------------------------------------------------------------- 0. requirements
for herramienta in docker java jq curl openssl python3 uv; do
  command -v "$herramienta" >/dev/null || falla "requirement: $herramienta is not installed"
done
[ -f "$ESCRITORIO/pyproject.toml" ] || falla "requirement: desktop agent repository not found at $ESCRITORIO"
for puerto in "$PUERTO_API" "$TT_POSTGRES_PUERTO" "$TT_FLOCI_PUERTO"; do
  if (exec 3<>"/dev/tcp/127.0.0.1/$puerto") 2>/dev/null; then
    falla "requirement: port $puerto is already in use (override it, see the top of this script)"
  fi
done
log "work directory $WORK; compose project $COMPOSE_PROJECT_NAME; API :$PUERTO_API, PostgreSQL :$TT_POSTGRES_PUERTO, Floci :$TT_FLOCI_PUERTO"

# ---------------------------------------------------------------- 1. agent, model and fall clip
# Prepared first: they do not depend on the stack and fail fast.
log "preparing the desktop agent (uv sync, MediaPipe model)"
(cd "$ESCRITORIO" && uv sync --locked --quiet && ./scripts/descargar_modelo.sh >/dev/null) \
  >"$WORK/escritorio-preparacion.log" 2>&1 || falla "desktop agent setup (see $WORK/escritorio-preparacion.log)"

VIDEO="${TT_E2E_VIDEO:-$ESCRITORIO/datos/urfd/fall-03-cam0.mp4}"
if [ ! -s "$VIDEO" ]; then
  log "downloading $URFD_URL"
  VIDEO="$WORK/fall-03-cam0.mp4"
  curl -fsSL --retry 3 -o "$VIDEO" "$URFD_URL" || falla "download of the URFD fall clip"
fi
# URFD frames hold the depth image on the left and the RGB image on the right: the agent must see
# only the RGB half (docs/INSTALLATION.md of the agent, section 12), written without loss (FFV1): a
# lossy re-encode changes the pixels MediaPipe sees and can lose a fall. The last frame (the person on the
# floor) is then held for 45 s, so the agent also confirms the fall (caida_confirmada after 30 s)
# before the clip loops back to a standing person.
CLIP="$WORK/caida-e2e.avi"
(cd "$ESCRITORIO" && uv run --quiet python - "$VIDEO" "$CLIP" 45) >"$WORK/video.log" 2>&1 <<'PY' || falla "fall clip preparation (see $WORK/video.log)"
import sys

import cv2

origen, destino, segundos = sys.argv[1], sys.argv[2], float(sys.argv[3])
video = cv2.VideoCapture(origen)
fps = video.get(cv2.CAP_PROP_FPS) or 30.0
salida = cv2.VideoWriter(destino, cv2.VideoWriter_fourcc(*"FFV1"), fps, (320, 240))
ultimo = None
while True:
    leido, cuadro = video.read()
    if not leido:
        break
    ultimo = cuadro[0:240, 320:640]
    salida.write(ultimo)
if ultimo is None:
    sys.exit(f"no frames in {origen}")
for _ in range(int(fps * segundos)):
    salida.write(ultimo)
salida.release()
PY
ok "fall clip ready (RGB half of $(basename "$VIDEO"), last frame held 45 s)"

# ---------------------------------------------------------------- 2. isolated stack
log "starting PostgreSQL and Floci (docker compose -p $COMPOSE_PROJECT_NAME)"
docker compose up -d --wait >"$WORK/compose-up.log" 2>&1 || falla "docker compose up (see $WORK/compose-up.log)"
esperar 60 docker compose exec -T postgres pg_isready -q -U tetengo -d tetengo || falla "PostgreSQL ready"
ok "isolated stack up"

mkdir -p "$WORK/claves"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$WORK/claves/privada.pem" 2>/dev/null
openssl rsa -in "$WORK/claves/privada.pem" -pubout -out "$WORK/claves/publica.pem" 2>/dev/null

if [ "${TT_E2E_SIN_BUILD:-}" != 1 ]; then
  log "building the API (./gradlew bootJar)"
  ./gradlew bootJar --quiet >"$WORK/gradle.log" 2>&1 || falla "API build (see $WORK/gradle.log)"
fi
JAR="$(find build/libs -name '*.jar' ! -name '*-plain.jar' | head -n1)"
[ -n "$JAR" ] || falla "API jar not found in build/libs"

log "starting the API on :$PUERTO_API (profile local, push provider registro)"
SPRING_PROFILES_ACTIVE=local \
  SERVER_PORT="$PUERTO_API" \
  SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$TT_POSTGRES_PUERTO/tetengo" \
  SPRING_DATASOURCE_USERNAME=tetengo \
  SPRING_DATASOURCE_PASSWORD=tetengo \
  TT_JWT_CLAVE_PUBLICA="file:$WORK/claves/publica.pem" \
  TT_JWT_CLAVE_PRIVADA="file:$WORK/claves/privada.pem" \
  TT_PUSH_PROVEEDOR=registro \
  TT_CORREO_PROVEEDOR=registro \
  TT_URL_TRANSMISION="ws://localhost:$PUERTO_API" \
  java -jar "$API_REPO/$JAR" >"$WORK/api.log" 2>&1 &
API_PID=$!
salud() {
  kill -0 "$API_PID" 2>/dev/null || falla "API process exited (see $WORK/api.log)"
  [ "$(curl -s "$API/actuator/health" | jq -r .status 2>/dev/null)" = UP ]
}
esperar "$ESPERA" salud || falla "API health UP within ${ESPERA}s"
ok "API healthy"

# ---------------------------------------------------------------- 3. demo household
TT_API="$API" scripts/seed-demo.sh "$WORK/agente.toml" >"$WORK/seed.log" 2>&1 || falla "seed-demo.sh (see $WORK/seed.log)"
TOKEN=""
TOKEN="$(llamar POST /api/sesiones "$(jq -nc --arg c "$DEMO_CORREO" --arg p "$DEMO_CONTRASENA" '{correo:$c,contrasena:$p}')" | jq -r .tokenAcceso)"
[ -n "$TOKEN" ] && [ "$TOKEN" != null ] || falla "sign in as the demo family account"
# A device for the family member, so the alert has a push destination.
[ "$(llamar POST /api/dispositivos '{"tokenPush":"e2e-smoke","plataforma":"ANDROID"}' -o /dev/null -w '%{http_code}')" = 201 ] ||
  falla "device registration"
ok "demo household seeded, family signed in, device registered"

# ---------------------------------------------------------------- 4. agent, headless
log "starting the desktop agent headless with the fall clip"
"$ESCRITORIO/.venv/bin/te-tengo-captura" --config "$WORK/agente.toml" --video "$CLIP" --sin-interfaz \
  --datos "$WORK/agente-datos" --logs "$WORK/agente-logs" >"$WORK/agente.log" 2>&1 &
AGENTE_PID=$!
agente_vivo() { kill -0 "$AGENTE_PID" 2>/dev/null || falla "agent process exited (see $WORK/agente.log)"; }

# ---------------------------------------------------------------- 5. assertions through the API
ALERTA=""
hay_caida() {
  agente_vivo
  ALERTA="$(llamar GET '/api/alertas?tipo=CAIDA' | jq -c '.elementos[0] // empty')"
  [ -n "$ALERTA" ]
}
esperar "$ESPERA" hay_caida || falla "a CAIDA alert within ${ESPERA}s of the agent start"
ALERTA_ID="$(jq -r .id <<<"$ALERTA")"
grep -q 'Cámara registrada' "$WORK/agente.log" || falla "the agent registered its camera"
ok "CAIDA alert $ALERTA_ID created from the agent's event"

# The push for this alert: ALERTA_CAIDA, or ALERTA_ACTUALIZADA_A_CAIDA when the agent first saw an
# unstable movement. The `registro` provider logs it with the number of devices.
PUSH="$(grep -m1 -E "Push ALERTA_(CAIDA|ACTUALIZADA_A_CAIDA) \(sin enviar, proveedor registro\) a 1 dispositivo.*alertaId=$ALERTA_ID" "$WORK/api.log" || true)"
[ -n "$PUSH" ] || falla "push provider invoked for the CAIDA alert"
[ "$(jq -r .notificadaEn <<<"$ALERTA")" != null ] || falla "alert marked as notified (notificadaEn)"

# Latency, on this machine's clock: the agent's detection log line → the API's push log line.
DETECTADO="$(grep -m1 'Evento detectado: caida ' "$WORK/agente.log" | cut -d' ' -f1,2 || true)"
latencias="$(python3 - "$ALERTA" "$DETECTADO" "${PUSH%% *}" <<'PY'
import json
import re
import sys
from datetime import datetime


def iso(texto: str) -> datetime:  # Java may print nanoseconds; Python parses microseconds
    return datetime.fromisoformat(re.sub(r"(\.\d{6})\d+", r"\1", texto).replace("Z", "+00:00"))


alerta, detectado, empujado = json.loads(sys.argv[1]), sys.argv[2], sys.argv[3]
servidor = (iso(alerta["notificadaEn"]) - iso(alerta["ocurridaEn"])).total_seconds() * 1000
extremo = "n/a"
if detectado:
    inicio = datetime.strptime(detectado, "%Y-%m-%d %H:%M:%S,%f").astimezone()
    extremo = f"{(iso(empujado) - inicio).total_seconds() * 1000:.0f} ms"
print(f"{extremo}|{servidor:.0f} ms")
PY
)"
LATENCIA="${latencias%%|*}" LATENCIA_API="${latencias#*|}"
ok "push provider invoked ($(sed -E 's/.*Push ([A-Z_]+) .*/\1/' <<<"$PUSH") to 1 device); detection → push $LATENCIA"

confirmada() {
  agente_vivo
  ALERTA="$(llamar GET "/api/alertas/$ALERTA_ID")"
  [ "$(jq -r .confirmada <<<"$ALERTA")" = true ]
}
esperar "$ESPERA" confirmada || falla "alert confirmada: true within ${ESPERA}s"
grep -q 'Push CAIDA_CONFIRMADA (sin enviar, proveedor registro)' "$WORK/api.log" ||
  falla "push provider invoked for CAIDA_CONFIRMADA"
ok "alert confirmed (caida_confirmada) and CAIDA_CONFIRMADA pushed"

clip_disponible() {
  ALERTA="$(llamar GET "/api/alertas/$ALERTA_ID")"
  [ "$(jq -r .clip <<<"$ALERTA")" = DISPONIBLE ]
}
esperar "$ESPERA" clip_disponible || falla "clip DISPONIBLE within ${ESPERA}s"
URL_CLIP="$(llamar GET "/api/alertas/$ALERTA_ID/clip" | jq -r .url)"
curl -fsS -o "$WORK/clip.mp4" "$URL_CLIP" || falla "clip download from Floci S3 through the pre-signed URL"
[ "$(head -c 8 "$WORK/clip.mp4" | tail -c 4)" = ftyp ] || falla "the downloaded clip is an MP4"
ok "clip DISPONIBLE and served from Floci S3 ($(wc -c <"$WORK/clip.mp4" | tr -d ' ') bytes MP4)"
