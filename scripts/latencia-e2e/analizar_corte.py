"""Case A (household link outage) results of scripts/latencia-e2e/medir_produccion.sh corte.

Usage: python3 analizar_corte.py <work directory>

Reads, on this machine's clock:

* ``proxy.jsonl`` (proxy_corte.py): t_cut (state ``corte``) and t_restore (back to ``paso``), and
  every CONNECT the agent made (host, result);
* ``agente.log`` (agente_corte.py): t0 of each fall (``LATENCIA inicio_caida n=<k>``), t1 (``Evento
  detectado: caida <id>``), every HTTP attempt (``CORTE http …``; for events, with the event id),
  the alert id the API returned (``LATENCIA evento <id> alerta <id> duplicado=<b>``), the clip
  upload (``Clip del evento <id> subido|descartado``) and the outbox warnings;
* with the phone, ``android.log`` and the clock offsets (as analizar_produccion.py): t4n, the first
  system notification whose tag is the alert id at or after t1, and t4, the last Firebase receipt
  of the app's process in [t1, t4n].

Per fall alert (``caida``): whether it was detected during the outage, delivery attempts, t2 (first
2xx of its POST), delays t_restore→t2 / →clip / →t4n, and duplicates: more than one 2xx for the
same event id, a ``200`` (the API had it already), an alert id shared by two fall events, or more
system notifications under one tag than notices the API was sent for it (fall + «se levantó» for
the same alert are two notices: same tag, replacing each other on screen).
Writes <work>/corte.csv (one row per fall), <work>/intentos.csv (every event POST attempt) and
prints a Markdown summary.
"""

import csv
import json
import re
import statistics
import sys
from pathlib import Path

from analizar import ANDROID_RECEPCION, ANDROID_TS, desfase_android, epoch_agente
from analizar_produccion import ANDROID_NOTIFICACION, ANDROID_PID, INICIO_PROCESO, PAQUETE

UUID = r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
HTTP = re.compile(
    r"CORTE http (\S+) (\S+) → (\S+) ([\d.]+)s inicio=([\d.]+)(?: evento=(\S+) tipo=(\S+))?"
)
WS = re.compile(r"CORTE ws (\S+) → (\S+)")


def cuantiles(valores: list[float]) -> str:
    if not valores:
        return "| — | — | — | — | 0 |"
    v = sorted(valores)
    q1, q3 = statistics.quantiles(v, n=4, method="inclusive")[::2] if len(v) >= 2 else (v[0], v[0])
    return f"| {statistics.median(v):.2f} | {q1:.2f}–{q3:.2f} | {v[0]:.2f} | {v[-1]:.2f} | {len(v)} |"


def politica(escritorio: str | None) -> str:
    """The outbox backoff constants, read from the agent's source (envios/cola.py)."""
    texto = ""
    if escritorio:
        ruta = Path(escritorio) / "src/te_tengo_captura/envios/cola.py"
        texto = ruta.read_text() if ruta.exists() else ""
    valores = dict(re.findall(r"^(ESPERA_BASE_S|ESPERA_MAXIMA_S|ESPERA_MAXIMA_URGENTE_S|MAX_INTENTOS_CLIP) = ([\d.]+)", texto, re.M))
    if not valores:
        return "(envios/cola.py not found)"
    return (
        f"per lane, after its n-th failure in a row it waits min(tope, {valores['ESPERA_BASE_S']} s × 2^(n−1)) "
        f"with equal jitter (half fixed, half random: [tope/2, tope]); tope = {valores['ESPERA_MAXIMA_URGENTE_S']} s "
        f"for urgent events (caida, caida_confirmada, movimiento_inestable, recuperacion), "
        f"{valores['ESPERA_MAXIMA_S']} s for other events, clips and movement files; a newly queued urgent event "
        f"is tried at once; a lane stops at its first temporary failure (events go out in detection order); "
        f"a success resets the lane; a clip is discarded after {valores['MAX_INTENTOS_CLIP']} failed uploads. "
        f"httpx timeouts: connect 5 s, read 10 s (clip write 60 s)."
    )


def ruta_generica(destino: str) -> str:
    return re.sub(UUID, "{id}", destino)


