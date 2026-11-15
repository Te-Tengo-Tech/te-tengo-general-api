#!/usr/bin/env bash
# Creates one test account with its own household in production for the security measurement
# (paper, Table 2, Security scenarios) and saves its session (tokens only, never the password)
# in ~/.tetengo-seguridad/<alias>.json, readable only by this user.
#
#   crear_cuenta.sh <alias> <correo> <nombre>
#
# The password is typed here, hidden, and only sent to the API over HTTPS.
set -euo pipefail

API="${TT_API:-https://api.tetengo.reqsai.tech}"
ALIAS="${1:?alias (p. ej. seguridad-a)}"
CORREO="${2:?correo}"
NOMBRE="${3:?nombre}"
DIR="$HOME/.tetengo-seguridad"
mkdir -p "$DIR" && chmod 700 "$DIR"

read -r -s -p "Contraseña para $CORREO: " CLAVE; echo
read -r -s -p "Repítela: " CLAVE2; echo
[ "$CLAVE" = "$CLAVE2" ] || { echo "No coinciden." >&2; exit 1; }

cuerpo() { CORREO="$CORREO" CLAVE="$CLAVE" NOMBRE="$NOMBRE" python3 -c '
import json, os, sys
d = {"correo": os.environ["CORREO"], "contrasena": os.environ["CLAVE"]}
if sys.argv[1] == "registro": d["nombre"] = os.environ["NOMBRE"]
print(json.dumps(d))' "$1"; }

post() { # ruta, cuerpo por stdin, [token]
  local auth=()
  [ -n "${2:-}" ] && auth=(-H "Authorization: Bearer $2")
  curl -sS -w '\n%{http_code}' -X POST "$API$1" -H 'Api-Version: 1' -H 'Content-Type: application/json' \
    ${auth[@]+"${auth[@]}"} --data-binary @-
}
codigo() { tail -n1 <<<"$1"; }
json() { sed '$d' <<<"$1"; }

r=$(cuerpo registro | post /api/cuentas)
case $(codigo "$r") in
  201) echo "Cuenta creada: $CORREO" ;;
  409) echo "La cuenta ya existía: sigo con el inicio de sesión" ;;
  *) echo "Registro falló ($(codigo "$r")): $(json "$r")" >&2; exit 1 ;;
esac

r=$(cuerpo sesion | post /api/sesiones)
[ "$(codigo "$r")" = 200 ] || { echo "Inicio de sesión falló ($(codigo "$r")): $(json "$r")" >&2; exit 1; }
unset CLAVE CLAVE2
sesion=$(json "$r")
guardar() { (umask 077; printf '%s\n' "$1" > "$DIR/$ALIAS.json"); }
guardar "$sesion" # the session is kept even if the household step fails (it can be refreshed)
token=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["tokenAcceso"])' <<<"$sesion")
hogar=$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("hogarId") or "")' <<<"$sesion")

if [ -z "$hogar" ]; then
  r=$(printf '{"adultoMayor":{"nombre":"Adulto de prueba %s","edad":78,"direccion":"Lima","convivencia":"SOLO"}}' "$ALIAS" \
      | post /api/hogar "$token")
  [ "$(codigo "$r")" = 201 ] || { echo "Crear hogar falló ($(codigo "$r")): $(json "$r")" >&2; exit 1; }
  sesion=$(json "$r")
  echo "Hogar creado"
fi

guardar "$sesion"
python3 -c 'import json,sys; s=json.load(open(sys.argv[1])); print("Listo:", sys.argv[2], "hogar", s.get("hogarId"), "rol", s.get("rol"))' "$DIR/$ALIAS.json" "$ALIAS"
