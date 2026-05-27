import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { Pool } from 'pg';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';

const app = express();
const PORT = process.env.PORT || 3001;

// ── DB ────────────────────────────────────────────────────────
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.DB_SSL === 'true' || process.env.NODE_ENV === 'production'
    ? { rejectUnauthorized: false }
    : false,
});

async function db<T>(sql: string, params?: unknown[]): Promise<T[]> {
  const c = await pool.connect();
  try { return (await c.query(sql, params)).rows as T[]; }
  finally { c.release(); }
}
async function dbOne<T>(sql: string, p?: unknown[]): Promise<T | null> {
  const r = await db<T>(sql, p); return r[0] ?? null;
}

// ── JWT ───────────────────────────────────────────────────────
const ACCESS_SECRET  = process.env.JWT_ACCESS_SECRET  || 'dev_access_secret';
const REFRESH_SECRET = process.env.JWT_REFRESH_SECRET || 'dev_refresh_secret';

function signAccess(payload: object)  { return jwt.sign(payload, ACCESS_SECRET,  { expiresIn: '15m' }); }
function signRefresh(payload: object) { return jwt.sign(payload, REFRESH_SECRET, { expiresIn: '30d' }); }
function verifyAccess(t: string)  { return jwt.verify(t, ACCESS_SECRET)  as { userId: string; email: string; role: string }; }
function verifyRefresh(t: string) { return jwt.verify(t, REFRESH_SECRET) as { userId: string; email: string; role: string }; }

// ── MIDDLEWARE ────────────────────────────────────────────────
app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGINS?.split(',') || '*' }));
app.use(express.json());
app.use(rateLimit({ windowMs: 15 * 60 * 1000, max: 50, message: { success: false, error: 'Too many requests' } }));

function ok<T>(res: express.Response, data: T, status = 200) {
  return res.status(status).json({ success: true, data });
}
function fail(res: express.Response, error: string, status = 400, code?: string) {
  return res.status(status).json({ success: false, error, code });
}

function authMiddleware(req: express.Request, res: express.Response, next: express.NextFunction) {
  const h = req.headers.authorization;
  if (!h?.startsWith('Bearer ')) return fail(res, 'Unauthorized', 401, 'UNAUTHORIZED');
  try {
    (req as any).user = verifyAccess(h.slice(7));
    next();
  } catch { return fail(res, 'Invalid or expired token', 401, 'TOKEN_EXPIRED'); }
}

// ── ROUTES ────────────────────────────────────────────────────

// POST /auth/register
app.post('/auth/register', async (req, res) => {
  try {
    const { email, password, name = '' } = req.body;
    if (!email || !password) return fail(res, 'email and password required');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return fail(res, 'Invalid email');
    if (password.length < 8) return fail(res, 'Password must be at least 8 characters');

    const existing = await dbOne('SELECT id FROM users WHERE email=$1', [email.toLowerCase()]);
    if (existing) return fail(res, 'Email already registered', 409, 'EMAIL_EXISTS');

    const hash = await bcrypt.hash(password, 12);
    const userId = uuidv4();
    await db(
      `INSERT INTO users(id,email,password_hash,name) VALUES($1,$2,$3,$4)`,
      [userId, email.toLowerCase(), hash, name]
    );

    // create default salary config
    await db(`INSERT INTO salary_config(user_id) VALUES($1) ON CONFLICT DO NOTHING`, [userId]);

    const tokenPayload = { userId, email: email.toLowerCase(), role: 'user' };
    const accessToken  = signAccess(tokenPayload);
    const refreshToken = signRefresh(tokenPayload);
    const refreshId    = uuidv4();
    const expiresAt    = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);

    await db(
      `INSERT INTO user_sessions(id,user_id,refresh_token,expires_at,ip_address) VALUES($1,$2,$3,$4,$5)`,
      [refreshId, userId, refreshToken, expiresAt, req.ip]
    );

    await db(
      `INSERT INTO audit_log(user_id,action,resource_type,ip_address) VALUES($1,'REGISTER','user',$2)`,
      [userId, req.ip]
    );

    return ok(res, { accessToken, refreshToken, user: { id: userId, email: email.toLowerCase(), name, role: 'user' } }, 201);
  } catch (e: any) {
    console.error('[register]', e.message);
    return fail(res, 'Server error', 500);
  }
});

