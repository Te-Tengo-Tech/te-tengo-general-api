#!/usr/bin/env bash
# End-to-end latency of a fall alert against the PRODUCTION Te Tengo API, with a real Android phone
# on mobile data connected to this Mac by USB.
#
# Unlike scripts/medir-latencia-e2e.sh nothing local is started: the real desktop agent runs as a PC
# linked (OAuth 2.0 device flow, US-28) to the family's own TEST household in production, replays
# URFD falls without its UI, and the phone shows the real push. Stages, on this machine's clock
# (scripts/latencia-e2e/analizar_produccion.py): t0 fall onset (URFD labels) → t1 agent `caida` →
# t2 production API 2xx → tc clip uploaded; t4/t4n push and notification on the phone (adb logcat,
# with the clock offset measured by desfase_adb.py). t3 (the API's log) is not available.
#
# Nothing here creates accounts, households or consents, or changes production configuration: the
# family creates its own test account, household and consent in the app, approves this PC's code,
# and puts its camera in a room. Every push goes to that household only.
#
# Usage:
#   scripts/latencia-e2e/medir_produccion.sh comprobar          checks only (phone, network, API, link)
#   scripts/latencia-e2e/medir_produccion.sh vincular           link this isolated PC (shows the code,
#                                                               waits for the approval and the camera)
#   scripts/latencia-e2e/medir_produccion.sh serie [video ...]  the series (default fall-01 … fall-30);
#                                                               links first if needed
#
# Isolation: the agent's data, logs, configuration and credentials (a 0600 JSON file instead of the
# macOS Keychain) live in TT_LAT_AGENTE (default ~/.tetengo-latencia-produccion), never in the real
# Te Tengo Captura's folders or Keychain entries; it never registers to start with the system, never
# updates itself and reports one synthetic webcam, «Video URFD (medición)».
# To unlink it afterwards: remove the PC «Medición latencia (Mac)» in the app, then delete that folder.
#
# Requires: adb (Android SDK platform-tools), jq, curl, python3, uv, te-tengo-desktop-pywebview
# checked out with its .venv, the MediaPipe model and the URFD videos (scripts/descargar_datasets.py).
#
# Environment (all optional):
#   TT_E2E_ESCRITORIO      desktop agent repository     (default: ../te-tengo-desktop-pywebview)
#   TT_LAT_API_URL         production API               (default: https://api.tetengo.reqsai.tech)
#   TT_LAT_AGENTE          isolated agent directory     (default: ~/.tetengo-latencia-produccion)
#   TT_LAT_DIR             results directory            (default: a new temporary directory)
#   TT_LAT_ESPERA_PREVIA   seconds the standing person is shown before each fall (default 45): the
#                          pause between falls, with the agent running (heartbeat), so the API never
#                          marks the camera disconnected
#   TT_LAT_SOSTENER        seconds the last frame is held (default 25; < 30 s: no `caida_confirmada`)
#   TT_LAT_PERMITIR_WIFI=1 do not require mobile data with Wi-Fi off
#   ANDROID_SERIAL         the phone, when adb sees more than one device
set -euo pipefail
ESTE="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
cd "$(dirname "$0")/../.."
API_REPO="$PWD"
AQUI="$API_REPO/scripts/latencia-e2e"
# The desktop repository next to this one (or next to the main checkout, from a worktree).
for candidato in "${TT_E2E_ESCRITORIO:-}" ../te-tengo-desktop-pywebview ../../te-tengo-desktop-pywebview; do
  [ -n "$candidato" ] && [ -d "$candidato" ] && break
done
ESCRITORIO="$(cd "$candidato" && pwd)" || { echo "desktop agent repository not found (TT_E2E_ESCRITORIO)" >&2; exit 2; }
PY="$ESCRITORIO/.venv/bin/python"
API="${TT_LAT_API_URL:-https://api.tetengo.reqsai.tech}"
AGENTE="${TT_LAT_AGENTE:-$HOME/.tetengo-latencia-produccion}"
PREVIA="${TT_LAT_ESPERA_PREVIA:-45}"
SOSTENER="${TT_LAT_SOSTENER:-25}"
PAQUETE=tech.tetengo.te_tengo
export PATH="$PATH:${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools"

MODO="${1:-}"
[ $# -gt 0 ] && shift
case "$MODO" in comprobar | vincular | serie) ;; *)
  sed -n '16,21p' "$ESTE"
  exit 2
  ;;
