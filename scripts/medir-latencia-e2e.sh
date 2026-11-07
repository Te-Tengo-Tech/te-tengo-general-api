#!/usr/bin/env bash
# End-to-end latency of a fall alert, split by stage, over many URFD falls.
#
# Like scripts/e2e.sh it starts an isolated stack (own compose project and host ports), runs this
# API on it, seeds the demo household and plays URFD fall videos through the real desktop agent
# without its UI (`--sin-interfaz`). Unlike e2e.sh it repeats the fall once per video, restarting the
# agent each time, and times every stage on this machine's clock:
#
#   t0  the first frame labelled "falling" in the URFD per-frame CSV is handed to the agent
#       (scripts/latencia-e2e/agente_con_marcas.py wraps the agent's video source to log it)
#   t1  the agent's classifier emits `caida`          (agent log: «Evento detectado: caida <id>»)
#   t2  the API accepted the event with a 2xx          (agent log: «Evento <id> enviado»)
#   tc  the event's 12 s clip is uploaded              (agent log: «Clip del evento <id> subido»)
#   t3  the API logs the push result: with TT_PUSH_PROVEEDOR=fcm, FCM's answer
#       (API log: «Push ALERTA_CAIDA (alerta <id>) aceptado por FCM …», or the refusal)
#   t3a/t3b  with TT_LAT_DEBUG=1: the API's first FCM HTTP request for the alert leaves / FCM's
#       first answer arrives (Google HTTP client log). If FCM refuses (e.g. THIRD_PARTY_AUTH_ERROR
#       when the Firebase project has no APNs key), the Admin SDK retries and t3 grows; t3b does not
#   t4  the push reached the device: with TT_LAT_SIMULADOR=1, the booted iOS simulator's
#       notification log (only meaningful with fcm and the app's real token registered)
#
# After each fall the alert is attended through the API, so no escalation push follows. Results:
# $WORK/mediciones.csv (one row per video) and $WORK/resumen.md (median, IQR, min–max per stage).
#
# Usage: scripts/medir-latencia-e2e.sh [video-name ...]     (default: fall-01 … fall-30 of URFD)
# Requires: Docker, JDK 25, jq, curl, openssl, python3, uv, te-tengo-desktop-pywebview checked out
# with the URFD videos and labels in datos/urfd (its scripts/descargar_datasets.py).
#
# Environment (all optional; ports as in scripts/e2e.sh, with other defaults so both can run):
#   TT_E2E_ESCRITORIO       desktop agent repository        (default: ../te-tengo-desktop-pywebview)
#   TT_LAT_DIR              work directory                  (default: a new temporary directory)
#   TT_LAT_SOSTENER         seconds the last frame is held   (default: 25; < 30 s, so the agent never
#                           sends `caida_confirmada` before it is stopped)
#   TT_PUSH_PROVEEDOR       registro (default) or fcm; fcm needs TT_FCM_CREDENCIALES (service-account
#                           JSON of a Firebase project you may send test pushes with)
#   TT_LAT_TOKEN_PUSH       register this push token for the demo account (e.g. a test phone's)
#   TT_LAT_ESPERAR_APP=1    instead, wait up to 15 min for the mobile app to sign in as the demo
#                           account (DEMO_CORREO / DEMO_CONTRASENA) and register its own token
#   TT_LAT_SIMULADOR=1      record the booted iOS simulator's notification log (t4)
#   TT_E2E_SIN_BUILD=1      reuse build/libs/*.jar instead of running `./gradlew bootJar`
#   TT_E2E_PUERTO_API (18080), TT_POSTGRES_PUERTO (15433), TT_FLOCI_PUERTO (14567),
#   TT_MEDIAMTX_PUERTO_RTSP/_HLS/_WEBRTC/_ICE/_API (18555/18898/18899/18199/19998)
set -euo pipefail
cd "$(dirname "$0")/.."
API_REPO="$PWD"

