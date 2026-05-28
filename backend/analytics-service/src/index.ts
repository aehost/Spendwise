import 'dotenv/config';
import Anthropic from '@anthropic-ai/sdk';
import { setDefaultResultOrder } from 'node:dns';
setDefaultResultOrder('ipv4first'); // Railway/Node 18+ prefers IPv6; force IPv4 for Supabase
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { randomUUID as uuidv4 } from 'crypto';
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

app.set('trust proxy', 1); // Trust Railway's reverse proxy
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

// ── POST /analytics/classify-merchant ────────────────────────
// Tier-5 fallback: uses Google Places Nearby Search to identify
// an unknown merchant via GPS coordinates, then maps place types
// to our category slugs.  Returns confidence 0-1.
app.post('/analytics/classify-merchant', async (req, res) => {
  try {
    const { merchant, sms_body, latitude, longitude, accuracy } = req.body as {
      merchant: string; sms_body?: string;
      latitude: number; longitude: number; accuracy?: number;
    };
    if (!merchant || latitude == null || longitude == null)
      return fail(res, 'merchant, latitude, longitude required');

    const PLACES_KEY = process.env.GOOGLE_PLACES_API_KEY;
    if (!PLACES_KEY) return ok(res, { category_slug: 'other', confidence: 0, source: 'places' });

    // Radius = max(50m, accuracy) capped at 200m to keep results tight
    const radius = Math.min(200, Math.max(50, accuracy || 100));
    const keyword = encodeURIComponent(merchant.slice(0, 60));
    const placesUrl =
      `https://maps.googleapis.com/maps/api/place/nearbysearch/json` +
      `?location=${latitude},${longitude}&radius=${radius}&keyword=${keyword}&key=${PLACES_KEY}`;

    const placesResp = await fetch(placesUrl);
    const placesData: any = await placesResp.json();

    if (placesData.status !== 'OK' || !placesData.results?.length) {
      return ok(res, { category_slug: 'other', confidence: 0.3, source: 'places' });
    }

    const topResult = placesData.results[0];
    const types: string[] = topResult.types || [];

    // Map Google Place types → app category slugs
    const PLACE_TYPE_MAP: Record<string, string> = {
      // FOOD
      restaurant: 'food', food: 'food', cafe: 'food', bakery: 'food',
      meal_delivery: 'food', meal_takeaway: 'food', bar: 'food',
      ice_cream_shop: 'food', pizza: 'food', fast_food_restaurant: 'food',
      // FUEL
      gas_station: 'fuel', petroleum_fuel: 'fuel',
      // GROCERIES
      grocery_or_supermarket: 'groceries', supermarket: 'groceries',
      convenience_store: 'groceries',
      // SHOPPING
      shopping_mall: 'shopping', clothing_store: 'shopping',
      electronics_store: 'shopping', department_store: 'shopping',
      furniture_store: 'shopping', shoe_store: 'shopping',
      jewelry_store: 'shopping', book_store: 'shopping',
      // TRAVEL
      travel_agency: 'travel', bus_station: 'travel', train_station: 'travel',
      airport: 'travel', lodging: 'travel', transit_station: 'travel',
      // HEALTH
      hospital: 'health', pharmacy: 'health', doctor: 'health',
      gym: 'health', physiotherapist: 'health', dentist: 'health',
      spa: 'health', beauty_salon: 'health',
      // BILLS
      bank: 'emi', atm: 'emi', finance: 'emi',
      // EDUCATION
      school: 'education', university: 'education', library: 'education',
      // ENTERTAINMENT
      movie_theater: 'entertainment', night_club: 'entertainment',
      amusement_park: 'entertainment', bowling_alley: 'entertainment',
    };

    let bestSlug = 'other';
    let matchedType = '';
    for (const t of types) {
      if (PLACE_TYPE_MAP[t]) { bestSlug = PLACE_TYPE_MAP[t]; matchedType = t; break; }
    }

    // Confidence: higher when merchant name overlaps with place name
    const placeNameLower = (topResult.name || '').toLowerCase();
    const merchantLower  = merchant.toLowerCase();
    const nameOverlap = placeNameLower.includes(merchantLower.slice(0, 6)) ||
                        merchantLower.includes(placeNameLower.slice(0, 6));
    const confidence = bestSlug === 'other' ? 0.3 : nameOverlap ? 0.92 : 0.75;

    return ok(res, {
      category_slug: bestSlug,
      display_name:  topResult.name,
      sub_category:  matchedType,
      confidence,
      source: 'places',
    });
  } catch (e: any) {
    console.error('[classify-merchant]', e.message);
    return fail(res, 'Server error', 500);
  }
});

