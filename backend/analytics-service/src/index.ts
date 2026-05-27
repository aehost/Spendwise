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
  ssl: process.env.DB_SSL === 'true' || process.env.NODE_ENV === 'production'
    ? { rejectUnauthorized: false }
    : false,
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

// Health check must be before auth middleware (public route)
app.get('/health', (_req, res) => res.json({ service: 'analytics', status: 'ok' }));

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

// ── GET /analytics/intelligence ──────────────────────────────
// Returns: recurring bill suggestions, smart insights, cash flow
// forecast, savings opportunities.  Powers the AI layer on Android.
app.get('/analytics/intelligence', async (req, res) => {
  try {
    const userId = uid(req);
    const now = new Date();
    const month = now.getMonth() + 1;
    const year  = now.getFullYear();
    const day   = now.getDate();
    const daysInMonth = new Date(year, month, 0).getDate();

    // ── 1. Recurring transaction detection ──────────────────────
    // Find merchants charged 2+ times in 90 days with <15% amount variance
    // and a regular interval (weekly / biweekly / monthly / quarterly)
    const recurringRaw = await db<any>(`
      WITH intervals AS (
        SELECT merchant, category_slug, amount, transaction_date,
          LAG(transaction_date) OVER (PARTITION BY merchant ORDER BY transaction_date) AS prev_date
        FROM transactions
        WHERE user_id=$1 AND is_credit=FALSE
          AND transaction_date >= NOW() - INTERVAL '90 days'
          AND amount > 10
      ),
      stats AS (
        SELECT merchant, category_slug,
          COUNT(*)::int                                   AS occurrences,
          ROUND(AVG(amount)::numeric, 2)                  AS avg_amount,
          ROUND(COALESCE(STDDEV(amount),0)::numeric, 2)   AS stddev_amount,
          ROUND(AVG(CASE WHEN prev_date IS NOT NULL
            THEN (transaction_date - prev_date) END)::numeric,1) AS avg_interval_days,
          MAX(transaction_date)                           AS last_seen
        FROM intervals
        GROUP BY merchant, category_slug
        HAVING COUNT(*) >= 2
      )
      SELECT * FROM stats
      WHERE avg_interval_days IS NOT NULL
        AND COALESCE(stddev_amount,0) / NULLIF(avg_amount,0) < 0.15
      ORDER BY occurrences DESC, avg_amount DESC
    `, [userId]);

    const recurring = recurringRaw.map((r: any) => {
      const interval = parseFloat(r.avg_interval_days);
      let cycle = 'unknown';
      if      (interval >=  6 && interval <=  8)  cycle = 'weekly';
      else if (interval >= 13 && interval <= 16)  cycle = 'biweekly';
      else if (interval >= 25 && interval <= 35)  cycle = 'monthly';
      else if (interval >= 85 && interval <= 95)  cycle = 'quarterly';
      else if (interval >= 350 && interval <= 380) cycle = 'annual';

      // Estimate next due day from last_seen + average interval
      let dueDayEstimate: number | null = null;
      if (cycle !== 'unknown' && r.last_seen) {
        const addDays = cycle === 'weekly' ? 7 : cycle === 'biweekly' ? 14 :
                        cycle === 'monthly' ? 30 : cycle === 'quarterly' ? 91 : 365;
        const next = new Date(new Date(r.last_seen).getTime() + addDays * 86400000);
        dueDayEstimate = next.getDate();
      }
      return {
        merchant: r.merchant,
        category_slug: r.category_slug,
        avg_amount: parseFloat(r.avg_amount),
        occurrences: r.occurrences,
        cycle,
        avg_interval_days: parseFloat(r.avg_interval_days),
        last_seen: r.last_seen,
        due_day_estimate: dueDayEstimate,
        confidence: Math.min(95, 50 + r.occurrences * 10),
      };
    }).filter((r: any) => r.cycle !== 'unknown');

    // ── 2. Smart insights ────────────────────────────────────────
    const [thisMonthRow, lastMonthRow, salaryRow, topCatRow] = await Promise.all([
      dbOne<any>(`SELECT
        COALESCE(SUM(CASE WHEN is_credit=FALSE THEN amount ELSE 0 END),0) as spent,
        COALESCE(SUM(CASE WHEN is_credit=TRUE  THEN amount ELSE 0 END),0) as income
        FROM transactions WHERE user_id=$1
        AND DATE_TRUNC('month',transaction_date)=DATE_TRUNC('month',CURRENT_DATE)`, [userId]),
      dbOne<any>(`SELECT COALESCE(SUM(CASE WHEN is_credit=FALSE THEN amount ELSE 0 END),0) as spent
        FROM transactions WHERE user_id=$1
        AND DATE_TRUNC('month',transaction_date)=DATE_TRUNC('month',CURRENT_DATE-INTERVAL '1 month')`, [userId]),
      dbOne<any>('SELECT amount FROM salary_config WHERE user_id=$1', [userId]),
      dbOne<any>(`SELECT category_slug,SUM(amount) as total FROM transactions
        WHERE user_id=$1 AND is_credit=FALSE
          AND DATE_TRUNC('month',transaction_date)=DATE_TRUNC('month',CURRENT_DATE)
        GROUP BY category_slug ORDER BY total DESC LIMIT 1`, [userId]),
    ]);

    const thisSpent  = parseFloat(thisMonthRow?.spent  || 0);
    const lastSpent  = parseFloat(lastMonthRow?.spent  || 0);
    const salaryAmt  = parseFloat(salaryRow?.amount    || 0);
    const burnRate   = day > 0 ? thisSpent / day : 0;
    const projected  = Math.round(burnRate * daysInMonth);

    const insights: Array<{ type: string; message: string; action?: string }> = [];

    if (lastSpent > 0) {
      const pctChange = ((thisSpent - lastSpent) / lastSpent) * 100;
      if (pctChange > 20)
        insights.push({ type: 'warning', message: `Spending is up ${Math.round(pctChange)}% vs last month`, action: 'review_spending' });
      else if (pctChange < -10)
        insights.push({ type: 'success', message: `Great! Spending is down ${Math.round(Math.abs(pctChange))}% vs last month` });
    }
    if (salaryAmt > 0 && projected > salaryAmt)
      insights.push({ type: 'alert', message: `At current pace you will overspend by ₹${(projected - salaryAmt).toLocaleString('en-IN')} this month`, action: 'set_budget' });
    if (topCatRow && salaryAmt > 0) {
      const pct = Math.round((parseFloat(topCatRow.total) / salaryAmt) * 100);
      if (pct > 30)
        insights.push({ type: 'tip', message: `${topCatRow.category_slug} is ${pct}% of your salary — consider a budget limit`, action: 'set_category_budget' });
    }
    if (recurring.length > 0)
      insights.push({ type: 'info', message: `${recurring.length} recurring payment${recurring.length > 1 ? 's' : ''} detected and auto-added to bills`, action: 'view_bills' });

    // ── 3. Cash-flow forecast ────────────────────────────────────
    const [billsRow, loansRow] = await Promise.all([
      dbOne<any>(`SELECT COALESCE(SUM(amount),0) as total FROM mandatory_bills
        WHERE user_id=$1 AND is_active=TRUE AND is_paid_this_month=FALSE`, [userId]),
      dbOne<any>(`SELECT COALESCE(SUM(emi_amount),0) as total FROM loans WHERE user_id=$1 AND is_active=TRUE`, [userId]),
    ]);
    const remainingBills = parseFloat(billsRow?.total || 0);
    const emiTotal       = parseFloat(loansRow?.total || 0);

    // ── 4. Savings opportunities ─────────────────────────────────
    const opportunities = await db<any>(`
      WITH monthly AS (
        SELECT category_slug,
          DATE_TRUNC('month',transaction_date) as mo,
          SUM(amount) as total
        FROM transactions
        WHERE user_id=$1 AND is_credit=FALSE
          AND transaction_date >= NOW() - INTERVAL '4 months'
        GROUP BY category_slug, DATE_TRUNC('month',transaction_date)
      ),
      hist AS (
        SELECT category_slug, AVG(total) as hist_avg
        FROM monthly WHERE mo < DATE_TRUNC('month',CURRENT_DATE)
        GROUP BY category_slug
      ),
      curr AS (
        SELECT category_slug, total as this_month
        FROM monthly WHERE mo = DATE_TRUNC('month',CURRENT_DATE)
      )
      SELECT c.category_slug, c.this_month, h.hist_avg,
        ROUND((c.this_month - h.hist_avg)::numeric,2) as overspend
      FROM curr c JOIN hist h USING(category_slug)
      WHERE c.this_month > h.hist_avg * 1.2
      ORDER BY overspend DESC LIMIT 5
    `, [userId]);

    return ok(res, {
      recurring_bills: recurring,
      insights,
      forecast: {
        salary: salaryAmt,
        projected_spend: projected,
        remaining_bills: Math.round(remainingBills),
        emi_total: Math.round(emiTotal),
        month_end_balance: Math.round(salaryAmt - projected - remainingBills - emiTotal),
        is_overspending: projected > salaryAmt,
        burn_rate: Math.round(burnRate),
      },
      savings_opportunities: opportunities.map((o: any) => ({
        category_slug: o.category_slug,
        this_month: parseFloat(o.this_month),
        historical_avg: Math.round(parseFloat(o.hist_avg)),
        overspend: parseFloat(o.overspend),
      })),
    });
  } catch (e: any) {
    console.error('[intelligence]', e.message);
    return fail(res, 'Server error', 500);
  }
});

