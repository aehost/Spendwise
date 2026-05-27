import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { Pool } from 'pg';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';

const app = express();
const PORT = process.env.PORT || 3003;

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false,
});

async function db<T>(sql: string, params?: unknown[]): Promise<T[]> {
  const c = await pool.connect();
  try { return (await c.query(sql, params)).rows as T[]; }
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

const ACCESS_SECRET = process.env.JWT_ACCESS_SECRET || 'dev_access_secret';

app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGINS?.split(',') || '*' }));
app.use(express.json());
app.use(rateLimit({ windowMs: 60_000, max: 300 }));

function ok<T>(res: express.Response, d: T, status = 200) { return res.status(status).json({ success: true, data: d }); }
function fail(res: express.Response, e: string, status = 400, code?: string) { return res.status(status).json({ success: false, error: e, code }); }

function auth(req: express.Request, res: express.Response, next: express.NextFunction) {
  const h = req.headers.authorization;
  if (!h?.startsWith('Bearer ')) return fail(res, 'Unauthorized', 401);
  try { (req as any).user = jwt.verify(h.slice(7), ACCESS_SECRET); next(); }
  catch { return fail(res, 'Invalid token', 401, 'TOKEN_EXPIRED'); }
}

app.use(auth);

const uid = (req: express.Request) => (req as any).user.userId as string;

// ── PROFILE ───────────────────────────────────────────────────
app.get('/profile', async (req, res) => {
  try {
    const u = await dbOne<any>('SELECT id,email,name,phone,currency_code,locale,role,sms_scan_from_ms,created_at FROM users WHERE id=$1', [uid(req)]);
    return u ? ok(res, u) : fail(res, 'Not found', 404);
  } catch { return fail(res, 'Server error', 500); }
});

app.put('/profile', async (req, res) => {
  try {
    const { name, phone, currency_code, locale } = req.body;
    const u = await dbOne<any>(
      `UPDATE users SET name=COALESCE($1,name),phone=COALESCE($2,phone),currency_code=COALESCE($3,currency_code),locale=COALESCE($4,locale),updated_at=NOW() WHERE id=$5 RETURNING id,email,name,phone,currency_code,locale`,
      [name, phone, currency_code, locale, uid(req)]
    );
    return ok(res, u);
  } catch { return fail(res, 'Server error', 500); }
});

// ── SMS SCAN TIMESTAMP ────────────────────────────────────────
app.get('/sms-scan-ts', async (req, res) => {
  try {
    const u = await dbOne<any>('SELECT sms_scan_from_ms FROM users WHERE id=$1', [uid(req)]);
    return ok(res, { sms_scan_from_ms: u?.sms_scan_from_ms || 0 });
  } catch { return fail(res, 'Server error', 500); }
});
app.put('/sms-scan-ts', async (req, res) => {
  try {
    const { sms_scan_from_ms } = req.body;
    await execute('UPDATE users SET sms_scan_from_ms=$1 WHERE id=$2', [sms_scan_from_ms, uid(req)]);
    return ok(res, { sms_scan_from_ms });
  } catch { return fail(res, 'Server error', 500); }
});

// ── BANK ACCOUNTS ─────────────────────────────────────────────
app.get('/bank-accounts', async (req, res) => {
  try {
    const rows = await db('SELECT * FROM bank_accounts WHERE user_id=$1 AND is_active=TRUE ORDER BY created_at ASC', [uid(req)]);
    return ok(res, rows);
  } catch { return fail(res, 'Server error', 500); }
});
app.post('/bank-accounts', async (req, res) => {
  try {
    const { name, last_four, balance = 0, color = '#6C63FF' } = req.body;
    if (!name) return fail(res, 'name required');
    const id = uuidv4();
    const row = await dbOne<any>(
      `INSERT INTO bank_accounts(id,user_id,name,last_four,balance,color,balance_updated_at) VALUES($1,$2,$3,$4,$5,$6,NOW()) RETURNING *`,
      [id, uid(req), name, last_four || null, balance, color]
    );
    return ok(res, row, 201);
  } catch { return fail(res, 'Server error', 500); }
});
app.put('/bank-accounts/:id', async (req, res) => {
  try {
    const { name, last_four, balance, color } = req.body;
    const row = await dbOne<any>(
      `UPDATE bank_accounts SET name=COALESCE($1,name),last_four=COALESCE($2,last_four),balance=COALESCE($3,balance),color=COALESCE($4,color),balance_updated_at=CASE WHEN $3 IS NOT NULL THEN NOW() ELSE balance_updated_at END,updated_at=NOW() WHERE id=$5 AND user_id=$6 RETURNING *`,
      [name, last_four, balance, color, req.params.id, uid(req)]
    );
    return row ? ok(res, row) : fail(res, 'Not found', 404);
  } catch { return fail(res, 'Server error', 500); }
});
app.delete('/bank-accounts/:id', async (req, res) => {
  try {
    const n = await execute('UPDATE bank_accounts SET is_active=FALSE WHERE id=$1 AND user_id=$2', [req.params.id, uid(req)]);
    return n ? ok(res, { deleted: true }) : fail(res, 'Not found', 404);
  } catch { return fail(res, 'Server error', 500); }
});

