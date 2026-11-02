# nginx with openssl (self-signed fallback certificate until certbot has issued the real one).
FROM nginx:1.29-alpine
RUN apk add --no-cache openssl
COPY select-cert.sh /usr/local/bin/select-cert.sh
COPY 15-select-cert.sh 40-reload-loop.sh /docker-entrypoint.d/
COPY templates/ /etc/nginx/templates/
RUN chmod +x /usr/local/bin/select-cert.sh /docker-entrypoint.d/15-select-cert.sh /docker-entrypoint.d/40-reload-loop.sh \
    && rm -f /etc/nginx/conf.d/default.conf
