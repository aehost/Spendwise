import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { Pool } from 'pg';
import jwt from 'jsonwebtoken';

const app = express();
const PORT = process.env.PORT || 3004;

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

const ACCESS_SECRET = process.env.JWT_ACCESS_SECRET || 'dev_access_secret';

app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGINS?.split(',') || '*' }));
app.use(express.json());
app.use(rateLimit({ windowMs: 60_000, max: 200 }));

function ok<T>(res: express.Response, d: T) { return res.json({ success: true, data: d }); }
function fail(res: express.Response, e: string, s = 400) { return res.status(s).json({ success: false, error: e }); }

function auth(req: express.Request, res: express.Response, next: express.NextFunction) {
  const h = req.headers.authorization;
  if (!h?.startsWith('Bearer ')) return fail(res, 'Unauthorized', 401);
  try { (req as any).user = jwt.verify(h.slice(7), ACCESS_SECRET); next(); }
  catch { return fail(res, 'Invalid token', 401); }
}

app.use(auth);
const uid = (req: express.Request) => (req as any).user.userId as string;

// ── GET /analytics/dashboard ──────────────────────────────────
app.get('/analytics/dashboard', async (req, res) => {
  try {
    const userId = uid(req);
    const now = new Date();
    const month = now.getMonth() + 1;
    const year  = now.getFullYear();
    const startDate = `${year}-${String(month).padStart(2,'0')}-01`;
    const endDate   = new Date(year, month, 0).toISOString().split('T')[0];

    // Salary
    const salary = await dbOne<any>('SELECT amount,expected_day FROM salary_config WHERE user_id=$1', [userId]);

    // Monthly spending totals
    const spending = await dbOne<any>(
      `SELECT
        COALESCE(SUM(CASE WHEN is_credit=FALSE AND category_slug != 'income' THEN amount ELSE 0 END),0) as total_spent,
        COALESCE(SUM(CASE WHEN is_credit=TRUE THEN amount ELSE 0 END),0) as total_credit,
        COUNT(*) FILTER (WHERE is_pending=TRUE) as pending_count
       FROM transactions WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3`,
      [userId, startDate, endDate]
    );

    // EMI total
    const emiTotal = await dbOne<any>('SELECT COALESCE(SUM(emi_amount),0) as emi_total FROM loans WHERE user_id=$1 AND is_active=TRUE', [userId]);

    // Bank balance total
    const bankBal = await dbOne<any>('SELECT COALESCE(SUM(balance),0) as total FROM bank_accounts WHERE user_id=$1 AND is_active=TRUE', [userId]);

    // CC outstanding total
    const ccTotal = await dbOne<any>('SELECT COALESCE(SUM(outstanding),0) as total FROM credit_cards WHERE user_id=$1 AND is_active=TRUE', [userId]);

    // Category breakdown
    const byCategory = await db<any>(
      `SELECT category_slug, SUM(amount) as total FROM transactions
       WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3 AND is_credit=FALSE
       GROUP BY category_slug ORDER BY total DESC`,
      [userId, startDate, endDate]
    );

    // Budgets for alert calculation
    const budgets = await db<any>('SELECT * FROM monthly_budgets WHERE user_id=$1 AND month=$2 AND year=$3', [userId, month, year]);

    // Budget alerts
    const budgetAlerts = budgets
      .map((b: any) => {
        const spent = byCategory.find((c: any) => c.category_slug === b.category_slug);
        const pct = b.amount > 0 ? (parseFloat(spent?.total || 0) / parseFloat(b.amount)) * 100 : 0;
        return { category_slug: b.category_slug, budget: b.amount, spent: parseFloat(spent?.total || 0), pct: Math.round(pct) };
      })
      .filter((a: any) => a.pct >= 80);

    // Burn rate
    const daysInMonth = new Date(year, month, 0).getDate();
    const daysPassed  = now.getDate();
    const burnRate    = daysPassed > 0 ? parseFloat(spending?.total_spent || 0) / daysPassed : 0;
    const projected   = burnRate * daysInMonth;

    const salaryAmt   = parseFloat(salary?.amount || 0);
    const totalSpent  = parseFloat(spending?.total_spent || 0);
    const emiTotalAmt = parseFloat(emiTotal?.emi_total || 0);
    const emiBurdenPct = salaryAmt > 0 ? Math.round((emiTotalAmt / salaryAmt) * 100) : 0;
    const savingsAmt  = salaryAmt - totalSpent;
    const savingsRate = salaryAmt > 0 ? Math.round((savingsAmt / salaryAmt) * 100) : 0;

    return ok(res, {
      month, year,
      salary: salary || { amount: 0, expected_day: 1 },
      bank_balance: parseFloat(bankBal?.total || 0),
      cc_outstanding: parseFloat(ccTotal?.total || 0),
      total_spent: totalSpent,
      total_credit: parseFloat(spending?.total_credit || 0),
      pending_count: parseInt(spending?.pending_count || 0),
      emi_total: emiTotalAmt,
      emi_burden_pct: emiBurdenPct,
      savings: savingsAmt,
      savings_rate: savingsRate,
      burn_rate: Math.round(burnRate),
      projected_spend: Math.round(projected),
      budget_alerts: budgetAlerts,
      by_category: byCategory,
    });
  } catch (e: any) {
    console.error('[dashboard]', e.message);
    return fail(res, 'Server error', 500);
  }
});