// ── CREDIT CARDS ──────────────────────────────────────────────
app.get('/credit-cards', async (req, res) => {
  try {
    return ok(res, await db('SELECT * FROM credit_cards WHERE user_id=$1 AND is_active=TRUE ORDER BY created_at ASC', [uid(req)]));
  } catch { return fail(res, 'Server error', 500); }
});
app.post('/credit-cards', async (req, res) => {
  try {
    const { name, credit_limit = 0, outstanding = 0, due_day = 1, min_due = 0, color = '#EC4899' } = req.body;
    if (!name) return fail(res, 'name required');
    const row = await dbOne<any>(
      `INSERT INTO credit_cards(id,user_id,name,credit_limit,outstanding,due_day,min_due,color) VALUES($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`,
      [uuidv4(), uid(req), name, credit_limit, outstanding, due_day, min_due, color]
    );
    return ok(res, row, 201);
  } catch { return fail(res, 'Server error', 500); }
});
app.put('/credit-cards/:id', async (req, res) => {
  try {
    const { name, credit_limit, outstanding, due_day, min_due, color } = req.body;
    const row = await dbOne<any>(
      `UPDATE credit_cards SET name=COALESCE($1,name),credit_limit=COALESCE($2,credit_limit),outstanding=COALESCE($3,outstanding),due_day=COALESCE($4,due_day),min_due=COALESCE($5,min_due),color=COALESCE($6,color),updated_at=NOW() WHERE id=$7 AND user_id=$8 RETURNING *`,
      [name, credit_limit, outstanding, due_day, min_due, color, req.params.id, uid(req)]
    );
    return row ? ok(res, row) : fail(res, 'Not found', 404);
  } catch { return fail(res, 'Server error', 500); }
});
app.delete('/credit-cards/:id', async (req, res) => {
  try {
    const n = await execute('UPDATE credit_cards SET is_active=FALSE WHERE id=$1 AND user_id=$2', [req.params.id, uid(req)]);
    return n ? ok(res, { deleted: true }) : fail(res, 'Not found', 404);
  } catch { return fail(res, 'Server error', 500); }
});

// ── LOANS ─────────────────────────────────────────────────────
app.get('/loans', async (req, res) => {
  try {
    return ok(res, await db('SELECT * FROM loans WHERE user_id=$1 AND is_active=TRUE ORDER BY interest_rate DESC', [uid(req)]));
  } catch { return fail(res, 'Server error', 500); }
});
app.post('/loans', async (req, res) => {
  try {
    const { name, emi_amount = 0, interest_rate = 0, outstanding = 0, months_remaining = 0, color = '#EF4444' } = req.body;
    if (!name) return fail(res, 'name required');
    const row = await dbOne<any>(
      `INSERT INTO loans(id,user_id,name,emi_amount,interest_rate,outstanding,months_remaining,color) VALUES($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`,
      [uuidv4(), uid(req), name, emi_amount, interest_rate, outstanding, months_remaining, color]
    );
    return ok(res, row, 201);
  } catch { return fail(res, 'Server error', 500); }
});
app.put('/loans/:id', async (req, res) => {
  try {
    const { name, emi_amount, interest_rate, outstanding, months_remaining, color } = req.body;
    const row = await dbOne<any>(
      `UPDATE loans SET name=COALESCE($1,name),emi_amount=COALESCE($2,emi_amount),interest_rate=COALESCE($3,interest_rate),outstanding=COALESCE($4,outstanding),months_remaining=COALESCE($5,months_remaining),color=COALESCE($6,color),updated_at=NOW() WHERE id=$7 AND user_id=$8 RETURNING *`,
      [name, emi_amount, interest_rate, outstanding, months_remaining, color, req.params.id, uid(req)]
    );
    return row ? ok(res, row) : fail(res, 'Not found', 404);
  } catch { return fail(res, 'Server error', 500); }
});
app.delete('/loans/:id', async (req, res) => {
  try {
    const n = await execute('UPDATE loans SET is_active=FALSE WHERE id=$1 AND user_id=$2', [req.params.id, uid(req)]);
    return n ? ok(res, { deleted: true }) : fail(res, 'Not found', 404);
  } catch { return fail(res, 'Server error', 500); }
});

