# SpendWise — Personal Finance Tracker

A full-stack personal finance management platform with an Android app, microservices backend, admin dashboard, and support dashboard.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────┐
│                  CLIENTS                         │
│  Android App  │  Admin Dashboard  │  Support     │
│  (Kotlin +    │  (React + Vite    │  Dashboard   │
│  Compose)     │  port 5173)       │  port 5174)  │
└──────────┬────────────┬───────────┬──────────────┘
           │            │           │
           └────────────┴───────────┘
                        │
               ┌────────▼────────┐
               │   API Gateway   │
               │   (port 3000)   │
               └────────┬────────┘
        ┌───────┬────────┼────────┬────────┐
        ▼       ▼        ▼        ▼        ▼
    Auth    Transaction  User  Analytics  (Admin
   Service   Service  Service  Service   routes in
   :3001      :3002    :3003    :3004    Gateway)
        └───────┴────────┴────────┴────────┘
                         │
               ┌─────────▼─────────┐
               │    PostgreSQL DB   │
               │  (Docker / Supabase│
               └───────────────────┘
```

---

## Prerequisites

- **Node.js** 20+ ([download](https://nodejs.org))
- **Docker Desktop** ([download](https://www.docker.com/products/docker-desktop)) — for local PostgreSQL + backend
- **Android Studio Hedgehog+** ([download](https://developer.android.com/studio)) — for Android development
- **Git** ([download](https://git-scm.com))

---

## Quick Start (Local Development)

### 1. Clone the Repository

```bash
git clone https://github.com/Pravveen/spendwise.git
cd spendwise
```

### 2. Start Backend (Docker Compose)

```bash
cd backend
docker compose up --build -d
```

This starts:
- **PostgreSQL** on port 5432 (auto-seeds schema + default admin user)
- **Auth Service** on port 3001
- **Transaction Service** on port 3002
- **User Service** on port 3003
- **Analytics Service** on port 3004
- **API Gateway** on port 3000

Verify all services are running:
```bash
docker compose ps
# All services should show "healthy" or "running"
```

Test the API:
```bash
curl http://localhost:3000/health
# Expected: {"status":"ok"}
```

### 3. Start Admin Dashboard

```bash
cd admin-dashboard
npm install
npm run dev
# Opens at http://localhost:5173
```

Login credentials:
- **Email:** `admin@spendwise.app`
- **Password:** `Admin@SpendWise2025`

### 4. Start Support Dashboard

```bash
cd support-dashboard
npm install
npm run dev
# Opens at http://localhost:5174
```

Use the same admin credentials, or create a support agent via Admin Dashboard → Settings.

### 5. Run Android App (Emulator)

1. Open **Android Studio** → Open → select the `android/` folder
2. Wait for Gradle sync to complete (~2-5 min first time)
3. Create an AVD: **Tools → Device Manager → Create Device**
   - Phone: Pixel 6 (or any)
   - System Image: API 33 (Android 13) — x86_64
4. Start the emulator
5. In Android Studio, click **▶ Run** (Shift+F10)

> **Note:** The emulator accesses your host machine at `10.0.2.2`. The app's `BASE_URL` is pre-configured to `http://10.0.2.2:3000` for debug builds.

---

## Default Credentials

| Account | Email | Password |
|---------|-------|----------|
| Admin | `admin@spendwise.app` | `Admin@SpendWise2025` |

> ⚠️ Change the admin password immediately in a production environment.

---

## Running Without Docker (Local Node.js)

If you prefer to run services individually:

```bash
cd backend

# 1. Start PostgreSQL (still requires Docker for the DB)
docker compose up postgres -d

# 2. Copy and fill environment variables
cp .env.local .env
# Edit .env with your PostgreSQL connection string

# 3. Apply database schema
psql $DATABASE_URL < ../database/schema.sql

# 4. Start each service (in separate terminals)
cd auth-service && npm install && npm run dev       # :3001
cd transaction-service && npm install && npm run dev # :3002
cd user-service && npm install && npm run dev        # :3003
cd analytics-service && npm install && npm run dev   # :3004
cd api-gateway && npm install && npm run dev         # :3000
```

---

## Production Setup (Supabase Free Tier)

### 1. Create Supabase Project

1. Sign up at [supabase.com](https://supabase.com) (free, no credit card)
2. Create a new project → choose region → set a database password
3. Go to **Project Settings → Database → Connection String → URI**
4. Copy the connection string (format: `postgresql://postgres:[password]@[host]:5432/postgres`)

### 2. Apply Schema

In Supabase Dashboard → **SQL Editor** → paste the contents of `database/schema.sql` → Run.

### 3. Update Environment Variables

In each service's `.env` or Docker environment, set:
```env
DATABASE_URL=postgresql://postgres:[password]@[host]:5432/postgres
NODE_ENV=production
```

### 4. Deploy Backend

Options:
- **Railway.app** — connect GitHub repo, auto-deploys each service folder
- **Render.com** — free tier available for Node.js services
- **Fly.io** — good free tier with 256MB RAM per app

Set environment variables in the platform dashboard.

---

## Project Structure

```
spendwise/
├── android/                    # Android app (Kotlin + Jetpack Compose)
│   └── app/src/main/java/com/spendwise/app/
│       ├── core/               # Constants, Extensions, Result
│       ├── data/               # Room DB, Retrofit APIs, Repositories
│       ├── di/                 # Hilt dependency injection modules
│       ├── domain/             # Use cases, domain models
│       ├── presentation/       # Compose screens & ViewModels
│       └── sms/                # SMS parsing & background receiver
│
├── backend/
│   ├── shared/                 # Shared types, DB pool, JWT, auth middleware
│   ├── auth-service/           # Auth: register, login, refresh, logout
│   ├── transaction-service/    # Transactions CRUD, batch import, summary
│   ├── user-service/           # Profile, bank accounts, cards, loans, budgets
│   ├── analytics-service/      # Dashboard, monthly, trend, categories
│   ├── api-gateway/            # Proxy + admin/support routes
│   └── docker-compose.yml      # Local orchestration
│
├── admin-dashboard/            # React admin UI (port 5173)
│   └── src/pages/
│       ├── Dashboard.tsx       # Stats + charts
│       ├── Users.tsx           # User management table
│       ├── UserDetail.tsx      # Per-user detail + tabs
│       ├── Transactions.tsx    # All transactions + CSV export
│       ├── Analytics.tsx       # Platform analytics charts
│       ├── Tickets.tsx         # Support ticket management
│       ├── AuditLog.tsx        # Security audit trail
│       └── Settings.tsx        # Admin settings + create agents
│
├── support-dashboard/          # React support UI (port 5174)
│   └── src/pages/
│       ├── TicketQueue.tsx     # Ticket list sorted by priority
│       ├── TicketDetail.tsx    # Ticket thread + reply + status
│       ├── UserLookup.tsx      # Customer search (read-only)
│       └── Profile.tsx         # Support agent profile
│
└── database/
    └── schema.sql              # Complete PostgreSQL schema (15 tables)
```

---

## Android App Features

- **Biometric Authentication** — fingerprint/face/PIN before app loads
- **SMS Auto-Parsing** — reads bank SMS from inbox, detects transactions
- **Real-time SMS Monitoring** — BroadcastReceiver catches new bank SMS
- **7-Screen Navigation** — Setup → Auth → Home → Transactions → Cards → Loans → Money → Settings
- **Dashboard** — balance, savings rate, EMI burden, budget alerts
- **Offline-First** — Room DB caches data locally
- **Dark Theme** — SpendWise brand colors (#6C63FF primary)

### SMS Parsing Patterns

The app automatically detects and categorizes these transaction types:

| Pattern | Example |
|---------|---------|
| Parentheses merchant | `debited to (Swiggy)` |
| "at" merchant | `paid at Amazon` |
| "towards" merchant | `paid towards Netflix` |
| UPI merchant | `UPI/CR/SWIGGY INDIA` |
| Transfer description | `IMPS transfer to HDFC` |
| Salary detection | `salary credited` |
| Generic "for" | `payment for Uber` |

---

## API Reference

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/register` | Create account |
| POST | `/auth/login` | Get access + refresh tokens |
| POST | `/auth/refresh` | Rotate token pair |
| POST | `/auth/logout` | Revoke session |
| GET | `/auth/me` | Current user profile |
| DELETE | `/auth/account` | Delete account (requires password) |

### Transactions

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/transactions` | Paginated list with filters |
| POST | `/transactions` | Create single transaction |
| PUT | `/transactions/:id` | Update transaction |
| DELETE | `/transactions/:id` | Delete transaction |
| POST | `/transactions/batch` | Bulk import (SMS dedup) |
| GET | `/transactions/summary` | Monthly totals by category |

### User Data

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET/PUT | `/users/profile` | Profile management |
| GET/POST | `/users/bank-accounts` | Bank accounts |
| GET/POST | `/users/credit-cards` | Credit cards |
| GET/POST | `/users/loans` | Loans |
| GET/PUT | `/users/salary` | Salary configuration |
| GET/POST | `/users/investments` | Investments |
| GET/POST | `/users/bills` | Mandatory bills |
| PUT | `/users/bills/:id/pay` | Mark bill as paid |
| GET/PUT | `/users/budgets` | Monthly budgets |

### Analytics

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/analytics/dashboard` | Full dashboard summary |
| GET | `/analytics/monthly` | Monthly report |
| GET | `/analytics/trend` | 6-month spend trend |
| GET | `/analytics/categories` | Category breakdown |

---

## Database Schema

15 tables:

| Table | Purpose |
|-------|---------|
| `users` | User accounts with roles (user/support/admin) |
| `user_sessions` | JWT refresh token sessions |
| `bank_accounts` | Bank account details + balance |
| `credit_cards` | Credit cards + outstanding + limit |
| `loans` | Loan tracking (EMI, interest rate) |
| `categories` | Transaction categories (14 seeded) |
| `transactions` | All financial transactions (dedup by sms_id) |
| `salary_config` | Salary setup (amount, day, type) |
| `salary_history` | Monthly salary credit records |
| `mandatory_bills` | Recurring bills + due dates |
| `monthly_budgets` | Per-category budget by month/year |
| `investments` | MF/stock/FD investment tracking |
| `support_tickets` | Customer support tickets |
| `ticket_messages` | Ticket conversation messages |
| `audit_log` | Security audit trail |

---

## Environment Variables

### Backend Services

```env
DATABASE_URL=postgresql://user:password@host:5432/dbname
JWT_ACCESS_SECRET=your-super-secret-access-key-min-32-chars
JWT_REFRESH_SECRET=your-super-secret-refresh-key-min-32-chars
PORT=3001              # (per service)
NODE_ENV=development
CORS_ORIGINS=http://localhost:5173,http://localhost:5174
```

### API Gateway Additional

```env
AUTH_SERVICE_URL=http://auth-service:3001
TRANSACTION_SERVICE_URL=http://transaction-service:3002
USER_SERVICE_URL=http://user-service:3003
ANALYTICS_SERVICE_URL=http://analytics-service:3004
```

---

## Development Scripts

```bash
# Backend
docker compose up -d            # Start all services
docker compose logs -f          # Stream all logs
docker compose logs auth-service # Logs for specific service
docker compose down             # Stop all services
docker compose down -v          # Stop + delete database volume

# Admin Dashboard
npm run dev                     # Development server (port 5173)
npm run build                   # Production build
npm run preview                 # Preview production build

# Support Dashboard
npm run dev                     # Development server (port 5174)
npm run build                   # Production build

# Android
./gradlew assembleDebug         # Build debug APK
./gradlew installDebug          # Build + install on connected device/emulator
./gradlew test                  # Run unit tests
```

---

## Troubleshooting

### Android emulator can't connect to backend
- Ensure Docker services are running: `docker compose ps`
- Use `10.0.2.2` NOT `localhost` in the emulator
- Check `android/app/src/main/res/xml/network_security_config.xml` allows cleartext for `10.0.2.2`
- Try: `adb reverse tcp:3000 tcp:3000` (alternative to using 10.0.2.2)

### Database connection errors
- Check PostgreSQL is running: `docker compose ps postgres`
- Verify DATABASE_URL format: `postgresql://postgres:password@localhost:5432/spendwise`
- Try connecting directly: `psql $DATABASE_URL`

### "Access denied" on admin dashboard
- Only users with `role = 'admin'` or `role = 'support'` can log in
- Default admin is seeded in `database/schema.sql`
- Check the seed ran: `SELECT * FROM users WHERE role = 'admin';`

### SMS not being read on Android emulator
- The emulator has no real SMS. Use the **Setup Screen** SMS scan date to import historical SMS
- For testing, use the API directly: `POST /transactions/batch`
- On a real device, grant READ_SMS and RECEIVE_SMS permissions in app settings

---

## Security Notes

- JWT access tokens expire in **15 minutes**; refresh tokens in **30 days**
- Passwords hashed with **bcrypt** (cost factor 12)
- All admin/support routes require valid JWT + role check
- `FLAG_SECURE` prevents screenshots in the Android app
- Biometric auth required before app loads
- Audit log captures all sensitive operations
- Support agents have **read-only** access to user transaction data

---

## License

MIT — Personal use. See LICENSE file.