// ── POST /analytics/auto-add-bills ───────────────────────────
// Called by Android after user approves recurring bill suggestions.
// Idempotent — skips bills that already exist by name.
app.post('/analytics/auto-add-bills', async (req, res) => {
  try {
    const userId = uid(req);
    const { bills } = req.body as { bills: Array<{ merchant: string; avg_amount: number; due_day_estimate?: number; category_slug?: string }> };
    if (!Array.isArray(bills) || !bills.length) return fail(res, 'bills array required');

    let created = 0;
    for (const b of bills) {
      const existing = await dbOne('SELECT id FROM mandatory_bills WHERE user_id=$1 AND LOWER(name)=LOWER($2)', [userId, b.merchant]);
      if (!existing) {
        await db(
          `INSERT INTO mandatory_bills(id,user_id,name,amount,due_day,category,icon,is_auto_detected,is_active)
           VALUES($1,$2,$3,$4,$5,$6,'💳',TRUE,TRUE)`,
          [uuidv4(), userId, b.merchant, b.avg_amount, b.due_day_estimate || 1, b.category_slug || 'bills']
        );
        created++;
      }
    }
    return ok(res, { created });
  } catch (e: any) {
    console.error('[auto-add-bills]', e.message);
    return fail(res, 'Server error', 500);
  }
});

app.listen(PORT, () => console.log(`[analytics-service] running on :${PORT}`));
export default app;
