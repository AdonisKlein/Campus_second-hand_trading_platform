# Microservice workspace

Each directory is an independent Maven/Spring Boot application. A service must be
buildable from its own directory; no service may depend on another service's Java
classes or repository implementation.

```powershell
cd services/account-service
mvn verify
```

To verify all five projects from the repository root:

```powershell
./scripts/ci/verify-services.ps1
```

Ports reserved during the migration:

| Application | Port |
| --- | ---: |
| API Gateway | 8080 |
| Account Service | 8081 |
| Marketplace Service | 8082 |
| Trading Service | 8083 |
| Governance Service | 8084 |

## Migration status

- Work item 2 has extracted Account Service. It owns only `users` and
  `email_verification`, applies its own Flyway V1, and exposes browser account
  routes plus token-protected internal authentication queries.
- API Gateway owns the browser Session, CSRF endpoint, login/logout, exact CORS,
  client identity-header removal and short-lived internal JWT creation.
- Marketplace Service is extracted and owns products, tags, images, public
  questions and the searchable public-user projection. Gateway routes its frozen
  `/api/items|media|messages|search|admin` paths directly to port 8082.
- Trading Service is extracted and owns purchase intents, the order state machine,
  trade desk, direct chat, unread cursors and blocks. Gateway routes
  `/api/orders/**` and `/api/chat/**` directly to port 8083. Governance is the only
  remaining health-checkable skeleton and its routes still use the monolith fallback.

## Extracted service local configuration

Account Service requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`VERIFICATION_PEPPER`, `INTERNAL_SERVICE_TOKEN` and `INTERNAL_JWT_SECRET`.
`MAIL_ENABLED=false` is suitable for a build-only local run; registration email
delivery requires the existing Mailpit or SMTP variables.

Gateway requires Redis plus the same `INTERNAL_SERVICE_TOKEN` and
`INTERNAL_JWT_SECRET`. Set `ACCOUNT_SERVICE_URI` (default `http://localhost:8081`)
and `MONOLITH_URI` (default `http://localhost:8088`). The two shared secrets must
match Account and the compatibility monolith. For the static development server,
the default exact CORS allowlist contains localhost/127.0.0.1 port 5500.

Marketplace Service requires its own `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, the
same `INTERNAL_SERVICE_TOKEN` and `INTERNAL_JWT_SECRET`, plus
`ACCOUNT_SERVICE_URI`, `TRADING_SERVICE_URI` and `UPLOAD_DIR`. Its internal REST
queries use a 300ms connect timeout and 800ms response timeout; only GET requests
retry once. `UserPublicProfileChanged` and its idempotent projection consumer are
already defined. The trading Saga RabbitMQ/Inbox/Outbox adapter is implemented;
the supported broker and deployment wiring is connected in work item 6.

Trading Service requires its own `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`INTERNAL_SERVICE_TOKEN`, `INTERNAL_JWT_SECRET`, `ACCOUNT_SERVICE_URI` and
`MARKETPLACE_SERVICE_URI`. It owns only trading/chat tables and communicates with
Account and Marketplace through HTTP ports. Set `TRADING_MESSAGING_ENABLED=true`
and configure `SPRING_RABBITMQ_*` only in the supported work item 6 topology;
transactional Inbox/Outbox and command/result consumers are already implemented.

Work items 2 through 4 validate these applications independently; the repository's default
Compose stack still runs the monolith. Do not mix an ad-hoc Account database with
the default monolith database and treat it as a complete environment. Work item 6
will add the supported Redis/Gateway/four-database Compose and Kind topology. In
that topology browsers call Gateway only; they never call internal Account
endpoints or receive the internal JWT.
