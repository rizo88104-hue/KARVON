# Deploying E-Market (karvon-api + karvon-web)

```
                 Internet ── 80/443 ──┐
                                      ▼
                              nginx (TLS, gzip)  ◄── certbot (renews)
             ┌────────────────┬───────┴──────────┬────────────────────┐
   SITE_DOMAIN (+/admin)  SITE_DOMAIN/api/v1/  API_DOMAIN          FILES_DOMAIN
             ▼                ▼                  ▼                    ▼
           web:3000 ──────► api:8080 ◄───────────┘                minio:9000
                              │   │                                   ▲
                       postgres  redis                        backup (daily pg_dump
                                                               → MinIO + BACKUP_DIR)
```

Everything runs from `docker-compose.prod.yml` on one server ([server-setup.md](server-setup.md)). Only nginx
publishes ports. The API and the site images are built by GitHub Actions and pulled from GHCR.

Both projects live in **one** GitHub repository, [rizo88104-hue/KARVON](https://github.com/rizo88104-hue/KARVON):

| Branch | Project (local folder) | Workflows | Deploys |
|---|---|---|---|
| `main` | API (KARVON-PAY) | `ci.yml`, `deploy.yml` ("Deploy API") | on push to `main` |
| `web` | site (karvon-web) | `ci-web.yml`, `deploy-web.yml` ("Deploy site") | on push to `web` |

GitHub tells workflows apart by file path, so the site's workflow files have their own names. Both deploys use the
concurrency group `deploy-production`, so they never run on the server at the same time. In karvon-web the local
branch `main` tracks `origin/web` (`push.default=upstream`): a plain `git push` there goes to `web`, never to
the API's `main`.

| Service | Image | Data |
|---|---|---|
| api | `ghcr.io/<owner>/karvon-api:<sha>` | Flyway migrates the database on startup |
| web | `ghcr.io/<owner>/karvon-web:<sha>` | — |
| postgres | `postgres:16-alpine` | volume `pgdata` |
| redis | `redis:7-alpine` (password, AOF) | volume `redisdata` |
| minio | Chainguard MinIO, pinned digest | volume `miniodata` (photos, backups) |
| nginx | built from `deploy/nginx` | certificates in volume `letsencrypt` |
| certbot | `certbot/certbot` | renews every 12 h |
| backup | built from `deploy/backup` | `BACKUP_DIR` on the host + bucket `backups` |

> MinIO stopped publishing free Docker Hub images in 2025, so the compose file uses Chainguard's free build,
> pinned by digest. To update it, pull `cgr.dev/chainguard/minio:latest` and put the new digest in the file.

## First deploy, step by step

### 1. DNS

A records to the server IP: `SITE_DOMAIN`, `www.SITE_DOMAIN`, `API_DOMAIN`, `FILES_DOMAIN`
(e.g. `karvon.uz`, `www.karvon.uz`, `api.karvon.uz`, `files.karvon.uz`). Wait until `dig +short api.karvon.uz`
shows the IP.

### 2. Server

Do everything in [server-setup.md](server-setup.md).

### 3. Files and secrets on the server

From your laptop (in the KARVON-PAY folder):

```bash
tar -czf - docker-compose.prod.yml deploy | ssh deploy@SERVER "tar -xzf - -C /opt/emarket"
scp .env.prod.example deploy@SERVER:/opt/emarket/.env.prod
```

On the server:

```bash
cd /opt/emarket
chmod 600 .env.prod
nano .env.prod          # fill in EVERY value; secrets: openssl rand -hex 32
```

Start with the **test** keys of Payme and Click (see the last section). `PAYME_ALLOWED_IPS` and
`CLICK_ALLOWED_IPS` must be the providers' callback IPs (ask their support; the API refuses other IPs).

### 4. GitHub

In the repository (one for both projects), Settings → Secrets and variables → Actions:

| Kind | Name | Value |
|---|---|---|
| secret | `DEPLOY_HOST` | server IP or host name |
| secret | `DEPLOY_USER` | `deploy` |
| secret | `DEPLOY_SSH_KEY` | private key `emarket_deploy` (server-setup.md, step 1) |
| secret | `DEPLOY_KNOWN_HOSTS` | output of `ssh-keyscan -t ed25519 SERVER` |
| variable | `DEPLOY_PATH` | `/opt/emarket` |
| variable | `API_DOMAIN` | e.g. `api.karvon.uz` (Deploy API) |
| variable | `SITE_DOMAIN` | e.g. `karvon.uz` (Deploy site; without it its health check fails) |
| variable | `NEXT_PUBLIC_SENTRY_DSN` | browser DSN of the web Sentry project (Deploy site, optional) |

Create the environment **production** (Settings → Environments); both deploys use it. Add yourself as a required
reviewer if every deploy should wait for approval.

Run the workflows once so both images exist in GHCR: push to `main` (API) and to `web` (site), or Actions →
"Deploy API" → Run workflow. "Deploy site" is not in the Run workflow list (GitHub lists manual workflows only from
the default branch `main`), so the site is deployed by a push to `web`. On this first run the deploy steps may fail because the stack is not up yet — that is expected;
the images are pushed before the deploy job.

### Shell helpers (use them in every session on the server)

`.env.prod` is for Docker Compose, not for `source` (values like `BACKUP_CRON=0 3 * * *` are not shell code).

```bash
cd /opt/emarket
compose() { docker compose --env-file .env.prod -f docker-compose.prod.yml "$@"; }
for v in SITE_DOMAIN API_DOMAIN FILES_DOMAIN LETSENCRYPT_EMAIL DB_USERNAME BACKUP_DIR TELEGRAM_BOT_TOKEN TELEGRAM_WEBHOOK_SECRET; do
  export "$v=$(grep -E "^$v=" .env.prod | tail -n 1 | cut -d= -f2-)"
done
```

### 5. First start (manual, once)

The GitHub token of a workflow is only valid during the job, so for this first manual pull log in with a personal
access token that has only `read:packages` (GitHub → Settings → Developer settings → Tokens (classic)):

```bash
echo "<PAT>" | docker login ghcr.io -u <github-user> --password-stdin
compose pull api web
compose build nginx backup
compose up -d
docker logout ghcr.io
compose ps                      # all "healthy" / "Up"; minio-init "Exited (0)"
compose logs api | grep -i flyway | tail -3
```

nginx starts with a **self-signed** certificate (browsers warn) until step 6.

### 6. TLS certificate (Let's Encrypt)

One certificate for all four names, stored under the name of `SITE_DOMAIN`:

```bash
compose run --rm --entrypoint certbot certbot certonly --webroot -w /var/www/certbot \
  --cert-name "$SITE_DOMAIN" -d "$SITE_DOMAIN" -d "www.$SITE_DOMAIN" -d "$API_DOMAIN" -d "$FILES_DOMAIN" \
  --email "$LETSENCRYPT_EMAIL" --agree-tos --no-eff-email
compose exec nginx sh -c 'select-cert.sh && nginx -s reload'
curl -sI "https://$SITE_DOMAIN" | head -1          # HTTP/2 200, no certificate warning
```

The `certbot` service renews it automatically; nginx reloads the new certificate within 6 hours.

### 7. Connect the outside services

* **Telegram webhook.** The API does not register it itself. It is **required**: SMS is off, so login codes come only
  from the bot, and without the webhook nobody can log in.
  ```bash
  curl -fsS "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/setWebhook" \
    --data-urlencode "url=https://$API_DOMAIN/api/v1/telegram/webhook" \
    --data-urlencode "secret_token=$TELEGRAM_WEBHOOK_SECRET" \
    --data-urlencode 'allowed_updates=["message"]'
  curl -fsS "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/getWebhookInfo"   # url set, no last_error_message
  ```
  Optional: show the commands in the bot menu:
  `curl -fsS "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/setMyCommands" --data-urlencode 'commands=[{"command":"buyurtmalarim","description":"Buyurtmalarim"}]'`
* **Payme** cabinet → endpoint URL: `https://API_DOMAIN/api/v1/payments/payme`
* **Click** cabinet → Prepare URL `https://API_DOMAIN/api/v1/payments/click/prepare`,
  Complete URL `https://API_DOMAIN/api/v1/payments/click/complete`

### 8. Check

* `https://SITE_DOMAIN` opens. Log in with `ADMIN_PHONE`:
  1. Press "Telegram orqali kod olish".
  2. Press Start in the bot and share your number.
  3. Type the code the bot sends. Then open `/admin`.
* Admin → Content → Pages: create `faq`, `delivery`, `returns`, `public-offer`.
* `curl https://API_DOMAIN/actuator/health` → `{"status":"UP",...}`.
* Next morning: `ls -l $BACKUP_DIR` shows `karvon-YYYYMMDD-030000.dump`; do the restore check below once.

From now on, every push to `main` deploys the API and every push to `web` deploys the site.

## Monitoring

* **Uptime** — [UptimeRobot](https://uptimerobot.com) free plan (50 monitors, 5-minute checks, e-mail and
  Telegram alerts). Create two monitors:
  1. *Keyword* monitor `https://API_DOMAIN/actuator/health`, keyword `"UP"` (alert when missing);
  2. *HTTP(s)* monitor `https://SITE_DOMAIN/` (alert on non-2xx).
  Alternative with the same free features: Better Stack Uptime.
* **Errors** — [Sentry](https://sentry.io) free plan: create two projects, *Spring Boot* (`SENTRY_DSN_API`) and
  *Next.js* (`SENTRY_DSN_WEB` for the server; the GitHub variable `NEXT_PUBLIC_SENTRY_DSN` for the browser).
  Personal data is not sent (no request bodies, cookies or user data).
* **Disk** — `deploy/scripts/disk-alert.sh` in cron ([server-setup.md](server-setup.md), step 9).
* **Metrics** — `/actuator/prometheus` (Bearer `METRICS_TOKEN`) is only reachable inside the Docker network:
  `docker compose ... exec api wget -qO- --header "Authorization: Bearer $METRICS_TOKEN" localhost:8080/actuator/prometheus`.
* **Logs** — `compose logs -f api` (JSON, with `requestId`), `compose logs -f web nginx`. Docker keeps 5 × 20 MB
  per container.

## Backups and restore

`backup` runs `pg_dump` every day at `BACKUP_CRON` (03:00 Tashkent) and keeps `BACKUP_RETENTION_DAYS` (30) days in:
`BACKUP_DIR` on the host, the MinIO bucket `backups`, and optionally `BACKUP_OFFSITE_REMOTE` (any S3 storage
via rclone — recommended, because the first two are on the same server).

```bash
compose exec backup entrypoint.sh backup                       # a backup right now
ls -l "$BACKUP_DIR"                                             # karvon-YYYYMMDD-HHMMSS.dump
```

**Check a backup without touching production** (do this monthly):

```bash
compose exec backup entrypoint.sh restore latest --target-db karvon_restore_check
compose exec postgres psql -U "$DB_USERNAME" -d postgres -c 'DROP DATABASE karvon_restore_check'
```

**Restore production** (data after the backup is lost):

```bash
compose stop web api
compose exec backup entrypoint.sh restore latest                # or a file name: karvon-20261102-030000.dump
# from MinIO instead of BACKUP_DIR:  ... restore latest --from-minio
compose start api web
curl -fsS "https://$API_DOMAIN/actuator/health"
```

Both commands were tested on the local stack: restore into a check database and into the live database, from the
host directory and from MinIO; the API started healthy on the restored data.

## Roll back to the previous image

Every deploy records the image tags it replaced in `/opt/emarket/deploy-history.log`:

```
2026-11-02T10:15:00Z api 3063421a... -> f94f53a1...
```

```bash
cd /opt/emarket
tail -5 deploy-history.log
sed -i 's/^API_IMAGE_TAG=.*/API_IMAGE_TAG=<previous sha>/' .env.prod     # WEB_IMAGE_TAG for the site
compose pull api && compose up -d api                                   # needs docker login ghcr.io (PAT) if the image is not on the server
curl -fsS "https://$API_DOMAIN/actuator/health"
```

Or in GitHub: Actions → "Deploy API" → Run workflow on the older commit.

**Database:** Flyway migrations only go forward. Rolling the API back over a migration that removed or renamed
something needs the database backup from before the deploy (restore section). Our migrations so far only add.

## Rotate secrets

Edit `.env.prod`, then `compose up -d` — Compose recreates only the containers whose settings changed.

| Secret | Steps | Effect |
|---|---|---|
| `DB_PASSWORD` | `compose exec postgres psql -U $DB_USERNAME -d $DB_NAME -c "ALTER USER $DB_USERNAME PASSWORD 'new'"`, then `.env.prod`, `compose up -d` | none |
| `REDIS_PASSWORD` | `.env.prod`, `compose up -d` | cached catalog/settings are rebuilt; OTP codes in flight expire |
| `MINIO_ROOT_PASSWORD` | `.env.prod`, `compose up -d` (minio, api, backup restart) | photo links issued before stop working (they expire in 1 h anyway) |
| `JWT_SECRET` | `.env.prod`, `compose up -d api` | **everybody is logged out** |
| `METRICS_TOKEN` | `.env.prod`, `compose up -d api`; update the scraper | — |
| `TELEGRAM_WEBHOOK_SECRET` / `TELEGRAM_BOT_TOKEN` | `.env.prod`, `compose up -d api`, run `setWebhook` again (step 7) | — |
| `PAYME_KEY` | new key in the Payme cabinet, `.env.prod`, `compose up -d api` immediately | callbacks with the old key fail until the API restarts |
| `CLICK_SECRET_KEY` | the same in the Click cabinet | as Payme |
| `ESKIZ_PASSWORD`, `TAOBAO_API_KEY`, `TRANSLATION_API_KEY` | change at the provider, `.env.prod`, `compose up -d api` | — |
| `SENTRY_DSN_*` | new DSN in Sentry, `.env.prod`, `compose up -d api web`; browser DSN: GitHub variable + redeploy web | — |
| Deploy SSH key | new key pair, add `.pub` to `authorized_keys`, update the `DEPLOY_SSH_KEY` secret, remove the old line | — |

`.env.prod` stays `chmod 600`, is never committed and is not copied anywhere except the encrypted password manager.

## Payme and Click: from test to production

1. **Payme**: in the merchant cabinet switch the cash desk from the test key to the production key.
   In `.env.prod` set `PAYME_KEY` (production key), `PAYME_MERCHANT_ID` (production cash desk id) and
   `PAYME_CHECKOUT_URL=https://checkout.paycom.uz` (test: `https://test.paycom.uz`).
   Make sure the endpoint URL in the cabinet is `https://API_DOMAIN/api/v1/payments/payme`.
2. **Click**: from Click you get the production `CLICK_SERVICE_ID`, `CLICK_MERCHANT_ID` and `CLICK_SECRET_KEY`;
   set the Prepare/Complete URLs (step 7) for the production service.
3. Check `PAYME_ALLOWED_IPS` / `CLICK_ALLOWED_IPS` against the providers' current production IP lists.
4. `compose up -d api`.
5. Pay a real small order (e.g. 1 000 so'm product) with each provider, see it become PAID in `/admin`, cancel it
   as ADMIN (the money goes to the customer's wallet) and refund it to the card in the provider cabinet.

## Local test of the production stack

On a developer machine with Docker (KARVON-PAY and karvon-web side by side: branches `main` and `web`),
with fake values:

```bash
docker compose --env-file deploy/local-test.env -f docker-compose.prod.yml -f docker-compose.build.yml up -d --build
R="--resolve karvon.localhost:8443:127.0.0.1 --resolve api.karvon.localhost:8443:127.0.0.1"
curl -sk $R https://karvon.localhost:8443/ -o /dev/null -w "%{http_code}\n"            # 200 (self-signed TLS)
curl -sk $R https://api.karvon.localhost:8443/actuator/health                           # {"status":"UP"...}
docker compose --env-file deploy/local-test.env -f docker-compose.prod.yml down -v       # remove everything
```

Taobao, SMS and payments do not work there (fake keys); the catalog shows "could not load".