esac

WORK="${TT_LAT_DIR:-$(mktemp -d -t tt-latencia-prod.XXXXXX)}"
mkdir -p "$WORK/corridas"
WORK="$(cd "$WORK" && pwd)"
AGENTE_PID="" ADB_PID=""
INICIO=$SECONDS
log() { printf '[latencia-prod %4ds] %s\n' $((SECONDS - INICIO)) "$*"; }
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
  detener "$AGENTE_PID" 15
  detener "$ADB_PID" 5
  log "results and logs in $WORK (exit $codigo)"
  exit "$codigo"
}
trap limpiar EXIT
trap 'exit 130' INT TERM

# ---------------------------------------------------------------- isolated agent
mkdir -p "$AGENTE/datos" "$AGENTE/logs"
chmod 700 "$AGENTE"
CONFIG="$AGENTE/config.toml"
[ -f "$CONFIG" ] || printf 'api_url = "%s"\n' "$API" >"$CONFIG"
grep -q "^api_url = \"$API\"$" "$CONFIG" || falla "$CONFIG does not point at $API"
export TT_LAT_LLAVERO="$AGENTE/credenciales.json"
# Safety net: if anything still asked keyring for the system store, it fails instead of reaching
# the Keychain of a real Te Tengo Captura on this Mac.
export PYTHON_KEYRING_BACKEND=keyring.backends.fail.Keyring

vinculada() { [ -s "$TT_LAT_LLAVERO" ] && jq -e 'has("TeTengoCaptura/pc-renovacion")' "$TT_LAT_LLAVERO" >/dev/null 2>&1; }

arrancar_agente() { # clip onset_s log_file
  TT_LAT_INICIO_CAIDA="$2" "$PY" "$AQUI/agente_produccion.py" --config "$CONFIG" --video "$1" \
    --sin-interfaz --datos "$AGENTE/datos" --logs "$AGENTE/logs" >"$3" 2>&1 </dev/null &
  AGENTE_PID=$!
}

esperar() { # seconds command...
  local limite=$((SECONDS + $1))
  shift
  until "$@"; do
    [ $SECONDS -lt $limite ] || return 1
    sleep 1
  done
}

# ---------------------------------------------------------------- 0. requirements and context
for herramienta in adb jq curl python3; do
  command -v "$herramienta" >/dev/null || falla "requirement: $herramienta is not installed"
done
[ -x "$PY" ] || falla "the desktop agent has no .venv (cd $ESCRITORIO && uv sync)"
[ -f "$ESCRITORIO/models/pose_landmarker_full.task" ] || falla "MediaPipe model missing (cd $ESCRITORIO && make modelo)"
[ -f "$ESCRITORIO/datos/urfd/urfall-cam0-falls.csv" ] || falla "URFD videos missing (cd $ESCRITORIO && make datasets)"
log "API $API; agent $("$PY" -c 'import te_tengo_captura as t; print(t.__version__)' 2>/dev/null) from $ESCRITORIO; isolated in $AGENTE; results in $WORK"

# Plain round trip to production, read-only (context for t1→t2): a new connection each time.
echo "i,codigo,dns_s,conexion_s,tls_s,primer_byte_s,total_s" >"$WORK/salud.csv"
for i in $(seq 1 20); do
  curl -s -o /dev/null --max-time 10 -w "$i,%{http_code},%{time_namelookup},%{time_connect},%{time_appconnect},%{time_starttransfer},%{time_total}\n" \
    "$API/actuator/health" >>"$WORK/salud.csv" || echo "$i,000,,,,," >>"$WORK/salud.csv"
  sleep 0.5
done
SALUD="$(awk -F, 'NR>1 && $2==200 {print $7}' "$WORK/salud.csv" | sort -n | awk '{a[NR]=$1} END {if (NR) printf "%d/20 OK, median %.0f ms, min %.0f ms, max %.0f ms", NR, a[int((NR+1)/2)]*1000, a[1]*1000, a[NR]*1000}')"
log "GET /actuator/health from this Mac: ${SALUD:-no answer}"
[ -n "$SALUD" ] || falla "production API does not answer"

