"""Clock offset of an Android device (or emulator) against this machine, through adb.

Usage: python3 desfase_adb.py <output.csv> [round trips, default 30]

Each round trip reads this machine's clock (h0), reads the device's clock (d) and reads this
machine's clock again (h1). The offset of that round trip is d - (h0 + h1) / 2, and its error is at
most (h1 - h0) / 2: the device read its clock somewhere inside the round trip. The estimate is the
offset of the shortest round trip (the tightest bound); the median offset and its spread are printed
too. Device timestamps are moved onto this machine's clock as t_host = t_device - offset.

The device's clock is read with `adb shell date +%s.%N`. Older toybox builds (Android 7 and some 8)
have no %N and print the second only; then each round trip writes a line to the device's log instead
(`adb shell log -t TTLATENCIA <i>`), and d is that line's `logcat -v epoch,usec` timestamp: the same
clock and resolution the measurement reads t4 from.

Writes one row per round trip and prints `offset_s=<…> error_s=<…>` (the shortest round trip).
"""

import re
import statistics
import subprocess
import sys
import time
import uuid

FRACCION = re.compile(r"^\d{9,}\.\d{3,}$")


def adb(*argumentos: str) -> str:
    return subprocess.run(
        ["adb", *argumentos], capture_output=True, text=True, check=True, stdin=subprocess.DEVNULL
    ).stdout


def con_date(n: int) -> list[tuple[float, float, float]]:
    filas = []
    for _ in range(n):
        h0 = time.time()
        d = adb("shell", "date", "+%s.%N").strip()
        h1 = time.time()
        if not FRACCION.match(d):
            raise ValueError(d)
        filas.append((h0, float(d), h1))
        time.sleep(0.1)
    return filas


def con_log(n: int) -> list[tuple[float, float, float]]:
    marca = uuid.uuid4().hex[:8]
    anfitrion = []
    for i in range(n):
        h0 = time.time()
        adb("shell", "log", "-t", "TTLATENCIA", f"{marca}-{i}")
        h1 = time.time()
        anfitrion.append((h0, h1))
        time.sleep(0.1)
    salida = adb("logcat", "-d", "-v", "epoch,usec", "-s", "TTLATENCIA:*")
    dispositivo: dict[int, float] = {}
    for linea in salida.splitlines():
        m = re.match(rf"^\s*(\d{{9,}}\.\d+)\s.*TTLATENCIA.*\b{marca}-(\d+)\s*$", linea)
        if m:
            dispositivo[int(m.group(2))] = float(m.group(1))
    return [(h0, dispositivo[i], h1) for i, (h0, h1) in enumerate(anfitrion) if i in dispositivo]


def main() -> int:
    salida, n = sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 30
    metodo = "date"
    try:
        lecturas = con_date(n)
    except (ValueError, subprocess.CalledProcessError):
        metodo = "log"
        lecturas = con_log(n)
    if not lecturas:
        sys.exit("could not read the device's clock (neither date +%s.%N nor logcat marks)")
    filas = [(i, h0, d, h1, h1 - h0, d - (h0 + h1) / 2) for i, (h0, d, h1) in enumerate(lecturas)]
    with open(salida, "w") as archivo:
        archivo.write("i,host_antes,dispositivo,host_despues,rtt_s,desfase_s\n")
        for fila in filas:
            archivo.write(f"{fila[0]},{fila[1]:.6f},{fila[2]:.6f},{fila[3]:.6f},{fila[4]:.6f},{fila[5]:.6f}\n")
    mejor = min(filas, key=lambda f: f[4])
    desfases = [f[5] for f in filas]
    print(
        f"offset_s={mejor[5]:.6f} error_s={mejor[4] / 2:.6f} "
        f"median_offset_s={statistics.median(desfases):.6f} "
        f"min_max_offset_s={min(desfases):.6f}..{max(desfases):.6f} "
        f"median_rtt_s={statistics.median(f[4] for f in filas):.6f} n={len(filas)} method={metodo}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