ESCRITORIO="$(cd "${TT_E2E_ESCRITORIO:-../te-tengo-desktop-pywebview}" && pwd)"
PUERTO_API="${TT_E2E_PUERTO_API:-18080}"
export TT_POSTGRES_PUERTO="${TT_POSTGRES_PUERTO:-15433}"
export TT_FLOCI_PUERTO="${TT_FLOCI_PUERTO:-14567}"
export TT_MEDIAMTX_PUERTO_RTSP="${TT_MEDIAMTX_PUERTO_RTSP:-18555}"
export TT_MEDIAMTX_PUERTO_HLS="${TT_MEDIAMTX_PUERTO_HLS:-18898}"
export TT_MEDIAMTX_PUERTO_WEBRTC="${TT_MEDIAMTX_PUERTO_WEBRTC:-18899}"
export TT_MEDIAMTX_PUERTO_ICE="${TT_MEDIAMTX_PUERTO_ICE:-18199}"
export TT_MEDIAMTX_PUERTO_API="${TT_MEDIAMTX_PUERTO_API:-19998}"
export TT_API_PUERTO="$PUERTO_API"
TT_VIVO_SECRETO_AUTORIZACION="lat-$(openssl rand -hex 16)"
export TT_VIVO_SECRETO_AUTORIZACION
export COMPOSE_PROJECT_NAME="${TT_LAT_PROYECTO:-tt-latencia}"
PROVEEDOR="${TT_PUSH_PROVEEDOR:-registro}"
SOSTENER="${TT_LAT_SOSTENER:-25}"
WORK="${TT_LAT_DIR:-$(mktemp -d -t tt-latencia.XXXXXX)}"
mkdir -p "$WORK/corridas"
WORK="$(cd "$WORK" && pwd)"
API="http://localhost:$PUERTO_API"
AQUI="$API_REPO/scripts/latencia-e2e"
# Test values of the throwaway demo account on this isolated stack (seed-demo.sh reads them).
export DEMO_CORREO="${DEMO_CORREO:-carmen@tetengo.test}"
export DEMO_CONTRASENA="${DEMO_CONTRASENA:-TeTengo-demo-2026}"

API_PID="" AGENTE_PID="" SIM_PID=""
INICIO=$SECONDS
log() { printf '[latencia %4ds] %s\n' $((SECONDS - INICIO)) "$*"; }
falla() {
  log "FAIL $1"
  exit 1
}

detener() { # pid [seconds]
  local pid=$1 limite=${2:-20}
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null || return 0
  kill -TERM "$pid" 2>/dev/null || true
  for _ in $(seq 1 $((limite * 2))); do kill -0 "$pid" 2>/dev/null || return 0; sleep 0.5; done
  kill -KILL "$pid" 2>/dev/null || true
}

limpiar() {
  local codigo=$?
  trap - EXIT INT TERM
  detener "$AGENTE_PID" 10
  detener "$SIM_PID" 5
  detener "$API_PID" 15
  docker compose down -v --remove-orphans >"$WORK/compose-down.log" 2>&1 || true
  log "stack stopped; logs and results in $WORK (exit $codigo)"
  exit "$codigo"
}
trap limpiar EXIT
trap 'exit 130' INT TERM

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
for herramienta in docker jq curl openssl python3 uv java; do
  command -v "$herramienta" >/dev/null || falla "requirement: $herramienta is not installed"
done
[ -f "$ESCRITORIO/datos/urfd/urfall-cam0-falls.csv" ] || falla "URFD labels not found in $ESCRITORIO/datos/urfd"
if [ "$PROVEEDOR" = fcm ]; then
  [ -r "${TT_FCM_CREDENCIALES:-}" ] || falla "TT_PUSH_PROVEEDOR=fcm needs TT_FCM_CREDENCIALES (a readable service-account JSON)"
fi
for puerto in "$PUERTO_API" "$TT_POSTGRES_PUERTO" "$TT_FLOCI_PUERTO" "$TT_MEDIAMTX_PUERTO_RTSP" \
  "$TT_MEDIAMTX_PUERTO_HLS" "$TT_MEDIAMTX_PUERTO_WEBRTC" "$TT_MEDIAMTX_PUERTO_ICE" "$TT_MEDIAMTX_PUERTO_API"; do
  if (exec 3<>"/dev/tcp/127.0.0.1/$puerto") 2>/dev/null; then
    falla "requirement: port $puerto is already in use"
  fi