# ---------------------------------------------------------------- 1. phone and network
comprobar_telefono() {
  local estados n
  estados="$(adb devices | awk 'NR>1 && NF>=2 {print $2}')"
  n="$(grep -c . <<<"$estados" || true)"
  [ "$n" -ge 1 ] || falla "adb sees no device: connect the phone by USB and enable USB debugging"
  if [ -z "${ANDROID_SERIAL:-}" ] && [ "$n" -gt 1 ]; then falla "adb sees $n devices: set ANDROID_SERIAL"; fi
  adb get-state 2>/dev/null | grep -qx device ||
    falla "the phone is '$(tr '\n' ' ' <<<"$estados")': accept «Allow USB debugging» on the phone"
  TELEFONO="$(adb shell getprop ro.product.manufacturer | tr -d '\r') $(adb shell getprop ro.product.model | tr -d '\r')"
  ANDROID="$(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"
  adb shell pm list packages "$PAQUETE" | tr -d '\r' | grep -qx "package:$PAQUETE" ||
    falla "the Te Tengo app ($PAQUETE) is not installed on the phone"
  # Active network: the default network's NetworkAgentInfo in `dumpsys connectivity`
  # («NetworkAgentInfo [MOBILE (LTE) - 506]» on Android 7–9; «Transports: CELLULAR» later).
  local conectividad id_red linea
  conectividad="$(adb shell dumpsys connectivity | tr -d '\r')"
  id_red="$(grep -m1 'Active default network' <<<"$conectividad" | awk '{print $NF}' || true)"
  linea="$(grep -m1 -oE "NetworkAgentInfo \[[A-Z_]+ \([^)]*\) - $id_red\]" <<<"$conectividad" || true)"
  RED_TIPO="$(sed -E 's/NetworkAgentInfo \[([A-Z_]+) .*/\1/' <<<"$linea")"
  RED_SUBTIPO="$(sed -E 's/.*\(([^)]*)\).*/\1/' <<<"$linea")"
  if [ -z "$RED_TIPO" ]; then
    RED_TIPO="$(grep -m1 -oE 'Transports: [A-Z|]+' <<<"$conectividad" | sed 's/Transports: //' || true)"
  fi
  [ -n "$RED_SUBTIPO" ] || RED_SUBTIPO="$(adb shell getprop gsm.network.type | tr -d '\r')"
  OPERADOR="$(adb shell getprop gsm.operator.alpha | tr -d '\r')"
  local wifi_on
  wifi_on="$(adb shell settings get global wifi_on | tr -d '\r')"
  WIFI="$([ "$wifi_on" = 0 ] && echo apagado || echo "encendido (wifi_on=$wifi_on)")"
  CARGANDO="$(adb shell dumpsys battery | tr -d '\r' | grep -E 'USB powered: true' >/dev/null && echo sí || echo no)"
  log "phone: $TELEFONO, Android $ANDROID; network ${RED_TIPO:-?} ${RED_SUBTIPO:+($RED_SUBTIPO)}, operator ${OPERADOR:-?}, Wi-Fi $WIFI, USB charging $CARGANDO"
  if [ "${TT_LAT_PERMITIR_WIFI:-}" != 1 ]; then
    [ "$wifi_on" = 0 ] || falla "turn Wi-Fi OFF on the phone (mobile data only), or TT_LAT_PERMITIR_WIFI=1"
    grep -qiE 'mobile|cellular' <<<"$RED_TIPO" || falla "the phone's active network is '${RED_TIPO:-none}', not mobile data"
  fi
}

# ---------------------------------------------------------------- comprobar
if [ "$MODO" = comprobar ]; then
  comprobar_telefono
  python3 "$AQUI/desfase_adb.py" "$WORK/android-desfase.csv" 10
  if vinculada; then log "this isolated PC is linked (credentials in $TT_LAT_LLAVERO)"; else log "this isolated PC is NOT linked yet: run «vincular»"; fi
  exit 0
fi

# ---------------------------------------------------------------- clips
# One clip is ~120 MB (lossless, 45 s standing + fall + 25 s held): each is prepared (~5 s) while the
# previous fall's standing lead-in plays, and deleted after its run.
mkdir -p "$WORK/clips"
preparar_clip() { # video lead-in hold|solo
  "$PY" "$AQUI/preparar_clips.py" "$ESCRITORIO/datos/urfd" "$WORK/clips" "$2" "$3" "$1" \
    >"$WORK/clips/$1.tsv" 2>>"$WORK/clips.log"
}