// ── SALARY ────────────────────────────────────────────────────
app.get('/salary', async (req, res) => {
  try {
    const cfg = await dbOne<any>('SELECT * FROM salary_config WHERE user_id=$1', [uid(req)]) || { amount: 0, expected_day: 1 };
    const history = await db('SELECT * FROM salary_history WHERE user_id=$1 ORDER BY received_date DESC LIMIT 12', [uid(req)]);
    return ok(res, { ...cfg, history });
  } catch { return fail(res, 'Server error', 500); }
});
app.put('/salary', async (req, res) => {
  try {
    const { amount, expected_day } = req.body;
    await execute(
      `INSERT INTO salary_config(user_id,amount,expected_day,updated_at) VALUES($1,$2,$3,NOW())
       ON CONFLICT(user_id) DO UPDATE SET amount=COALESCE($2,salary_config.amount),expected_day=COALESCE($3,salary_config.expected_day),updated_at=NOW()`,
      [uid(req), amount, expected_day]
    );
    return ok(res, { amount, expected_day });
  } catch { return fail(res, 'Server error', 500); }
});
app.post('/salary/received', async (req, res) => {
  try {
    const { amount, received_date, note = '' } = req.body;
    if (!amount || !received_date) return fail(res, 'amount and received_date required');
    const row = await dbOne<any>(
      `INSERT INTO salary_history(id,user_id,amount,received_date,note) VALUES($1,$2,$3,$4,$5) RETURNING *`,
      [uuidv4(), uid(req), amount, received_date, note]
    );
    return ok(res, row, 201);
  } catch { return fail(res, 'Server error', 500); }
});

// ── INVESTMENTS ───────────────────────────────────────────────
app.get('/investments', async (req, res) => {
  try {
    return ok(res, await db('SELECT * FROM investments WHERE user_id=$1 AND is_active=TRUE ORDER BY created_at ASC', [uid(req)]));
  } catch { return fail(res, 'Server error', 500); }
});
app.post('/investments', async (req, res) => {
  try {
    const { name, monthly_amount = 0, current_balance = 0 } = req.body;
    if (!name) return fail(res, 'name required');
    const row = await dbOne<any>(
      `INSERT INTO investments(id,user_id,name,monthly_amount,current_balance) VALUES($1,$2,$3,$4,$5) RETURNING *`,
      [uuidv4(), uid(req), name, monthly_amount, current_balance]
    );
    return ok(res, row, 201);
  } catch { return fail(res, 'Server error', 500); }
});
app.put('/investments/:id', async (req, res) => {
  try {
    const { name, monthly_amount, current_balance } = req.body;
    const row = await dbOne<any>(
      `UPDATE investments SET name=COALESCE($1,name),monthly_amount=COALESCE($2,monthly_amount),current_balance=COALESCE($3,current_balance),updated_at=NOW() WHERE id=$4 AND user_id=$5 RETURNING *`,
      [name, monthly_amount, current_balance, req.params.id, uid(req)]
    );
    return row ? ok(res, row) : fail(res, 'Not found', 404);
  } catch { return fail(res, 'Server error', 500); }
});
app.delete('/investments/:id', async (req, res) => {
  try {
    const n = await execute('UPDATE investments SET is_active=FALSE WHERE id=$1 AND user_id=$2', [req.params.id, uid(req)]);
    return n ? ok(res, { deleted: true }) : fail(res, 'Not found', 404);
  } catch { return fail(res, 'Server error', 500); }
});

