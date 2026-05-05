#!/bin/sh
set -e

KEYS_DIR="/app/keys"

# Gera chaves RSA para JWT se não existirem
if [ ! -f "$KEYS_DIR/privateKey.pem" ] || [ ! -f "$KEYS_DIR/publicKey.pem" ]; then
    echo "[INFO] Gerando par de chaves RSA para JWT..."
    mkdir -p "$KEYS_DIR"
    openssl genrsa -out "$KEYS_DIR/privateKey_raw.pem" 2048 2>/dev/null
    openssl pkcs8 -topk8 -inform PEM -in "$KEYS_DIR/privateKey_raw.pem" \
        -out "$KEYS_DIR/privateKey.pem" -nocrypt
    openssl rsa -in "$KEYS_DIR/privateKey_raw.pem" -pubout \
        -out "$KEYS_DIR/publicKey.pem" 2>/dev/null
    rm "$KEYS_DIR/privateKey_raw.pem"
    echo "[INFO] Chaves RSA geradas em $KEYS_DIR"
fi

export JWT_PRIVATE_KEY_LOCATION="$KEYS_DIR/privateKey.pem"
export JWT_PUBLIC_KEY_LOCATION="$KEYS_DIR/publicKey.pem"

exec "$@"
