# SpendWise — Production Deployment Guide

All backend services deploy to **Railway** (Docker images from GHCR).  
Both dashboards deploy to **Vercel** (static SPA).  
Database is **Neon PostgreSQL** (serverless PG 17, free tier).

---

## Architecture

```
Android App
    │
    ▼
Railway: api-gateway  (:3000)  ◄── public HTTPS endpoint
    │
    ├── auth-service        (:3001)  ← internal only
    ├── transaction-service (:3002)  ← internal only
    ├── user-service        (:3003)  ← internal only
    └── analytics-service   (:3004)  ← internal only
              │
              ▼
         Neon (PostgreSQL)

Vercel: admin-dashboard    → calls Railway gateway
Vercel: support-dashboard  → calls Railway gateway
```

---

## Step 1 — Database (Neon PostgreSQL)

**Production DB:** Neon serverless PostgreSQL (free tier, IPv4, PG 17).

### First-time setup (if starting fresh)

1. Go to [neon.tech](https://neon.tech) → **New Project** → choose region (e.g. `us-east-1` or `ap-southeast-1`)
2. Copy the **Connection string** from Dashboard → Connection Details  
   It looks like: `postgresql://neondb_owner:PASSWORD@ep-xxx.region.aws.neon.tech/neondb?sslmode=require`
3. Apply the schema:
   ```bash
   psql "YOUR_NEON_URL" -f database/schema.sql
   psql "YOUR_NEON_URL" -f database/migrations/001_intelligence.sql
   ```

### Already provisioned

The production Neon database is already running. Use the existing `DATABASE_URL` from Railway service env vars. No action needed unless migrating or recreating.

---

## Step 2 — Deploy to Railway

### 2a. Create a Railway project

1. Go to [railway.app](https://railway.app) → **New Project**
2. Choose **Empty Project** — you'll add 5 services manually

### 2b. Deploy each microservice

Repeat these steps **5 times** (one per service):

| Service name (use exactly this) | Docker image | Port |
|---|---|---|
| `api-gateway` | `ghcr.io/aehost/spendwise-api-gateway:latest` | 3000 |
| `auth-service` | `ghcr.io/aehost/spendwise-auth-service:latest` | 3001 |
| `transaction-service` | `ghcr.io/aehost/spendwise-transaction-service:latest` | 3002 |
| `user-service` | `ghcr.io/aehost/spendwise-user-service:latest` | 3003 |
| `analytics-service` | `ghcr.io/aehost/spendwise-analytics-service:latest` | 3004 |

For each service:

1. Click **New Service → Docker Image**
2. Enter the image URL from the table above (e.g. `ghcr.io/aehost/spendwise-api-gateway:latest`)
3. Set environment variables (see Section 2c below)
4. Click **Deploy**

> **Railway private networking**: Services on the same Railway project can reach each other via `<service-name>.railway.internal`. The api-gateway uses this internally.
>
> **Image updates**: When a new Docker image is pushed to GHCR, change any env var (e.g. add a space to a value and save) to trigger a redeploy that picks up the latest image.

### 2c. Environment variables per service

#### `auth-service`, `transaction-service`, `user-service`, `analytics-service`

| Variable | Value |
|---|---|
| `DATABASE_URL` | `postgresql://neondb_owner:PASSWORD@ep-xxx.region.aws.neon.tech/neondb?sslmode=require` |
| `JWT_ACCESS_SECRET` | Same long random string on all services |
| `JWT_REFRESH_SECRET` | *(auth-service only)* Different long random string |
| `JWT_ACCESS_EXPIRES` | `15m` |
| `JWT_REFRESH_EXPIRES` | `30d` |
| `CORS_ORIGINS` | `*` *(internal services only talk to gateway)* |
| `PORT` | `3001` / `3002` / `3003` / `3004` respectively |
| `NODE_ENV` | `production` |
| `GOOGLE_PLACES_API_KEY` | *(analytics only, optional)* |

> ℹ️ **No `DB_SSL` needed** — `sslmode=require` in the Neon URL enables SSL automatically.

#### `api-gateway`

| Variable | Value |
|---|---|
| `DATABASE_URL` | same Neon URL |
| `JWT_ACCESS_SECRET` | same as above |
| `AUTH_SERVICE_URL` | `http://auth-service.railway.internal:3001` |
| `TRANSACTION_SERVICE_URL` | `http://transaction-service.railway.internal:3002` |
| `USER_SERVICE_URL` | `http://user-service.railway.internal:3003` |
| `ANALYTICS_SERVICE_URL` | `http://analytics-service.railway.internal:3004` |
| `CORS_ORIGINS` | `https://YOUR-ADMIN.vercel.app,https://YOUR-SUPPORT.vercel.app` *(fill in after Vercel deploy)* |
| `PORT` | `3000` |
| `NODE_ENV` | `production` |

> **Tip**: Generate secure JWT secrets with:  
> `node -e "console.log(require('crypto').randomBytes(64).toString('hex'))"`

### 2d. Get the gateway public URL

After deploying `api-gateway`:
1. Click the service → **Settings → Networking → Generate Domain**
2. Note the URL (e.g. `https://api-gateway-production-xxxx.up.railway.app`)
3. You'll need this for the Android app and Vercel env vars

---

## Step 3 — Deploy Dashboards to Vercel

### Admin Dashboard

1. Go to [vercel.com](https://vercel.com) → **Add New Project**
2. Import your GitHub repo
3. Set **Root Directory** to `admin-dashboard`
4. Framework: **Vite** (auto-detected)
5. Add environment variable:
   - `VITE_API_URL` = `https://api-gateway-production-xxxx.up.railway.app`  
     *(paste your Railway gateway URL from Step 2d — **no** `/api` suffix)*
6. Click **Deploy**
7. Note the Vercel URL (e.g. `https://spendwise-admin.vercel.app`)

### Support Dashboard

Repeat above but:
- Root Directory: `support-dashboard`
- Same `VITE_API_URL` value (no `/api` suffix)
- Note the support dashboard Vercel URL

---

## Step 4 — Update CORS on api-gateway

Go back to Railway → `api-gateway` → **Variables** and update:

```
CORS_ORIGINS=https://spendwise-admin.vercel.app,https://spendwise-support.vercel.app
```

Railway auto-redeploys on variable changes.

---

## Step 5 — Update Android App

In [android/app/build.gradle](android/app/build.gradle), update the release `BASE_URL`:

```groovy
buildConfigField "String", "BASE_URL", '"https://api-gateway-production-xxxx.up.railway.app/"'
```

> ℹ️ **No `/api/` suffix** — the gateway serves routes at the root (`/auth/`, `/transactions/`, `/user/`, `/analytics/`).

Then build the release APK:
```bash
cd android
./gradlew assembleRelease
```

---

## Step 6 — Verify Everything Works

### Backend health checks

```bash
# Gateway
curl https://your-gateway.up.railway.app/health

# Expected response:
# {"gateway":"ok","ts":"...","services":{"auth":"http://...","transactions":"http://...",...}}
```

### Test auth flow

```bash
# Register
curl -X POST https://your-gateway.up.railway.app/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test1234!","name":"Test User"}'

# Login
curl -X POST https://your-gateway.up.railway.app/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test1234!"}'
```

### Dashboard login

Open your admin dashboard Vercel URL → login with your admin account credentials.

---

## Cost Estimate

| Service | Free Tier | Paid |
|---|---|---|
| Railway | $5/month free credits (Hobby plan) | $20/month Pro |
| Vercel | Free (2 projects, 100GB bandwidth) | $20/month Pro |
| Neon | Free (500MB DB, 190 compute hours/month) | $19/month Pro |
| **Total** | **$0 to start** | ~$59/month for serious scale |

Railway's free $5/month covers ~500 container-hours. With 5 lightweight Node.js services that go to sleep when idle, this stretches a long way. You can also consolidate all 5 services under one `$5` plan on Hobby.

---

## Secrets Checklist

- [ ] JWT secrets are **not** in the repo (set only in Railway env vars)
- [ ] Neon `DATABASE_URL` is **not** in the repo (set only in Railway env vars)
- [ ] `.env` files are in `.gitignore` ✓
- [ ] `.env.example` files use placeholder values only ✓
- [ ] Release keystore is **not** in the repo (`android/spendwise.keystore` is gitignored ✓)

---

## Rollback

Railway keeps the last 10 deployments. Click the service → **Deployments** → choose a previous build → **Redeploy**.

Vercel keeps all deployments. Click the project → **Deployments** → promote any previous deployment to production.

---

## Custom Domain (Optional)

### Railway (api.yourdomain.com)
1. Railway → api-gateway → Settings → Networking → Custom Domain
2. Add `api.yourdomain.com`
3. Add CNAME record in your DNS provider

### Vercel
1. Vercel → Project → Settings → Domains
2. Add `admin.yourdomain.com` and `support.yourdomain.com`
3. Follow DNS instructions

After custom domains, update:
- `CORS_ORIGINS` in Railway gateway env vars
- `VITE_API_URL` in both Vercel projects
- `BASE_URL` in `android/app/build.gradle`
