"""Per-stage latency of the fall alerts recorded by scripts/latencia-e2e/medir_produccion.sh.

Usage: python3 analizar_produccion.py <work directory>

Like analizar.py, but against the production API, whose log this machine cannot read: there is no
t3 (the API's push result). Per run (<work>/corridas/<video>/agente.log, agente_produccion.py's
marks), on this machine's clock:

  t0  first frame at or after the labelled fall onset handed to the agent
  t1  the classifier emits `caida`                     («Evento detectado: caida <id>»)
  t2  the production API accepted the event (2xx)      («Evento <id> enviado»); the alert id it
                                                       returned is logged too («LATENCIA evento … alerta …»)
  tc  the event's clip is uploaded to Azure storage    («Clip del evento <id> subido»)
  t4n the system notification for that alert is posted on the phone (event log
      `notification_enqueue`, whose tag is the alert id; the first one at or after t1, as a later
      notice of the same alert, e.g. the escalation, replaces it under the same tag)
  t4  the push reached the app's process (Firebase SDK «FirebaseMessaging: Starting service» from
      one of the app's pids, app-pid-*.txt and «Start proc <pid>:tech.tetengo.te_tengo»): the
      last one in [t1, t4n]; without t4n, the first one in [t1, t1 + 30 s] (marked approximate,
      since another alert's escalation could arrive in that window)

The phone's timestamps (`adb logcat -v epoch,usec`) are moved onto this machine's clock with the
offset measured before and after the series (desfase_adb.py), interpolated linearly in time.
Writes <work>/mediciones.csv and prints a Markdown summary.
"""

import csv
import json
import re
import statistics
import sys
from pathlib import Path

from analizar import ANDROID_RECEPCION, ANDROID_TS, desfase_android, epoch_agente, primera

ANDROID_NOTIFICACION = re.compile(r"notification_enqueue: \[\d+,\d+,(\S+?),-?\d+,([^,\]]+),")
ANDROID_PID = re.compile(r"^\s*\d{9,}\.\d+\s+(\d+)\s")
PAQUETE = "tech.tetengo.te_tengo"
INICIO_PROCESO = re.compile(rf"Start proc (\d+):{re.escape(PAQUETE)}[/ ]")


def cuantiles(valores: list[float]) -> str:
    if not valores:
        return "| — | — | — | — | 0 |"
    v = sorted(valores)
    q1, q3 = (statistics.quantiles(v, n=4, method="inclusive")[::2] if len(v) >= 2 else (v[0], v[0]))
    return f"| {statistics.median(v):.2f} | {q1:.2f}–{q3:.2f} | {v[0]:.2f} | {v[-1]:.2f} | {len(v)} |"


