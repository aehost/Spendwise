import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { createProxyMiddleware } from 'http-proxy-middleware';
import jwt from 'jsonwebtoken';
import { Pool } from 'pg';

const app = express();
const PORT = process.env.PORT || 3000;

const AUTH_URL        = process.env.AUTH_SERVICE_URL        || 'http://localhost:3001';
const TRANSACTION_URL = process.env.TRANSACTION_SERVICE_URL || 'http://localhost:3002';
const USER_URL        = process.env.USER_SERVICE_URL        || 'http://localhost:3003';
const ANALYTICS_URL   = process.env.ANALYTICS_SERVICE_URL  || 'http://localhost:3004';
const ACCESS_SECRET   = process.env.JWT_ACCESS_SECRET       || 'dev_access_secret';

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false,
});

async function db<T>(sql: string, p?: unknown[]): Promise<T[]> {
  const c = await pool.connect();
  try { return (await c.query(sql, p)).rows as T[]; }
  finally { c.release(); }
}
async function dbOne<T>(sql: string, p?: unknown[]): Promise<T | null> {
  return (await db<T>(sql, p))[0] ?? null;
}
async function execute(sql: string, p?: unknown[]): Promise<number> {
  const c = await pool.connect();
  try { return (await c.query(sql, p)).rowCount ?? 0; }
  finally { c.release(); }
}

// ── HELPERS ───────────────────────────────────────────────────
function ok<T>(res: express.Response, d: T, status = 200) { return res.status(status).json({ success: true, data: d }); }
function fail(res: express.Response, e: string, s = 400, code?: string) { return res.status(s).json({ success: false, error: e, code }); }

function verifyJwt(h?: string) {
  if (!h?.startsWith('Bearer ')) return null;
  try { return jwt.verify(h.slice(7), ACCESS_SECRET) as { userId: string; email: string; role: string }; }
  catch { return null; }
}

function authMiddleware(req: express.Request, res: express.Response, next: express.NextFunction) {
  const user = verifyJwt(req.headers.authorization);
  if (!user) return fail(res, 'Unauthorized', 401, 'UNAUTHORIZED');
  (req as any).user = user;
  next();
}

function requireRole(...roles: string[]) {
  return (req: express.Request, res: express.Response, next: express.NextFunction) => {
    const user = (req as any).user;
    if (!user || !roles.includes(user.role)) return fail(res, 'Forbidden', 403, 'FORBIDDEN');
    next();
  };
}

// ── SETUP ─────────────────────────────────────────────────────
app.use(helmet({ contentSecurityPolicy: false }));
app.use(cors({
  origin: process.env.CORS_ORIGINS?.split(',') || ['http://localhost:5173', 'http://localhost:5174'],
  credentials: true
}));
app.use(express.json({ limit: '10mb' }));
app.use(rateLimit({ windowMs: 60_000, max: 500, standardHeaders: true, legacyHeaders: false }));

// Request logger
app.use((req, _res, next) => {
  console.log(`[GW] ${req.method} ${req.path}`);
  next();
});

// ── PROXY HELPERS ─────────────────────────────────────────────
function proxy(target: string, pathRewrite?: Record<string, string>) {
  return createProxyMiddleware({
    target,
    changeOrigin: true,
    pathRewrite,
    on: {
      error: (err, _req, res: any) => {
        console.error('[proxy error]', err.message);
        res.status(502).json({ success: false, error: 'Service unavailable', code: 'SERVICE_UNAVAILABLE' });
      }
    }
  });
}

// ── ROUTES ────────────────────────────────────────────────────

// Health
app.get('/health', (_req, res) => res.json({
  gateway: 'ok', ts: new Date(),
  services: { auth: AUTH_URL, transactions: TRANSACTION_URL, user: USER_URL, analytics: ANALYTICS_URL }
}));

// Proxy: Auth service
app.use('/api/auth', proxy(AUTH_URL, { '^/api/auth': '/auth' }));

