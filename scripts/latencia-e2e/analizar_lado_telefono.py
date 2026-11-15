"""Case B (the phone loses its network while falls are detected): results of
``medir_produccion.sh corte`` with ``TT_CORTE_LADO=telefono``.

Inputs in the run directory:

* ``corte.csv`` (analizar_corte.py): per fall alert, t2 (API 2xx), t4 (Firebase receipt in the app)
  and t4n (system notification), all on this Mac's clock.
* ``red_telefono.csv``: samples taken from this Mac (``antes``/``despues`` = Mac clock around one
  ``adb shell`` call) of whether the phone has a route on wlan0 and answers a ping to 8.8.8.8.

An offline window starts at the first sample without route or ping and ends at the first sample
with both again (``t_vuelta``, that sample's ``antes``: the phone was back at most one sample later).
An alert is affected when its t2 falls inside a window: the API sent its push while the phone was
offline. For those: t_vuelta → t4 / t4n. Duplicates: more than one alert per tag is impossible by
construction; we count alerts with no notification and notifications whose tag is not an alert.

``--en-linea S DIR`` only checks (exit 0/1) that the last S seconds of samples are all online and
that at least one offline window happened, so the measurement script can wait for the shortcut.
"""

from __future__ import annotations

import csv
import statistics
import sys
from pathlib import Path


def muestras(work: Path) -> list[tuple[float, float, bool]]:
    filas = []
    with (work / "red_telefono.csv").open() as f:
        for r in csv.DictReader(f):
            try:
                filas.append((float(r["antes"]), float(r["despues"]), r["ruta_wlan0"].strip() not in ("", "0") and r["ping"].strip() == "1"))
            except (ValueError, KeyError, AttributeError):
                continue
    return filas


def ventanas(m: list[tuple[float, float, bool]]) -> list[tuple[float, float | None]]:
    """Offline windows (t_caida, t_vuelta); t_vuelta None if still offline at the end."""
    res: list[tuple[float, float | None]] = []
    inicio = None
    previo_ok = None
    for antes, despues, ok in m:
        if not ok and inicio is None:
            # It went down between the last good sample's end and this sample's start.
            inicio = antes if previo_ok is None else (previo_ok + antes) / 2
        elif ok and inicio is not None:
            res.append((inicio, antes))
            inicio = None
        if ok:
            previo_ok = despues
    if inicio is not None:
        res.append((inicio, None))
    return res


def resumen(v: list[float]) -> str:
    if not v:
        return "— | — | — | — | 0"
    v = sorted(v)
    q = statistics.quantiles(v, n=4) if len(v) > 1 else [v[0], v[0], v[0]]
    return f"{statistics.median(v):.2f} | {q[0]:.2f}–{q[2]:.2f} | {v[0]:.2f} | {v[-1]:.2f} | {len(v)}"


def main() -> int:
    args = sys.argv[1:]
    if args and args[0] == "--en-linea":
        segundos, work = float(args[1]), Path(args[2])
        m = muestras(work) if (work / "red_telefono.csv").exists() else []
        if not m or not ventanas(m):
            return 1
        fin = m[-1][1]
        ultimos = [ok for a, _, ok in m if a >= fin - segundos]
        return 0 if ultimos and all(ultimos) and m[0][0] <= fin - segundos else 1

    work = Path(args[0])
    m = muestras(work)
    vs = ventanas(m)
    paso = statistics.median(d - a for a, d, _ in m) if m else float("nan")
    filas = list(csv.DictReader((work / "corte.csv").open()))
    f = lambda r, k: float(r[k]) if r.get(k) else None  # noqa: E731

    print("# Caso B: el teléfono pierde la red mientras se detectan caídas\n")
    print(f"- Muestras de red del teléfono: {len(m)} (una cada {paso:.2f} s de mediana, desde esta Mac por adb)")
    for i, (a, b) in enumerate(vs, 1):
        print(f"- Ventana sin red {i}: {'%.1f s' % (b - a) if b else 'sin volver'}")
    print()

    afectadas, normales = [], []
    for r in filas:
        t2 = f(r, "t2")
        dentro = next(((a, b) for a, b in vs if t2 is not None and a <= t2 < (b if b else float("inf"))), None)
        (afectadas if dentro else normales).append((r, dentro))

    def notificada(r: dict) -> bool:
        return bool(r.get("t4n"))

    print("| Métrica | Valor |\n|---|---|")
    print(f"| Alertas de caída (API 2xx) | {sum(1 for r in filas if r.get('t2'))} de {len(filas)} |")
    print(f"| … aceptadas por la API con el teléfono sin red | {len(afectadas)} |")
    print(f"| … notificadas en el teléfono | {sum(notificada(r) for r, _ in afectadas)} de {len(afectadas)} |")
    print(f"| Todas las alertas notificadas en el teléfono | {sum(notificada(r) for r in filas)} de {len(filas)} |")
    print(f"| Duplicados en la API (2xx repetido o 200 «ya estaba») | {sum(1 for r in filas if (r.get('respuestas_2xx') or '0') not in ('0', '1') or r.get('duplicado_api_200') == 'True')} |")
    print()
    d_vuelta_n = [f(r, "t4n") - b for r, (a, b) in afectadas if b and r.get("t4n")]
    d_vuelta_p = [f(r, "t4") - b for r, (a, b) in afectadas if b and r.get("t4")]
    d_api_n_af = [f(r, "t4n") - f(r, "t2") for r, _ in afectadas if r.get("t4n")]
    d_api_n_no = [f(r, "t4n") - f(r, "t2") for r, _ in normales if r.get("t4n") and r.get("t2")]
    print("| Retardo | Mediana (s) | RIC (s) | Mín (s) | Máx (s) | n |\n|---|---|---|---|---|---|")
    print(f"| **vuelta de la red → notificación** (alertas enviadas sin red) | {resumen(d_vuelta_n)} |")
    print(f"| vuelta de la red → push en el proceso de la app | {resumen(d_vuelta_p)} |")
    print(f"| API 2xx → notificación (alertas enviadas sin red) | {resumen(d_api_n_af)} |")
    print(f"| API 2xx → notificación (teléfono con red, control) | {resumen(d_api_n_no)} |")
    print()
    print("| Video | Alerta | Teléfono sin red al enviar | API 2xx → notificación (s) | Vuelta → notificación (s) |\n|---|---|---|---|---|")
    for r in filas:
        dentro = next((x for x in vs if r.get("t2") and x[0] <= float(r["t2"]) < (x[1] or float("inf"))), None)
        an = f"{float(r['t4n']) - float(r['t2']):.2f}" if r.get("t4n") and r.get("t2") else "—"
        vn = f"{float(r['t4n']) - dentro[1]:.2f}" if dentro and dentro[1] and r.get("t4n") else "—"
        print(f"| {r['video']} | `{str(r.get('alerta'))[-12:]}` | {'sí' if dentro else 'no'} | {an} | {vn} |")
    return 0


if __name__ == "__main__":
    sys.exit(main())
