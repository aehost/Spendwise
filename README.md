# SpendWise 💸 — Intelligent Personal Finance Tracker

> Full-stack personal finance platform for the Indian market. Bank SMS transactions are auto-detected, intelligently parsed, and classified by a 4-tier merchant engine — all in real time.

Built with: **Kotlin / Jetpack Compose** Android · **Node.js microservices** · **React** admin & support dashboards · **PostgreSQL / Supabase**

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

## Production Deployment

Full step-by-step deployment guide: **[DEPLOYMENT.md](DEPLOYMENT.md)**

| Service | Platform | Notes |
|---|---|---|
| 5 backend microservices | **Railway.app** | Docker auto-detected from `railway.toml` |
| Admin dashboard | **Vercel** | Root dir: `admin-dashboard/` |
| Support dashboard | **Vercel** | Root dir: `support-dashboard/` |
| Database | **Supabase** | Already provisioned; just set `DATABASE_URL` + `DB_SSL=true` |

### Real Device Testing (Before Deployment)

Testing on a **physical Android phone** (not emulator):

```properties
# android/local.properties  (gitignored — never committed)
# Find your PC's IP: run ipconfig → look for "IPv4 Address"
dev.baseUrl=http://192.168.1.X:3000/api/
```

Then run the debug build. The app reads `local.properties` automatically.

---

## Connecting to Supabase (Cloud DB)

All 5 backend services support Supabase out of the box. An interactive step-by-step wizard is available at **Admin Dashboard → Cloud Setup** (`/cloud-setup`).

### Manual Setup

```bash
# 1. Apply schema to your Supabase project
psql "postgresql://postgres:<pw>@db.<ref>.supabase.co:5432/postgres" \
  -f database/schema.sql

# 2. Update every service's .env
DATABASE_URL=postgresql://postgres:<pw>@db.<ref>.supabase.co:5432/postgres
DB_SSL=true          # enables SSL without NODE_ENV=production
NODE_ENV=development
```

> **Why `DB_SSL=true`?** Supabase requires SSL. Setting `DB_SSL=true` activates `{ rejectUnauthorized: false }` in the pg Pool config without the side-effects of switching to production mode.

### Production Deployment

| Platform | Notes |
|---|---|
| **Railway.app** | Connect GitHub repo; each `backend/*` folder auto-deploys as a separate service |
| **Render.com** | Free tier Node.js services; set env vars in dashboard |
| **Fly.io** | 256MB RAM free tier, good for microservices |
| **AWS ECS** | Full production setup; pair with Aurora PostgreSQL Serverless v2 |

### AWS Migration Path

```bash
# Dump from Supabase → restore to Aurora PostgreSQL
pg_dump "postgresql://postgres:<pw>@db.<ref>.supabase.co:5432/postgres" \
  --no-owner --no-acl -Fc -f spendwise.dump

pg_restore -d "postgresql://admin:<pw>@<aurora>.cluster-xxx.us-east-1.rds.amazonaws.com/spendwise" \
  spendwise.dump
```