// ── GET /analytics/monthly-report ────────────────────────────
// Comprehensive analytics: category breakdown, daily heatmap,
// top merchants, day-of-week pattern, waste, anomalies, budget
// performance, health score, upcoming bills, trends.
app.get('/analytics/monthly-report', async (req, res) => {
  try {
    const userId = uid(req);
    const now    = new Date();
    const month  = parseInt(String(req.query.month || now.getMonth() + 1));
    const year   = parseInt(String(req.query.year  || now.getFullYear()));
    const start  = `${year}-${String(month).padStart(2,'0')}-01`;
    const end    = new Date(year, month, 0).toISOString().split('T')[0];
    const daysInMonth = new Date(year, month, 0).getDate();
    const daysPassed  = month === now.getMonth()+1 && year === now.getFullYear()
      ? now.getDate() : daysInMonth;

    // Prev month dates for comparison
    const prevMonth = month === 1 ? 12 : month - 1;
    const prevYear  = month === 1 ? year - 1 : year;
    const prevStart = `${prevYear}-${String(prevMonth).padStart(2,'0')}-01`;
    const prevEnd   = new Date(prevYear, prevMonth, 0).toISOString().split('T')[0];

    const [
      totals, prevTotals, byCategory, prevByCategory,
      dailySpending, topMerchants, dayOfWeek,
      wasteRow, budgets, bills, salary, loanTotal,
      allMerchants, prevMerchants, anomalyRows,
    ] = await Promise.all([

      // Current month totals
      dbOne<any>(`
        SELECT
          COALESCE(SUM(CASE WHEN is_credit=FALSE AND category_slug!='income' THEN amount ELSE 0 END),0) as total_spent,
          COALESCE(SUM(CASE WHEN is_credit=TRUE THEN amount ELSE 0 END),0) as total_income,
          COALESCE(SUM(CASE WHEN is_waste=TRUE THEN amount ELSE 0 END),0) as total_waste,
          COUNT(*)::int as tx_count,
          COALESCE(AVG(CASE WHEN is_credit=FALSE THEN amount ELSE NULL END),0) as avg_tx,
          MAX(CASE WHEN is_credit=FALSE THEN transaction_date ELSE NULL END) as peak_day_placeholder
        FROM transactions WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3`,
        [userId, start, end]),

      // Prev month totals
      dbOne<any>(`
        SELECT COALESCE(SUM(CASE WHEN is_credit=FALSE AND category_slug!='income' THEN amount ELSE 0 END),0) as total_spent
        FROM transactions WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3`,
        [userId, prevStart, prevEnd]),

      // Category breakdown (current)
      db<any>(`
        SELECT category_slug, SUM(amount) as amount, COUNT(*)::int as count
        FROM transactions
        WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3 AND is_credit=FALSE
        GROUP BY category_slug ORDER BY amount DESC`,
        [userId, start, end]),

      // Category breakdown (prev month) for comparison
      db<any>(`
        SELECT category_slug, SUM(amount) as amount
        FROM transactions
        WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3 AND is_credit=FALSE
        GROUP BY category_slug`,
        [userId, prevStart, prevEnd]),

      // Daily spending heatmap
      db<any>(`
        SELECT transaction_date::text as date, SUM(amount) as amount, COUNT(*)::int as count
        FROM transactions
        WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3 AND is_credit=FALSE
        GROUP BY transaction_date ORDER BY transaction_date`,
        [userId, start, end]),

      // Top merchants
      db<any>(`
        SELECT merchant, category_slug,
          SUM(amount) as total_spent, COUNT(*)::int as visit_count,
          ROUND(AVG(amount)::numeric,2) as avg_amount,
          MAX(transaction_date)::text as last_visit
        FROM transactions
        WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3 AND is_credit=FALSE
        GROUP BY merchant, category_slug ORDER BY total_spent DESC LIMIT 10`,
        [userId, start, end]),

      // Day-of-week analysis
      db<any>(`
        SELECT TO_CHAR(transaction_date,'Dy') as day,
          ROUND(AVG(daily_total)::numeric,2) as avg_spend,
          SUM(tx_count)::int as transaction_count
        FROM (
          SELECT transaction_date,
            SUM(amount) as daily_total, COUNT(*)::int as tx_count
          FROM transactions
          WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3 AND is_credit=FALSE
          GROUP BY transaction_date
        ) d
        GROUP BY TO_CHAR(transaction_date,'Dy')
        ORDER BY MIN(EXTRACT(DOW FROM transaction_date))`,
        [userId, start, end]),

      // Waste totals
      dbOne<any>(`
        SELECT
          COALESCE(SUM(amount),0) as waste_total,
          COUNT(*)::int as waste_count,
          (SELECT category_slug FROM transactions
           WHERE user_id=$1 AND is_waste=TRUE AND transaction_date BETWEEN $2 AND $3
           GROUP BY category_slug ORDER BY SUM(amount) DESC LIMIT 1) as top_waste_cat
        FROM transactions WHERE user_id=$1 AND is_waste=TRUE
          AND transaction_date BETWEEN $2 AND $3`,
        [userId, start, end]),

      // Budget data
      db<any>('SELECT * FROM monthly_budgets WHERE user_id=$1 AND month=$2 AND year=$3', [userId, month, year]),

      // Upcoming bills
      db<any>(`
        SELECT name, amount, due_day, icon, is_paid_this_month, category
        FROM mandatory_bills WHERE user_id=$1 AND is_active=TRUE
        ORDER BY due_day`,
        [userId]),

      // Salary config
      dbOne<any>('SELECT amount FROM salary_config WHERE user_id=$1', [userId]),

      // EMI total
      dbOne<any>('SELECT COALESCE(SUM(emi_amount),0) as total FROM loans WHERE user_id=$1 AND is_active=TRUE', [userId]),

      // All distinct merchants this month (for new merchant detection)
      db<any>(`
        SELECT DISTINCT merchant FROM transactions
        WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3`,
        [userId, start, end]),

      // All distinct merchants last month
      db<any>(`
        SELECT DISTINCT merchant FROM transactions
        WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3`,
        [userId, prevStart, prevEnd]),

      // Anomaly detection: amounts > 3x merchant average
      db<any>(`
        WITH merchant_avg AS (
          SELECT merchant, AVG(amount) as avg_amt, STDDEV(amount) as std_amt
          FROM transactions WHERE user_id=$1 AND is_credit=FALSE
            AND transaction_date >= NOW() - INTERVAL '3 months'
          GROUP BY merchant HAVING COUNT(*) >= 3
        )
        SELECT t.merchant, t.category_slug, t.amount, t.transaction_date::text as date
        FROM transactions t JOIN merchant_avg m USING(merchant)
        WHERE t.user_id=$1
          AND t.transaction_date BETWEEN $2 AND $3
          AND t.is_credit=FALSE
          AND t.amount > m.avg_amt + 2.5 * COALESCE(m.std_amt, m.avg_amt*0.5)
        ORDER BY t.amount DESC LIMIT 5`,
        [userId, start, end]),
    ]);

    // ── Derived calculations ──────────────────────────────────────
    const totalSpent   = parseFloat(totals?.total_spent   || 0);
    const totalIncome  = parseFloat(totals?.total_income  || 0);
    const totalWaste   = parseFloat(wasteRow?.waste_total || 0);
    const prevSpent    = parseFloat(prevTotals?.total_spent || 0);
    const salaryAmt    = parseFloat(salary?.amount || 0);
    const emiAmt       = parseFloat(loanTotal?.total || 0);
    const txCount      = totals?.tx_count || 0;
    const avgTx        = parseFloat(totals?.avg_tx || 0);
    const burnRate     = daysPassed > 0 ? totalSpent / daysPassed : 0;
    const vsLastMonth  = prevSpent > 0 ? Math.round(((totalSpent - prevSpent) / prevSpent) * 100) : 0;
    const savings      = (salaryAmt || totalIncome) - totalSpent;
    const savingsRate  = (salaryAmt || totalIncome) > 0
      ? Math.round((savings / (salaryAmt || totalIncome)) * 100) : 0;

    // Peak spending day
    const peakDay = dailySpending.reduce((best: any, d: any) =>
      parseFloat(d.amount) > parseFloat(best?.amount || 0) ? d : best, dailySpending[0]);

    // Category breakdown with comparison
    const allCategories = ['food','fuel','groceries','shopping','travel','entertainment',
      'bills','health','education','insurance','emi','investments','other'];
    const categoryBreakdown = allCategories.map(slug => {
      const curr = byCategory.find((c: any) => c.category_slug === slug);
      const prev = prevByCategory.find((c: any) => c.category_slug === slug);
      const amount  = parseFloat(curr?.amount || 0);
      const prevAmt = parseFloat(prev?.amount || 0);
      const budget  = budgets.find((b: any) => b.category_slug === slug);
      const budgetAmt  = parseFloat(budget?.amount || 0);
      const budgetPct  = budgetAmt > 0 ? Math.round((amount / budgetAmt) * 100) : null;
      const status     = !budgetAmt ? 'ok' : budgetPct! >= 100 ? 'over' : budgetPct! >= 80 ? 'warning' : 'ok';
      const vsLM = prevAmt > 0 ? Math.round(((amount - prevAmt) / prevAmt) * 100) : 0;
      return {
        category_slug: slug, amount,
        count: curr?.count || 0,
        pct_of_total: totalSpent > 0 ? Math.round((amount / totalSpent) * 100) : 0,
        vs_last_month: vsLM,
        budget: budgetAmt || null,
        budget_pct: budgetPct,
        budget_status: status,
      };
    }).filter(c => c.amount > 0);

    // Budget performance (only tracked categories)
    const budgetPerformance = budgets.map((b: any) => {
      const spent    = parseFloat(byCategory.find((c: any) => c.category_slug === b.category_slug)?.amount || 0);
      const budget   = parseFloat(b.amount);
      const variance = spent - budget;
      const status   = variance > 0 ? 'over' : variance > -budget * 0.2 ? 'warning' : 'ok';
      const daysLeft = daysInMonth - daysPassed;
      const projEnd  = daysPassed > 0 ? Math.round((spent / daysPassed) * daysInMonth) : 0;
      return {
        category_slug: b.category_slug, budget, spent,
        variance: Math.round(variance),
        status, days_remaining: daysLeft, projected_end: projEnd,
      };
    });

    // Upcoming bills
    const upcomingBills = bills.map((b: any) => {
      const today = now.getDate();
      let daysUntil = b.due_day - today;
      if (daysUntil < 0) daysUntil += daysInMonth;
      return {
        name: b.name, amount: parseFloat(b.amount),
        due_date: `${year}-${String(month).padStart(2,'0')}-${String(b.due_day).padStart(2,'0')}`,
        days_until: daysUntil,
        paid: b.is_paid_this_month,
        icon: b.icon || '📄',
      };
    }).sort((a: any, b: any) => a.days_until - b.days_until);

    // Financial health score (0-100)
    const healthFactors = [];
    let healthScore = 100;

    // Savings rate factor (0-35 pts)
    const srScore = savingsRate >= 30 ? 35 : savingsRate >= 20 ? 28 : savingsRate >= 10 ? 18 : savingsRate > 0 ? 8 : 0;
    healthScore  -= (35 - srScore);
    healthFactors.push({ name: 'Savings Rate', score: srScore, status: srScore >= 28 ? 'good' : srScore >= 18 ? 'neutral' : 'bad', detail: `${savingsRate}% of income saved` });

    // EMI burden (0-25 pts)
    const emiBurden = salaryAmt > 0 ? (emiAmt / salaryAmt) * 100 : 0;
    const emiScore = emiBurden <= 20 ? 25 : emiBurden <= 35 ? 18 : emiBurden <= 50 ? 10 : 2;
    healthScore  -= (25 - emiScore);
    healthFactors.push({ name: 'EMI Burden', score: emiScore, status: emiScore >= 18 ? 'good' : emiScore >= 10 ? 'neutral' : 'bad', detail: `${Math.round(emiBurden)}% of income in EMIs` });

    // Budget adherence (0-25 pts)
    const overBudgetCats = budgetPerformance.filter((b: any) => b.status === 'over').length;
    const budgetScore = overBudgetCats === 0 ? 25 : overBudgetCats === 1 ? 18 : overBudgetCats <= 3 ? 10 : 2;
    healthScore -= (25 - budgetScore);
    healthFactors.push({ name: 'Budget Control', score: budgetScore, status: budgetScore >= 18 ? 'good' : budgetScore >= 10 ? 'neutral' : 'bad', detail: `${overBudgetCats} categor${overBudgetCats === 1 ? 'y' : 'ies'} over budget` });

    // Waste ratio (0-15 pts)
    const wastePct = totalSpent > 0 ? Math.round((totalWaste / totalSpent) * 100) : 0;
    const wasteScore = wastePct <= 5 ? 15 : wastePct <= 10 ? 10 : wastePct <= 20 ? 5 : 0;
    healthScore -= (15 - wasteScore);
    healthFactors.push({ name: 'Impulse Control', score: wasteScore, status: wasteScore >= 10 ? 'good' : wasteScore >= 5 ? 'neutral' : 'bad', detail: `${wastePct}% of spending marked as waste` });

    healthScore = Math.max(0, Math.min(100, healthScore));
    const grade = healthScore >= 90 ? 'A+' : healthScore >= 80 ? 'A' : healthScore >= 70 ? 'B' : healthScore >= 55 ? 'C' : 'D';

    // New merchants this month
    const prevMerchantSet = new Set(prevMerchants.map((m: any) => m.merchant));
    const newMerchantCount = allMerchants.filter((m: any) => !prevMerchantSet.has(m.merchant)).length;
    const recurringMerchantCount = allMerchants.filter((m: any) => prevMerchantSet.has(m.merchant)).length;

    // Top growing / shrinking category
    const sortedByGrowth = categoryBreakdown.filter(c => c.vs_last_month !== 0)
      .sort((a, b) => b.vs_last_month - a.vs_last_month);
    const topGrowing   = sortedByGrowth[0]?.category_slug || null;
    const topShrinking = sortedByGrowth[sortedByGrowth.length - 1]?.category_slug || null;

    // Spending trend
    const spendingTrend = vsLastMonth > 5 ? 'up' : vsLastMonth < -5 ? 'down' : 'stable';

    // Smart insights
    const insights: Array<{ type: string; message: string; action?: string }> = [];
    if (vsLastMonth > 20) insights.push({ type: 'warning', message: `Spending up ${vsLastMonth}% vs last month`, action: 'review_spending' });
    if (vsLastMonth < -10) insights.push({ type: 'success', message: `Great job! Spending down ${Math.abs(vsLastMonth)}% vs last month` });
    if (savingsRate < 10 && salaryAmt > 0) insights.push({ type: 'alert', message: 'Savings rate below 10% — consider reducing discretionary spend', action: 'set_budget' });
    if (wastePct > 15) insights.push({ type: 'tip', message: `${wastePct}% of spending is impulse/waste — review marked transactions`, action: 'review_waste' });
    if (emiBurden > 40) insights.push({ type: 'warning', message: `EMI burden is ${Math.round(emiBurden)}% of income (ideal <30%)`, action: 'view_loans' });
    if (newMerchantCount > 5) insights.push({ type: 'info', message: `${newMerchantCount} new merchants this month — check for subscriptions`, action: 'view_transactions' });
    const upcomingUnpaid = upcomingBills.filter((b: any) => !b.paid && b.days_until <= 5);
    if (upcomingUnpaid.length) insights.push({ type: 'alert', message: `${upcomingUnpaid.length} bill${upcomingUnpaid.length > 1 ? 's' : ''} due in the next 5 days`, action: 'view_bills' });

    return ok(res, {
      month, year,
      summary: {
        income: salaryAmt || totalIncome,
        total_spent: totalSpent,
        savings: Math.round(savings),
        savings_rate: savingsRate,
        vs_last_month: vsLastMonth,
        burn_rate: Math.round(burnRate),
        peak_day: peakDay?.date || null,
        peak_amount: parseFloat(peakDay?.amount || 0),
        transaction_count: txCount,
        avg_transaction: Math.round(avgTx),
      },
      category_breakdown: categoryBreakdown,
      daily_spending: dailySpending.map((d: any) => ({
        date: d.date, amount: parseFloat(d.amount), count: d.count,
      })),
      top_merchants: topMerchants.map((m: any) => ({
        merchant: m.merchant, category_slug: m.category_slug,
        total_spent: parseFloat(m.total_spent), visit_count: m.visit_count,
        avg_amount: parseFloat(m.avg_amount), last_visit: m.last_visit,
      })),
      day_of_week: dayOfWeek.map((d: any) => ({
        day: d.day, avg_spend: parseFloat(d.avg_spend), transaction_count: d.transaction_count,
      })),
      waste_analysis: {
        total_waste: totalWaste,
        waste_pct: wastePct,
        top_waste_category: wasteRow?.top_waste_cat || null,
        waste_transactions: wasteRow?.waste_count || 0,
      },
      budget_performance: budgetPerformance,
      upcoming_bills: upcomingBills,
      health_score: { score: healthScore, grade, factors: healthFactors },
      anomalies: anomalyRows.map((a: any) => ({
        merchant: a.merchant, category_slug: a.category_slug,
        amount: parseFloat(a.amount), date: a.date,
        reason: `Amount is significantly above your average for this merchant`,
        anomaly_type: 'high_amount',
      })),
      insights,
      trends: {
        spending_trend: spendingTrend,
        spending_trend_pct: vsLastMonth,
        top_growing_category: topGrowing,
        top_shrinking_category: topShrinking,
        new_merchants: newMerchantCount,
        recurring_merchants: recurringMerchantCount,
      },
    });
  } catch (e: any) {
    console.error('[monthly-report]', e.message);
    return fail(res, 'Server error', 500);
  }
});