def main() -> int:
    work = Path(sys.argv[1])
    entorno = json.loads((work / "entorno.json").read_text()) if (work / "entorno.json").exists() else {}
    proxy = [json.loads(x) for x in (work / "proxy.jsonl").read_text().splitlines() if x.strip()]
    estados = [(e["epoch"], e["estado"]) for e in proxy if e["evento"] == "estado"]
    t_corte = next((t for t, s in estados if s == "corte"), None)
    t_rest = next((t for t, s in estados if s == "paso" and t_corte is not None and t > t_corte), None)
    connects = [e for e in proxy if e["evento"] == "connect"]
    no_connect = [e for e in proxy if e["evento"] == "no_connect"]

    lineas = (work / "agente.log").read_text(errors="replace").splitlines()
    t0: dict[int, float] = {}
    detectados: list[tuple[float, str, str]] = []  # (t1, tipo, evento)
    intentos: list[dict[str, object]] = []
    http: list[tuple[float, str, str, str, float]] = []  # inicio, method, destino, resultado, dur
    alertas: dict[str, tuple[str | None, bool]] = {}
    clips: dict[str, tuple[float, str]] = {}
    ws: list[tuple[float, str, str]] = []
    pendientes: list[tuple[float, int]] = []
    encolados: dict[str, tuple[float, float, str]] = {}  # id → (called at, wait, what)
    atrasos: list[tuple[float, float, float]] = []
    for linea in lineas:
        if m := re.search(r"LATENCIA inicio_caida n=(\d+) epoch=([\d.]+)", linea):
            t0[int(m.group(1))] = float(m.group(2))
        elif m := re.search(rf"Evento detectado: (\S+) ({UUID})", linea):
            if (t := epoch_agente(linea)) is not None:
                detectados.append((t, m.group(1), m.group(2)))
        elif m := HTTP.search(linea):
            metodo, destino, resultado, dur, inicio = m.group(1), m.group(2), m.group(3), float(m.group(4)), float(m.group(5))
            http.append((inicio, metodo, destino, resultado, dur))
            if m.group(6):
                intentos.append({"evento": m.group(6), "tipo": m.group(7), "inicio": inicio, "fin": inicio + dur,
                                 "resultado": resultado, "duracion_s": dur})
        elif m := re.search(rf"LATENCIA evento ({UUID}) alerta (\S+) duplicado=(\w+)", linea):
            previo = alertas.get(m.group(1))
            alerta = None if m.group(2) == "None" else m.group(2)
            alertas[m.group(1)] = (alerta or (previo[0] if previo else None),
                                   (m.group(3) == "True") or bool(previo and previo[1]))
        elif m := re.search(rf"Clip del evento ({UUID}) (subido|descartado)", linea):
            if (t := epoch_agente(linea)) is not None and m.group(1) not in clips:
                clips[m.group(1)] = (t, m.group(2))
        elif m := WS.search(linea):
            if (t := epoch_agente(linea)) is not None:
                ws.append((t, m.group(1), m.group(2)))
        elif m := re.search(rf"CORTE encolar (\S+) ({UUID}) (\S*) ?espera=([\d.]+)s inicio=([\d.]+)", linea):
            encolados.setdefault(m.group(2) + ("" if m.group(1) == "agregar_evento" else "/" + m.group(1)),
                                 (float(m.group(5)), float(m.group(4)), m.group(3)))
        elif m := re.search(r"CORTE lectura_atrasada desde=([\d.]+) hasta=([\d.]+) max=([\d.]+)s", linea):
            atrasos.append((float(m.group(1)), float(m.group(2)), float(m.group(3))))
        elif m := re.search(r"Envío pendiente \((\d+) en cola\)", linea):
            if (t := epoch_agente(linea)) is not None:
                pendientes.append((t, int(m.group(1))))

    # Phone (optional).
    desfase = desfase_android(work)
    recepciones: list[float] = []
    notificaciones: dict[str, list[float]] = {}
    android = work / "android.log"
    hay_telefono = android.exists() and desfase is not None
    if hay_telefono:
        texto = android.read_text(errors="replace").splitlines()
        pids = {p for ruta in work.glob("app-pid-*.txt") for p in ruta.read_text().split()}
        pids |= {m.group(1) for x in texto if (m := INICIO_PROCESO.search(x))}
        for x in texto:
            m = ANDROID_TS.match(x)
            if not m:
                continue
            t_disp = float(m.group(1))
            t_host = t_disp - desfase(t_disp)
            if ANDROID_RECEPCION.search(x):
                mp = ANDROID_PID.match(x)
                if not pids or (mp and mp.group(1) in pids):
                    recepciones.append(t_host)
            elif (mn := ANDROID_NOTIFICACION.search(x)) and mn.group(1) == PAQUETE:
                notificaciones.setdefault(mn.group(2), []).append(t_host)
        recepciones.sort()

    nombres = {}
    if (work / "clip.tsv").exists():
        filas_clip = [x.split("\t") for x in (work / "clip.tsv").read_text().splitlines() if x and not x.startswith("TOTAL")]
        nombres = {n: f[0] for n, f in enumerate(filas_clip, 1)}
    # Notices the API sent per alert: every distinct event accepted (not as a duplicate) with that alert id.
    avisos_por_alerta: dict[str, int] = {}
    for evento, (alerta, _) in alertas.items():
        if alerta:
            avisos_por_alerta[alerta] = avisos_por_alerta.get(alerta, 0) + 1

    filas: list[dict[str, object]] = []
    # «Evento detectado» is logged after the event is in the outbox: when the capture thread waited
    # for the outbox lock, the detection instant is when agregar_evento was called.
    detectados = [(encolados[e][0] if e in encolados else t, tipo, e) for t, tipo, e in detectados]
    caidas = [d for d in detectados if d[1] == "caida"]
    orden_t0 = sorted(t0.items())
    for t1, _, evento in caidas:
        n = max((k for k, t in orden_t0 if t <= t1 + 0.001), default=None)
        mios = sorted((i for i in intentos if i["evento"] == evento), key=lambda i: i["inicio"])
        exitos = [i for i in mios if str(i["resultado"]).startswith("2")]
        t2 = float(exitos[0]["fin"]) if exitos else None  # type: ignore[arg-type]
        alerta, duplicado = alertas.get(evento, (None, False))
        clip = clips.get(evento)
        en_corte = t_corte is not None and t_rest is not None and t_corte <= t1 < t_rest
        en_cola_al_restaurar = t_rest is not None and t1 < t_rest and (t2 is None or t2 > t_rest)
        t4n = t4 = None
        if hay_telefono and alerta:
            t4n = next((t for t in sorted(notificaciones.get(alerta, [])) if t >= t1), None)
            if t4n is not None:
                previas = [t for t in recepciones if t1 <= t <= t4n + 0.001]
                t4 = previas[-1] if previas else None

        def d(a: float | None, b: float | None) -> float | None:
            return None if a is None or b is None else b - a

        n_notif = len([t for t in notificaciones.get(alerta or "", []) if t >= t1]) if hay_telefono else None
        filas.append({
            "n": n, "video": nombres.get(n, "") if n else "", "evento": evento, "alerta": alerta or "",
            "en_corte": en_corte, "en_cola_al_restaurar": en_cola_al_restaurar,
            "t0": t0.get(n) if n else None, "t1": t1, "t2": t2, "tclip": clip[0] if clip else None,
            "clip": clip[1] if clip else "", "t4": t4, "t4n": t4n,
            "espera_encolar_s": encolados[evento][1] if evento in encolados else 0.0,
            "intentos": len(mios), "intentos_fallidos": len(mios) - len(exitos),
            "fallos": ";".join(sorted({str(i["resultado"]) for i in mios if not str(i["resultado"]).startswith("2")})),
            "respuestas_2xx": len(exitos), "duplicado_api_200": duplicado,
            "intentos_clip": sum(1 for h in http if evento in h[2] and h[1] == "POST"),
            "avisos_api": avisos_por_alerta.get(alerta or "", 0), "notificaciones_telefono": n_notif,
            "d01_deteccion": d(t0.get(n) if n else None, t1),
            "d12_deteccion_a_api": d(t1, t2),
            "d_restauracion_a_api": d(t_rest, t2) if en_cola_al_restaurar else None,
            "d_restauracion_a_clip": d(t_rest, clip[0] if clip and clip[1] == "subido" else None)
            if t_rest is not None and t1 < t_rest and clip and clip[0] > t_rest else None,
            "d_restauracion_a_notificacion": d(t_rest, t4n) if en_cola_al_restaurar else None,
            "d_restauracion_a_push": d(t_rest, t4) if en_cola_al_restaurar else None,
            "d2_4n_api_a_notificacion": d(t2, t4n),
            "d14n_deteccion_a_notificacion": d(t1, t4n),
        })
    if not filas:
        sys.exit("no caida detected in agente.log")
    with (work / "corte.csv").open("w", newline="") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=list(filas[0]))
        escritor.writeheader()
        for fila in filas:
            escritor.writerow({k: (f"{v:.3f}" if isinstance(v, float) else v) for k, v in fila.items()})
    with (work / "intentos.csv").open("w", newline="") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=["evento", "tipo", "inicio", "fin", "resultado", "duracion_s", "fase"])
        escritor.writeheader()
        for i in sorted(intentos, key=lambda i: i["inicio"]):
            fase = ("antes" if t_corte is None or float(i["inicio"]) < t_corte  # type: ignore[arg-type]
                    else "corte" if t_rest is None or float(i["inicio"]) < t_rest else "despues")  # type: ignore[arg-type]
            escritor.writerow({**i, "inicio": f"{i['inicio']:.3f}", "fin": f"{i['fin']:.3f}", "fase": fase})

    # ------------------------------------------------------------------ summary
    entregadas = [f for f in filas if f["respuestas_2xx"]]
    en_cola = [f for f in filas if f["en_cola_al_restaurar"]]
    print(f"# Caso A: corte del enlace del hogar (agente → API) con caídas detectadas ({len(filas)} alertas de caída)\n")
    duracion = (t_rest - t_corte) if t_corte and t_rest else None
    print(f"- Corte (proxy local, {entorno.get('nuevas', '?')} conexiones nuevas, {entorno.get('existentes', '?')} túneles abiertos): "
          + (f"{duracion:.1f} s" if duracion else "sin corte registrado"))
    for clave, titulo in (("api", "API"), ("fecha", "Fecha (UTC)"), ("agente", "Agente de escritorio"),
                          ("videos", "Videos"), ("previa_s", "De pie antes de cada caída (s)"),
                          ("sostener_s", "Último cuadro sostenido (s)"), ("telefono", "Teléfono"),
                          ("red_tipo", "Red del teléfono"), ("wifi", "Wi-Fi del teléfono")):
        if clave in entorno:
            print(f"- {titulo}: {entorno[clave]}")
    print(f"- Política de reintentos de la cola (envios/cola.py): {politica(entorno.get('escritorio'))}")
    print()
    print("| Métrica | Valor |\n|---|---|")
    print(f"| Alertas de caída generadas (`caida` detectada) | {len(filas)} |")
    print(f"| … detectadas durante el corte | {sum(1 for f in filas if f['en_corte'])} |")
    print(f"| … en cola al restaurar el enlace | {len(en_cola)} |")
    print(f"| Entregadas a la API (2xx) | {len(entregadas)} de {len(filas)} |")
    print(f"| … con alerta creada por la API | {sum(1 for f in filas if f['alerta'])} |")
    print(f"| Entregadas de las que estaban en cola | {sum(1 for f in en_cola if f['respuestas_2xx'])} de {len(en_cola)} |")
    dup_evento = sum(1 for f in filas if int(f["respuestas_2xx"]) > 1)  # type: ignore[arg-type]
    dup_api = sum(1 for f in filas if f["duplicado_api_200"])
    por_alerta: dict[str, int] = {}
    for f in filas:
        if f["alerta"]:
            por_alerta[str(f["alerta"])] = por_alerta.get(str(f["alerta"]), 0) + 1
    print(f"| Duplicados: mismo evento aceptado más de una vez (2xx) | {dup_evento} |")
    print(f"| Duplicados: la API respondió 200 «ya estaba» | {dup_api} |")
    print(f"| Duplicados: dos caídas con el mismo id de alerta | {sum(1 for v in por_alerta.values() if v > 1)} |")
    if hay_telefono:
        notificadas = sum(1 for f in filas if f["t4n"] is not None)
        exceso = sum(1 for f in filas if f["notificaciones_telefono"] is not None and f["avisos_api"]
                     and int(f["notificaciones_telefono"]) > int(f["avisos_api"]))  # type: ignore[arg-type]
        print(f"| Notificación del sistema en el teléfono (por tag = id de alerta) | {notificadas} de {len(filas)} |")
        print(f"| Duplicados en el teléfono: más notificaciones bajo un tag que avisos enviados | {exceso} |")
    else:
        print("| Teléfono | no conectado en esta corrida (sin t4/t4n) |")
    detectadas_n = {f["n"] for f in filas}
    perdidas = [k for k in sorted(t0) if k not in detectadas_n]
    if perdidas:
        def fase(k: int) -> str:
            return "durante el corte" if t_corte and t_rest and t_corte <= t0[k] < t_rest else "fuera del corte"
        print(f"| **Caídas reproducidas sin `caida` detectada** | {len(perdidas)} de {len(t0)}: "
              + ", ".join(f"{nombres.get(k, k)} ({fase(k)})" for k in perdidas) + " |")
    no_entregadas = [f["evento"] for f in filas if not f["respuestas_2xx"]]
    if no_entregadas:
        print(f"| Sin entregar al terminar | {', '.join(map(str, no_entregadas))} |")
    print()
    print("| Retardo | Mediana (s) | RIC (s) | Mín (s) | Máx (s) | n |\n|---|---|---|---|---|---|")
    for titulo, clave, subconjunto in (
        ("**restauración → API 2xx** (alertas en cola)", "d_restauracion_a_api", en_cola),
        ("restauración → clip subido (alertas detectadas antes de restaurar)", "d_restauracion_a_clip", filas),
        ("**restauración → notificación en el teléfono**", "d_restauracion_a_notificacion", en_cola),
        ("restauración → push en el proceso de la app", "d_restauracion_a_push", en_cola),
        ("detección → API 2xx (alertas en cola)", "d12_deteccion_a_api", en_cola),
        ("detección → API 2xx (alertas fuera del corte, control)", "d12_deteccion_a_api",
         [f for f in filas if not f["en_cola_al_restaurar"]]),
        ("t0 → detección", "d01_deteccion", filas),
        ("API 2xx → notificación", "d2_4n_api_a_notificacion", filas),
    ):
        valores = [v for f in subconjunto if isinstance(v := f[clave], float)]
        if valores or "teléfono" not in titulo and "push" not in titulo:
            print(f"| {titulo} " + cuantiles(valores))
    print()
    print("| n | Video | Evento | Durante el corte | Intentos (fallidos) | Fallos | t1→2xx (s) | Restauración→2xx (s) | Restauración→clip (s) | Alerta |")
    print("|---|---|---|---|---|---|---|---|---|---|")
    for f in filas:
        def s(v: object) -> str:
            return f"{v:.2f}" if isinstance(v, float) else "—"
        print(f"| {f['n']} | {f['video']} | `{str(f['evento'])[-12:]}` | {'sí' if f['en_corte'] else 'no'} | "
              f"{f['intentos']} ({f['intentos_fallidos']}) | {f['fallos'] or '—'} | {s(f['d12_deteccion_a_api'])} | "
              f"{s(f['d_restauracion_a_api'])} | {s(f['d_restauracion_a_clip'])} | `{str(f['alerta'])[-12:] or 'None'}` |")

    # Retry behaviour observed during the outage: urgent-lane event attempts.
    if t_corte and t_rest:
        durante = sorted((i for i in intentos if t_corte <= float(i["inicio"]) < t_rest), key=lambda i: i["inicio"])  # type: ignore[arg-type]
        huecos = [float(b["inicio"]) - float(a["fin"]) for a, b in zip(durante, durante[1:])]  # type: ignore[arg-type]
        print(f"\nIntentos de eventos durante el corte: {len(durante)} "
              f"({', '.join(sorted({str(i['resultado']) for i in durante})) or '—'}); "
              f"duración de cada intento fallido: {', '.join('%.1f' % float(i['duracion_s']) for i in durante) or '—'} s; "
              f"espera entre el fin de un intento y el siguiente: {', '.join('%.1f' % h for h in huecos) or '—'} s.")
        primeros = [h for h in http if h[0] >= t_rest]
        if primeros:
            print(f"Primer intento HTTP después de restaurar: +{primeros[0][0] - t_rest:.2f} s ({primeros[0][1]} {ruta_generica(primeros[0][2])} → {primeros[0][3]}).")
        latidos = [h for h in http if h[2].endswith("/senal") and h[0] >= t_rest and h[3].startswith("2")]
        if latidos:
            print(f"Primer latido 2xx después de restaurar: +{latidos[0][0] + latidos[0][4] - t_rest:.2f} s.")
        ws_ok = [w for w in ws if w[0] >= t_rest and w[2] == "abierto"]
        if ws_ok:
            print(f"Canal WebSocket reabierto: +{ws_ok[0][0] - t_rest:.2f} s ({len(ws_ok)} conexiones después de restaurar).")
        maximo = max((p for t, p in pendientes if t_corte <= t <= t_rest + 60), default=None)
        if maximo is not None:
            print(f"Máximo de elementos en la cola durante el corte (aviso «Envío pendiente»): {maximo}.")

    if encolados:
        print("\nEsperas del hilo de captura para encolar en la bandeja de salida (≥ 0,05 s): "
              + "; ".join(f"{k.split('/')[-1] if '/' in k else 'agregar_evento'} {v[2] or ''} {v[1]:.2f} s "
                          f"({'durante el corte' if t_corte and t_rest and t_corte <= v[0] < t_rest else 'fuera del corte'})"
                          for k, v in sorted(encolados.items(), key=lambda kv: kv[1][0])) + ".")
    if atrasos:
        print("Tramos con cuadros entregados tarde al agente (hilo de captura bloqueado; una webcam real los "
              "habría descartado): " + "; ".join(f"{h - d:.1f} s, atraso máx {mx:.1f} s"
              f"{' (durante el corte)' if t_corte and t_rest and t_corte <= d < t_rest + 1 else ''}" for d, h, mx in atrasos) + ".")

    # Paths: every request the agent made, by path and phase (the proxy saw all of them, below).
    print("\n| Ruta (host + path) | Antes del corte | Durante el corte | Después |\n|---|---|---|---|")
    tabla: dict[str, list[list[int]]] = {}
    for inicio, metodo, destino, resultado, _ in http:
        fase = 0 if t_corte is None or inicio < t_corte else 1 if t_rest is None or inicio < t_rest else 2
        clave = f"{metodo} {ruta_generica(destino)}"
        celdas = tabla.setdefault(clave, [[0, 0], [0, 0], [0, 0]])
        celdas[fase][0 if resultado.startswith(("2", "3")) else 1] += 1
    for clave in sorted(tabla):
        print(f"| `{clave}` | " + " | ".join(f"{ok} ok / {ko} fallo" for ok, ko in tabla[clave]) + " |")
    hosts: dict[str, dict[str, int]] = {}
    for e in connects:
        hosts.setdefault(f"{e['host']}:{e['puerto']}", {}).setdefault(str(e["resultado"]), 0)
        hosts[f"{e['host']}:{e['puerto']}"][str(e["resultado"])] += 1
    print(f"\nCONNECT vistos por el proxy: " + "; ".join(f"`{h}` {r}" for h, r in sorted(hosts.items())))
    if no_connect:
        print(f"Peticiones sin túnel (no CONNECT) al proxy: {len(no_connect)}: {sorted({e['destino'] for e in no_connect})}")
    conexiones = work / "conexiones.txt"
    if conexiones.exists():
        texto = conexiones.read_text()
        directas = sorted({x for x in re.findall(r"->(\S+)", texto) if not x.startswith(("127.0.0.1", "[::1]", "localhost"))})
        print(f"Conexiones TCP del proceso del agente fuera del proxy (lsof cada 2 s): "
              f"{'ninguna' if not directas else ', '.join(directas)}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