// Proxy: Transaction service
app.use('/api/transactions', proxy(TRANSACTION_URL, { '^/api/transactions': '/transactions' }));

// Proxy: User service
app.use('/api/user', proxy(USER_URL, { '^/api/user': '' }));

// Proxy: Analytics service
app.use('/api/analytics', proxy(ANALYTICS_URL, { '^/api/analytics': '/analytics' }));

// ── ADMIN ROUTES ──────────────────────────────────────────────
const adminRouter = express.Router();
adminRouter.use(authMiddleware, requireRole('admin'));

// GET /api/admin/stats
adminRouter.get('/stats', async (_req, res) => {
  try {
    const [users, txToday, txTotal, activeToday, tickets] = await Promise.all([
      dbOne<any>('SELECT COUNT(*) as total FROM users'),
      dbOne<any>(`SELECT COUNT(*) as count FROM transactions WHERE created_at >= CURRENT_DATE`),
      dbOne<any>('SELECT COUNT(*) as count FROM transactions'),
      dbOne<any>(`SELECT COUNT(DISTINCT user_id) as count FROM user_sessions WHERE created_at >= CURRENT_DATE AND is_revoked=FALSE`),
      dbOne<any>(`SELECT COUNT(*) FILTER (WHERE status='open') as open, COUNT(*) FILTER (WHERE status='in_progress') as in_progress FROM support_tickets`),
    ]);
    return ok(res, {
      total_users: parseInt(users?.total || 0),
      transactions_today: parseInt(txToday?.count || 0),
      total_transactions: parseInt(txTotal?.count || 0),
      active_users_today: parseInt(activeToday?.count || 0),
      open_tickets: parseInt(tickets?.open || 0),
      in_progress_tickets: parseInt(tickets?.in_progress || 0),
    });
  } catch (e: any) { return fail(res, 'Server error', 500); }
});

// GET /api/admin/users
adminRouter.get('/users', async (req, res) => {
  try {
    const page  = parseInt(String(req.query.page  || 1));
    const limit = parseInt(String(req.query.limit || 20));
    const search = req.query.search as string;
    const role   = req.query.role as string;
    const active = req.query.is_active as string;

    let sql = `SELECT u.id,u.email,u.name,u.role,u.is_active,u.is_verified,u.created_at,u.last_login_at,
                      (SELECT COUNT(*) FROM transactions WHERE user_id=u.id) as tx_count
               FROM users u WHERE 1=1`;
    const params: unknown[] = [];
    let i = 1;
    if (search) { sql += ` AND (u.email ILIKE $${i++} OR u.name ILIKE $${i++})`; params.push(`%${search}%`, `%${search}%`); i++; }
    if (role)   { sql += ` AND u.role=$${i++}`; params.push(role); }
    if (active !== undefined) { sql += ` AND u.is_active=$${i++}`; params.push(active === 'true'); }

    const countResult = await db<{ count: string }>(sql.replace(/SELECT u\.id.*FROM users u/, 'SELECT COUNT(*) FROM users u'), params);
    const total = parseInt(countResult[0]?.count || '0');

    sql += ` ORDER BY u.created_at DESC LIMIT $${i++} OFFSET $${i++}`;
    params.push(limit, (page-1)*limit);
    const users = await db(sql, params);

    return ok(res, { users, total, page, limit, pages: Math.ceil(total/limit) });
  } catch (e: any) { console.error(e.message); return fail(res, 'Server error', 500); }
});