done
if [ $# -gt 0 ]; then VIDEOS=("$@"); else VIDEOS=(); for i in $(seq -w 1 30); do VIDEOS+=("fall-$i"); done; fi
log "work directory $WORK; provider $PROVEEDOR; ${#VIDEOS[@]} videos; last frame held ${SOSTENER}s"

# ---------------------------------------------------------------- 1. agent and fall clips
log "preparing the desktop agent (uv sync, MediaPipe model) and the clips"
(cd "$ESCRITORIO" && uv sync --locked --quiet && ./scripts/descargar_modelo.sh >/dev/null) \
  >"$WORK/escritorio-preparacion.log" 2>&1 || falla "desktop agent setup (see $WORK/escritorio-preparacion.log)"
# As in e2e.sh: only the RGB half of each URFD frame, lossless (FFV1), last frame held. The onset is
# the first frame labelled 0 ("falling") in urfall-cam0-falls.csv, (frame - 1) / 30 s, as in the
# agent's validation (scripts/barrer_rangos.py).
"$ESCRITORIO/.venv/bin/python" - "$ESCRITORIO/datos/urfd" "$WORK/clips" "$SOSTENER" "${VIDEOS[@]}" \
  >"$WORK/clips.tsv" 2>"$WORK/clips.log" <<'PY' || falla "clip preparation (see $WORK/clips.log)"
import csv
import sys
from pathlib import Path

import cv2

datos, destino, sostener, nombres = Path(sys.argv[1]), Path(sys.argv[2]), float(sys.argv[3]), sys.argv[4:]
destino.mkdir(parents=True, exist_ok=True)
inicios: dict[str, int] = {}
with (datos / "urfall-cam0-falls.csv").open(newline="") as archivo:
    for fila in csv.reader(archivo):
        if fila and fila[2].strip() == "0":
            inicios.setdefault(fila[0], int(fila[1]))
for nombre in nombres:
    video = cv2.VideoCapture(str(datos / f"{nombre}-cam0.mp4"))
    fps = video.get(cv2.CAP_PROP_FPS) or 30.0
    salida_ruta = destino / f"{nombre}.avi"
    salida = cv2.VideoWriter(str(salida_ruta), cv2.VideoWriter_fourcc(*"FFV1"), fps, (320, 240))
    ultimo, cuadros = None, 0
    while True:
        leido, cuadro = video.read()
        if not leido:
            break
        ultimo = cuadro[0:240, 320:640]
        salida.write(ultimo)
        cuadros += 1
    if ultimo is None:
        sys.exit(f"no frames in {nombre}")
    for _ in range(int(fps * sostener)):
        salida.write(ultimo)
    salida.release()
    print(f"{nombre}\t{salida_ruta}\t{(inicios[nombre] - 1) / 30:.4f}\t{cuadros / fps:.3f}")
PY
log "clips ready ($(wc -l <"$WORK/clips.tsv" | tr -d ' '))"

# ---------------------------------------------------------------- 2. isolated stack and API
log "starting PostgreSQL, Floci and MediaMTX (docker compose -p $COMPOSE_PROJECT_NAME)"
docker compose up -d --wait >"$WORK/compose-up.log" 2>&1 || falla "docker compose up (see $WORK/compose-up.log)"
esperar 60 docker compose exec -T postgres pg_isready -q -U tetengo -d tetengo || falla "PostgreSQL ready"

mkdir -p "$WORK/claves"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$WORK/claves/privada.pem" 2>/dev/null
openssl rsa -in "$WORK/claves/privada.pem" -pubout -out "$WORK/claves/publica.pem" 2>/dev/null
if [ "${TT_E2E_SIN_BUILD:-}" != 1 ]; then
  log "building the API (./gradlew bootJar)"
  ./gradlew bootJar --quiet >"$WORK/gradle.log" 2>&1 || falla "API build (see $WORK/gradle.log)"
fi
# The newest jar: build/libs keeps the jars of older versions too.
JAR="$(ls -t build/libs/*.jar 2>/dev/null | grep -v -- '-plain\.jar$' | head -n1 || true)"
[ -n "$JAR" ] || falla "API jar not found in build/libs"
API_ENTORNO=(
  SPRING_PROFILES_ACTIVE=local
  SERVER_PORT="$PUERTO_API"
  SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$TT_POSTGRES_PUERTO/tetengo"
  SPRING_DATASOURCE_USERNAME=tetengo
  SPRING_DATASOURCE_PASSWORD=tetengo
  TT_FLOCI_PUERTO="$TT_FLOCI_PUERTO"
  TT_VIVO_SECRETO_AUTORIZACION="$TT_VIVO_SECRETO_AUTORIZACION"
  TT_PUSH_PROVEEDOR="$PROVEEDOR"
  TT_CORREO_PROVEEDOR=registro
  TT_VIVO_URL_PUBLICACION="rtsp://localhost:$TT_MEDIAMTX_PUERTO_RTSP/camaras/{camaraId}"
  TT_VIVO_URL_HLS="http://localhost:$TT_MEDIAMTX_PUERTO_HLS"
  TT_VIVO_URL_WEBRTC="http://localhost:$TT_MEDIAMTX_PUERTO_WEBRTC/camaras/{camaraId}/whep"
  TT_VIVO_MEDIAMTX_API="http://localhost:$TT_MEDIAMTX_PUERTO_API"
  TT_JWT_CLAVE_PUBLICA="file:$WORK/claves/publica.pem"
  TT_JWT_CLAVE_PRIVADA="file:$WORK/claves/privada.pem"
)
[ "$PROVEEDOR" != fcm ] || API_ENTORNO+=(TT_FCM_CREDENCIALES="$TT_FCM_CREDENCIALES")
# TT_LAT_DEBUG=1: the API's and Firebase's DEBUG lines, to split the API → FCM stage further.
[ "${TT_LAT_DEBUG:-}" != 1 ] || API_ENTORNO+=(LOGGING_LEVEL_TECH_TETENGO=DEBUG LOGGING_LEVEL_COM_GOOGLE=DEBUG)
log "starting the API $JAR on :$PUERTO_API (profile local, push provider $PROVEEDOR)"
env "${API_ENTORNO[@]}" java -jar "$API_REPO/$JAR" >"$WORK/api.log" 2>&1 &
API_PID=$!
salud() {
  kill -0 "$API_PID" 2>/dev/null || falla "API process exited (see $WORK/api.log)"
  [ "$(curl -s "$API/actuator/health" | jq -r .status 2>/dev/null)" = UP ]
}
esperar 180 salud || falla "API health UP"

# ---------------------------------------------------------------- 3. demo household and device
TT_API="$API" scripts/seed-demo.sh "$WORK/agente.toml" >"$WORK/seed.log" 2>&1 || falla "seed-demo.sh (see $WORK/seed.log)"
TOKEN="$(llamar POST /api/sesiones "$(jq -nc --arg c "$DEMO_CORREO" --arg p "$DEMO_CONTRASENA" '{correo:$c,contrasena:$p}')" | jq -r .tokenAcceso)"
[ -n "$TOKEN" ] && [ "$TOKEN" != null ] || falla "sign in as the demo family account"
if [ -n "${TT_LAT_TOKEN_PUSH:-}" ]; then
  [ "$(llamar POST /api/dispositivos "$(jq -nc --arg t "$TT_LAT_TOKEN_PUSH" --arg p "${TT_LAT_PLATAFORMA:-IOS}" '{tokenPush:$t,plataforma:$p}')" -o /dev/null -w '%{http_code}')" = 201 ] ||
    falla "device registration"
elif [ "${TT_LAT_ESPERAR_APP:-}" = 1 ]; then
  log "waiting for the mobile app: sign in as $DEMO_CORREO against $API and allow notifications"
  con_dispositivo() {
    [ "$(docker compose exec -T postgres psql -U tetengo -d tetengo -At -c 'select count(*) from dispositivos where activo')" -ge 1 ]
  }
  esperar 900 con_dispositivo || falla "no device registered by the app"
else
  [ "$(llamar POST /api/dispositivos '{"tokenPush":"latencia-e2e","plataforma":"ANDROID"}' -o /dev/null -w '%{http_code}')" = 201 ] ||
    falla "device registration"
fi
docker compose exec -T postgres psql -U tetengo -d tetengo -At \
  -c "select plataforma, length(token_push) from dispositivos where activo" >"$WORK/dispositivos.txt"
log "demo household seeded; active devices (platform, token length): $(tr '\n' ' ' <"$WORK/dispositivos.txt")"

if [ "${TT_LAT_SIMULADOR:-}" = 1 ]; then
  xcrun simctl spawn booted log stream --style compact --level info \
    --predicate 'subsystem == "com.apple.apsd" OR subsystem == "com.apple.UserNotifications" OR subsystem == "com.google.firebase" OR process == "Runner"' \
    >"$WORK/simulador.log" 2>&1 &
  SIM_PID=$!
  log "recording the iOS simulator's notification log"
fi

# ---------------------------------------------------------------- 4. one fall per agent run
atender_activas() {
  local id
  for id in $(llamar GET '/api/alertas' | jq -r '.elementos[] | select(.estado == "ACTIVA") | .id'); do
    llamar POST "/api/alertas/$id/atencion" '{}' -o /dev/null || true
  done
}

while IFS=$'\t' read -r NOMBRE CLIP INICIO_CAIDA DURACION; do
  DIR="$WORK/corridas/$NOMBRE"
  mkdir -p "$DIR"
  printf '%s\n' "$INICIO_CAIDA" >"$DIR/inicio_caida.txt"
  log "$NOMBRE: onset at ${INICIO_CAIDA}s of ${DURACION}s"
  TT_LAT_INICIO_CAIDA="$INICIO_CAIDA" "$ESCRITORIO/.venv/bin/python" "$AQUI/agente_con_marcas.py" \
    --config "$WORK/agente.toml" --video "$CLIP" --sin-interfaz \
    --datos "$DIR/agente-datos" --logs "$DIR/agente-logs" >"$DIR/agente.log" 2>&1 &
  AGENTE_PID=$!
  # Until the fall's clip is uploaded, or the video and the hold are over (the classifier missed it).
  terminado() {
    kill -0 "$AGENTE_PID" 2>/dev/null || return 0
    local id
    id="$(grep -m1 -oE 'Evento detectado: caida [0-9a-f-]+' "$DIR/agente.log" | awk '{print $4}' || true)"
    if [ -n "$id" ] && grep -q "Clip del evento $id subido" "$DIR/agente.log"; then return 0; fi
    local primero
    primero="$(grep -m1 -oE 'primer_cuadro epoch=[0-9.]+' "$DIR/agente.log" | cut -d= -f2 || true)"
    [ -n "$primero" ] && python3 -c "import sys,time; sys.exit(0 if time.time() > $primero + $DURACION + $SOSTENER - 2 else 1)"
  }
  esperar $((90 + SOSTENER)) terminado || log "$NOMBRE: timed out"
  sleep 1
  detener "$AGENTE_PID" 10
  AGENTE_PID=""
  ALERTA="$(llamar GET '/api/alertas?tipo=CAIDA' | jq -c '.elementos[0] // empty')"
  ID_ALERTA="$(jq -r '.id // empty' <<<"$ALERTA")"
  if [ -n "$ID_ALERTA" ] && ! grep -qx "$ID_ALERTA" "$WORK/alertas-vistas.txt" 2>/dev/null; then
    echo "$ID_ALERTA" >>"$WORK/alertas-vistas.txt"
    printf '%s\n' "$ALERTA" >"$DIR/alerta.json"
    log "$NOMBRE: alert $ID_ALERTA ($(jq -r .estadoAviso <<<"$ALERTA"))"
  else
    log "$NOMBRE: no new fall alert"
  fi
  sleep 3
  atender_activas
  sleep 2
done <"$WORK/clips.tsv"

sleep 5
detener "$SIM_PID" 5
SIM_PID=""
python3 "$AQUI/analizar.py" "$WORK" | tee "$WORK/resumen.md"
