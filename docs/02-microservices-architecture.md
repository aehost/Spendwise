# Microservices Architecture

## Service Registry

| Service | Port | Responsibility |
|---|---|---|
| **API Gateway** | 3000 | Auth proxy, admin/support routes, rate limiting, CORS |
| **Auth Service** | 3001 | Register, login, refresh token, password change |
| **Transaction Service** | 3002 | CRUD transactions, batch SMS import, monthly summary |
| **User Service** | 3003 | Profile, bank accounts, credit cards, loans |
| **Analytics Service** | 3004 | Spending trends, category breakdown, insights |

## API Gateway Design

The gateway acts as a single entry point for all clients:

```
Client Request
    │
    ▼
Rate Limiter (500 req/min)
    │
    ▼
Helmet (Security Headers)
    │
    ▼
CORS Check
    │
    ├── /auth/*  ──────────────────── JWT not required ──► Auth Service :3001
    ├── /transactions/* ────────────── JWT required ──────► Transaction Service :3002
    ├── /users/* ───────────────────── JWT required ──────► User Service :3003
    ├── /analytics/* ───────────────── JWT required ──────► Analytics Service :3004
    ├── /admin/* ───────────────────── JWT + role=admin ──► Inline handler (DB direct)
    └── /support/* ─────────────────── JWT + role=support ► Inline handler (DB direct)
```

**Note on admin/support routes**: These are handled inline in the gateway (direct DB queries) rather than being proxied to a dedicated service. This is intentional — admin/support operations are infrequent and latency-sensitive for the dashboard UI.

## Service Communication

All services are stateless and communicate only via:
1. **HTTP REST** (no message queues in current implementation)
2. **Shared PostgreSQL database** — each service has its own connection pool

Services do NOT call each other directly. All cross-service operations go through the gateway or directly to the database.

## Auth Flow

```
1. POST /auth/register  → hash password (bcrypt 12), insert user, return tokens
2. POST /auth/login     → verify password, issue access_token (15m) + refresh_token (30d)
3. GET  /auth/me        → verify JWT, return user profile
4. POST /auth/refresh   → verify refresh token in DB, issue new access_token
5. POST /auth/logout    → revoke refresh token in DB
```

Access token payload:
```json
{ "userId": "uuid", "email": "user@email.com", "role": "user|admin|support" }
```

## Database Connection Strategy

Each service maintains its own `pg.Pool`:
- Pool size: 10 connections (default)
- SSL: disabled locally, required in production
- All queries use parameterized statements (SQL injection prevention)

## Scalability Design

### Horizontal Scaling
- All services are **stateless** — can run multiple instances behind a load balancer
- JWT verification is in-process (no shared session store needed)
- Rate limiting uses in-memory counters (move to Redis for multi-instance)

### Vertical Scaling
- PostgreSQL connection pooling via `pg.Pool` (10 connections per service × 5 services = 50 total)
- For production: use PgBouncer to pool connections at DB level

### Estimated Capacity
| Load | Architecture |
|---|---|
| < 10K users | Single instance per service, single Postgres |
| 10K–100K users | Load-balanced gateway, 2-3 instances per service, managed Postgres |
| 100K–1M users | Kubernetes, read replicas, Redis cache, CDN for static assets |
| > 1M users | CQRS, event sourcing, Kafka, sharded PostgreSQL |

## Service Health Checks

All services expose `GET /health` returning:
```json
{ "service": "transactions", "status": "ok", "ts": "2025-01-01T00:00:00Z" }
```

The gateway's `/health` aggregates all service URLs:
```json
{ "gateway": "ok", "services": { "auth": "...", "transactions": "...", "user": "...", "analytics": "..." } }
```

## Security Model

| Layer | Implementation |
|---|---|
| Transport | HTTPS (TLS 1.2+) in production |
| Auth | JWT RS256 (or HS256 in dev) |
| Password | bcrypt cost=12 |
| Input validation | Express JSON + manual field checks |
| SQL injection | All queries parameterized |
| Rate limiting | express-rate-limit (500/min gateway, 200/min services) |
| CORS | Whitelist specific origins |
| Headers | Helmet.js (CSP, HSTS, XSS protection) |

## Data Flow: SMS Transaction Import

```
Android App reads SMS
    │
    ▼
ParseSmsUseCase.parse(smsBody)
    ├── Extract: amount, merchant, card last-4, UPI VPA, loan account
    └── MerchantMatcher.classify(merchant, smsBody)
           ├── Tier 1: EXACT_MAP lookup  (300+ merchants)
           ├── Tier 2: UPI domain match  (50+ handles)
           ├── Tier 3: Jaro-Winkler fuzzy (threshold 0.82)
           └── Tier 4: Keyword scoring
    │
    ▼
POST /transactions/batch (gateway → transaction-service)
    ├── Deduplication: check sms_id + sms_raw fingerprint
    ├── Auto-classify any unclassified transactions (server-side MerchantMatcher)
    └── INSERT with ON CONFLICT DO NOTHING
    │
    ▼
Transaction stored in PostgreSQL
    │
    ▼
Home screen refreshes → GET /transactions
```