// GET /api/admin/users/:id
adminRouter.get('/users/:id', async (req, res) => {
  try {
    const user = await dbOne<any>(
      `SELECT u.id,u.email,u.name,u.phone,u.role,u.is_active,u.is_verified,u.created_at,u.last_login_at,u.currency_code,u.locale,
              (SELECT COUNT(*) FROM transactions WHERE user_id=u.id) as tx_count,
              (SELECT COUNT(*) FROM bank_accounts WHERE user_id=u.id AND is_active=TRUE) as account_count,
              (SELECT COUNT(*) FROM credit_cards WHERE user_id=u.id AND is_active=TRUE) as card_count,
              (SELECT COUNT(*) FROM loans WHERE user_id=u.id AND is_active=TRUE) as loan_count
       FROM users u WHERE u.id=$1`,
      [req.params.id]
    );
    if (!user) return fail(res, 'User not found', 404);

    const recentTx = await db('SELECT * FROM transactions WHERE user_id=$1 ORDER BY transaction_date DESC LIMIT 10', [req.params.id]);
    const accounts = await db('SELECT * FROM bank_accounts WHERE user_id=$1 AND is_active=TRUE', [req.params.id]);

    return ok(res, { ...user, recent_transactions: recentTx, bank_accounts: accounts });
  } catch { return fail(res, 'Server error', 500); }
});

// PUT /api/admin/users/:id/status
adminRouter.put('/users/:id/status', async (req, res) => {
  try {
    const { is_active } = req.body;
    const n = await execute('UPDATE users SET is_active=$1,updated_at=NOW() WHERE id=$2', [is_active, req.params.id]);
    if (!n) return fail(res, 'User not found', 404);
    await db(`INSERT INTO audit_log(user_id,action,resource_type,resource_id,metadata) VALUES($1,'UPDATE_STATUS','user',$2,$3)`,
      [(req as any).user.userId, req.params.id, JSON.stringify({ is_active })]);
    return ok(res, { updated: true, is_active });
  } catch { return fail(res, 'Server error', 500); }
});

// PUT /api/admin/users/:id/role
adminRouter.put('/users/:id/role', async (req, res) => {
  try {
    const { role } = req.body;
    if (!['user','admin','support'].includes(role)) return fail(res, 'Invalid role');
    const n = await execute('UPDATE users SET role=$1,updated_at=NOW() WHERE id=$2', [role, req.params.id]);
    if (!n) return fail(res, 'User not found', 404);
    await db(`INSERT INTO audit_log(user_id,action,resource_type,resource_id,metadata) VALUES($1,'UPDATE_ROLE','user',$2,$3)`,
      [(req as any).user.userId, req.params.id, JSON.stringify({ role })]);
    return ok(res, { updated: true, role });
  } catch { return fail(res, 'Server error', 500); }
});

// GET /api/admin/transactions
adminRouter.get('/transactions', async (req, res) => {
  try {
    const page  = parseInt(String(req.query.page  || 1));
    const limit = Math.min(parseInt(String(req.query.limit || 50)), 200);
    const { user_email, category, start, end } = req.query;

    let sql = `SELECT t.*,u.email as user_email FROM transactions t JOIN users u ON u.id=t.user_id WHERE 1=1`;
    const params: unknown[] = [];
    let i = 1;
    if (user_email) { sql += ` AND u.email ILIKE $${i++}`; params.push(`%${user_email}%`); }
    if (category)   { sql += ` AND t.category_slug=$${i++}`; params.push(category); }
    if (start)      { sql += ` AND t.transaction_date>=$${i++}`; params.push(start); }
    if (end)        { sql += ` AND t.transaction_date<=$${i++}`; params.push(end); }

    const countSql = sql.replace('SELECT t.*,u.email as user_email', 'SELECT COUNT(*)');
    const total = parseInt((await db<{ count: string }>(countSql, params))[0]?.count || '0');

    sql += ` ORDER BY t.transaction_date DESC,t.created_at DESC LIMIT $${i++} OFFSET $${i++}`;
    params.push(limit, (page-1)*limit);
    const transactions = await db(sql, params);

    return ok(res, { transactions, total, page, limit, pages: Math.ceil(total/limit) });
  } catch (e: any) { console.error(e.message); return fail(res, 'Server error', 500); }
});

