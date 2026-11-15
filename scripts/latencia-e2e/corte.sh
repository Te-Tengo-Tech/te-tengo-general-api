# shellcheck shell=bash
# Case A of the reliability test: the household link (agent → API) fails while falls are detected.
# Sourced by `medir_produccion.sh corte [video ...]`, after its health check, with its functions and
# variables (log, falla, detener, esperar, vinculada, comprobar_telefono, PY, AQUI, AGENTE, CONFIG,
# ESCRITORIO, API, WORK, PAQUETE, SALUD, SIN_TELEFONO). See the «corte» notes in its header.
#
# One isolated agent process (agente_corte.py: agente_produccion.py plus per-fall t0 marks and a log
# line per HTTP attempt) plays every fall from one concatenated clip, with HTTPS_PROXY/HTTP_PROXY
# pointing to proxy_corte.py. The outage is scheduled on the clip's timeline: it starts
# TT_CORTE_ADELANTO s before the onset of fall TT_CORTE_DESDE and lasts TT_CORTE_DURACION s; the
# proxy logs both switches with epoch timestamps (proxy.jsonl). The agent's TCP sockets are listed
# every 2 s (conexiones.txt) to show that nothing bypasses the proxy.

DURACION="${TT_CORTE_DURACION:-90}"
DESDE="${TT_CORTE_DESDE:-3}"
ADELANTO="${TT_CORTE_ADELANTO:-5}"
C_PREVIA="${TT_CORTE_PREVIA:-15}"
C_SOSTENER="${TT_CORTE_SOSTENER:-15}"
C_COLA="${TT_CORTE_COLA:-150}"
NUEVAS="${TT_CORTE_NUEVAS:-agujero}"
EXISTENTES="${TT_CORTE_EXISTENTES:-cortar}"
N_CAIDAS="${TT_CORTE_N:-10}"
# Case B (TT_CORTE_LADO=telefono): the proxy never cuts; the phone's own network drops (the family
# turns its hotspot off and on, e.g. with an iOS Shortcut) and red_telefono.csv records, from this
# Mac every ~0.3 s, whether the phone has a route on wlan0 and answers a ping (analizar_lado_telefono.py).
LADO="${TT_CORTE_LADO:-agente}"
# Without videos given, they are taken in this order and any fall the offline replay of the exact
# clip does not detect is replaced by the next one (see the clip section).
if [ $# -gt 0 ]; then
  VIDEOS=("$@") RESERVA=()
else
  RESERVA=()
  for i in $(seq 10 30) $(seq 1 9); do RESERVA+=("$(printf "fall-%02d" "$i")"); done
  VIDEOS=("${RESERVA[@]:0:$N_CAIDAS}")
  RESERVA=("${RESERVA[@]:$N_CAIDAS}")
fi
[ "$DESDE" -ge 1 ] && [ "$DESDE" -le ${#VIDEOS[@]} ] || falla "TT_CORTE_DESDE=$DESDE is not between 1 and ${#VIDEOS[@]}"
ahora() { python3 -c 'import time; print(f"{time.time():.3f}")'; }
dormir_hasta() { python3 -c 'import sys, time; time.sleep(max(0.0, float(sys.argv[1]) - time.time()))' "$1"; }
mayor() { awk -v a="$1" -v b="$2" 'BEGIN { exit !(a >= b) }'; }

TELEFONO="" ANDROID="" RED_TIPO="" RED_SUBTIPO="" OPERADOR="" WIFI="" CARGANDO=""
if [ "$SIN_TELEFONO" = 1 ]; then
  log "TT_LAT_SIN_TELEFONO=1: no phone (no t4/t4n); only agent → API delivery is measured"
else
  comprobar_telefono
  if adb shell dumpsys activity activities | tr -d '\r' | grep -E 'mResumedActivity|ResumedActivity:' | grep -q "$PAQUETE"; then
    log "the Te Tengo app is in the foreground: press Home or lock the screen (a foreground app shows no system notification)"
    esperar 180 sh -c "! adb shell dumpsys activity activities | tr -d '\r' | grep -E 'mResumedActivity|ResumedActivity:' | grep -q $PAQUETE" ||
      falla "the app stayed in the foreground"
  fi
fi
# Linking needs the family approving a code in the app: never done from here.
vinculada || falla "this isolated PC is not linked: run «$0 vincular» first (approve the code in the app)"

# ---------------------------------------------------------------- clip
mkdir -p "$WORK/clips"
CLIP="$WORK/clips/corte.avi"
# In one continuous process a fall is only detected if the classifier saw the person upright after
# the previous one, which depends on each video's first frame and on MediaPipe's tracking state:
# the exact clip is replayed offline first (simular_clip_corte.py, the agent's own detection chain
# and model), so the network test starts with falls the agent detects without any outage.
for intento in 1 2 3 4 5; do
  log "preparing one clip: ${#VIDEOS[@]} falls (${VIDEOS[*]}), standing ${C_PREVIA}s before each, lying ${C_SOSTENER}s after, ${C_COLA}s standing at the end"
  "$PY" "$AQUI/preparar_clip_corte.py" "$ESCRITORIO/datos/urfd" "$CLIP" "$C_PREVIA" "$C_SOSTENER" "$C_COLA" "${VIDEOS[@]}" \
    >"$WORK/clip.tsv" 2>>"$WORK/clips.log" || falla "clip preparation (see $WORK/clips.log)"
  [ "${TT_CORTE_SIN_SIMULAR:-}" = 1 ] && break
  log "offline replay of the clip (no network)…"
  if "$PY" "$AQUI/simular_clip_corte.py" "$CLIP" "$WORK/clip.tsv" 2>/dev/null | grep -E '^(fall-|[0-9]+ of )' >"$WORK/simulacion.txt"; then
    log "offline replay: $(tail -1 "$WORK/simulacion.txt")"
    break
  fi
  FALLAN=()
  while IFS= read -r v; do FALLAN+=("$v"); done < <(grep -E '→ 0 caida' "$WORK/simulacion.txt" | cut -d: -f1)
  [ ${#FALLAN[@]} -gt 0 ] || falla "offline replay failed (see $WORK/simulacion.txt)"
  log "offline replay: not detected in this clip: ${FALLAN[*]}"
  [ ${#RESERVA[@]} -ge ${#FALLAN[@]} ] || falla "falls not detected offline (${FALLAN[*]}) and no spare videos: pass others or TT_CORTE_SIN_SIMULAR=1"
  QUEDAN=()
  for v in "${VIDEOS[@]}"; do [[ " ${FALLAN[*]} " == *" $v "* ]] || QUEDAN+=("$v"); done
  VIDEOS=("${QUEDAN[@]}" "${RESERVA[@]:0:${#FALLAN[@]}}")
  RESERVA=("${RESERVA[@]:${#FALLAN[@]}}")
  [ "$intento" -lt 5 ] || falla "the offline replay still misses falls after 5 clips (see $WORK/simulacion.txt)"
done
INICIOS="$(grep -v '^TOTAL' "$WORK/clip.tsv" | cut -f2 | paste -sd, -)"
FIN_ULTIMA="$(grep -v '^TOTAL' "$WORK/clip.tsv" | tail -1 | cut -f3)"
TOTAL="$(grep '^TOTAL' "$WORK/clip.tsv" | cut -f3)"
INICIO_K="$(sed -n "${DESDE}p" "$WORK/clip.tsv" | cut -f2)"
DENTRO="$(awk -F'\t' -v a="$INICIO_K" -v d="$DURACION" -v ad="$ADELANTO" \
  '$1 != "TOTAL" && $2 >= a - ad && $2 < a - ad + d { printf "%s ", $1 }' "$WORK/clip.tsv")"
log "clip ${TOTAL}s; outage of ${DURACION}s from ${ADELANTO}s before fall $DESDE's onset: falls inside: ${DENTRO:-none}"

# ---------------------------------------------------------------- proxy
CONTROL="$WORK/proxy.control"
echo paso >"$CONTROL"
"$PY" "$AQUI/proxy_corte.py" --control "$CONTROL" --registro "$WORK/proxy.jsonl" --puerto-archivo "$WORK/proxy.puerto" \
  --nuevas "$NUEVAS" --existentes "$EXISTENTES" >"$WORK/proxy.log" 2>&1 </dev/null &
PROXY_PID=$!
esperar 15 test -s "$WORK/proxy.puerto" || falla "the proxy did not start (see $WORK/proxy.log)"
PROXY="http://127.0.0.1:$(cat "$WORK/proxy.puerto")"
log "proxy $PROXY (outage: new connections $NUEVAS, open tunnels $EXISTENTES)"

jq -n --arg api "$API" --arg fecha "$(date -u +%FT%TZ)" --arg telefono "$TELEFONO" --arg android "$ANDROID" \
  --arg red_tipo "$RED_TIPO" --arg red_subtipo "$RED_SUBTIPO" --arg operador "$OPERADOR" --arg wifi "$WIFI" \
  --arg cargando "$CARGANDO" --arg agente "$("$PY" -c 'import te_tengo_captura as t; print(t.__version__)')" \
  --arg escritorio "$ESCRITORIO" --arg salud "$SALUD" --arg videos "${VIDEOS[*]}" --arg previa "$C_PREVIA" \
  --arg sostener "$C_SOSTENER" --arg cola "$C_COLA" --arg duracion "$DURACION" --arg desde "$DESDE" \
  --arg adelanto "$ADELANTO" --arg nuevas "$NUEVAS" --arg existentes "$EXISTENTES" --arg dentro "$DENTRO" \
  --arg sin_telefono "${SIN_TELEFONO:-0}" \
  '{api:$api,fecha:$fecha,agente:$agente,escritorio:$escritorio,salud:$salud,videos:$videos,previa_s:$previa,
    sostener_s:$sostener,cola_s:$cola,corte_s:$duracion,corte_desde:$desde,corte_adelanto_s:$adelanto,
    nuevas:$nuevas,existentes:$existentes,caidas_en_el_corte_previstas:$dentro,sin_telefono:$sin_telefono}
   + (if $sin_telefono == "1" then {} else {telefono:$telefono,android:$android,red_tipo:$red_tipo,
      red_subtipo:$red_subtipo,operador:$operador,wifi:$wifi,cargando:$cargando} end)' >"$WORK/entorno.json"

if [ "$SIN_TELEFONO" != 1 ]; then
  python3 "$AQUI/desfase_adb.py" "$WORK/android-desfase-antes.csv" | tee "$WORK/android-desfase-antes.txt"
  adb shell setprop log.tag.FirebaseMessaging VERBOSE >/dev/null 2>&1 || true
  adb shell pidof "$PAQUETE" | tr -d '\r' >"$WORK/app-pid-antes.txt" || true
  adb logcat -v epoch,usec -b main,system,events >"$WORK/android.log" 2>&1 </dev/null &
  ADB_PID=$!
fi
RED_PID=""
if [ "$LADO" = telefono ]; then
  [ "$SIN_TELEFONO" != 1 ] || falla "TT_CORTE_LADO=telefono needs the phone"
  (
    echo "antes,despues,ruta_wlan0,ping"
    while true; do
      a="$(ahora)"
      r="$(adb shell 'grep -c wlan0 /proc/net/route; ping -c1 -W1 8.8.8.8 >/dev/null 2>&1 && echo 1 || echo 0' 2>/dev/null | tr -d '\r' | paste -sd, -)"
      echo "$a,$(ahora),${r:-,}"
      sleep 0.2
    done
  ) >"$WORK/red_telefono.csv" 2>/dev/null &
  RED_PID=$!
fi

# ---------------------------------------------------------------- agent behind the proxy
unset NO_PROXY no_proxy ALL_PROXY all_proxy
HTTPS_PROXY="$PROXY" HTTP_PROXY="$PROXY" https_proxy="$PROXY" http_proxy="$PROXY" \
  TT_LAT_INICIO_CAIDA="${INICIOS%%,*}" TT_CORTE_INICIOS="$INICIOS" \
  "$PY" "$AQUI/agente_corte.py" --config "$CONFIG" --video "$CLIP" --sin-interfaz \
  --datos "$AGENTE/datos" --logs "$AGENTE/logs" >"$WORK/agente.log" 2>&1 </dev/null &
AGENTE_PID=$!
(
  while kill -0 "$AGENTE_PID" 2>/dev/null; do
    printf '# %s\n' "$(ahora)"
    lsof -nP -a -p "$AGENTE_PID" -i 2>/dev/null | tail -n +2
    sleep 2
  done
) >"$WORK/conexiones.txt" 2>/dev/null &
LSOF_PID=$!
if ! esperar 60 grep -qE 'Cámara [0-9a-f-]+ en uso' "$WORK/agente.log"; then
  grep -q 'Código para vincular esta PC' "$WORK/agente.log" &&
    falla "the PC is no longer linked (see $WORK/agente.log): run «$0 vincular» (approve the code in the app)"
  falla "no camera in use after 60 s: put «Video URFD (medición)» in a room in the app (see $WORK/agente.log)"
fi
log "camera in use: $(grep -m1 -oE 'Cámara [0-9a-f-]+ en uso: .*' "$WORK/agente.log")"
esperar 30 grep -q 'LATENCIA primer_cuadro epoch=' "$WORK/agente.log" || falla "the clip did not start playing (see $WORK/agente.log)"
PRIMERO="$(grep -m1 -oE 'primer_cuadro epoch=[0-9.]+' "$WORK/agente.log" | cut -d= -f2)"
T_CORTE="$(awk -v p="$PRIMERO" -v i="$INICIO_K" -v a="$ADELANTO" 'BEGIN { printf "%.3f", p + i - a }')"
T_REST="$(awk -v t="$T_CORTE" -v d="$DURACION" 'BEGIN { printf "%.3f", t + d }')"
mayor "$T_CORTE" "$(ahora)" || falla "the outage would start in the past: raise TT_CORTE_PREVIA or TT_CORTE_DESDE"
log "first frame at $PRIMERO; outage planned $T_CORTE → $T_REST"

# ---------------------------------------------------------------- outage
if [ "$LADO" = telefono ]; then
  log "CASE B: the agent link stays up. RUN THE «Corte B» SHORTCUT ON THE iPHONE NOW (hotspot off/on)"
  touch "$WORK/atajo.ya"
else
dormir_hasta "$T_CORTE"
echo corte >"$CONTROL"
log "LINK DOWN (proxy: corte) for ${DURACION}s"
dormir_hasta "$T_REST"
echo paso >"$CONTROL"
log "LINK RESTORED (proxy: paso)"
fi

# ---------------------------------------------------------------- drain
FIN_MIN="$(awk -v p="$PRIMERO" -v f="$FIN_ULTIMA" 'BEGIN { printf "%.3f", p + f + 10 }')"
LIMITE="$(awk -v p="$PRIMERO" -v t="$TOTAL" 'BEGIN { printf "%.3f", p + t - 3 }')"
corte_terminado() {
  kill -0 "$AGENTE_PID" 2>/dev/null || return 0
  local t id
  t="$(ahora)"
  mayor "$t" "$LIMITE" && return 0 # the clip would loop and replay the falls
  mayor "$t" "$FIN_MIN" || return 1
  for id in $(grep -oE 'Evento detectado: [a-z_]+ [0-9a-f-]+' "$WORK/agente.log" | awk '{print $4}'); do
    grep -qE "Evento $id enviado|rechazó el evento $id" "$WORK/agente.log" || return 1
  done
  for id in $(grep -oE 'Evento detectado: caida [0-9a-f-]+' "$WORK/agente.log" | awk '{print $4}'); do
    grep -qE "Clip del evento $id (subido|descartado)" "$WORK/agente.log" || return 1
  done
  return 0
}
esperar "$(printf "%.0f" "$TOTAL")" corte_terminado || true
mayor "$(ahora)" "$LIMITE" && log "WARNING: the clip ended before every event and clip was delivered (still in the outbox)"
log "every event and clip delivered (or end of clip): stopping the agent"
# late notifications; in case B, until the phone has been back online for TT_CORTE_FINAL s (default 60)
if [ "$LADO" = telefono ]; then
  # Stop the agent first: left running, the clip loops and keeps raising alerts in the household.
  detener "$AGENTE_PID" 15
  AGENTE_PID=""
  log "waiting for the phone to stay online ${TT_CORTE_FINAL:-60}s (the shortcut must have finished)"
  esperar 900 python3 "$AQUI/analizar_lado_telefono.py" --en-linea "${TT_CORTE_FINAL:-60}" "$WORK" || log "WARNING: the phone was still offline"
  sleep 15
else
  [ "$SIN_TELEFONO" = 1 ] || sleep 15
fi
detener "$AGENTE_PID" 15
AGENTE_PID=""
detener "$PROXY_PID" 5
PROXY_PID=""
detener "$LSOF_PID" 3
LSOF_PID=""
[ -z "$RED_PID" ] || { kill "$RED_PID" 2>/dev/null || true; RED_PID=""; }
rm -f "$CLIP"
if [ "$SIN_TELEFONO" != 1 ]; then
  detener "$ADB_PID" 5
  ADB_PID=""
  adb shell pidof "$PAQUETE" | tr -d '\r' >"$WORK/app-pid-despues.txt" || true
  python3 "$AQUI/desfase_adb.py" "$WORK/android-desfase-despues.csv" | tee "$WORK/android-desfase-despues.txt"
fi
python3 "$AQUI/analizar_corte.py" "$WORK" | tee "$WORK/resumen.md"
if [ "$LADO" = telefono ]; then python3 "$AQUI/analizar_lado_telefono.py" "$WORK" | tee "$WORK/resumen-telefono.md"; fi
