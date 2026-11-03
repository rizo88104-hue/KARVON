# Karvon API

Backend of Karvon: customers in Uzbekistan buy Taobao products through us.
Project rules and architecture: see [CLAUDE.md](CLAUDE.md).

## Requirements

- JDK 21
- Docker (Docker Desktop on Windows) — for local PostgreSQL/Redis and for the tests

## Run locally

1. Create your local settings (optional, the defaults match `docker-compose.yml`):

   ```bash
   cp .env.example .env
   ```

2. Start PostgreSQL 16 and Redis 7:

   ```bash
   docker compose up -d
   docker compose ps        # both services should be "healthy"
   ```

3. Start the app with the `dev` profile:

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

   On Windows PowerShell use `.\mvnw.cmd` instead of `./mvnw`.
   In IntelliJ IDEA: run `KarvonApplication` with *Active profiles* = `dev`.

4. Check it:

   - http://localhost:8080/api/v1/ping
   - http://localhost:8080/actuator/health
   - http://localhost:8080/swagger-ui

Flyway applies the database migrations from `src/main/resources/db/migration` on startup.

Stop the containers with `docker compose down` (add `-v` to also delete the data).

## Login and notifications: Telegram (SMS is off)

SMS is turned off (`SMS_ENABLED=false`, the default). The Eskiz code stays, but `NoopSmsSender` only logs.
Login codes and order messages go to Telegram; a user Telegram cannot reach gets the messages in-app
(`GET /api/v1/me/notifications`, the bell on the site).

1. `POST /api/v1/auth/otp/request {phone}`
   - **`TELEGRAM`**: the phone has a linked chat, so the code goes there.
   - **`TELEGRAM_LINK`**: there is no linked chat. The answer carries `link` = `https://t.me/<bot>?start=<token>`, valid 10 min.
2. In the bot, **Start** shows the button "Telefon raqamni yuborish". The bot accepts only the sender's own contact with the
   same phone. Then the chat is linked (a new customer is created) and the code is sent.
3. `POST /api/v1/auth/otp/verify` works as before, with the same limits.

Bot command: `/buyurtmalarim` lists the last 5 orders.

### Dev without a bot

Leave `TELEGRAM_BOT_TOKEN` empty. The fake bot only logs. After a `TELEGRAM_LINK` answer, call
`POST /api/v1/dev/otp/{phone}/telegram-login`; then the code is at `GET /api/v1/dev/otp/{phone}`.

### Dev with your real bot

1. Create a separate dev bot with @BotFather. Do not use the production bot: long polling does not work while a webhook
   is set.
2. Put its token and username in `.env`:

   ```properties
   TELEGRAM_BOT_TOKEN=123456:ABC...
   TELEGRAM_BOT_USERNAME=emarket_dev_bot
   ```

3. Restart the app. The log says `Telegram bot: long polling started (dev)`; the token is never logged.

If the bot ever had a webhook, remove it once:
`curl "https://api.telegram.org/bot<TOKEN>/deleteWebhook"`

## Tests

```bash
./mvnw verify
```

Integration tests start their own PostgreSQL and Redis with Testcontainers, so Docker must be running
(`docker compose` is not needed for tests).

## Running the jar in production

Run with `SPRING_PROFILES_ACTIVE=prod` and set every variable from `.env.example` as a real environment variable.
`application-prod.yml` has no defaults, so the app does not start if a value is missing.

## Production

Docker images, `docker-compose.prod.yml` (API, site, PostgreSQL, Redis, MinIO, nginx with Let's Encrypt, daily
backups), GitHub Actions CI/CD and monitoring: see [docs/deploy.md](docs/deploy.md) and
[docs/server-setup.md](docs/server-setup.md). All variables: [.env.prod.example](.env.prod.example).
