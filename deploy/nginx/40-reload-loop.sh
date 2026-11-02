#!/bin/sh
# Picks up certificates renewed (or first issued) by the certbot service: every 6 hours re-select and reload.
(
    while :; do
        sleep 21600
        /usr/local/bin/select-cert.sh >/dev/null && nginx -s reload
    done
) &