// ── MANDATORY BILLS ───────────────────────────────────────────
app.get('/bills', async (req, res) => {
  try {
    return ok(res, await db('SELECT * FROM mandatory_bills WHERE user_id=$1 ORDER BY due_day ASC', [uid(req)]));
  } catch { return fail(res, 'Server error', 500); }
});
app.post('/bills', async (req, res) => {
  try {
    const { name, icon = '💡', amount = 0, due_day = 1 } = req.body;
    if (!name) return fail(res, 'name required');
    const row = await dbOne<any>(
      `INSERT INTO mandatory_bills(id,user_id,name,icon,amount,due_day) VALUES($1,$2,$3,$4,$5,$6) RETURNING *`,
      [uuidv4(), uid(req), name, icon, amount, due_day]
    );
    return ok(res, row, 201);
  } catch { return fail(res, 'Server error', 500); }
});
app.put('/bills/:id', async (req, res) => {
  try {
    const { name, icon, amount, due_day } = req.body;
    const row = await dbOne<any>(
      `UPDATE mandatory_bills SET name=COALESCE($1,name),icon=COALESCE($2,icon),amount=COALESCE($3,amount),due_day=COALESCE($4,due_day),updated_at=NOW() WHERE id=$5 AND user_id=$6 RETURNING *`,
      [name, icon, amount, due_day, req.params.id, uid(req)]
    );
    return row ? ok(res, row) : fail(res, 'Not found', 404);
  } catch { return fail(res, 'Server error', 500); }
});
app.delete('/bills/:id', async (req, res) => {
  try {
    const n = await execute('DELETE FROM mandatory_bills WHERE id=$1 AND user_id=$2', [req.params.id, uid(req)]);
    return n ? ok(res, { deleted: true }) : fail(res, 'Not found', 404);
  } catch { return fail(res, 'Server error', 500); }
});
app.post('/bills/:id/pay', async (req, res) => {
  try {
    const row = await dbOne<any>(
      `UPDATE mandatory_bills SET paid_this_month=TRUE,paid_at=NOW() WHERE id=$1 AND user_id=$2 RETURNING *`,
      [req.params.id, uid(req)]
    );
    return row ? ok(res, row) : fail(res, 'Not found', 404);
  } catch { return fail(res, 'Server error', 500); }
});

// ── MONTHLY BUDGETS ───────────────────────────────────────────
app.get('/budgets', async (req, res) => {
  try {
    const now = new Date();
    const month = parseInt(String(req.query.month || now.getMonth() + 1));
    const year  = parseInt(String(req.query.year  || now.getFullYear()));
    const rows = await db('SELECT * FROM monthly_budgets WHERE user_id=$1 AND month=$2 AND year=$3', [uid(req), month, year]);
    return ok(res, { month, year, budgets: rows });
  } catch { return fail(res, 'Server error', 500); }
});
app.put('/budgets', async (req, res) => {
  try {
    const { budgets, month, year } = req.body; // budgets: [{category_slug, amount}]
    if (!Array.isArray(budgets)) return fail(res, 'budgets array required');
    const now = new Date();
    const m = month || now.getMonth() + 1;
    const y = year  || now.getFullYear();
    for (const b of budgets) {
      await execute(
        `INSERT INTO monthly_budgets(id,user_id,category_slug,amount,month,year) VALUES($1,$2,$3,$4,$5,$6)
         ON CONFLICT(user_id,category_slug,month,year) DO UPDATE SET amount=$4,updated_at=NOW()`,
        [uuidv4(), uid(req), b.category_slug, b.amount, m, y]
      );
    }
    return ok(res, { month: m, year: y, saved: budgets.length });
  } catch { return fail(res, 'Server error', 500); }
});

app.get('/health', (_req, res) => res.json({ service: 'user', status: 'ok', ts: new Date() }));

app.listen(PORT, () => console.log(`[user-service] running on :${PORT}`));
export default app;
