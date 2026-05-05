#!/bin/bash
# ===================================================
# Script para gerar/regenerar as chaves RSA JWT
# Uso: ./generate-keys.sh [--force]
# ===================================================

KEYS_DIR="keys"

if [ "$1" == "--force" ]; then
    echo "[INFO] Regenerando chaves (--force)..."
    rm -f "$KEYS_DIR/privateKey.pem" "$KEYS_DIR/publicKey.pem"
fi

if [ -f "$KEYS_DIR/privateKey.pem" ] && [ -f "$KEYS_DIR/publicKey.pem" ]; then
    echo "[INFO] Chaves já existem em $KEYS_DIR/. Use --force para regenerar."
    exit 0
fi

mkdir -p "$KEYS_DIR"

echo "[INFO] Gerando chave RSA 2048-bit..."
openssl genrsa -out "$KEYS_DIR/privateKey_raw.pem" 2048 2>/dev/null

echo "[INFO] Convertendo para formato PKCS#8..."
openssl pkcs8 -topk8 -inform PEM \
    -in "$KEYS_DIR/privateKey_raw.pem" \
    -out "$KEYS_DIR/privateKey.pem" \
    -nocrypt

echo "[INFO] Extraindo chave pública..."
openssl rsa -in "$KEYS_DIR/privateKey_raw.pem" -pubout \
    -out "$KEYS_DIR/publicKey.pem" 2>/dev/null

rm "$KEYS_DIR/privateKey_raw.pem"

echo "[OK] Chaves geradas:"
echo "     - $KEYS_DIR/privateKey.pem (chave privada PKCS#8 - NUNCA compartilhe!)"
echo "     - $KEYS_DIR/publicKey.pem  (chave pública X.509)"
