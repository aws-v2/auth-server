#!/bin/bash

# Script to generate a self-signed SSL certificate for development
# For production, use a certificate from a trusted CA (Let's Encrypt, DigiCert, etc.)

KEYSTORE_FILE="src/main/resources/keystore.p12"
KEYSTORE_PASSWORD="changeit"
KEY_ALIAS="auth-server"
VALIDITY_DAYS=365
DOMAIN="localhost"

echo "Generating self-signed SSL certificate for development..."

# Generate keystore with self-signed certificate
keytool -genkeypair \
    -alias $KEY_ALIAS \
    -keyalg RSA \
    -keysize 2048 \
    -storetype PKCS12 \
    -keystore $KEYSTORE_FILE \
    -storepass $KEYSTORE_PASSWORD \
    -validity $VALIDITY_DAYS \
    -dname "CN=$DOMAIN, OU=Development, O=Serwin, L=City, ST=State, C=US" \
    -ext "SAN=dns:localhost,ip:127.0.0.1"

echo "Keystore generated successfully at: $KEYSTORE_FILE"
echo "Keystore password: $KEYSTORE_PASSWORD"
echo ""
echo "To use this certificate, set the following environment variables:"
echo "SSL_ENABLED=true"
echo "SSL_KEYSTORE_PATH=classpath:keystore.p12"
echo "SSL_KEYSTORE_PASSWORD=$KEYSTORE_PASSWORD"
echo "SSL_KEY_ALIAS=$KEY_ALIAS"
echo ""
echo "NOTE: This is a self-signed certificate for development only."
echo "For production, use a certificate from a trusted Certificate Authority."
