# End-to-End Workflow Guide

## 1. Local Development Setup

### Prerequisites
- Node.js 20 LTS
- PostgreSQL 17
- Android Studio (Hedgehog or newer)
- npm 9+

### Step 1 — Start PostgreSQL
```powershell
# Windows (if installed as service)
Start-Service -Name "postgresql-x64-17"

# Verify
psql -U postgres -c "SELECT version();"
```

### Step 2 — Apply Database Schema
```powershell
psql -U postgres -d spendwise -f database/schema.sql
```

### Step 3 — Install Dependencies
```powershell
# Gateway
cd backend/api-gateway; npm install; cd ../..

# Auth service
cd backend/auth-service; npm install; cd ../..

# Transaction service
cd backend/transaction-service; npm install; cd ../..

# User service
cd backend/user-service; npm install; cd ../..

# Analytics service
cd backend/analytics-service; npm install; cd ../..

# Admin dashboard
cd admin-dashboard; npm install; cd ..

# Support dashboard
cd support-dashboard; npm install; cd ..
```

### Step 4 — Create .env Files
Copy the environment variables from [06-credentials-and-config.md](06-credentials-and-config.md) and create `.env` files in each service directory.

### Step 5 — Start All Services
Open 7 terminal windows (or use a process manager):

```powershell
# Terminal 1 — API Gateway
cd backend/api-gateway; npm run dev

# Terminal 2 — Auth Service
cd backend/auth-service; npm run dev

# Terminal 3 — Transaction Service
cd backend/transaction-service; npm run dev

# Terminal 4 — User Service
cd backend/user-service; npm run dev

# Terminal 5 — Analytics Service
cd backend/analytics-service; npm run dev

# Terminal 6 — Admin Dashboard
cd admin-dashboard; npm run dev

# Terminal 7 — Support Dashboard
cd support-dashboard; npm run dev
```

### Step 6 — Verify All Services
```powershell
curl http://localhost:3000/health
curl http://localhost:3001/health
curl http://localhost:3002/health
curl http://localhost:3003/health
curl http://localhost:3004/health
```

---

## 2. Admin Dashboard Workflow

Navigate to http://localhost:5173

### Login
- Email: `admin@spendwise.app`
- Password: `Admin@SpendWise2025`

### Key Workflows

**View Platform Stats**
`Dashboard` → KPI cards showing users, transactions, active users, tickets

**Manage Users**
`Users` → Search → Click user → View/Edit profile, change role, suspend account

**View User Analytics**
`Users` → Click user → `View Analytics` button → See 6-month spending trend + AI insights + Download PDF

**Monitor Transactions**
`Transactions` → Filter by date/category → Export CSV

**Handle Tickets**
`Tickets` → Click ticket → Read thread → Reply or change status

**Create Support Agent**
`Settings` → "Create Support Agent" section → Fill email/name/password → Submit

**Connect to Cloud DB**
`Cloud Setup` (sidebar) → Follow 6-step wizard to migrate to Supabase

---

## 3. Support Dashboard Workflow

Navigate to http://localhost:5174

### Login
- Email: `support@spendwise.app`
- Password: `Support@SpendWise2025`

### Key Workflows

**Process Tickets**
`Ticket Queue` → Click highest-priority open ticket → Read full thread → Reply (Ctrl+Enter) → Set status `in_progress`/`resolved`

**Look Up User**
`User Lookup` → Search by email/name → View profile + last 10 transactions (read-only, privacy compliant)

**Update Ticket**
`Ticket Detail` → Change status dropdown or priority → Auto-saves

---

## 4. Android App Workflow

### First Launch
1. Install APK or run from Android Studio
2. Grant SMS permission when prompted
3. `Setup Screen` → Select SMS start date (how far back to scan)
4. App scans historical SMS → batch-imports to backend
5. `Home Screen` shows spending summary

### Adding Transactions
**Automatic**: New bank SMS received → `SmsReceiver` triggers → `ParseSmsUseCase` extracts details → `MerchantMatcher` classifies → stored via API

**Manual**: `+` button → Fill form → Submit

### Viewing Transactions
`Transactions` tab → Filter by date/category/account → Tap transaction to edit

### Managing Accounts
`Money` tab → Bank Accounts / Credit Cards / Loans

---

## 5. SMS Parsing Workflow

```
Bank sends SMS to device
    │
    ▼
SmsReceiver.onReceive() (BroadcastReceiver)
    │
    ├── Is it a bank SMS? (Constants.BANK_PATTERN check)
    │      No  → ignore
    │      Yes ↓
    ▼
ParseSmsUseCase.parse(smsBody)
    ├── extractAmount()        → Rs./INR/₹ patterns
    ├── detectIsCredit()       → credit/debit keywords
    ├── detectCreditCard()     → "credit card", "CC", "card ending"
    ├── extractMerchant()      → 7 pattern cascade
    ├── extractUpiVpa()        → user@bank format
    ├── extractCardLast4()     → "card ending XXXX1234"
    ├── extractAccountLast4()  → "A/C **1234"
    ├── extractLoanAccount()   → "Loan A/C 1234567890"
    ├── extractBankName()      → known bank name list
    └── extractBillPayee()     → "bill payment for BSNL"
    │
    ▼
MerchantMatcher.classify(merchant, smsBody)
    ├── Tier 1: EXACT_MAP (300+ merchants)
    ├── Tier 2: UPI_DOMAIN_MAP (50+ handles)
    ├── Tier 3: Jaro-Winkler fuzzy match (≥0.82)
    └── Tier 4: KEYWORD_SCORES (regex scoring)
    │
    ▼
API: POST /transactions/batch
    ├── Server-side deduplication (sms_id + sms_raw fingerprint)
    ├── Server-side re-classification (backup if client missed)
    └── INSERT ... ON CONFLICT DO NOTHING
```

---

## 6. Production Deployment Checklist

- [ ] Change all default passwords
- [ ] Set strong JWT secrets (256-bit random)
- [ ] Enable SSL in PostgreSQL connections
- [ ] Set `NODE_ENV=production`
- [ ] Set `CORS_ORIGINS` to your actual domain
- [ ] Deploy backend to Railway/Render/EC2
- [ ] Migrate DB to Supabase/Neon/RDS
- [ ] Configure HTTPS with valid certificate
- [ ] Sign Android APK with release keystore
- [ ] Update `BASE_URL` in Android app
- [ ] Run `npm audit fix` on all services
- [ ] Set up monitoring (Sentry, Datadog, or similar)
- [ ] Configure database backups
