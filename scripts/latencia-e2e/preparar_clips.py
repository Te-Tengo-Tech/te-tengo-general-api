"""URFD fall clips for the production measurement (scripts/latencia-e2e/medir_produccion.sh).

Usage: <desktop>/.venv/bin/python preparar_clips.py <urfd dir> <output dir> <lead-in s> <hold s>
       <video-name ...>

As in scripts/medir-latencia-e2e.sh: only the RGB half of each URFD frame (320 × 240), lossless
(FFV1), the last frame held for <hold> s (< 30 s, so the agent never sends `caida_confirmada`).
Unlike it, each clip starts with its first frame (the person standing, before the fall) held for
<lead-in> s: the agent keeps running and sending its heartbeat during the pause between falls, so the
API never marks the camera disconnected (3 missed 30 s heartbeats) and pushes «Cámara desconectada».

With <hold> = `solo`, each clip is only the lead-in (the first frame held): what the camera shows
while the PC is being linked; the agent plays it in a loop.

Prints one line per clip: name, path, onset (s from the start of the clip, lead-in included: the
first frame labelled 0 «falling» in urfall-cam0-falls.csv, (frame - 1) / 30 s, as in the agent's
validation), duration of the URFD part (s), lead-in (s).
"""

import csv
import sys
from pathlib import Path

import cv2


def main() -> int:
    datos, destino = Path(sys.argv[1]), Path(sys.argv[2])
    previa, nombres = float(sys.argv[3]), sys.argv[5:]
    solo = sys.argv[4] == "solo"
    sostener = 0.0 if solo else float(sys.argv[4])
    destino.mkdir(parents=True, exist_ok=True)
    inicios: dict[str, int] = {}
    with (datos / "urfall-cam0-falls.csv").open(newline="") as archivo:
        for fila in csv.reader(archivo):
            if fila and fila[2].strip() == "0":
                inicios.setdefault(fila[0], int(fila[1]))
    for nombre in nombres:
        video = cv2.VideoCapture(str(datos / f"{nombre}-cam0.mp4"))
        fps = video.get(cv2.CAP_PROP_FPS) or 30.0
        cuadros = []
        while True:
            leido, cuadro = video.read()
            if not leido:
                break
            cuadros.append(cuadro[0:240, 320:640])
        if not cuadros:
            sys.exit(f"no frames in {nombre}")
        ruta = destino / f"{nombre}{'-de-pie' if solo else ''}.avi"
        salida = cv2.VideoWriter(str(ruta), cv2.VideoWriter_fourcc(*"FFV1"), fps, (320, 240))
        n_previa = int(round(fps * previa))
        for _ in range(n_previa):
            salida.write(cuadros[0])
        for cuadro in [] if solo else cuadros:
            salida.write(cuadro)
        for _ in range(int(fps * sostener)):
            salida.write(cuadros[-1])
        salida.release()
        inicio = n_previa / fps + (inicios[nombre] - 1) / 30
        print(f"{nombre}\t{ruta}\t{inicio:.4f}\t{len(cuadros) / fps:.3f}\t{n_previa / fps:.3f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