// ── POST /analytics/ai-coach ──────────────────────────────────
// Claude-powered financial coach with user's full financial context
app.post('/analytics/ai-coach', async (req, res) => {
  try {
    const userId = uid(req);
    const { message, history = [] } = req.body as {
      message: string;
      history: Array<{ role: 'user' | 'assistant'; content: string }>;
    };
    if (!message?.trim()) return fail(res, 'message required');

    const apiKey = process.env.ANTHROPIC_API_KEY;
    if (!apiKey) return fail(res, 'AI coach not configured', 503);

    const now   = new Date();
    const month = now.getMonth() + 1;
    const year  = now.getFullYear();
    const start = `${year}-${String(month).padStart(2,'0')}-01`;
    const end   = new Date(year, month, 0).toISOString().split('T')[0];

    const [salary, spending, bankBal, loans, bills, goals, topCats, ccTotal] = await Promise.all([
      dbOne<any>('SELECT amount,expected_day FROM salary_config WHERE user_id=$1', [userId]),
      dbOne<any>(`SELECT COALESCE(SUM(CASE WHEN is_credit=FALSE THEN amount ELSE 0 END),0) as spent,
        COALESCE(SUM(CASE WHEN is_credit=TRUE THEN amount ELSE 0 END),0) as income
        FROM transactions WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3`, [userId,start,end]),
      dbOne<any>('SELECT COALESCE(SUM(balance),0) as total FROM bank_accounts WHERE user_id=$1 AND is_active=TRUE', [userId]),
      db<any>('SELECT name,emi_amount,interest_rate,outstanding,months_remaining FROM loans WHERE user_id=$1 AND is_active=TRUE', [userId]),
      db<any>('SELECT name,amount,due_day,is_paid_this_month FROM mandatory_bills WHERE user_id=$1 AND is_active=TRUE', [userId]),
      db<any>('SELECT title,target_amount,current_amount,deadline FROM financial_goals WHERE user_id=$1 AND is_completed=FALSE', [userId]),
      db<any>(`SELECT category_slug,SUM(amount) as total FROM transactions WHERE user_id=$1
        AND transaction_date BETWEEN $2 AND $3 AND is_credit=FALSE
        GROUP BY category_slug ORDER BY total DESC LIMIT 5`, [userId,start,end]),
      dbOne<any>('SELECT COALESCE(SUM(outstanding),0) as total FROM credit_cards WHERE user_id=$1 AND is_active=TRUE', [userId]),
    ]);

    const salaryAmt = parseFloat(salary?.amount || 0);
    const spent     = parseFloat(spending?.spent || 0);
    const savingsRate = salaryAmt > 0 ? Math.round(((salaryAmt - spent) / salaryAmt) * 100) : 0;
    const emiTotal  = loans.reduce((s: number, l: any) => s + parseFloat(l.emi_amount || 0), 0);

    const systemPrompt = `You are SpendWise AI Coach, a friendly and knowledgeable personal finance advisor for Indian users.
You have access to the user's real financial data for ${now.toLocaleString('default',{month:'long'})} ${year}:

FINANCIAL SNAPSHOT:
- Monthly Salary: ₹${salaryAmt.toLocaleString('en-IN')}
- Spent this month: ₹${spent.toLocaleString('en-IN')} (${savingsRate}% savings rate)
- Bank Balance: ₹${parseFloat(bankBal?.total||0).toLocaleString('en-IN')}
- CC Outstanding: ₹${parseFloat(ccTotal?.total||0).toLocaleString('en-IN')}
- Total EMI/month: ₹${emiTotal.toLocaleString('en-IN')} (${salaryAmt>0?Math.round((emiTotal/salaryAmt)*100):0}% of income)

TOP SPENDING CATEGORIES:
${topCats.map((c:any) => `- ${c.category_slug}: ₹${parseFloat(c.total).toLocaleString('en-IN')}`).join('\n')}

LOANS: ${loans.length === 0 ? 'None' : loans.map((l:any) =>
  `${l.name} (₹${parseFloat(l.emi_amount).toLocaleString('en-IN')}/mo, ${l.interest_rate}% rate, ₹${parseFloat(l.outstanding).toLocaleString('en-IN')} outstanding, ${l.months_remaining} months left)`
).join('; ')}

BILLS: ${bills.length === 0 ? 'None' : bills.map((b:any) =>
  `${b.name} ₹${parseFloat(b.amount).toLocaleString('en-IN')} due ${b.due_day}th ${b.is_paid_this_month ? '(✓ paid)' : '(unpaid)'}`
).join('; ')}

ACTIVE GOALS: ${goals.length === 0 ? 'None' : goals.map((g:any) =>
  `${g.title}: ₹${parseFloat(g.current_amount).toLocaleString('en-IN')} / ₹${parseFloat(g.target_amount).toLocaleString('en-IN')}`
).join('; ')}

Guidelines:
- Give specific, actionable advice using the user's real numbers
- Use ₹ currency and Indian financial context (UPI, NEFT, Section 80C, etc.)
- Be concise: 2-4 sentences per response unless detail is requested
- Use emojis sparingly to make responses feel friendly
- If you suggest saving a specific amount, make it realistic based on their data`;

    const anthropic = new Anthropic({ apiKey });
    const msgs: Array<{role:'user'|'assistant';content:string}> = [
      ...history.slice(-8), // keep last 8 turns for context
      { role: 'user', content: message }
    ];

    const response = await anthropic.messages.create({
      model: 'claude-haiku-4-5-20251001',
      max_tokens: 500,
      system: systemPrompt,
      messages: msgs,
    });

    const reply = (response.content[0] as any).text || '';
    return ok(res, { reply, input_tokens: response.usage.input_tokens, output_tokens: response.usage.output_tokens });
  } catch (e: any) {
    console.error('[ai-coach]', e.message);
    return fail(res, 'AI coach error', 500);
  }
});

