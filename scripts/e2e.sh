#!/usr/bin/env bash
# End-to-end smoke test of the agent ↔ API contract: agent → API → alert.
#
# It starts an isolated stack (own compose project and host ports, so it runs next to the
# developer's stack), runs the API on it, seeds the demo household, plays a URFD fall clip through
# the real desktop agent without its UI (`--sin-interfaz`) and asserts through the API, as the demo
# family account, that:
#   1. a CAIDA alert appears and the push provider was invoked for it;
#   2. it becomes `confirmada: true` (the agent's `caida_confirmada`, 30 s on the floor);
#   3. its clip becomes DISPONIBLE and the pre-signed URL serves the MP4 from Floci's S3;
#   4. live view wiring: a live view session opens, and MediaMTX asks this API before serving the
#      session's LL-HLS playlist and its WebRTC (WHEP) endpoint (authorized with the session's token,
#      401 without it).
# Everything is torn down on exit. It prints a PASS/FAIL summary and the detection → alert latency.
#
# Usage: scripts/e2e.sh
# Requires: Docker, JDK 25 (not with TT_E2E_IMAGEN), jq, curl, openssl, python3, uv, and
# te-tengo-desktop-pywebview checked out.
#
# Environment (all optional):
#   TT_E2E_ESCRITORIO   desktop agent repository           (default: ../te-tengo-desktop-pywebview)
#   TT_E2E_VIDEO        URFD fall video, full depth+RGB frame (default: <desktop>/datos/urfd/fall-03-cam0.mp4,
#                       downloaded from the URFD site when missing). fall-03: MediaPipe finds the
#                       person in every frame, so `caida` comes on the first pass.
#   TT_E2E_PUERTO_API   API port                           (default: 18080)
#   TT_POSTGRES_PUERTO  PostgreSQL host port               (default: 15432)
#   TT_FLOCI_PUERTO     Floci host port                    (default: 14566)
#   TT_MEDIAMTX_PUERTO_RTSP / _HLS / _WEBRTC / _ICE / _API  MediaMTX host ports
#                       (default: 18554 / 18888 / 18889 / 18189 / 19997)
#   TT_E2E_PROYECTO     compose project name               (default: tt-e2e)
#   TT_E2E_DIR          work directory for logs and files  (default: a new temporary directory)
#   TT_E2E_ESPERA       seconds each assertion may wait    (default: 120)
#   TT_E2E_SIN_BUILD=1  reuse build/libs/*.jar instead of running `./gradlew bootJar`
#   TT_E2E_IMAGEN       run the API from this container image instead of the jar (no Gradle build),
#                       e.g. ghcr.io/te-tengo-tech/te-tengo-general-api@sha256:... (the Release
#                       workflow's verification). The container uses the host network, so it sees the stack
#                       on localhost like the jar does: Linux, or Docker Desktop with host networking on.
set -euo pipefail
cd "$(dirname "$0")/.."
API_REPO="$PWD"

ESCRITORIO="$(cd "${TT_E2E_ESCRITORIO:-../te-tengo-desktop-pywebview}" && pwd)"
PUERTO_API="${TT_E2E_PUERTO_API:-18080}"
export TT_POSTGRES_PUERTO="${TT_POSTGRES_PUERTO:-15432}"
export TT_FLOCI_PUERTO="${TT_FLOCI_PUERTO:-14566}"
export TT_MEDIAMTX_PUERTO_RTSP="${TT_MEDIAMTX_PUERTO_RTSP:-18554}"
export TT_MEDIAMTX_PUERTO_HLS="${TT_MEDIAMTX_PUERTO_HLS:-18888}"
export TT_MEDIAMTX_PUERTO_WEBRTC="${TT_MEDIAMTX_PUERTO_WEBRTC:-18889}"
export TT_MEDIAMTX_PUERTO_ICE="${TT_MEDIAMTX_PUERTO_ICE:-18189}"
export TT_MEDIAMTX_PUERTO_API="${TT_MEDIAMTX_PUERTO_API:-19997}"
# MediaMTX (compose.yaml) authorizes through this API on the host, with a throwaway shared secret.
export TT_API_PUERTO="$PUERTO_API"
TT_VIVO_SECRETO_AUTORIZACION="e2e-$(openssl rand -hex 16)"
export TT_VIVO_SECRETO_AUTORIZACION
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

IMAGEN="${TT_E2E_IMAGEN:-}"
CONTENEDOR_API="$COMPOSE_PROJECT_NAME-api"
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
  [ -z "$IMAGEN" ] || docker rm -f "$CONTENEDOR_API" >/dev/null 2>&1 || true
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
herramientas=(docker jq curl openssl python3 uv)
[ -n "$IMAGEN" ] || herramientas+=(java)
for herramienta in "${herramientas[@]}"; do
  command -v "$herramienta" >/dev/null || falla "requirement: $herramienta is not installed"
