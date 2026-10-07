#!/usr/bin/env bash
# Creates the installation credential of one household webcam (AGENT_CONTRACT.md).
# The project team runs it when installing the agent and writes the printed credential into the
# agent's fixed configuration. Only its SHA-256 is stored.
#
# Usage: scripts/create-installation.sh <hogar-id>
# Connection: the usual libpq variables (PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD).
set -euo pipefail
HOGAR_ID="${1:?Usage: $0 <hogar-id>}"
CREDENCIAL="$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n')"
psql -v ON_ERROR_STOP=1 -q \
  -v hogar="$HOGAR_ID" -v credencial="$CREDENCIAL" <<'SQL'
insert into instalaciones (id, hogar_id, credencial_hash, creado_en, actualizado_en)
values (uuidv7(), :'hogar', encode(sha256(convert_to(:'credencial', 'UTF8')), 'hex'), now(), now());
SQL
echo "Installation credential (store it in the agent's configuration; it is not shown again):"
echo "$CREDENCIAL"
