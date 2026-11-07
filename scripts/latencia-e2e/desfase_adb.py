"""Clock offset of an Android device (or emulator) against this machine, through adb.

Usage: python3 desfase_adb.py <output.csv> [round trips, default 30]

Each round trip reads this machine's clock (h0), runs `adb shell date +%s.%N` (the device's clock,
d) and reads this machine's clock again (h1). The offset of that round trip is d - (h0 + h1) / 2,
and its error is at most (h1 - h0) / 2: the device read its clock somewhere inside the round trip.
The estimate is the offset of the shortest round trip (the tightest bound); the median offset and
its spread are printed too. Device timestamps are moved onto this machine's clock as
t_host = t_device - offset.

Writes one row per round trip and prints `offset_s=<…> error_s=<…>` (the shortest round trip).
"""

import statistics
import subprocess
import sys
import time


def main() -> int:
    salida, n = sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 30
    filas = []
    for i in range(n):
        h0 = time.time()
        d = subprocess.run(["adb", "shell", "date", "+%s.%N"], capture_output=True, text=True, check=True)
        h1 = time.time()
        dispositivo = float(d.stdout.strip())
        filas.append((i, h0, dispositivo, h1, h1 - h0, dispositivo - (h0 + h1) / 2))
        time.sleep(0.1)
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
        f"median_rtt_s={statistics.median(f[4] for f in filas):.6f} n={n}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