done
[ -f "$ESCRITORIO/pyproject.toml" ] || falla "requirement: desktop agent repository not found at $ESCRITORIO"
for puerto in "$PUERTO_API" "$TT_POSTGRES_PUERTO" "$TT_FLOCI_PUERTO" "$TT_MEDIAMTX_PUERTO_RTSP" \
  "$TT_MEDIAMTX_PUERTO_HLS" "$TT_MEDIAMTX_PUERTO_WEBRTC" "$TT_MEDIAMTX_PUERTO_ICE" "$TT_MEDIAMTX_PUERTO_API"; do
  if (exec 3<>"/dev/tcp/127.0.0.1/$puerto") 2>/dev/null; then
    falla "requirement: port $puerto is already in use (override it, see the top of this script)"
  fi
done
log "work directory $WORK; compose project $COMPOSE_PROJECT_NAME; API :$PUERTO_API, PostgreSQL :$TT_POSTGRES_PUERTO, Floci :$TT_FLOCI_PUERTO, MediaMTX :$TT_MEDIAMTX_PUERTO_RTSP/:$TT_MEDIAMTX_PUERTO_HLS"

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
log "starting PostgreSQL, Floci and MediaMTX (docker compose -p $COMPOSE_PROJECT_NAME)"
docker compose up -d --wait >"$WORK/compose-up.log" 2>&1 || falla "docker compose up (see $WORK/compose-up.log)"
esperar 60 docker compose exec -T postgres pg_isready -q -U tetengo -d tetengo || falla "PostgreSQL ready"
ok "isolated stack up"

mkdir -p "$WORK/claves"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$WORK/claves/privada.pem" 2>/dev/null
openssl rsa -in "$WORK/claves/privada.pem" -pubout -out "$WORK/claves/publica.pem" 2>/dev/null

# The API's configuration, the same for the jar and the image.
API_ENTORNO=(
  SPRING_PROFILES_ACTIVE=local
  SERVER_PORT="$PUERTO_API"
  SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$TT_POSTGRES_PUERTO/tetengo"
  SPRING_DATASOURCE_USERNAME=tetengo
  SPRING_DATASOURCE_PASSWORD=tetengo
  TT_FLOCI_PUERTO="$TT_FLOCI_PUERTO"
  TT_VIVO_SECRETO_AUTORIZACION="$TT_VIVO_SECRETO_AUTORIZACION"
  TT_PUSH_PROVEEDOR=registro
  TT_CORREO_PROVEEDOR=registro
  TT_VIVO_URL_PUBLICACION="rtsp://localhost:$TT_MEDIAMTX_PUERTO_RTSP/camaras/{camaraId}"
  TT_VIVO_URL_HLS="http://localhost:$TT_MEDIAMTX_PUERTO_HLS"
  TT_VIVO_URL_WEBRTC="http://localhost:$TT_MEDIAMTX_PUERTO_WEBRTC/camaras/{camaraId}/whep"
  TT_VIVO_MEDIAMTX_API="http://localhost:$TT_MEDIAMTX_PUERTO_API"
)