// ── GET /analytics/health-score ───────────────────────────────
app.get('/analytics/health-score', async (req, res) => {
  try {
    const userId = uid(req);
    const now    = new Date();
    const month  = now.getMonth() + 1;
    const year   = now.getFullYear();
    const start  = `${year}-${String(month).padStart(2,'0')}-01`;
    const end    = new Date(year, month, 0).toISOString().split('T')[0];

    const [salary, spending, loans, budgets, byCategory, bankBal, goals, bills] = await Promise.all([
      dbOne<any>('SELECT amount FROM salary_config WHERE user_id=$1', [userId]),
      dbOne<any>(`SELECT COALESCE(SUM(CASE WHEN is_credit=FALSE THEN amount ELSE 0 END),0) as spent
        FROM transactions WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3`, [userId,start,end]),
      db<any>('SELECT emi_amount,interest_rate FROM loans WHERE user_id=$1 AND is_active=TRUE', [userId]),
      db<any>('SELECT category_slug,amount FROM monthly_budgets WHERE user_id=$1 AND month=$2 AND year=$3', [userId,month,year]),
      db<any>(`SELECT category_slug,SUM(amount) as total FROM transactions
        WHERE user_id=$1 AND transaction_date BETWEEN $2 AND $3 AND is_credit=FALSE
        GROUP BY category_slug`, [userId,start,end]),
      dbOne<any>('SELECT COALESCE(SUM(balance),0) as total FROM bank_accounts WHERE user_id=$1 AND is_active=TRUE', [userId]),
      db<any>('SELECT target_amount,current_amount FROM financial_goals WHERE user_id=$1 AND is_completed=FALSE', [userId]),
      db<any>('SELECT amount,is_paid_this_month FROM mandatory_bills WHERE user_id=$1 AND is_active=TRUE', [userId]),
    ]);

    const salaryAmt   = parseFloat(salary?.amount || 0);
    const spent       = parseFloat(spending?.spent || 0);
    const bankBalance = parseFloat(bankBal?.total || 0);
    const emiTotal    = loans.reduce((s: number, l: any) => s + parseFloat(l.emi_amount || 0), 0);
    const savingsRate = salaryAmt > 0 ? Math.round(((salaryAmt - spent) / salaryAmt) * 100) : 0;
    const emiBurden   = salaryAmt > 0 ? Math.round((emiTotal / salaryAmt) * 100) : 0;
    const monthlyExpenses = spent + emiTotal;
    const emergencyMonths = monthlyExpenses > 0 ? bankBalance / monthlyExpenses : 0;

    const overBudgetCats = budgets.filter((b: any) => {
      const cat = byCategory.find((c: any) => c.category_slug === b.category_slug);
      return cat && parseFloat(cat.total) > parseFloat(b.amount);
    }).length;

    const billsPaidPct = bills.length > 0
      ? Math.round((bills.filter((b: any) => b.is_paid_this_month).length / bills.length) * 100)
      : 100;

    const goalProgress = goals.length > 0
      ? Math.round(goals.reduce((s: number, g: any) =>
          s + (parseFloat(g.target_amount) > 0 ? parseFloat(g.current_amount) / parseFloat(g.target_amount) : 0)
        , 0) / goals.length * 100)
      : 50;

    // Factor scoring
    const srScore    = savingsRate >= 30 ? 20 : savingsRate >= 20 ? 16 : savingsRate >= 10 ? 10 : savingsRate > 0 ? 5 : 0;
    const emiScore   = emiBurden <= 20 ? 20 : emiBurden <= 30 ? 15 : emiBurden <= 40 ? 8 : 2;
    const emgScore   = emergencyMonths >= 6 ? 20 : emergencyMonths >= 3 ? 15 : emergencyMonths >= 1 ? 8 : 2;
    const budScore   = overBudgetCats === 0 ? 20 : overBudgetCats === 1 ? 14 : overBudgetCats <= 3 ? 7 : 2;
    const billScore  = billsPaidPct >= 100 ? 10 : billsPaidPct >= 80 ? 7 : billsPaidPct >= 60 ? 4 : 1;
    const goalScore  = goalProgress >= 70 ? 10 : goalProgress >= 40 ? 6 : goalProgress >= 10 ? 3 : 1;

    const score = Math.min(100, srScore + emiScore + emgScore + budScore + billScore + goalScore);
    const grade = score >= 90 ? 'A+' : score >= 80 ? 'A' : score >= 70 ? 'B+' : score >= 60 ? 'B' : score >= 50 ? 'C' : 'D';
    const level = score >= 90 ? 'Financially Elite' : score >= 75 ? 'Financially Fit' :
                  score >= 60 ? 'Getting Stable' : score >= 45 ? 'Building Foundation' : 'Needs Attention';

    return ok(res, {
      score, grade, level,
      factors: [
        { name: 'Savings Rate', score: srScore, max: 20, pct: Math.round((srScore/20)*100),
          status: srScore >= 16 ? 'good' : srScore >= 10 ? 'neutral' : 'bad',
          detail: `${savingsRate}% of income saved`, tip: savingsRate < 20 ? 'Aim for 20%+ savings rate' : 'Great savings discipline!' },
        { name: 'EMI Burden', score: emiScore, max: 20, pct: Math.round((emiScore/20)*100),
          status: emiScore >= 15 ? 'good' : emiScore >= 8 ? 'neutral' : 'bad',
          detail: `${emiBurden}% of income in EMIs`, tip: emiBurden > 30 ? 'EMIs over 30% — consider prepayment' : 'EMI load is manageable' },
        { name: 'Emergency Fund', score: emgScore, max: 20, pct: Math.round((emgScore/20)*100),
          status: emgScore >= 15 ? 'good' : emgScore >= 8 ? 'neutral' : 'bad',
          detail: `${emergencyMonths.toFixed(1)} months of expenses covered`, tip: emergencyMonths < 3 ? 'Build to 3-6 months of expenses' : 'Emergency fund is healthy' },
        { name: 'Budget Control', score: budScore, max: 20, pct: Math.round((budScore/20)*100),
          status: budScore >= 14 ? 'good' : budScore >= 7 ? 'neutral' : 'bad',
          detail: `${overBudgetCats} categor${overBudgetCats === 1 ? 'y' : 'ies'} over budget`, tip: overBudgetCats > 0 ? 'Review overspent categories' : 'All budgets on track!' },
        { name: 'Bills On Time', score: billScore, max: 10, pct: Math.round((billScore/10)*100),
          status: billScore >= 7 ? 'good' : billScore >= 4 ? 'neutral' : 'bad',
          detail: `${billsPaidPct}% bills paid this month`, tip: billsPaidPct < 100 ? 'Pay all bills to avoid late fees' : 'All bills paid!' },
        { name: 'Goal Progress', score: goalScore, max: 10, pct: Math.round((goalScore/10)*100),
          status: goalScore >= 6 ? 'good' : goalScore >= 3 ? 'neutral' : 'bad',
          detail: goals.length > 0 ? `${goalProgress}% avg goal completion` : 'No goals set yet',
          tip: goals.length === 0 ? 'Set financial goals to get started' : goalProgress < 40 ? 'Increase goal contributions' : 'Making good progress!' },
      ],
    });
  } catch (e: any) {
    console.error('[health-score]', e.message);
    return fail(res, 'Server error', 500);
  }
});

