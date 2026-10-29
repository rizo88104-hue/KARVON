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

## Tests

```bash
./mvnw verify
```

Integration tests start their own PostgreSQL and Redis with Testcontainers, so Docker must be running
(`docker compose` is not needed for tests).

## Production

Run with `SPRING_PROFILES_ACTIVE=prod` and set every variable from `.env.example` as a real environment variable.
`application-prod.yml` has no defaults, so the app does not start if a value is missing.