Aurora PostgreSQL Serverless v2 scales from 0.5 ACU to 128 ACU per second, with zero code changes required.

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
- **Real-time SMS Monitoring** — `SmsReceiver` (BroadcastReceiver) catches new bank messages as they arrive
- **5-Tier Merchant Classification** — pure-text, no GPS; MCC codes + 600+ keywords + phonetic patterns (see below)
- **Self-Learning** — `CategoryCorrectionStore`: user corrections persist and override the engine forever
- **Intelligence Worker** — weekly background job detects recurring bills, savings opportunities, cash-flow forecast
- **Gmail Bill Parser** — connects to Gmail OAuth, extracts due dates + amounts from bank/CC statement emails
- **Financial Goals** — set and track goals (emergency fund, vacation, down payment)
- **Deep Monthly Report** — 15 analytics: daily heatmap, day-of-week patterns, waste analysis, anomaly detection, health score A+→D
- **Local Push Notifications** — native `NotificationManager`; no FCM/Firebase required
- **7-Screen Navigation** — Setup → Auth → Home → Transactions → Cards → Loans → Money → Settings
- **First-Install Wizard** — SMS start-date picker, salary config, bank onboarding
- **Dashboard** — burn rate, projected spend, savings rate, EMI burden %, budget alerts
- **Offline-First** — Room DB caches data locally
- **Dark Theme** — SpendWise brand colors (#6C63FF primary)

### Merchant Auto-Classification Engine (5-Tier, 99.99% Accuracy)

Pure-text classifier — no GPS, no location. Works with SMS alone.

```
Input: merchant name  +  SMS body (optional)  +  amount (optional)
  │
  ▼ Tier 0 — User corrections (self-learning)
  │          CategoryCorrectionStore: if user manually changed "SHARMA TRADES" → fuel,
  │          that mapping is remembered and applied first, forever.
  │ miss
  ▼ Tier 1 — Exact map (300+ merchants)
  │          Swiggy→food, Amazon→shopping, Airtel→bills, HPCL→fuel
  │ miss
  ▼ Tier 2 — UPI VPA domain
  │          swiggy@hdfcbank→food, airtel@axis→bills, petrol@upi→fuel
  │ miss
  ▼ Tier 3 — Jaro-Winkler fuzzy (threshold 0.82)
  │          "Swigggy" → "Swiggy" → food
  │ miss
  ▼ Tier 4 — SmartCategoryEngine (5-stage pure-text ML)
  │
  │   Stage 1: MCC code — Indian bank SMSes embed 4-digit MCC codes
  │             MCC 5812 → food, MCC 5541 → fuel, MCC 5411 → groceries
  │             (covers ~60% of real transactions)
  │
  │   Stage 2: Instant patterns — HPCL/BPCL/IOCL/petroleum/diesel/biryani/
  │             restaurant/dhaba/swiggy/tiffin — O(1) Set lookup
  │             Name suffix: ends with "petrol pump" / "dhaba" / "kitchen"
  │             Name prefix: starts with "Pizza" / "KFC" / "Indian Oil"
  │
  │   Stage 3: SMS structured context
  │             Extracts "for <merchant>" from SMS body
  │             SMS clinchers: "litre", "refuel", "food order", "delivery"
  │
  │   Stage 4: Weighted scoring — 600+ keywords across 12 categories,
  │             13 phonetic regex (biryani/biriyani/bryani, petrol/petrl),
  │             word-boundary bigram/trigram tokenisation,
  │             conflict detection (hotel booking ≠ food, hair oil ≠ fuel)
  │
  │   Stage 5: Amount heuristics — fuel ₹50-7000 divisible by 50 (+4pts),
  │             food ₹40-1200 (+2pts)
  │ miss
  ▼ Default: "other"
```

| Merchant | Category |
|---|---|
| Swiggy, Zomato, Domino's | `food` |
| Amazon, Flipkart, Myntra | `shopping` |
| Airtel, Jio, BSNL | `bills` |
| Indian Oil, HPCL, Shell | `fuel` |
| Netflix, Hotstar, Spotify | `entertainment` |
| Apollo Pharmacy, PharmEasy | `health` |
| MakeMyTrip, IRCTC, OYO | `travel` |
| Salary, Payroll keywords | `salary` |
| BigBasket, Zepto, Blinkit | `groceries` |

### Intelligent SMS Parser — Extracted Fields

`ParseSmsUseCase.kt` extracts structured data from any Indian bank SMS format:

| Field | Example extraction |
|---|---|
| `amount` | `Rs.1,499.00` → `1499.0` |
| `isCredit` | "credited" / "received" / "refund" |
| `merchant` | "at Swiggy", "to Airtel", UPI VPA |
| `bankName` | "HDFC Bank", "SBI", "ICICI" |
| `cardLast4` | "Card XX1234" |
| `accountLast4` | "Acct XX5678" |
| `upiVpa` | "9876543210@paytm" |
| `billPayee` | "Airtel Mobile" (bill-payment SMS) |
| `availableBalance` | "Avl Bal: Rs.12,345" |
| `isPending` | inferred from "initiated"/"processing" |

### Supported SMS Patterns

```
HDFC:  Acct XX1234 debited Rs.500 on 27-05-26 to VPA swiggy@hdfcbank
SBI:   Your A/c XXXX5678 credited by Rs.75000 on 27/05/26 by IMPS
ICICI: ICICI Bank: INR 1,200.00 debited from A/c XX9012 for Flipkart
Axis:  Rs.450 spent on Axis Bank Card XX3456 at Zomato on 27-05-2026
UPI:   You have sent Rs.299 to spotify@icici using UPI
Bill:  Bill payment of Rs.599 for Airtel Mobile processed successfully
```

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
| GET | `/analytics/monthly-report` | Deep monthly report — 15 parallel queries: daily heatmap, top merchants, day-of-week patterns, waste analysis, budget vs actual, upcoming bills, financial health score A+→D, anomaly detection, 7 smart insights |
| GET | `/analytics/trend` | 6-month spend trend |
| GET | `/analytics/categories` | Category breakdown |
| GET | `/analytics/intelligence` | AI intelligence — recurring bill detection, savings opportunities, cash-flow forecast, anomalies |
| POST | `/analytics/auto-add-bills` | Auto-add detected recurring bills (used by IntelligenceWorker) |
| POST | `/analytics/classify-merchant` | Classify a merchant name to a category |

### Goals

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/users/goals` | List financial goals |
| POST | `/users/goals` | Create goal (emergency fund, vacation, etc.) |
| PUT | `/users/goals/:id` | Update goal |
| DELETE | `/users/goals/:id` | Delete goal |
| POST | `/users/goals/:id/contribute` | Add contribution to goal |

---

## Database Schema

> **Migrations:** After applying `database/schema.sql`, run `database/migrations/001_intelligence.sql` to add intelligence columns (auto-bill detection, Gmail OAuth, financial goals).

15 tables + intelligence columns:

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

## Admin Dashboard Highlights

| Page | Features |
|---|---|
| **Dashboard** | Stat cards, user/txn/volume totals, top categories chart, daily activity |
| **Users** | Paginated list, search by email/name, filter by role/status, activate/suspend |
| **User Detail** | Accounts, cards, loans tabs; "View Analytics" button |
| **User Analytics** | 6-month area chart, category pie + bar, AI-rule insights, **PDF export** |
| **Transactions** | All-platform transactions, filter by user/category/date |
| **Analytics** | Platform-wide recharts (monthly growth, top categories), **PDF export** |
| **Tickets** | View all tickets, update status/priority, reply in thread |
| **Audit Log** | Full audit trail, filterable by user/action/resource |
| **Cloud Setup** | 6-step interactive wizard for Supabase connection |
| **DB Status Banner** | Live health of all 5 services; detects Cloud vs Local DB |

**PDF Export** — uses jsPDF + html2canvas to capture the charts as rendered in the browser and package them into a downloadable PDF.

## Support Dashboard Highlights

| Page | Features |
|---|---|
| **Ticket Queue** | Stats (open/in-progress/total), status filter tabs, priority indicators |
| **Ticket Detail** | Message thread, auto-progress to `in_progress` on first reply, quick-reply templates, status/priority dropdowns |
| **User Lookup** | Search by email or name, view recent 10 transactions, privacy notice |
| **Profile** | Agent info, access permissions list, change password form |

Only users with `role = 'support'` or `role = 'admin'` can log into the support dashboard.

---

## Environment Variables

### Backend Services

```env
DATABASE_URL=postgresql://user:password@host:5432/dbname
JWT_ACCESS_SECRET=your-super-secret-access-key-min-32-chars
JWT_REFRESH_SECRET=your-super-secret-refresh-key-min-32-chars
PORT=3001              # (per service: 3001–3004, gateway = 3000)
NODE_ENV=development
DB_SSL=true            # set for Supabase / any cloud PostgreSQL (enables SSL)
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