// GET /api/admin/audit-log
adminRouter.get('/audit-log', async (req, res) => {
  try {
    const page  = parseInt(String(req.query.page  || 1));
    const limit = Math.min(parseInt(String(req.query.limit || 50)), 200);
    const rows = await db(
      `SELECT al.*,u.email FROM audit_log al LEFT JOIN users u ON u.id=al.user_id
       ORDER BY al.created_at DESC LIMIT $1 OFFSET $2`,
      [limit, (page-1)*limit]
    );
    const total = parseInt((await dbOne<any>('SELECT COUNT(*) as count FROM audit_log'))?.count || '0');
    return ok(res, { log: rows, total, page, limit });
  } catch { return fail(res, 'Server error', 500); }
});

// POST /api/admin/agents — create support agent
adminRouter.post('/agents', async (req, res) => {
  try {
    const { email, name, password } = req.body;
    if (!email || !password || !name) return fail(res, 'email, name, password required');
    // Forward to auth service to create account, then update role
    const bcrypt = await import('bcryptjs');
    const { v4: uuidv4 } = await import('uuid');
    const hash = await bcrypt.default.hash(password, 12);
    const id   = uuidv4();
    await db('INSERT INTO users(id,email,password_hash,name,role,is_verified) VALUES($1,$2,$3,$4,$5,TRUE)',
      [id, email.toLowerCase(), hash, name, 'support']);
    return ok(res, { id, email, name, role: 'support' }, 201);
  } catch (e: any) {
    if (e.code === '23505') return fail(res, 'Email already exists', 409);
    return fail(res, 'Server error', 500);
  }
});

app.use('/api/admin', adminRouter);

// ── SUPPORT ROUTES ────────────────────────────────────────────
const supportRouter = express.Router();
supportRouter.use(authMiddleware, requireRole('admin', 'support'));

// GET /api/support/tickets
supportRouter.get('/tickets', async (req, res) => {
  try {
    const page   = parseInt(String(req.query.page || 1));
    const limit  = Math.min(parseInt(String(req.query.limit || 20)), 100);
    const status = req.query.status as string;
    const priority = req.query.priority as string;

    let sql = `SELECT t.*,u.email as user_email,a.name as assigned_name
               FROM support_tickets t
               LEFT JOIN users u ON u.id=t.user_id
               LEFT JOIN users a ON a.id=t.assigned_to WHERE 1=1`;
    const params: unknown[] = [];
    let i = 1;
    if (status)   { sql += ` AND t.status=$${i++}`;   params.push(status); }
    if (priority) { sql += ` AND t.priority=$${i++}`; params.push(priority); }

    const total = parseInt((await db<{ count: string }>(sql.replace('SELECT t.*,u.email as user_email,a.name as assigned_name', 'SELECT COUNT(*)'), params))[0]?.count || '0');
    sql += ` ORDER BY CASE t.priority WHEN 'critical' THEN 1 WHEN 'high' THEN 2 WHEN 'medium' THEN 3 ELSE 4 END, t.created_at DESC LIMIT $${i++} OFFSET $${i++}`;
    params.push(limit, (page-1)*limit);
    const tickets = await db(sql, params);

    return ok(res, { tickets, total, page, limit, pages: Math.ceil(total/limit) });
  } catch { return fail(res, 'Server error', 500); }
});

// GET /api/support/tickets/:id
supportRouter.get('/tickets/:id', async (req, res) => {
  try {
    const ticket = await dbOne<any>(
      `SELECT t.*,u.email as user_email,u.name as user_name,a.name as assigned_name
       FROM support_tickets t LEFT JOIN users u ON u.id=t.user_id LEFT JOIN users a ON a.id=t.assigned_to
       WHERE t.id=$1`,
      [req.params.id]
    );
    if (!ticket) return fail(res, 'Ticket not found', 404);
    const messages = await db<any>(
      `SELECT m.*,u.name as sender_name,u.role as sender_role FROM ticket_messages m LEFT JOIN users u ON u.id=m.sender_id
       WHERE m.ticket_id=$1 ORDER BY m.created_at ASC`,
      [req.params.id]
    );
    return ok(res, { ...ticket, messages });
  } catch { return fail(res, 'Server error', 500); }
});