// POST /auth/login
app.post('/auth/login', async (req, res) => {
  try {
    const { email, password } = req.body;
    if (!email || !password) return fail(res, 'email and password required');

    const user = await dbOne<any>('SELECT * FROM users WHERE email=$1 AND is_active=TRUE', [email.toLowerCase()]);
    if (!user) return fail(res, 'Invalid email or password', 401, 'INVALID_CREDENTIALS');

    const match = await bcrypt.compare(password, user.password_hash);
    if (!match) return fail(res, 'Invalid email or password', 401, 'INVALID_CREDENTIALS');

    await db(`UPDATE users SET last_login_at=NOW() WHERE id=$1`, [user.id]);

    const tokenPayload = { userId: user.id, email: user.email, role: user.role };
    const accessToken  = signAccess(tokenPayload);
    const refreshToken = signRefresh(tokenPayload);
    const expiresAt    = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);

    await db(
      `INSERT INTO user_sessions(id,user_id,refresh_token,expires_at,ip_address) VALUES($1,$2,$3,$4,$5)`,
      [uuidv4(), user.id, refreshToken, expiresAt, req.ip]
    );

    await db(
      `INSERT INTO audit_log(user_id,action,resource_type,ip_address) VALUES($1,'LOGIN','user',$2)`,
      [user.id, req.ip]
    );

    return ok(res, {
      accessToken,
      refreshToken,
      user: { id: user.id, email: user.email, name: user.name, role: user.role }
    });
  } catch (e: any) {
    console.error('[login]', e.message);
    return fail(res, 'Server error', 500);
  }
});

// POST /auth/refresh
app.post('/auth/refresh', async (req, res) => {
  try {
    const { refreshToken } = req.body;
    if (!refreshToken) return fail(res, 'refreshToken required');

    const session = await dbOne<any>(
      `SELECT * FROM user_sessions WHERE refresh_token=$1 AND is_revoked=FALSE AND expires_at>NOW()`,
      [refreshToken]
    );
    if (!session) return fail(res, 'Invalid or expired refresh token', 401, 'REFRESH_INVALID');

    let payload: any;
    try { payload = verifyRefresh(refreshToken); }
    catch { return fail(res, 'Invalid refresh token', 401, 'REFRESH_INVALID'); }

    const user = await dbOne<any>('SELECT * FROM users WHERE id=$1 AND is_active=TRUE', [payload.userId]);
    if (!user) return fail(res, 'User not found', 401);

    const newAccess  = signAccess({ userId: user.id, email: user.email, role: user.role });
    const newRefresh = signRefresh({ userId: user.id, email: user.email, role: user.role });
    const expiresAt  = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);

    // Rotate refresh token
    await db(`UPDATE user_sessions SET is_revoked=TRUE WHERE id=$1`, [session.id]);
    await db(
      `INSERT INTO user_sessions(id,user_id,refresh_token,expires_at,ip_address) VALUES($1,$2,$3,$4,$5)`,
      [uuidv4(), user.id, newRefresh, expiresAt, req.ip]
    );

    return ok(res, { accessToken: newAccess, refreshToken: newRefresh });
  } catch (e: any) {
    console.error('[refresh]', e.message);
    return fail(res, 'Server error', 500);
  }
});

// POST /auth/logout
app.post('/auth/logout', async (req, res) => {
  try {
    const { refreshToken } = req.body;
    if (refreshToken) {
      await db(`UPDATE user_sessions SET is_revoked=TRUE WHERE refresh_token=$1`, [refreshToken]);
    }
    return ok(res, { message: 'Logged out' });
  } catch (e: any) {
    return fail(res, 'Server error', 500);
  }
});

// GET /auth/me
app.get('/auth/me', authMiddleware, async (req, res) => {
  try {
    const user = await dbOne<any>(
      'SELECT id,email,name,phone,currency_code,locale,role,created_at,last_login_at FROM users WHERE id=$1',
      [(req as any).user.userId]
    );
    if (!user) return fail(res, 'User not found', 404);
    return ok(res, user);
  } catch (e: any) {
    return fail(res, 'Server error', 500);
  }
});

// DELETE /auth/account
app.delete('/auth/account', authMiddleware, async (req, res) => {
  try {
    const { password } = req.body;
    const userId = (req as any).user.userId;
    const user = await dbOne<any>('SELECT * FROM users WHERE id=$1', [userId]);
    if (!user) return fail(res, 'User not found', 404);
    if (password) {
      const match = await bcrypt.compare(password, user.password_hash);
      if (!match) return fail(res, 'Incorrect password', 401);
    }
    await db(`DELETE FROM users WHERE id=$1`, [userId]);
    return ok(res, { message: 'Account deleted' });
  } catch (e: any) {
    return fail(res, 'Server error', 500);
  }
});

// Health check
app.get('/health', (_req, res) => res.json({ service: 'auth', status: 'ok', ts: new Date() }));

app.listen(PORT, () => console.log(`[auth-service] running on :${PORT}`));
export default app;
