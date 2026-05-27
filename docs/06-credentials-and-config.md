# Credentials & Configuration Reference

> ⚠️ **SECURITY**: These are development defaults only. Change all passwords before production deployment.

---

## Default Login Credentials

### Admin Dashboard (http://localhost:5173)
| Field | Value |
|---|---|
| **URL** | http://localhost:5173 |
| **Email** | admin@spendwise.app |
| **Password** | Admin@SpendWise2025 |
| **Role** | admin |

### Support Dashboard (http://localhost:5174)
| Field | Value |
|---|---|
| **URL** | http://localhost:5174 |
| **Email** | support@spendwise.app |
| **Password** | Support@SpendWise2025 |
| **Role** | support |

### Demo User (Android App / API)
| Field | Value |
|---|---|
| **Email** | demo@spendwise.app |
| **Password** | Demo@12345 |
| **Role** | user |

---

## Database (Local PostgreSQL 17)

| Setting | Value |
|---|---|
| **Host** | localhost |
| **Port** | 5432 |
| **Database** | spendwise |
| **User** | postgres |
| **Password** | postgres |
| **Connection String** | `postgresql://postgres:postgres@localhost:5432/spendwise` |

### Connect via psql
```bash
psql -U postgres -d spendwise
```

### Connect via pgAdmin
1. Open pgAdmin 4
2. Add Server → Name: SpendWise Local
3. Host: localhost, Port: 5432
4. Username: postgres, Password: postgres

---

## JWT Secrets (Dev)

| Secret | Value |
|---|---|
| **Access Secret** | `spendwise_access_secret_super_long_key_2025_dev` |
| **Refresh Secret** | `spendwise_refresh_secret_super_long_key_2025_dev` |
| **Access Token TTL** | 15 minutes |
| **Refresh Token TTL** | 30 days |

---

## Service Ports

| Service | Port | Health Check |
|---|---|---|
| API Gateway | 3000 | GET http://localhost:3000/health |
| Auth Service | 3001 | GET http://localhost:3001/health |
| Transaction Service | 3002 | GET http://localhost:3002/health |
| User Service | 3003 | GET http://localhost:3003/health |
| Analytics Service | 3004 | GET http://localhost:3004/health |
| Admin Dashboard | 5173 | http://localhost:5173 |
| Support Dashboard | 5174 | http://localhost:5174 |

---

## Environment Variables

### backend/api-gateway/.env
```env
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/spendwise
JWT_ACCESS_SECRET=spendwise_access_secret_super_long_key_2025_dev
JWT_REFRESH_SECRET=spendwise_refresh_secret_super_long_key_2025_dev
PORT=3000
NODE_ENV=development
CORS_ORIGINS=http://localhost:5173,http://localhost:5174
AUTH_SERVICE_URL=http://localhost:3001
TRANSACTION_SERVICE_URL=http://localhost:3002
USER_SERVICE_URL=http://localhost:3003
ANALYTICS_SERVICE_URL=http://localhost:3004
```

### backend/auth-service/.env
```env
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/spendwise
JWT_ACCESS_SECRET=spendwise_access_secret_super_long_key_2025_dev
JWT_REFRESH_SECRET=spendwise_refresh_secret_super_long_key_2025_dev
PORT=3001
NODE_ENV=development
CORS_ORIGINS=http://localhost:3000
```

### backend/transaction-service/.env
```env
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/spendwise
JWT_ACCESS_SECRET=spendwise_access_secret_super_long_key_2025_dev
PORT=3002
NODE_ENV=development
```

### backend/user-service/.env
```env
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/spendwise
JWT_ACCESS_SECRET=spendwise_access_secret_super_long_key_2025_dev
PORT=3003
NODE_ENV=development
```

### backend/analytics-service/.env
```env
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/spendwise
JWT_ACCESS_SECRET=spendwise_access_secret_super_long_key_2025_dev
PORT=3004
NODE_ENV=development
```

---

## Admin Password Hash (for SQL seeding)

If you need to re-seed the admin user:

```sql
-- Admin@SpendWise2025
UPDATE users SET password_hash = '$2a$12$uKas5pi98cWITmmCLwKI6ORAyXX7WIKPsLRDPlOdeSd3v67oirKcy'
WHERE email = 'admin@spendwise.app';
```

To generate a new hash for any password:
```bash
node -e "const b = require('bcryptjs'); b.hash('YourPassword', 12).then(console.log)"
```

---

## Android App Configuration

### Local Development
Edit `android/app/src/main/java/com/spendwise/app/di/NetworkModule.kt`:
```kotlin
private const val BASE_URL = "http://10.0.2.2:3000/"  // Android emulator
// OR for physical device:
private const val BASE_URL = "http://192.168.1.100:3000/"  // Your machine's IP
```

### Production (after deploying backend)
```kotlin
private const val BASE_URL = "https://api.your-domain.com/"
```

---

## Android Keystore (Release Signing)

> Never commit `android/spendwise.keystore` to version control!

| Setting | Value |
|---|---|
| **Keystore file** | `android/spendwise.keystore` (gitignored) |
| **Key alias** | spendwise |
| **Validity** | 10000 days |

Generate keystore:
```bash
keytool -genkey -v -keystore android/spendwise.keystore \
  -alias spendwise -keyalg RSA -keysize 2048 -validity 10000
```

---

## Supabase Cloud (Production)

After completing Cloud Setup (`/cloud-setup` in admin dashboard):

| Setting | Value |
|---|---|
| **Project URL** | https://[ref].supabase.co |
| **Anon Key** | (from Supabase Dashboard → Settings → API) |
| **Service Role Key** | (keep secret — backend only) |
| **Connection String** | postgres://postgres:[pass]@db.[ref].supabase.co:5432/postgres |
