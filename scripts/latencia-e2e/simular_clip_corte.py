"""Offline check of a concatenated outage clip: which classifier events it produces (no network).

Usage: <desktop>/.venv/bin/python simular_clip_corte.py <clip.avi> <clip.tsv>

Replays the clip written by preparar_clip_corte.py through the agent's own detection chain, as fast
as possible: FuenteArchivo, the 8 fps Muestreador, 480p + JPEG 80 (``preparar``), the agent's
MediaPipe estimator (VIDEO mode, its default model: models/pose_landmarker_lite.task) and ClasificadorCinematico with
the shipped thresholds (config.UMBRALES_CALIBRADOS; the production API may send others at start).
Prints each event with its clip time and, per fall, whether exactly one `caida` falls between its
onset and the next fall's onset. Run it before the series: in one continuous process a fall is
only detected if the classifier saw the person upright again after the previous one (`recuperacion`
needs the key points visible and upright for a while), which depends on each video's first frame.
Exit status 1 if some fall gets no `caida`.
"""

import sys
from pathlib import Path

from te_tengo_captura.captura.fuentes import FuenteArchivo, Muestreador, preparar
from te_tengo_captura.captura.pose import EstimadorMediaPipe, ruta_modelo_predeterminada
from te_tengo_captura.config import UMBRALES_CALIBRADOS
from te_tengo_deteccion.clasificacion.estados import ClasificadorCinematico
from te_tengo_deteccion.clasificacion.umbrales import Umbrales


def main() -> int:
    clip, tsv = Path(sys.argv[1]), Path(sys.argv[2])
    caidas = [x.split("\t") for x in tsv.read_text().splitlines() if x and not x.startswith("TOTAL")]
    inicios = [(nombre, float(inicio)) for nombre, inicio, _ in caidas]
    modelo = ruta_modelo_predeterminada()
    fuente = FuenteArchivo(clip)
    if not fuente.abrir():
        sys.exit(f"cannot open {clip}")
    muestreador = Muestreador()
    estimador = EstimadorMediaPipe(modelo)
    clasificador = ClasificadorCinematico(Umbrales(**UMBRALES_CALIBRADOS))
    eventos: list[tuple[float, str]] = []
    while (leido := fuente.leer()) is not None:
        instante, imagen = leido
        if not muestreador.tomar(instante):
            continue
        fotograma = preparar(imagen, instante)
        pose = estimador.estimar(fotograma.imagen, round(instante * 1000))
        for evento in clasificador.actualizar(instante, pose):
            eventos.append((instante, evento.tipo.value))
            print(f"{instante:8.2f}  {evento.tipo.value}")
    fuente.cerrar()
    faltan = 0
    for i, (nombre, inicio) in enumerate(inicios):
        fin = inicios[i + 1][1] if i + 1 < len(inicios) else float("inf")
        tipos = [t for s, t in eventos if inicio - 1 <= s < fin - 1]
        n = tipos.count("caida")
        faltan += n == 0
        print(f"{nombre}: onset {inicio:.1f} s → {n} caida; events until the next fall: {', '.join(tipos) or '—'}")
    print(f"{len(inicios) - faltan} of {len(inicios)} falls detected")
    return 1 if faltan else 0


if __name__ == "__main__":
    sys.exit(main())