// ── GET /analytics/monthly ────────────────────────────────────
app.get('/analytics/monthly', async (req, res) => {
  try {
    const userId = uid(req);
    const now = new Date();
    const month = parseInt(String(req.query.month || now.getMonth() + 1));
    const year  = parseInt(String(req.query.year  || now.getFullYear()));
    const startDate = `${year}-${String(month).padStart(2,'0')}-01`;
    const endDate   = new Date(year, month, 0).toISOString().split('T')[0];

    const [totals, byCategory, transactions, salary] = await Promise.all([
      dbOne<any>(
        `SELECT COALESCE(SUM(CASE WHEN is_credit=FALSE THEN amount ELSE 0 END),0) as total_debit,
                COALESCE(SUM(CASE WHEN is_credit=TRUE THEN amount ELSE 0 END),0) as total_credit,
                COALESCE(SUM(CASE WHEN is_waste=TRUE THEN amount ELSE 0 END),0) as total_waste
         FROM transactions WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3`,
        [userId, startDate, endDate]
      ),
      db<any>(
        `SELECT category_slug, SUM(amount) as total, COUNT(*) as count FROM transactions
         WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3 AND is_credit=FALSE
         GROUP BY category_slug ORDER BY total DESC`,
        [userId, startDate, endDate]
      ),
      db<any>(
        `SELECT * FROM transactions WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3 ORDER BY transaction_date DESC`,
        [userId, startDate, endDate]
      ),
      dbOne<any>('SELECT * FROM salary_history WHERE user_id=$1 AND received_date BETWEEN $2 AND $3 LIMIT 1', [userId, startDate, endDate]),
    ]);

    return ok(res, { month, year, ...totals, by_category: byCategory, transactions, salary_received: salary });
  } catch { return fail(res, 'Server error', 500); }
});

// ── GET /analytics/trend ──────────────────────────────────────
app.get('/analytics/trend', async (req, res) => {
  try {
    const userId = uid(req);
    const months = await db<any>(
      `SELECT TO_CHAR(transaction_date,'YYYY-MM') as month,
              SUM(CASE WHEN is_credit=FALSE THEN amount ELSE 0 END) as spent,
              SUM(CASE WHEN is_credit=TRUE  THEN amount ELSE 0 END) as income
       FROM transactions WHERE user_id=$1 AND transaction_date >= NOW() - INTERVAL '6 months'
       GROUP BY TO_CHAR(transaction_date,'YYYY-MM') ORDER BY month ASC`,
      [userId]
    );
    return ok(res, months);
  } catch { return fail(res, 'Server error', 500); }
});

// ── GET /analytics/categories ─────────────────────────────────
app.get('/analytics/categories', async (req, res) => {
  try {
    const userId = uid(req);
    const now = new Date();
    const month = parseInt(String(req.query.month || now.getMonth() + 1));
    const year  = parseInt(String(req.query.year  || now.getFullYear()));
    const startDate = `${year}-${String(month).padStart(2,'0')}-01`;
    const endDate   = new Date(year, month, 0).toISOString().split('T')[0];

    const [spending, budgets] = await Promise.all([
      db<any>(
        `SELECT category_slug, SUM(amount) as spent FROM transactions
         WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3 AND is_credit=FALSE
         GROUP BY category_slug`,
        [userId, startDate, endDate]
      ),
      db<any>('SELECT * FROM monthly_budgets WHERE user_id=$1 AND month=$2 AND year=$3', [userId, month, year]),
    ]);

    const categories = [
      'food','fuel','shopping','bills','emi','entertainment','health','travel',
      'family','investment','income','savings','waste','other'
    ].map(slug => {
      const s = spending.find((x: any) => x.category_slug === slug);
      const b = budgets.find((x: any) => x.category_slug === slug);
      const spent = parseFloat(s?.spent || 0);
      const budget = parseFloat(b?.amount || 0);
      return { category_slug: slug, spent, budget, pct: budget > 0 ? Math.round((spent/budget)*100) : null };
    });

    return ok(res, { month, year, categories });
  } catch { return fail(res, 'Server error', 500); }
});

app.get('/health', (_req, res) => res.json({ service: 'analytics', status: 'ok' }));

app.listen(PORT, () => console.log(`[analytics-service] running on :${PORT}`));
export default app;