# ---------------------------------------------------------------- vincular
vincular_pc() {
  # While linking, the camera plays fall-01's first frame (a person standing) in a loop: no fall.
  preparar_clip fall-01 10 solo || falla "clip preparation (see $WORK/clips.log)"
  arrancar_agente "$(cut -f2 "$WORK/clips/fall-01.tsv")" 100000 "$WORK/vincular.log"
  if vinculada; then
    log "already linked: checking that the camera is in use"
  else
    esperar 60 grep -q 'Código para vincular esta PC' "$WORK/vincular.log" || falla "no link code (see $WORK/vincular.log)"
    CODIGO="$(grep -m1 -oE 'Código para vincular esta PC: [^ ]+' "$WORK/vincular.log" | awk '{print $NF}')"
    URL="$(grep -m1 'Código para vincular esta PC' "$WORK/vincular.log" | awk -F' · ' '{print $NF}')"
    printf '\n  ┌──────────────────────────────────────────────────────────────┐\n'
    printf '  │  Código para vincular «Medición latencia (Mac)»:  %-10s │\n' "$CODIGO"
    printf '  └──────────────────────────────────────────────────────────────┘\n'
    printf '  En la app (cuenta y hogar de PRUEBA): Agregar PC / vincular PC e ingresa el código,\n'
    printf '  o abre en el teléfono: %s\n  Vence en 10–15 min; el agente pide otro solo si vence.\n\n' "$URL"
    esperar 1800 grep -q 'Esta PC quedó vinculada' "$WORK/vincular.log" || falla "the code was not approved in 30 min"
    log "linked"
  fi
  log "now, in the app: put the PC's camera «Video URFD (medición)» in a room (e.g. «Sala»)"
  esperar 900 grep -qE 'Cámara [0-9a-f-]+ en uso' "$WORK/vincular.log" ||
    falla "the camera was not put in a room within 15 min (see $WORK/vincular.log)"
  log "camera in use: $(grep -m1 -oE 'Cámara [0-9a-f-]+ en uso: .*' "$WORK/vincular.log")"
  if grep -qiE 'consentimiento|captura no permitida|SIN_CONSENTIMIENTO' "$WORK/vincular.log"; then
    log "WARNING: the log mentions consent; check the household's camera consent in the app"
  fi
  sleep 5
  detener "$AGENTE_PID" 15
  AGENTE_PID=""
}
if [ "$MODO" = vincular ]; then
  vincular_pc
  log "ready. Start the series within a minute or so (or the app will say «Cámara desconectada» once):"
  log "  $0 serie"
  exit 0
fi

# ---------------------------------------------------------------- serie
comprobar_telefono
# Not linked yet: link now (the code is approved in the app), then the series starts right away.
vinculada || vincular_pc
app_al_frente() {
  adb shell dumpsys activity activities | tr -d '\r' | grep -E 'mResumedActivity|ResumedActivity:' | grep -q "$PAQUETE"
}
if app_al_frente; then
  log "the Te Tengo app is in the foreground: press Home or lock the screen (a foreground app shows no system notification)"
  esperar 180 sh -c "! adb shell dumpsys activity activities | tr -d '\r' | grep -E 'mResumedActivity|ResumedActivity:' | grep -q $PAQUETE" ||
    falla "the app stayed in the foreground"
fi
if [ $# -gt 0 ]; then VIDEOS=("$@"); else VIDEOS=(); for i in $(seq -w 1 30); do VIDEOS+=("fall-$i"); done; fi
log "${#VIDEOS[@]} falls; standing ${PREVIA}s before each, last frame held ${SOSTENER}s"
preparar_clip "${VIDEOS[0]}" "$PREVIA" "$SOSTENER" || falla "clip preparation (see $WORK/clips.log)"
jq -n --arg api "$API" --arg fecha "$(date -u +%FT%TZ)" --arg telefono "$TELEFONO" --arg android "$ANDROID" \
  --arg red_tipo "${RED_TIPO:-?}" --arg red_subtipo "${RED_SUBTIPO:-?}" --arg operador "${OPERADOR:-?}" \
  --arg wifi "$WIFI" --arg cargando "$CARGANDO" \
  --arg agente "$("$PY" -c 'import te_tengo_captura as t; print(t.__version__)')" --arg previa "$PREVIA" \
  --arg salud "$SALUD" \
  '{api:$api,fecha:$fecha,telefono:$telefono,android:$android,red_tipo:$red_tipo,red_subtipo:$red_subtipo,
    operador:$operador,wifi:$wifi,cargando:$cargando,agente:$agente,espera_previa_s:$previa,salud:$salud}' >"$WORK/entorno.json"

