# How to Connect Local App to Cloud Database

This guide shows you exactly how to switch from local PostgreSQL to Supabase (or any cloud Postgres provider) from your local machine.

---

## Option A: Use the Cloud Setup Wizard (Recommended)

1. Start the admin dashboard: `cd admin-dashboard && npm run dev`
2. Go to http://localhost:5173 → Login as admin
3. Click **"Cloud Setup"** in the left sidebar
4. Follow the 6-step wizard — each step is explained with copy-paste commands

---

## Option B: Manual Setup

### Step 1 — Create Supabase Project

1. Go to https://supabase.com → Sign up / Log in
2. Click **"New Project"**
3. Fill in:
   - **Name**: spendwise-prod
   - **Database Password**: (choose strong password, save it!)
   - **Region**: Asia South (Mumbai) — best for India
4. Wait ~2 minutes for provisioning

---

### Step 2 — Get Connection String

1. In Supabase project dashboard
2. Go to **Settings** → **Database**
3. Scroll to **"Connection string"** section
4. Click **"URI"** tab
5. Copy the connection string — looks like:
   ```
   postgresql://postgres:[YOUR-PASSWORD]@db.xxxxxxxxxxxx.supabase.co:5432/postgres
   ```
6. Replace `[YOUR-PASSWORD]` with your actual password

---

### Step 3 — Apply Schema to Cloud Database

**Option A: SQL Editor (Supabase)**
1. In Supabase → **SQL Editor** → **New Query**
2. Open `database/schema.sql` from this repo
3. Copy-paste entire content
4. Click **Run** (Ctrl+Enter)

**Option B: psql CLI**
```bash
psql "postgresql://postgres:[PASSWORD]@db.[REF].supabase.co:5432/postgres" \
  -f database/schema.sql
```

**Option C: pgAdmin 4**
1. Register server: Host = `db.[REF].supabase.co`, Port = 5432
2. Username = `postgres`, Password = (your DB password)
3. Right-click database → **Restore** → select schema.sql

---

### Step 4 — Seed Admin User

Run this in Supabase SQL Editor or psql:

```sql
INSERT INTO users (id, email, password_hash, name, role, is_verified, is_active)
VALUES (
  gen_random_uuid(),
  'admin@spendwise.app',
  '$2a$12$uKas5pi98cWITmmCLwKI6ORAyXX7WIKPsLRDPlOdeSd3v67oirKcy',
  'SpendWise Admin',
  'admin',
  TRUE,
  TRUE
) ON CONFLICT (email) DO NOTHING;
```

---

### Step 5 — Update All Backend .env Files

Replace `DATABASE_URL` in **each** of these files:
- `backend/api-gateway/.env`
- `backend/auth-service/.env`
- `backend/transaction-service/.env`
- `backend/user-service/.env`
- `backend/analytics-service/.env`

Change from:
```env
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/spendwise
```
To:
```env
DATABASE_URL=postgresql://postgres:[YOUR-PASSWORD]@db.[REF].supabase.co:5432/postgres
```

---

### Step 6 — Restart Services

```powershell
# Stop all running services (Ctrl+C in each terminal)
# Then restart each one:
cd backend/api-gateway; npm run dev
cd backend/auth-service; npm run dev
# ... etc
```

---

### Step 7 — Verify Connection

```powershell
# Should now show data from Supabase
curl http://localhost:3000/health

# Login should work
curl -X POST http://localhost:3000/auth/login `
  -H "Content-Type: application/json" `
  -d '{"email":"admin@spendwise.app","password":"Admin@SpendWise2025"}'
```

---

## Connecting Android App to Cloud

After the backend is pointing to cloud DB:

### Emulator
```kotlin
// NetworkModule.kt
private const val BASE_URL = "http://10.0.2.2:3000/"  // Maps to localhost on your machine
```
The Android emulator uses `10.0.2.2` to reach the host machine's `localhost`.

### Physical Android Device (same WiFi)
1. Find your machine's local IP:
   ```powershell
   ipconfig | findstr IPv4
   # e.g. 192.168.1.105
   ```
2. Update NetworkModule.kt:
   ```kotlin
   private const val BASE_URL = "http://192.168.1.105:3000/"
   ```
3. Rebuild and install APK on device

### Physical Device + Cloud Backend
When you deploy the backend to Railway/Render:
```kotlin
private const val BASE_URL = "https://your-app.railway.app/"
```

---

## Troubleshooting

| Problem | Solution |
|---|---|
| `Connection refused` | Check DATABASE_URL format; ensure Supabase SSL is handled (`ssl: { rejectUnauthorized: false }`) |
| `SSL required` | Add `?sslmode=require` to connection string or use `ssl: { rejectUnauthorized: false }` in Pool config |
| `Auth failed` | Re-run the seed SQL to create admin user |
| `Tables don't exist` | Re-run database/schema.sql |
| `CORS error in dashboard` | Update `CORS_ORIGINS` env var to include your dashboard URL |
| Android `CLEARTEXT not permitted` | Ensure backend uses HTTPS, or add `android:usesCleartextTraffic="true"` in AndroidManifest for dev |

---

## Keeping Local and Cloud in Sync

For development, you may want to keep local DB as development environment and only push tested features to production (cloud DB):

1. **Development**: Use local PostgreSQL (fast iterations)
2. **Staging**: Small Supabase project with test data
3. **Production**: Supabase production project

Switch environments by changing `DATABASE_URL` in `.env` files.