// POST /api/support/tickets
supportRouter.post('/tickets', async (req, res) => {
  try {
    const { subject, description, priority = 'medium', category, user_id } = req.body;
    if (!subject || !description) return fail(res, 'subject and description required');
    const { v4: uuidv4 } = await import('uuid');
    const row = await dbOne<any>(
      `INSERT INTO support_tickets(id,user_id,subject,description,priority,category) VALUES($1,$2,$3,$4,$5,$6) RETURNING *`,
      [uuidv4(), user_id || null, subject, description, priority, category || null]
    );
    return ok(res, row, 201);
  } catch { return fail(res, 'Server error', 500); }
});

// PUT /api/support/tickets/:id
supportRouter.put('/tickets/:id', async (req, res) => {
  try {
    const { status, priority, assigned_to } = req.body;
    const resolvedAt = status === 'resolved' ? 'NOW()' : 'resolved_at';
    const row = await dbOne<any>(
      `UPDATE support_tickets SET
        status=COALESCE($1,status),priority=COALESCE($2,priority),
        assigned_to=COALESCE($3::uuid,assigned_to),
        resolved_at=${resolvedAt},updated_at=NOW()
       WHERE id=$4 RETURNING *`,
      [status, priority, assigned_to || null, req.params.id]
    );
    return row ? ok(res, row) : fail(res, 'Not found', 404);
  } catch (e: any) { console.error(e); return fail(res, 'Server error', 500); }
});

// POST /api/support/tickets/:id/messages
supportRouter.post('/tickets/:id/messages', async (req, res) => {
  try {
    const { message, is_internal = false } = req.body;
    if (!message) return fail(res, 'message required');
    const { v4: uuidv4 } = await import('uuid');
    const row = await dbOne<any>(
      `INSERT INTO ticket_messages(id,ticket_id,sender_id,message,is_internal) VALUES($1,$2,$3,$4,$5) RETURNING *`,
      [uuidv4(), req.params.id, (req as any).user.userId, message, is_internal]
    );
    // Update ticket updated_at
    await execute('UPDATE support_tickets SET updated_at=NOW() WHERE id=$1', [req.params.id]);
    return ok(res, row, 201);
  } catch { return fail(res, 'Server error', 500); }
});

// GET /api/support/users/:id — limited view
supportRouter.get('/users/:id', async (req, res) => {
  try {
    const user = await dbOne<any>(
      `SELECT u.id,u.email,u.name,u.phone,u.created_at,u.last_login_at,
              (SELECT COUNT(*) FROM transactions WHERE user_id=u.id) as tx_count,
              (SELECT COUNT(*) FROM support_tickets WHERE user_id=u.id) as ticket_count
       FROM users u WHERE u.id=$1`,
      [req.params.id]
    );
    if (!user) return fail(res, 'User not found', 404);
    return ok(res, user);
  } catch { return fail(res, 'Server error', 500); }
});

// GET /api/support/users — search
supportRouter.get('/users', async (req, res) => {
  try {
    const search = req.query.search as string;
    if (!search) return ok(res, []);
    const users = await db(
      `SELECT id,email,name,phone,created_at,last_login_at FROM users WHERE email ILIKE $1 OR name ILIKE $1 LIMIT 20`,
      [`%${search}%`]
    );
    return ok(res, users);
  } catch { return fail(res, 'Server error', 500); }
});

app.use('/api/support', supportRouter);

// ── CATCH-ALL ─────────────────────────────────────────────────
app.use((_req, res) => fail(res, 'Route not found', 404, 'NOT_FOUND'));

app.listen(PORT, () => {
  console.log(`[api-gateway] running on :${PORT}`);
  console.log(`  → Auth:         ${AUTH_URL}`);
  console.log(`  → Transactions: ${TRANSACTION_URL}`);
  console.log(`  → User:         ${USER_URL}`);
  console.log(`  → Analytics:    ${ANALYTICS_URL}`);
});

export default app;