python3 "$AQUI/desfase_adb.py" "$WORK/android-desfase-antes.csv" | tee "$WORK/android-desfase-antes.txt"
adb shell setprop log.tag.FirebaseMessaging VERBOSE >/dev/null 2>&1 || true
adb shell pidof "$PAQUETE" | tr -d '\r' >"$WORK/app-pid-antes.txt" || true
adb logcat -v epoch,usec -b main,system,events >"$WORK/android.log" 2>&1 </dev/null &
ADB_PID=$!

PREP_PID=""
for N in $(seq 1 ${#VIDEOS[@]}); do
  NOMBRE="${VIDEOS[$((N - 1))]}"
  if [ -n "$PREP_PID" ]; then wait "$PREP_PID" || falla "clip preparation of $NOMBRE (see $WORK/clips.log)"; fi
  PREP_PID=""
  IFS=$'\t' read -r _ CLIP INICIO_CAIDA DURACION PREVIA_REAL <"$WORK/clips/$NOMBRE.tsv"
  [ -s "$CLIP" ] || falla "clip of $NOMBRE missing"
  DIR="$WORK/corridas/$NOMBRE"
  mkdir -p "$DIR"
  printf '%s\n' "$INICIO_CAIDA" >"$DIR/inicio_caida.txt"
  log "[$N/${#VIDEOS[@]}] $NOMBRE: standing ${PREVIA_REAL}s, onset at ${INICIO_CAIDA}s"
  arrancar_agente "$CLIP" "$INICIO_CAIDA" "$DIR/agente.log"
  if ! esperar 60 grep -qE 'Cámara [0-9a-f-]+ en uso' "$DIR/agente.log"; then
    grep -q 'Código para vincular esta PC' "$DIR/agente.log" && falla "the PC is no longer linked (see $DIR/agente.log)"
    falla "no camera in use after 60 s: put «Video URFD (medición)» in a room in the app (see $DIR/agente.log)"
  fi
  # The next clip is prepared now, while this one shows the person standing (before the onset).
  if [ "$N" -lt ${#VIDEOS[@]} ]; then
    preparar_clip "${VIDEOS[$N]}" "$PREVIA" "$SOSTENER" &
    PREP_PID=$!
  fi
  terminado() {
    kill -0 "$AGENTE_PID" 2>/dev/null || return 0
    local id primero
    id="$(grep -m1 -oE 'Evento detectado: caida [0-9a-f-]+' "$DIR/agente.log" | awk '{print $4}' || true)"
    if [ -n "$id" ] && grep -qE "Clip del evento $id (subido|descartado)" "$DIR/agente.log"; then return 0; fi
    primero="$(grep -m1 -oE 'primer_cuadro epoch=[0-9.]+' "$DIR/agente.log" | cut -d= -f2 || true)"
    [ -n "$primero" ] && python3 -c "import sys,time; sys.exit(0 if time.time() > $primero + $PREVIA_REAL + $DURACION + $SOSTENER - 2 else 1)"
  }
  esperar $((PREVIA + 120 + SOSTENER)) terminado || log "$NOMBRE: timed out"
  sleep 1
  detener "$AGENTE_PID" 15
  AGENTE_PID=""
  rm -f "$CLIP"
  RESULTADO="$(grep -m1 -oE 'LATENCIA evento [0-9a-f-]+ alerta [^ ]+' "$DIR/agente.log" || true)"
  if grep -q 'Evento detectado: caida' "$DIR/agente.log"; then
    log "$NOMBRE: caida detected; ${RESULTADO:-event not accepted by the API}"
    [ -z "$RESULTADO" ] || ! grep -q 'alerta None' <<<"$RESULTADO" ||
      log "WARNING: no alert created (no camera consent, or the camera is paused?)"
  else
    log "$NOMBRE: no caida detected"
  fi
done

sleep 10
detener "$ADB_PID" 5
ADB_PID=""
adb shell pidof "$PAQUETE" | tr -d '\r' >"$WORK/app-pid-despues.txt" || true
python3 "$AQUI/desfase_adb.py" "$WORK/android-desfase-despues.csv" | tee "$WORK/android-desfase-despues.txt"
python3 "$AQUI/analizar_produccion.py" "$WORK" | tee "$WORK/resumen.md"