def main() -> int:
    work = Path(sys.argv[1])
    desfase = desfase_android(work)
    recepciones: list[float] = []
    notificaciones: dict[str, list[float]] = {}
    android = work / "android.log"
    if android.exists() and desfase is not None:
        lineas = android.read_text(errors="replace").splitlines()
        # Only the Te Tengo app's own Firebase receipts: its pids before/after the series and every
        # process the system started for it meanwhile (other apps use Firebase Messaging too).
        pids = {p for ruta in work.glob("app-pid-*.txt") for p in ruta.read_text().split()}
        pids |= {m.group(1) for linea in lineas if (m := INICIO_PROCESO.search(linea))}
        for linea in lineas:
            m = ANDROID_TS.match(linea)
            if not m:
                continue
            t_disp = float(m.group(1))
            t_host = t_disp - desfase(t_disp)
            if ANDROID_RECEPCION.search(linea):
                mp = ANDROID_PID.match(linea)
                if not pids or (mp and mp.group(1) in pids):
                    recepciones.append(t_host)
            elif (mn := ANDROID_NOTIFICACION.search(linea)) and mn.group(1) == PAQUETE:
                notificaciones.setdefault(mn.group(2), []).append(t_host)
        recepciones.sort()

    filas: list[dict[str, object]] = []
    for corrida in sorted((work / "corridas").iterdir()):
        ruta = corrida / "agente.log"
        if not ruta.exists():
            continue
        agente = ruta.read_text(errors="replace").splitlines()
        fila: dict[str, object] = {"video": corrida.name}
        m0 = next((re.search(r"inicio_caida epoch=([\d.]+)", x) for x in agente if "LATENCIA inicio_caida" in x), None)
        t0 = float(m0.group(1)) if m0 else None
        t1, m1 = primera(agente, r"Evento detectado: caida ([0-9a-f-]+)", epoch_agente)
        evento = m1.group(1) if m1 else None
        t2 = tc = t4 = t4n = None
        alerta = None
        aproximado = False
        if evento:
            t2, _ = primera(agente, rf"Evento {evento} enviado", epoch_agente)
            tc, _ = primera(agente, rf"Clip del evento {evento} subido", epoch_agente)
            _, ma = primera(agente, rf"LATENCIA evento {evento} alerta (\S+)", epoch_agente)
            alerta = ma.group(1) if ma and ma.group(1) != "None" else None
        if t1 is not None:
            if alerta:
                t4n = next((t for t in sorted(notificaciones.get(alerta, [])) if t >= t1), None)
            if t4n is not None:
                previas = [t for t in recepciones if t1 <= t <= t4n + 0.001]
                t4 = previas[-1] if previas else None
            else:
                t4 = next((t for t in recepciones if t1 <= t <= t1 + 30), None)
                aproximado = t4 is not None

        def d(a: float | None, b: float | None) -> float | None:
            return None if a is None or b is None else b - a

        fila.update(evento=evento, alerta=alerta, t4_aproximado=aproximado or "")
        for nombre, t in (("t0", t0), ("t1", t1), ("t2", t2), ("tclip", tc), ("t4", t4), ("t4n", t4n)):
            fila[nombre] = f"{t:.3f}" if t else ""
        fila["d01_deteccion"] = d(t0, t1)
        fila["d12_evento_aceptado"] = d(t1, t2)
        fila["d02_caida_a_api"] = d(t0, t2)
        fila["d24_api_a_telefono"] = d(t2, t4)
        fila["d24n_api_a_notificacion"] = d(t2, t4n)
        fila["d44n_recepcion_a_notificacion"] = d(t4, t4n)
        fila["d04_total"] = d(t0, t4)
        fila["d04n_total_notificacion"] = d(t0, t4n)
        fila["d1c_deteccion_a_clip"] = d(t1, tc)
        fila["d0c_caida_a_clip"] = d(t0, tc)
        fila["evento_antes_que_clip"] = "" if t2 is None or tc is None else str(t2 < tc)
        filas.append(fila)
    if not filas:
        sys.exit("no runs in corridas/")

    with (work / "mediciones.csv").open("w", newline="") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=list(filas[0]))
        escritor.writeheader()
        for fila in filas:
            escritor.writerow({k: (f"{v:.3f}" if isinstance(v, float) else v) for k, v in fila.items()})

    entorno = json.loads((work / "entorno.json").read_text()) if (work / "entorno.json").exists() else {}
    detectadas = sum(1 for f in filas if f["evento"])
    con_alerta = sum(1 for f in filas if f["alerta"])
    print(f"# Latencia de la alerta de caída contra producción ({len(filas)} caídas URFD reproducidas)\n")
    print(f"- Detectadas como `caida`: {detectadas} de {len(filas)}; con alerta creada por la API: {con_alerta}.")
    for clave, titulo in (
        ("api", "API"),
        ("fecha", "Fecha (UTC)"),
        ("telefono", "Teléfono"),
        ("android", "Android"),
        ("red_tipo", "Red activa del teléfono"),
        ("red_subtipo", "Tecnología móvil"),
        ("operador", "Operador"),
        ("wifi", "Wi-Fi"),
        ("cargando", "Cargando por USB"),
        ("agente", "Agente de escritorio"),
        ("espera_previa_s", "Pausa con la persona de pie antes de cada caída (s)"),
    ):
        if clave in entorno:
            print(f"- {titulo}: {entorno[clave]}")
    for nombre in ("android-desfase-antes.txt", "android-desfase-despues.txt"):
        if (work / nombre).exists():
            print(f"- Desfase de reloj ({nombre.split('-')[-1][:-4]}): `{(work / nombre).read_text().strip()}`")
    print()
    print("| Etapa | Mediana (s) | RIC (s) | Mín (s) | Máx (s) | n |")
    print("|---|---|---|---|---|---|")
    for titulo, clave in (
        ("t0→t1 detección (inicio de la caída → `caida`)", "d01_deteccion"),
        ("t1→t2 evento aceptado por la API de producción (2xx)", "d12_evento_aceptado"),
        ("t0→t2 caída → API", "d02_caida_a_api"),
        ("t2→t4 API acepta → push en el proceso de la app", "d24_api_a_telefono"),
        ("t2→t4n API acepta → notificación del sistema", "d24n_api_a_notificacion"),
        ("t4→t4n push en la app → notificación", "d44n_recepcion_a_notificacion"),
        ("**t0→t4 caída → push en el teléfono**", "d04_total"),
        ("**t0→t4n caída → notificación en el teléfono**", "d04n_total_notificacion"),
        ("t1→tclip detección → clip subido", "d1c_deteccion_a_clip"),
        ("t0→tclip caída → clip subido", "d0c_caida_a_clip"),
    ):
        valores = [v for f in filas if isinstance(v := f[clave], float)]
        print(f"| {titulo} " + cuantiles(valores))
    antes = [f["evento_antes_que_clip"] for f in filas if f["evento_antes_que_clip"]]
    print(f"\nEvento aceptado antes de subir su clip: {antes.count('True')} de {len(antes)}.")
    aprox = sum(1 for f in filas if f["t4_aproximado"])
    if aprox:
        print(f"t4 sin notificación del sistema (emparejado por ventana de 30 s, aproximado): {aprox}.")
    total = [v for f in filas if isinstance(v := f["d04n_total_notificacion"], float)] or [
        v for f in filas if isinstance(v := f["d04_total"], float)
    ]
    if total:
        print(f"Dentro de 10 s (caída → teléfono): {sum(1 for v in total if v < 10)} de {len(total)}.")
    salud = work / "salud.csv"
    if salud.exists():
        with salud.open() as archivo:
            tiempos = [float(f["total_s"]) for f in csv.DictReader(archivo) if f["codigo"] == "200"]
        if tiempos:
            print(
                f"\nContexto: `GET /actuator/health` desde esta Mac, {len(tiempos)} peticiones (HTTP/2, "
                f"conexión nueva cada una): mediana {statistics.median(tiempos) * 1000:.0f} ms, "
                f"mín {min(tiempos) * 1000:.0f} ms, máx {max(tiempos) * 1000:.0f} ms."
            )
    return 0


if __name__ == "__main__":
    sys.exit(main())
