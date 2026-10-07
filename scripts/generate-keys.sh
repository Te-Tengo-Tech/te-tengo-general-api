#!/usr/bin/env bash
# Generates the RS256 key pair that signs JWTs in local development (.claves/ is not versioned).
set -euo pipefail
mkdir -p .claves
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .claves/privada.pem
openssl rsa -in .claves/privada.pem -pubout -out .claves/publica.pem
echo "Keys created in .claves/ (never committed)."