if [ -n "$IMAGEN" ]; then
  # The image's user (uid 10001) must read the mounted keys.
  chmod 755 "$WORK/claves"
  chmod 644 "$WORK"/claves/*.pem
  docker_entorno=()
  for variable in "${API_ENTORNO[@]}"; do docker_entorno+=(-e "$variable"); done
  log "starting the API from the image $IMAGEN on :$PUERTO_API (profile local, push provider registro)"
  # Foreground `docker run` in the background: its PID stands for the API (docker forwards SIGTERM),
  # and its output is the API log.
  docker run --rm --name "$CONTENEDOR_API" --network host \
    -v "$WORK/claves:/run/secrets/tetengo:ro" \
    -e TT_JWT_CLAVE_PUBLICA=file:/run/secrets/tetengo/publica.pem \
    -e TT_JWT_CLAVE_PRIVADA=file:/run/secrets/tetengo/privada.pem \
    "${docker_entorno[@]}" "$IMAGEN" >"$WORK/api.log" 2>&1 &
else
  if [ "${TT_E2E_SIN_BUILD:-}" != 1 ]; then
    log "building the API (./gradlew bootJar)"
    ./gradlew bootJar --quiet >"$WORK/gradle.log" 2>&1 || falla "API build (see $WORK/gradle.log)"
  fi
  JAR="$(find build/libs -name '*.jar' ! -name '*-plain.jar' | head -n1)"
  [ -n "$JAR" ] || falla "API jar not found in build/libs"

  log "starting the API on :$PUERTO_API (profile local, push provider registro)"
  env "${API_ENTORNO[@]}" \
    TT_JWT_CLAVE_PUBLICA="file:$WORK/claves/publica.pem" \
    TT_JWT_CLAVE_PRIVADA="file:$WORK/claves/privada.pem" \
    java -jar "$API_REPO/$JAR" >"$WORK/api.log" 2>&1 &
fi
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
# unstable movement. The `registro` provider logs it with the number of devices. It goes out right
# after the agent's request commits, on another thread, so wait a few seconds for it.
PUSH=""
push_enviado() {
  PUSH="$(grep -m1 -E "Push ALERTA_(CAIDA|ACTUALIZADA_A_CAIDA) \(sin enviar, proveedor registro\) a 1 dispositivo.*alertaId=$ALERTA_ID" "$WORK/api.log" || true)"
  [ -n "$PUSH" ]
}
esperar 10 push_enviado || falla "push provider invoked for the CAIDA alert"
notificada() {
  ALERTA="$(llamar GET "/api/alertas/$ALERTA_ID")"
  [ "$(jq -r .notificadaEn <<<"$ALERTA")" != null ] && [ "$(jq -r .estadoAviso <<<"$ALERTA")" = ENTREGADO ]
}
esperar 10 notificada || falla "alert marked as notified (notificadaEn, estadoAviso ENTREGADO)"

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
push_confirmada() { grep -q 'Push CAIDA_CONFIRMADA (sin enviar, proveedor registro)' "$WORK/api.log"; }
esperar 10 push_confirmada || falla "push provider invoked for CAIDA_CONFIRMADA"
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

# ---------------------------------------------------------------- 6. live view wiring (API ↔ MediaMTX)
# TODO: once te-tengo-desktop-pywebview publishes live view to MediaMTX (AGENT_CONTRACT.md, "Live
# view"), also assert that the agent receives transmitir:true and that the playlist is served (200)
# once it publishes. Until then MediaMTX has no publisher, so an authorized read answers 404 and an
# unauthorized one 401.
CAMARA_ID="$(llamar GET /api/camaras | jq -r '.[0].id')"
SESION="$(llamar POST "/api/camaras/$CAMARA_ID/vista-en-vivo" '{"alertaId":null}')"
URL_VIVO="$(jq -r .urlTransmision <<<"$SESION")"
[[ "$URL_VIVO" == "http://localhost:$TT_MEDIAMTX_PUERTO_HLS/camaras/$CAMARA_ID/index.m3u8?token="* ]] ||
  falla "live view session with an LL-HLS urlTransmision (got: $SESION)"
URL_WEBRTC="$(jq -r .urlWebrtc <<<"$SESION")"
[[ "$URL_WEBRTC" == "http://localhost:$TT_MEDIAMTX_PUERTO_WEBRTC/camaras/$CAMARA_ID/whep?token="* ]] ||
  falla "live view session with a WHEP urlWebrtc (got: $SESION)"
leer_vivo() { curl -sL -o /dev/null -w '%{http_code}' "$1"; }
# A recvonly H.264 offer, as a browser sends it (MediaMTX checks the token before the stream). SDP
# lines end with CRLF, the last one too, so it is piped rather than captured with $(...).
oferta_sdp() {
  printf '%s\r\n' 'v=0' 'o=- 1 2 IN IP4 127.0.0.1' 's=-' 't=0 0' 'a=group:BUNDLE 0' \
    'm=video 9 UDP/TLS/RTP/SAVPF 96' 'c=IN IP4 0.0.0.0' 'a=ice-ufrag:ttee' 'a=ice-pwd:tetengoendtoendtestpwd0' \
    'a=fingerprint:sha-256 7B:8B:F0:65:5F:78:E2:51:3B:AC:6F:F3:3F:46:1B:35:DC:B8:5F:64:1A:24:C2:43:F0:A1:58:D0:A1:2C:19:08' \
    'a=setup:actpass' 'a=mid:0' 'a=recvonly' 'a=rtcp-mux' 'a=rtpmap:96 H264/90000' \
    'a=fmtp:96 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f'
}
leer_webrtc() {
  oferta_sdp | curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/sdp' --data-binary @- "$1"
}
CON_TOKEN="$(leer_vivo "$URL_VIVO")"
SIN_TOKEN="$(leer_vivo "${URL_VIVO%%\?*}")"
[[ "$CON_TOKEN" =~ ^(200|404)$ ]] && [ "$SIN_TOKEN" = 401 ] ||
  falla "MediaMTX authorizes through the API (with token: $CON_TOKEN, expected 200 or 404; without: $SIN_TOKEN, expected 401)"
WHEP_CON_TOKEN="$(leer_webrtc "$URL_WEBRTC")"
WHEP_SIN_TOKEN="$(leer_webrtc "${URL_WEBRTC%%\?*}")"
[[ "$WHEP_CON_TOKEN" =~ ^(201|404)$ ]] && [ "$WHEP_SIN_TOKEN" = 401 ] ||
  falla "MediaMTX authorizes WHEP through the API (with token: $WHEP_CON_TOKEN, expected 201 or 404; without: $WHEP_SIN_TOKEN, expected 401)"
[ "$(llamar DELETE "/api/vista-en-vivo/$(jq -r .sesionId <<<"$SESION")" '' -o /dev/null -w '%{http_code}')" = 204 ] ||
  falla "close the live view session"
[ "$(leer_vivo "$URL_VIVO")" = 401 ] || falla "MediaMTX denies the token of a closed session"
[ "$(leer_webrtc "$URL_WEBRTC")" = 401 ] || falla "MediaMTX denies the token of a closed session over WHEP"
ok "live view: session opened, MediaMTX authorized its token through the API (HLS $CON_TOKEN, WHEP $WHEP_CON_TOKEN) and denied it once closed"
