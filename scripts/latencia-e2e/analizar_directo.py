"""FCM → device alone, from a run of fcm_directo.py with the device's log recorded.

Usage: python3 analizar_directo.py <dir>

<dir> holds envios.csv (fcm_directo.py), android.log (`adb logcat -v epoch,usec -b
main,system,events`) and android-desfase-antes/despues.csv (desfase_adb.py). For each send, t4 is
the first «FirebaseMessaging: Starting service» after the request left, and t4n the first
`notification_enqueue` of the app after t4, both moved onto this machine's clock as in analizar.py.
Writes <dir>/directo.csv and prints a Markdown summary.
"""

import csv
import sys
from pathlib import Path

from analizar import ANDROID_RECEPCION, ANDROID_TS, desfase_android, resumen


def main() -> int:
    work = Path(sys.argv[1])
    desfase = desfase_android(work)
    if desfase is None:
        sys.exit("no clock offset measurement")
    recepciones, notificaciones = [], []
    for linea in (work / "android.log").read_text(errors="replace").splitlines():
        m = ANDROID_TS.match(linea)
        if not m:
            continue
        t = float(m.group(1))
        t -= desfase(t)
        if ANDROID_RECEPCION.search(linea):
            recepciones.append(t)
        elif "notification_enqueue" in linea and "tech.tetengo.te_tengo" in linea:
            notificaciones.append(t)
    with (work / "envios.csv").open() as archivo:
        envios = [f for f in csv.DictReader(archivo) if f["http"] == "200"]
    filas = []
    for f in envios:
        te, tr = float(f["t_envio"]), float(f["t_respuesta"])
        t4 = next((t for t in sorted(recepciones) if t >= te), None)
        if t4 is not None and t4 - te > 30:
            t4 = None
        t4n = next((t for t in sorted(notificaciones) if t4 is not None and t >= t4), None)
        if t4n is not None and t4 is not None and t4n - t4 > 5:
            t4n = None
        filas.append({
            "i": f["i"], "t_envio": te, "t_respuesta": tr, "t4": t4, "t4n": t4n,
            "rtt_fcm": tr - te,
            "envio_a_dispositivo": None if t4 is None else t4 - te,
            "fcm_acepta_a_dispositivo": None if t4 is None else t4 - tr,
            "dispositivo_a_notificacion": None if t4n is None or t4 is None else t4n - t4,
            "envio_a_notificacion": None if t4n is None else t4n - te,
        })
    with (work / "directo.csv").open("w", newline="") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=list(filas[0]))
        escritor.writeheader()
        for fila in filas:
            escritor.writerow({k: ("" if v is None else f"{v:.6f}" if isinstance(v, float) else v) for k, v in fila.items()})
    print(f"# FCM → device alone ({len(envios)} direct sends accepted by FCM)\n")
    print("| Stage | Median (s) | IQR (s) | Min–max (s) | n |")
    print("|---|---|---|---|---|")
    for titulo, clave in (
        ("request out → FCM answers (HTTP v1 round trip)", "rtt_fcm"),
        ("request out → push in the app's process", "envio_a_dispositivo"),
        ("**FCM accepted → push in the app's process**", "fcm_acepta_a_dispositivo"),
        ("push in the app → system notification posted", "dispositivo_a_notificacion"),
        ("request out → notification posted", "envio_a_notificacion"),
    ):
        print(f"| {titulo} " + resumen([v for f in filas if isinstance(v := f[clave], float)]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
