"""Per-stage latency of the fall alerts recorded by scripts/medir-latencia-e2e.sh.

Usage: python3 analizar.py <work directory>

Reads, for every run in <work>/corridas/<video>/: the agent log (with the marks of
agente_con_marcas.py), alerta.json; and the API log (<work>/api.log) and, when present, the iOS
simulator log (<work>/simulador.log). All timestamps come from this machine's clock. Writes
<work>/mediciones.csv and prints a Markdown summary (median, IQR, min–max, n per stage).
"""

import csv
import json
import re
import statistics
import sys
from datetime import datetime
from pathlib import Path

ISO_API = re.compile(r"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+(?:Z|[+-]\d{2}:?\d{2}))")
AGENTE_TS = re.compile(r"^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3})")
SIM_TS = re.compile(r"^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})")
# The notice that opens a fall alert: ALERTA_CAIDA, or ALERTA_ACTUALIZADA_A_CAIDA when the agent
# first reported an unstable movement.
TIPOS_CAIDA = r"(ALERTA_CAIDA|ALERTA_ACTUALIZADA_A_CAIDA)"
# The iOS simulator receives the remote notification (apsd hands it to the app's topic) or shows it.
SIM_RECEPCION = re.compile(
    r"Received message for enabled topic|Received incoming message on topic|"
    r"Received remote notification request|Received incoming notification",
    re.I,
)


def epoch_agente(linea: str) -> float | None:
    m = AGENTE_TS.match(linea)
    if not m:
        return None
    return datetime.strptime(m.group(1), "%Y-%m-%d %H:%M:%S,%f").astimezone().timestamp()


def epoch_api(linea: str) -> float | None:
    m = ISO_API.match(linea)
    if not m:
        return None
    texto = re.sub(r"(\.\d{6})\d+", r"\1", m.group(1)).replace("Z", "+00:00")
    return datetime.fromisoformat(texto).timestamp()


def epoch_sim(linea: str) -> float | None:
    m = SIM_TS.match(linea)
    if not m:
        return None
    return datetime.strptime(m.group(1), "%Y-%m-%d %H:%M:%S.%f").astimezone().timestamp()


def primera(lineas: list[str], patron: str, a_epoch) -> tuple[float | None, re.Match[str] | None]:  # type: ignore[no-untyped-def]
    expresion = re.compile(patron)
    for linea in lineas:
        m = expresion.search(linea)
        if m:
            return a_epoch(linea), m
    return None, None


def resumen(valores: list[float]) -> str:
    if not valores:
        return "| — | — | — | 0 |"
    v = sorted(valores)
    if len(v) >= 2:
        q1, _, q3 = statistics.quantiles(v, n=4, method="inclusive")
    else:
        q1 = q3 = v[0]
    return f"| {statistics.median(v):.2f} | {q1:.2f}–{q3:.2f} | {v[0]:.2f}–{v[-1]:.2f} | {len(v)} |"


