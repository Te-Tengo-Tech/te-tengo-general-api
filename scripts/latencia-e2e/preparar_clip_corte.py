"""One concatenated URFD clip with several falls, for the link-outage test (medir_produccion.sh corte).

Usage: <desktop>/.venv/bin/python preparar_clip_corte.py <urfd dir> <output.avi> <lead-in s> <hold s>
       <tail s> <video-name ...>

The outage must cover several falls while the agent keeps running (heartbeat, outbox, channels),
so unlike preparar_clips.py (one clip and one agent process per fall) the falls are played by a
single agent process from one file. For each video, as in preparar_clips.py: its first frame (the
person standing) held for <lead-in> s, the RGB half of the URFD frames (320 × 240, lossless FFV1),
its last frame held for <hold> s (< 30 s: no `caida_confirmada`). After the last fall, <tail> s of
that video's first frame (standing): the time the queue has to drain before the file would loop.
Going from lying (hold) to standing (next lead-in) is a `recuperacion` for the classifier («se
levantó» to the household), as when a person gets up after a fall.

Prints one line per fall: name, onset (s from the start of the file: lead-in included, the first
frame labelled 0 «falling» in urfall-cam0-falls.csv, (frame - 1) / 30 s), end of its hold (s); and a
last line `TOTAL <path> <duration s>`.
"""

import csv
import sys
from pathlib import Path

import cv2


def main() -> int:
    datos, ruta = Path(sys.argv[1]), Path(sys.argv[2])
    previa, sostener, cola = float(sys.argv[3]), float(sys.argv[4]), float(sys.argv[5])
    nombres = sys.argv[6:]
    if sostener >= 30:
        sys.exit("hold must be < 30 s (no caida_confirmada)")
    inicios: dict[str, int] = {}
    with (datos / "urfall-cam0-falls.csv").open(newline="") as archivo:
        for fila in csv.reader(archivo):
            if fila and fila[2].strip() == "0":
                inicios.setdefault(fila[0], int(fila[1]))
    ruta.parent.mkdir(parents=True, exist_ok=True)
    salida = None
    fps = 30.0
    escritos = 0
    lineas = []
    primero = None
    for nombre in nombres:
        video = cv2.VideoCapture(str(datos / f"{nombre}-cam0.mp4"))
        fps_video = video.get(cv2.CAP_PROP_FPS) or 30.0
        cuadros = []
        while True:
            leido, cuadro = video.read()
            if not leido:
                break
            cuadros.append(cuadro[0:240, 320:640])
        if not cuadros:
            sys.exit(f"no frames in {nombre}")
        if salida is None:
            fps = fps_video
            salida = cv2.VideoWriter(str(ruta), cv2.VideoWriter_fourcc(*"FFV1"), fps, (320, 240))
        primero = cuadros[0]
        n_previa = int(round(fps * previa))
        for _ in range(n_previa):
            salida.write(cuadros[0])
        inicio = (escritos + n_previa) / fps + (inicios[nombre] - 1) / 30
        escritos += n_previa
        for cuadro in cuadros:
            salida.write(cuadro)
        escritos += len(cuadros)
        for _ in range(int(fps * sostener)):
            salida.write(cuadros[-1])
        escritos += int(fps * sostener)
        lineas.append(f"{nombre}\t{inicio:.4f}\t{escritos / fps:.3f}")
    assert salida is not None and primero is not None
    for _ in range(int(fps * cola)):
        salida.write(primero)
    escritos += int(fps * cola)
    salida.release()
    print("\n".join(lineas))
    print(f"TOTAL\t{ruta}\t{escritos / fps:.3f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
