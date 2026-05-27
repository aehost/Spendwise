import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { Pool } from 'pg';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import { matchMerchant } from './merchantDb';

const app = express();
const PORT = process.env.PORT || 3002;

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
app.use(express.json({ limit: '5mb' }));
app.use(rateLimit({ windowMs: 60_000, max: 200 }));

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
    (req as any).user = jwt.verify(h.slice(7), ACCESS_SECRET) as { userId: string; email: string; role: string };
    next();
  } catch { return fail(res, 'Invalid or expired token', 401, 'TOKEN_EXPIRED'); }
}

// Health check must be before auth middleware (public route)
app.get('/health', (_req, res) => res.json({ service: 'transactions', status: 'ok', ts: new Date() }));

app.use(authMiddleware);

// ── GET /transactions ─────────────────────────────────────────
app.get('/transactions', async (req, res) => {
  try {
    const userId = (req as any).user.userId;
    const { category, is_pending, is_credit, start, end, bank_account_id, search, page = '1', limit = '50' } = req.query;

    let sql = `SELECT t.*, ba.name as account_name, ba.color as account_color
               FROM transactions t
               LEFT JOIN bank_accounts ba ON ba.id = t.bank_account_id
               WHERE t.user_id=$1`;
    const params: unknown[] = [userId];
    let i = 2;

    if (category)        { sql += ` AND t.category_slug=$${i++}`;    params.push(category); }
    if (is_pending)      { sql += ` AND t.is_pending=$${i++}`;        params.push(is_pending === 'true'); }
    if (is_credit)       { sql += ` AND t.is_credit=$${i++}`;         params.push(is_credit === 'true'); }
    if (bank_account_id) { sql += ` AND t.bank_account_id=$${i++}`;   params.push(bank_account_id); }
    if (start)           { sql += ` AND t.transaction_date>=$${i++}`; params.push(start); }
    if (end)             { sql += ` AND t.transaction_date<=$${i++}`; params.push(end); }
    if (search)          { sql += ` AND (t.merchant ILIKE $${i++} OR t.note ILIKE $${i++})`; const s = `%${search}%`; params.push(s, s); i++; }

    const pageNum  = parseInt(String(page));
    const pageSize = Math.min(parseInt(String(limit)), 200);
    const offset   = (pageNum - 1) * pageSize;

    // Count total
    const countSql = sql.replace(/SELECT t\.\*, ba\.name as account_name, ba\.color as account_color/, 'SELECT COUNT(*)');
    const countResult = await db<{ count: string }>(countSql, params);
    const total = parseInt(countResult[0]?.count || '0');

    sql += ` ORDER BY t.transaction_date DESC, t.created_at DESC LIMIT $${i++} OFFSET $${i++}`;
    params.push(pageSize, offset);

    const rows = await db(sql, params);
    return ok(res, { transactions: rows, total, page: pageNum, limit: pageSize, pages: Math.ceil(total / pageSize) });
  } catch (e: any) {
    console.error('[GET /transactions]', e.message);
    return fail(res, 'Server error', 500);
  }
});

// ── POST /transactions ────────────────────────────────────────
app.post('/transactions', async (req, res) => {
  try {
    const userId = (req as any).user.userId;
    const { amount, merchant = 'Unknown', category_slug = 'other', transaction_date,
            note = '', is_waste = false, is_pending = false, is_credit = false,
            loan_id, credit_card_id, bank_account_id, contact_name, sms_raw, sms_id } = req.body;

    if (!amount || !transaction_date) return fail(res, 'amount and transaction_date required');

    // Auto-classify merchant if category is missing or generic
    let effectiveCategory = category_slug;
    if (!effectiveCategory || effectiveCategory === 'other') {
      const classified = matchMerchant(merchant, sms_raw);
      effectiveCategory = classified.categorySlug;
    }

    const id = uuidv4();
    const rows = await db<any>(
      `INSERT INTO transactions
        (id,user_id,amount,merchant,category_slug,transaction_date,note,is_waste,is_pending,is_credit,
         loan_id,credit_card_id,bank_account_id,contact_name,sms_raw,sms_id)
       VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16)
       RETURNING *`,
      [id, userId, amount, merchant, effectiveCategory, transaction_date, note, is_waste, is_pending, is_credit,
       loan_id || null, credit_card_id || null, bank_account_id || null, contact_name || null, sms_raw || null, sms_id || null]
    );

    await db(
      `INSERT INTO audit_log(user_id,action,resource_type,resource_id) VALUES($1,'CREATE','transaction',$2)`,
      [userId, id]
    );

    return ok(res, rows[0], 201);
  } catch (e: any) {
    console.error('[POST /transactions]', e.message);
    return fail(res, 'Server error', 500);
  }
});