// ── GET /analytics/cash-flow ──────────────────────────────────
// Returns predicted cash flow events for next 3 months
app.get('/analytics/cash-flow', async (req, res) => {
  try {
    const userId = uid(req);
    const now    = new Date();

    const [salary, bills, loans, bankBal] = await Promise.all([
      dbOne<any>('SELECT amount,expected_day FROM salary_config WHERE user_id=$1', [userId]),
      db<any>('SELECT name,amount,due_day,icon,is_paid_this_month FROM mandatory_bills WHERE user_id=$1 AND is_active=TRUE', [userId]),
      db<any>('SELECT name,emi_amount FROM loans WHERE user_id=$1 AND is_active=TRUE', [userId]),
      dbOne<any>('SELECT COALESCE(SUM(balance),0) as total FROM bank_accounts WHERE user_id=$1 AND is_active=TRUE', [userId]),
    ]);

    const salaryAmt    = parseFloat(salary?.amount || 0);
    const salaryDay    = salary?.expected_day || 1;
    const startBalance = parseFloat(bankBal?.total || 0);
    const emiTotal     = loans.reduce((s: number, l: any) => s + parseFloat(l.emi_amount || 0), 0);
    const totalBills   = bills.reduce((s: number, b: any) => s + parseFloat(b.amount || 0), 0);
    const dailySpend   = (salaryAmt > 0 ? salaryAmt * 0.03 : 1000); // ~3% of salary per day as baseline

    const events: any[] = [];
    let runningBalance = startBalance;

    // Generate events for next 90 days
    for (let d = 0; d < 90; d++) {
      const date = new Date(now);
      date.setDate(now.getDate() + d);
      const dayOfMonth = date.getDate();
      const dateStr = date.toISOString().split('T')[0];

      const dayEvents: any[] = [];

      // Salary credit
      if (dayOfMonth === salaryDay && salaryAmt > 0) {
        runningBalance += salaryAmt;
        dayEvents.push({ type: 'credit', label: 'Salary Credit', amount: salaryAmt, icon: '💰', category: 'salary' });
      }

      // Bills due
      for (const bill of bills) {
        if (bill.due_day === dayOfMonth) {
          // Skip if already paid this month for current month
          const isCurrentMonth = date.getMonth() === now.getMonth() && date.getFullYear() === now.getFullYear();
          if (!(isCurrentMonth && bill.is_paid_this_month)) {
            runningBalance -= parseFloat(bill.amount);
            dayEvents.push({ type: 'debit', label: bill.name, amount: parseFloat(bill.amount), icon: bill.icon || '📄', category: 'bill' });
          }
        }
      }

      // EMI deductions (assume 5th of month as default EMI date)
      if (dayOfMonth === 5 && emiTotal > 0) {
        runningBalance -= emiTotal;
        dayEvents.push({ type: 'debit', label: 'EMI Payments', amount: emiTotal, icon: '🏦', category: 'emi' });
      }

      // Daily spend baseline
      runningBalance -= dailySpend;

      if (dayEvents.length > 0 || runningBalance < salaryAmt * 0.1) {
        events.push({
          date: dateStr,
          events: dayEvents,
          projected_balance: Math.round(runningBalance),
          is_low_balance: runningBalance < salaryAmt * 0.05,
          is_negative: runningBalance < 0,
        });
      }
    }

    return ok(res, {
      starting_balance: Math.round(startBalance),
      salary_amount: salaryAmt,
      salary_day: salaryDay,
      monthly_bills_total: Math.round(totalBills),
      monthly_emi_total: Math.round(emiTotal),
      daily_spend_estimate: Math.round(dailySpend),
      events: events.slice(0, 90),
    });
  } catch (e: any) {
    console.error('[cash-flow]', e.message);
    return fail(res, 'Server error', 500);
  }
});

