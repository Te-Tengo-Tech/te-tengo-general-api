#!/usr/bin/env bash
# Genera el par de claves RS256 para firmar los JWT en desarrollo local (.claves/ no se versiona).
set -euo pipefail
mkdir -p .claves
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .claves/privada.pem
openssl rsa -in .claves/privada.pem -pubout -out .claves/publica.pem
echo "Claves creadas en .claves/ (no se suben al repositorio)."