// ── PUT /transactions/:id ─────────────────────────────────────
app.put('/transactions/:id', async (req, res) => {
  try {
    const userId = (req as any).user.userId;
    const { id } = req.params;
    const { merchant, category_slug, note, is_waste, is_pending, loan_id, credit_card_id, bank_account_id, contact_name, amount, transaction_date } = req.body;

    const existing = await dbOne<any>('SELECT * FROM transactions WHERE id=$1 AND user_id=$2', [id, userId]);
    if (!existing) return fail(res, 'Transaction not found', 404);

    const rows = await db<any>(
      `UPDATE transactions SET
        merchant=COALESCE($1,merchant),
        category_slug=COALESCE($2,category_slug),
        note=COALESCE($3,note),
        is_waste=COALESCE($4,is_waste),
        is_pending=COALESCE($5,is_pending),
        loan_id=COALESCE($6::uuid,loan_id),
        credit_card_id=COALESCE($7::uuid,credit_card_id),
        bank_account_id=COALESCE($8::uuid,bank_account_id),
        contact_name=COALESCE($9,contact_name),
        amount=COALESCE($10,amount),
        transaction_date=COALESCE($11,transaction_date),
        updated_at=NOW()
       WHERE id=$12 AND user_id=$13
       RETURNING *`,
      [merchant, category_slug, note, is_waste, is_pending,
       loan_id || null, credit_card_id || null, bank_account_id || null, contact_name || null,
       amount, transaction_date, id, userId]
    );

    return ok(res, rows[0]);
  } catch (e: any) {
    console.error('[PUT /transactions/:id]', e.message);
    return fail(res, 'Server error', 500);
  }
});

// ── DELETE /transactions/:id ──────────────────────────────────
app.delete('/transactions/:id', async (req, res) => {
  try {
    const userId = (req as any).user.userId;
    const { id } = req.params;
    const n = await execute('DELETE FROM transactions WHERE id=$1 AND user_id=$2', [id, userId]);
    if (!n) return fail(res, 'Transaction not found', 404);
    return ok(res, { deleted: true });
  } catch (e: any) {
    return fail(res, 'Server error', 500);
  }
});

// ── POST /transactions/batch (SMS import) ─────────────────────
app.post('/transactions/batch', async (req, res) => {
  try {
    const userId = (req as any).user.userId;
    const { transactions } = req.body;
    if (!Array.isArray(transactions) || !transactions.length) return fail(res, 'transactions array required');

    // Fetch existing sms_ids and sms_raw for deduplication
    const existingSmsIds = new Set(
      (await db<{ sms_id: string }>('SELECT sms_id FROM transactions WHERE user_id=$1 AND sms_id IS NOT NULL', [userId]))
        .map(r => r.sms_id)
    );
    const existingRaws = new Set(
      (await db<{ sms_raw: string }>('SELECT LEFT(sms_raw,200) as sms_raw FROM transactions WHERE user_id=$1 AND sms_raw IS NOT NULL', [userId]))
        .map(r => r.sms_raw)
    );

    const toInsert = transactions.filter((t: any) => {
      if (t.sms_id && existingSmsIds.has(String(t.sms_id))) return false;
      if (t.sms_raw && existingRaws.has(t.sms_raw.substring(0, 200))) return false;
      return true;
    });

    let inserted = 0;
    for (const t of toInsert) {
      const id = uuidv4();
      // Auto-classify if category is missing or generic
      let cat = t.category_slug;
      if (!cat || cat === 'other') {
        cat = matchMerchant(t.merchant || 'Unknown', t.sms_raw).categorySlug;
      }
      await db(
        `INSERT INTO transactions(id,user_id,amount,merchant,category_slug,transaction_date,note,is_waste,
          is_pending,is_credit,bank_account_id,sms_raw,sms_id)
         VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)
         ON CONFLICT DO NOTHING`,
        [id, userId, t.amount, t.merchant || 'Unknown', cat,
         t.transaction_date, t.note || '', t.is_waste || false, t.is_pending ?? true,
         t.is_credit || false, t.bank_account_id || null,
         t.sms_raw ? t.sms_raw.substring(0, 500) : null, t.sms_id ? String(t.sms_id) : null]
      );
      inserted++;
    }

    return ok(res, { inserted, skipped: transactions.length - inserted });
  } catch (e: any) {
    console.error('[POST /transactions/batch]', e.message);
    return fail(res, 'Server error', 500);
  }
});

// ── GET /transactions/summary ─────────────────────────────────
app.get('/transactions/summary', async (req, res) => {
  try {
    const userId = (req as any).user.userId;
    const now = new Date();
    const month = parseInt(String(req.query.month || now.getMonth() + 1));
    const year  = parseInt(String(req.query.year  || now.getFullYear()));

    const startDate = `${year}-${String(month).padStart(2, '0')}-01`;
    const endDate   = new Date(year, month, 0).toISOString().split('T')[0];

    const [totals] = await db<any>(
      `SELECT
        SUM(CASE WHEN is_credit=FALSE THEN amount ELSE 0 END) as total_debit,
        SUM(CASE WHEN is_credit=TRUE  THEN amount ELSE 0 END) as total_credit,
        COUNT(*) FILTER (WHERE is_pending=TRUE)               as pending_count
       FROM transactions
       WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3`,
      [userId, startDate, endDate]
    );

    const byCategory = await db<any>(
      `SELECT category_slug, SUM(amount) as total, COUNT(*) as count
       FROM transactions
       WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3 AND is_credit=FALSE
       GROUP BY category_slug
       ORDER BY total DESC`,
      [userId, startDate, endDate]
    );

    return ok(res, { ...totals, by_category: byCategory, month, year, start_date: startDate, end_date: endDate });
  } catch (e: any) {
    return fail(res, 'Server error', 500);
  }
});

app.listen(PORT, () => console.log(`[transaction-service] running on :${PORT}`));
export default app;