// ── GET /analytics/debt-payoff ────────────────────────────────
// Snowball vs Avalanche debt payoff comparison
app.get('/analytics/debt-payoff', async (req, res) => {
  try {
    const userId = uid(req);
    const [loans, creditCards, salary] = await Promise.all([
      db<any>('SELECT id,name,emi_amount,interest_rate,outstanding,months_remaining,color FROM loans WHERE user_id=$1 AND is_active=TRUE', [userId]),
      db<any>('SELECT id,name,outstanding,min_due,color FROM credit_cards WHERE user_id=$1 AND is_active=TRUE AND outstanding>0', [userId]),
      dbOne<any>('SELECT amount FROM salary_config WHERE user_id=$1', [userId]),
    ]);

    // Combine debts: loans + credit cards (CC interest assumed at 36%/year)
    const allDebts = [
      ...loans.map((l: any) => ({
        id: l.id, name: l.name, type: 'loan',
        outstanding: parseFloat(l.outstanding),
        monthly_payment: parseFloat(l.emi_amount),
        interest_rate: parseFloat(l.interest_rate),
        color: l.color,
      })),
      ...creditCards.map((c: any) => ({
        id: c.id, name: c.name + ' CC', type: 'credit_card',
        outstanding: parseFloat(c.outstanding),
        monthly_payment: parseFloat(c.min_due) || Math.max(500, parseFloat(c.outstanding) * 0.05),
        interest_rate: 36,
        color: c.color,
      })),
    ].filter(d => d.outstanding > 0);

    const totalDebt    = allDebts.reduce((s, d) => s + d.outstanding, 0);
    const totalMonthly = allDebts.reduce((s, d) => s + d.monthly_payment, 0);

    function simulate(debts: any[], order: 'snowball' | 'avalanche') {
      const sorted = [...debts].sort((a, b) =>
        order === 'snowball'
          ? a.outstanding - b.outstanding
          : b.interest_rate - a.interest_rate
      );
      let months = 0, totalInterest = 0;
      const balances = sorted.map(d => ({ ...d, balance: d.outstanding }));
      let extra = 0;
      while (balances.some(d => d.balance > 0) && months < 600) {
        months++;
        let freed = extra;
        for (const d of balances) {
          if (d.balance <= 0) { freed += d.monthly_payment; continue; }
          const monthlyRate = d.interest_rate / 100 / 12;
          const interest = d.balance * monthlyRate;
          totalInterest += interest;
          const payment = Math.min(d.balance + interest, d.monthly_payment + (freed > 0 ? freed : 0));
          freed = 0;
          d.balance = Math.max(0, d.balance + interest - payment);
          if (d.balance === 0) freed += d.monthly_payment;
        }
      }
      const payoffDate = new Date();
      payoffDate.setMonth(payoffDate.getMonth() + months);
      return { months, totalInterest: Math.round(totalInterest), payoffDate: payoffDate.toISOString().split('T')[0], order_names: sorted.map(d => d.name) };
    }

    const snowball  = simulate(allDebts, 'snowball');
    const avalanche = simulate(allDebts, 'avalanche');
    const interestSaved = snowball.totalInterest - avalanche.totalInterest;

    return ok(res, {
      total_debt: Math.round(totalDebt),
      total_monthly_payment: Math.round(totalMonthly),
      debts: allDebts,
      snowball: { ...snowball, description: 'Pay smallest balance first — quick wins boost motivation' },
      avalanche: { ...avalanche, description: 'Pay highest interest first — saves the most money' },
      recommended: interestSaved > 0 ? 'avalanche' : 'snowball',
      interest_saved_by_avalanche: Math.max(0, interestSaved),
    });
  } catch (e: any) {
    console.error('[debt-payoff]', e.message);
    return fail(res, 'Server error', 500);
  }
});

