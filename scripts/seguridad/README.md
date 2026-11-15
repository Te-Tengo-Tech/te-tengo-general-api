# Security measurement against production

Measures the two Security scenarios of the paper's Table 2 against the deployed API, with two
throw-away accounts and the isolated desktop agent of `scripts/latencia-e2e`.

1. `crear_cuenta.sh seguridad-a <email> <name>` and `crear_cuenta.sh seguridad-b <email> <name>`:
   one account and household each; the password is typed (hidden) and never stored, only the session
   (`~/.tetengo-seguridad/<alias>.json`, mode 0600).
2. `medir_seguridad.py vincular`: links the isolated agent to household A (approves its code with A's
   session), grants A's consent and puts the synthetic webcam in «Sala».
3. `medir_seguridad.py medir --n 10 --m 5` (accountability): A opens the live view legitimately;
   B (another household) opens A's camera and changes A's open session; the media server is read
   without a token, with a made-up token and with the token of an ended session; A opens it after
   revoking consent (re-granted at the end). Writes `intentos.csv` and `resumen.json`, with the new
   access-log and denial-log entries (`GET /api/accesos-vista-en-vivo[/denegados]`, ADR 0015).
4. `medir_seguridad.py confidencialidad --previa 600 --vivo 60 --tras-caida 120` (confidentiality):
   the agent monitors a standing person, one live view is watched, then a fall happens. Every HTTPS
   connection of the agent goes through `../latencia-e2e/proxy_corte.py --progreso 1` (host and bytes
   of each tunnel); anything else is sampled with `nettop`. `resumen_confidencialidad.json` gives the
   bytes uploaded per phase and destination.

Results of 2026-11-15 (API 0.8.0): 55 of 55 unauthorized attempts rejected and logged, 6 of 6 granted
views logged; 7.6 min of monitoring uploaded 11 KB/min to the API and no video; video left only during
the live view (4.0 MB in 1 min) and as one 298-KB clip after the fall.