def main() -> int:
    work = Path(sys.argv[1])
    api = (work / "api.log").read_text(errors="replace").splitlines()
    sim_ruta = work / "simulador.log"
    sim = sim_ruta.read_text(errors="replace").splitlines() if sim_ruta.exists() else []
    peticiones_fcm = sorted(
        t for i, linea in enumerate(api) if "REQUEST  ----" in linea
        and i + 1 < len(api) and "fcm.googleapis.com" in api[i + 1] and (t := epoch_api(linea))
    )
    respuestas_fcm = sorted(t for linea in api if "RESPONSE ----" in linea and (t := epoch_api(linea)))
    recepciones = sorted(
        t for t in (epoch_sim(linea) for linea in sim if SIM_RECEPCION.search(linea)) if t
    )
    filas = []
    for corrida in sorted((work / "corridas").iterdir()):
        agente = (corrida / "agente.log").read_text(errors="replace").splitlines()
        fila: dict[str, object] = {"video": corrida.name}
        fila["inicio_caida_s"] = float((corrida / "inicio_caida.txt").read_text())
        m0 = next((re.search(r"inicio_caida epoch=([\d.]+)", x) for x in agente if "LATENCIA inicio_caida" in x), None)
        t0 = float(m0.group(1)) if m0 else None
        t1, m1 = primera(agente, r"Evento detectado: caida ([0-9a-f-]+)", epoch_agente)
        evento = m1.group(1) if m1 else None
        t2 = tc = t3 = t4 = None
        alerta = id_mensaje = None
        if evento:
            t2, _ = primera(agente, rf"Evento {evento} enviado", epoch_agente)
            tc, _ = primera(agente, rf"Clip del evento {evento} subido", epoch_agente)
        if (corrida / "alerta.json").exists():
            alerta = json.loads((corrida / "alerta.json").read_text())["id"]
            t3, m3 = primera(
                api,
                rf"Push {TIPOS_CAIDA} \(alerta {alerta}\) aceptado por FCM para \S+ \S+: (\S+)"
                rf"|Push {TIPOS_CAIDA} \(sin enviar, proveedor registro\).*alertaId={alerta}"
                rf"|Push {TIPOS_CAIDA} .*{alerta}",
                epoch_api,
            )
            if m3 and m3.group(2):
                id_mensaje = m3.group(2)
            if t3 is not None and recepciones:
                t4 = next((t for t in recepciones if t >= t3 - 0.05), None)
                if t4 is not None and t4 - t3 > 30:
                    t4 = None
        # With TT_LAT_DEBUG=1 the Google HTTP client logs every FCM request and response: the first
        # request after t2 is the API's dispatch (t3a) and its response is FCM's first answer (t3b).
        # t3 above is the API's own result line, after the Admin SDK's retries if FCM refused.
        t3a = t3b = None
        if t2 is not None:
            t3a = next((t for t in peticiones_fcm if t >= t2), None)
            if t3a is not None:
                t3b = next((t for t in respuestas_fcm if t >= t3a), None)
            if t3a is not None and t3a - t2 > 10:
                t3a = t3b = None
        fila.update(evento=evento, alerta=alerta, fcm_mensaje=id_mensaje)
        for nombre, t in (("t0", t0), ("t1", t1), ("t2", t2), ("tclip", tc), ("t3a", t3a),
                          ("t3b", t3b), ("t3", t3), ("t4", t4)):
            fila[nombre] = f"{t:.3f}" if t else ""

        def d(a: float | None, b: float | None) -> float | None:
            return None if a is None or b is None else b - a

        fila["d01_deteccion"] = d(t0, t1)
        fila["d12_envio_evento"] = d(t1, t2)
        fila["d23a_api_interno"] = d(t2, t3a)
        fila["d3a3b_fcm_rtt"] = d(t3a, t3b)
        fila["d23_api_a_fcm"] = d(t2, t3)
        fila["d03b_caida_a_respuesta_fcm"] = d(t0, t3b)
        fila["d34_fcm_a_dispositivo"] = d(t3, t4)
        fila["d03_caida_a_push_enviado"] = d(t0, t3)
        fila["d04_total"] = d(t0, t4)
        fila["d13_deteccion_a_push_enviado"] = d(t1, t3)
        fila["d1c_deteccion_a_clip_subido"] = d(t1, tc)
        fila["evento_antes_que_clip"] = "" if t2 is None or tc is None else str(t2 < tc)
        filas.append(fila)

    with (work / "mediciones.csv").open("w", newline="") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=list(filas[0]))
        escritor.writeheader()
        for fila in filas:
            escritor.writerow({k: (f"{v:.3f}" if isinstance(v, float) else v) for k, v in fila.items()})

    etapas = [
        ("t0→t1 detection (onset → `caida`)", "d01_deteccion"),
        ("t1→t2 event accepted by the API (2xx)", "d12_envio_evento"),
        ("t2→t3a API: commit, async dispatch, FCM request out", "d23a_api_interno"),
        ("t3a→t3b FCM HTTP v1 round trip (first answer)", "d3a3b_fcm_rtt"),
        ("t2→t3 API → FCM result logged by the API", "d23_api_a_fcm"),
        ("t3→t4 FCM → device", "d34_fcm_a_dispositivo"),
        ("t1→t3 detection → push sent", "d13_deteccion_a_push_enviado"),
        ("**t0→t3b fall → FCM answers the API**", "d03b_caida_a_respuesta_fcm"),
        ("t0→t3 fall → API logs the FCM result", "d03_caida_a_push_enviado"),
        ("**t0→t4 fall → push on device**", "d04_total"),
        ("t1→tclip detection → clip uploaded", "d1c_deteccion_a_clip_subido"),
    ]
    detectadas = sum(1 for f in filas if f["evento"])
    print(f"# Fall alert latency ({len(filas)} URFD falls replayed, {detectadas} detected as `caida`)\n")
    print("| Stage | Median (s) | IQR (s) | Min–max (s) | n |")
    print("|---|---|---|---|---|")
    for titulo, clave in etapas:
        valores = [v for f in filas if isinstance(v := f[clave], float)]
        print(f"| {titulo} " + resumen(valores))
    antes = [f["evento_antes_que_clip"] for f in filas if f["evento_antes_que_clip"]]
    print(f"\nEvent accepted before its clip was uploaded: {antes.count('True')} of {len(antes)}.")
    total = [v for f in filas if isinstance(v := f["d04_total"], float)]
    if not total:
        total = [v for f in filas if isinstance(v := f["d03b_caida_a_respuesta_fcm"], float)]
    if not total:
        total = [v for f in filas if isinstance(v := f["d03_caida_a_push_enviado"], float)]
    if total:
        print(f"Within 10 s: {sum(1 for v in total if v < 10)} of {len(total)}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