// ── POST /analytics/tax-estimate ─────────────────────────────
// Indian income tax computation — Old vs New Regime (FY 2024-25)
app.post('/analytics/tax-estimate', async (req, res) => {
  try {
    const {
      annual_salary,
      other_income = 0,
      section_80c = 0,     // PPF, ELSS, LIC, PF (max ₹1.5L)
      section_80d = 0,     // Health insurance (max ₹25K self + ₹25K parents)
      hra_exemption = 0,
      nps_80ccd = 0,       // NPS additional (max ₹50K)
      home_loan_interest = 0, // Section 24b (max ₹2L for self-occupied)
    } = req.body;

    if (!annual_salary || annual_salary <= 0) return fail(res, 'annual_salary required');

    const grossIncome = parseFloat(annual_salary) + parseFloat(other_income);

    // ── Old Regime ────────────────────────────────────────────
    const stdDeductionOld  = 50000;
    const deduction80C     = Math.min(parseFloat(section_80c), 150000);
    const deduction80D     = Math.min(parseFloat(section_80d), 50000);
    const deductionNPS     = Math.min(parseFloat(nps_80ccd), 50000);
    const deductionHLI     = Math.min(parseFloat(home_loan_interest), 200000);
    const hraDeduction     = parseFloat(hra_exemption);

    const totalDeductionsOld = stdDeductionOld + deduction80C + deduction80D + deductionNPS + deductionHLI + hraDeduction;
    const taxableOld = Math.max(0, grossIncome - totalDeductionsOld);

    function calcOldTax(income: number): number {
      if (income <= 250000)  return 0;
      if (income <= 500000)  return (income - 250000) * 0.05;
      if (income <= 1000000) return 12500 + (income - 500000) * 0.20;
      return 12500 + 100000 + (income - 1000000) * 0.30;
    }
    let taxOld = calcOldTax(taxableOld);
    if (taxableOld <= 500000) taxOld = 0; // 87A rebate
    const surchargeOld = grossIncome > 5000000 ? taxOld * 0.10 :
                         grossIncome > 10000000 ? taxOld * 0.15 : 0;
    const cessOld = (taxOld + surchargeOld) * 0.04;
    const totalTaxOld = Math.round(taxOld + surchargeOld + cessOld);

    // ── New Regime (FY 2024-25) ───────────────────────────────
    const stdDeductionNew = 75000; // Enhanced std deduction in new regime
    const taxableNew = Math.max(0, grossIncome - stdDeductionNew);

    function calcNewTax(income: number): number {
      if (income <= 300000)  return 0;
      if (income <= 700000)  return (income - 300000) * 0.05;
      if (income <= 1000000) return 20000 + (income - 700000) * 0.10;
      if (income <= 1200000) return 50000 + (income - 1000000) * 0.15;
      if (income <= 1500000) return 80000 + (income - 1200000) * 0.20;
      return 140000 + (income - 1500000) * 0.30;
    }
    let taxNew = calcNewTax(taxableNew);
    if (taxableNew <= 700000) taxNew = 0; // 87A rebate under new regime
    const surchargeNew = grossIncome > 5000000 ? taxNew * 0.10 : 0;
    const cessNew = (taxNew + surchargeNew) * 0.04;
    const totalTaxNew = Math.round(taxNew + surchargeNew + cessNew);

    const savings80CRemaining = Math.max(0, 150000 - parseFloat(section_80c));
    const recommended = totalTaxNew < totalTaxOld ? 'new' : 'old';
    const taxSavingsByRegime = Math.abs(totalTaxOld - totalTaxNew);

    return ok(res, {
      gross_income: Math.round(grossIncome),
      old_regime: {
        total_deductions: Math.round(totalDeductionsOld),
        taxable_income: Math.round(taxableOld),
        tax_before_cess: Math.round(taxOld),
        total_tax: totalTaxOld,
        effective_rate: grossIncome > 0 ? Math.round((totalTaxOld / grossIncome) * 100 * 10) / 10 : 0,
        monthly_tds: Math.round(totalTaxOld / 12),
      },
      new_regime: {
        total_deductions: stdDeductionNew,
        taxable_income: Math.round(taxableNew),
        tax_before_cess: Math.round(taxNew),
        total_tax: totalTaxNew,
        effective_rate: grossIncome > 0 ? Math.round((totalTaxNew / grossIncome) * 100 * 10) / 10 : 0,
        monthly_tds: Math.round(totalTaxNew / 12),
      },
      recommended,
      tax_savings_by_switching: taxSavingsByRegime,
      suggestions: [
        ...(savings80CRemaining > 0 ? [`Invest ₹${savings80CRemaining.toLocaleString('en-IN')} more in 80C (ELSS/PPF) to maximize old-regime deduction`] : []),
        ...(deductionNPS < 50000 ? [`Contribute ₹${(50000-deductionNPS).toLocaleString('en-IN')} to NPS for extra ₹50K deduction (Section 80CCD(1B))`] : []),
        ...(recommended === 'new' ? ['New regime saves you more — avoid unnecessary old-regime lock-ins'] : ['Old regime is better — maximize your deductions']),
      ],
    });
  } catch (e: any) {
    console.error('[tax-estimate]', e.message);
    return fail(res, 'Server error', 500);
  }
});

app.listen(PORT, () => console.log(`[analytics-service] running on :${PORT}`));
export default app;
