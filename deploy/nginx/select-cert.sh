#!/bin/sh
# Points /etc/nginx/ssl/{fullchain,privkey}.pem at the Let's Encrypt certificate of $SITE_DOMAIN when it exists,
# otherwise at a self-signed one (so nginx can start before the first certbot run, and for local tests).
set -eu
live="/etc/letsencrypt/live/${SITE_DOMAIN}"
self="/etc/nginx/ssl-selfsigned"
mkdir -p /etc/nginx/ssl
if [ -s "$live/fullchain.pem" ] && [ -s "$live/privkey.pem" ]; then
    ln -sf "$live/fullchain.pem" /etc/nginx/ssl/fullchain.pem
    ln -sf "$live/privkey.pem" /etc/nginx/ssl/privkey.pem
    echo "select-cert: Let's Encrypt certificate for ${SITE_DOMAIN}"
else
    if [ ! -s "$self/fullchain.pem" ]; then
        mkdir -p "$self"
        openssl req -x509 -nodes -newkey rsa:2048 -days 30 -subj "/CN=${SITE_DOMAIN}" \
            -addext "subjectAltName=DNS:${SITE_DOMAIN},DNS:www.${SITE_DOMAIN},DNS:${API_DOMAIN},DNS:${FILES_DOMAIN}" \
            -keyout "$self/privkey.pem" -out "$self/fullchain.pem" 2>/dev/null
    fi
    ln -sf "$self/fullchain.pem" /etc/nginx/ssl/fullchain.pem
    ln -sf "$self/privkey.pem" /etc/nginx/ssl/privkey.pem
    echo "select-cert: no Let's Encrypt certificate yet, using a self-signed one"
fi
