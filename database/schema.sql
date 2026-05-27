-- ============================================================
--  SpendWise Database Schema (PostgreSQL / Supabase)
--  Run this once to initialize the database.
--  Compatible with Supabase free tier (no extensions needed).
-- ============================================================

-- ── USERS ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
  id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  email         VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  name          VARCHAR(255) DEFAULT '',
  phone         VARCHAR(20),
  currency_code VARCHAR(10)  DEFAULT 'INR',
  locale        VARCHAR(10)  DEFAULT 'en-IN',
  role          VARCHAR(20)  DEFAULT 'user',  -- user | admin | support
  is_active     BOOLEAN      DEFAULT TRUE,
  is_verified   BOOLEAN      DEFAULT FALSE,
  last_login_at TIMESTAMPTZ,
  sms_scan_from_ms BIGINT   DEFAULT 0,
  created_at    TIMESTAMPTZ  DEFAULT NOW(),
  updated_at    TIMESTAMPTZ  DEFAULT NOW()
);

-- ── SESSIONS (refresh tokens) ─────────────────────────────────
CREATE TABLE IF NOT EXISTS user_sessions (
  id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  refresh_token VARCHAR(500) UNIQUE NOT NULL,
  device_info   JSONB,
  ip_address    TEXT,
  created_at    TIMESTAMPTZ DEFAULT NOW(),
  expires_at    TIMESTAMPTZ NOT NULL,
  is_revoked    BOOLEAN     DEFAULT FALSE
);

-- ── BANK ACCOUNTS ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS bank_accounts (
  id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id            UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name               VARCHAR(255) NOT NULL,
  last_four          CHAR(4),
  balance            DECIMAL(15,2) DEFAULT 0,
  balance_updated_at TIMESTAMPTZ,
  color              VARCHAR(7)   DEFAULT '#6C63FF',
  is_active          BOOLEAN      DEFAULT TRUE,
  created_at         TIMESTAMPTZ  DEFAULT NOW(),
  updated_at         TIMESTAMPTZ  DEFAULT NOW()
);

-- ── CREDIT CARDS ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS credit_cards (
  id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name         VARCHAR(255) NOT NULL,
  credit_limit DECIMAL(15,2) DEFAULT 0,
  outstanding  DECIMAL(15,2) DEFAULT 0,
  due_day      INT          CHECK (due_day BETWEEN 1 AND 31),
  min_due      DECIMAL(15,2) DEFAULT 0,
  color        VARCHAR(7)   DEFAULT '#EC4899',
  is_active    BOOLEAN      DEFAULT TRUE,
  created_at   TIMESTAMPTZ  DEFAULT NOW(),
  updated_at   TIMESTAMPTZ  DEFAULT NOW()
);

-- ── LOANS ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS loans (
  id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name             VARCHAR(255) NOT NULL,
  emi_amount       DECIMAL(15,2) DEFAULT 0,
  interest_rate    DECIMAL(5,2)  DEFAULT 0,
  outstanding      DECIMAL(15,2) DEFAULT 0,
  months_remaining INT           DEFAULT 0,
  color            VARCHAR(7)   DEFAULT '#EF4444',
  is_active        BOOLEAN      DEFAULT TRUE,
  created_at       TIMESTAMPTZ  DEFAULT NOW(),
  updated_at       TIMESTAMPTZ  DEFAULT NOW()
);

-- ── CATEGORIES (seeded) ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS categories (
  id        SERIAL      PRIMARY KEY,
  slug      VARCHAR(50) UNIQUE NOT NULL,
  name      VARCHAR(100) NOT NULL,
  icon      VARCHAR(10),
  is_system BOOLEAN     DEFAULT TRUE
);

-- ── TRANSACTIONS ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS transactions (
  id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  amount           DECIMAL(15,2) NOT NULL,
  merchant         VARCHAR(255)  DEFAULT 'Unknown',
  category_slug    VARCHAR(50)   DEFAULT 'other',
  transaction_date DATE          NOT NULL,
  note             TEXT          DEFAULT '',
  is_waste         BOOLEAN       DEFAULT FALSE,
  is_pending       BOOLEAN       DEFAULT TRUE,
  is_credit        BOOLEAN       DEFAULT FALSE,
  loan_id          UUID          REFERENCES loans(id) ON DELETE SET NULL,
  credit_card_id   UUID          REFERENCES credit_cards(id) ON DELETE SET NULL,
  bank_account_id  UUID          REFERENCES bank_accounts(id) ON DELETE SET NULL,
  contact_name     VARCHAR(255),
  sms_raw          TEXT,
  sms_id           VARCHAR(50),
  created_at       TIMESTAMPTZ   DEFAULT NOW(),
  updated_at       TIMESTAMPTZ   DEFAULT NOW()
);

-- ── SALARY CONFIG ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS salary_config (
  id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID         UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  amount       DECIMAL(15,2) DEFAULT 0,
  expected_day INT          DEFAULT 1 CHECK (expected_day BETWEEN 1 AND 31),
  updated_at   TIMESTAMPTZ  DEFAULT NOW()
);

-- ── SALARY HISTORY ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS salary_history (
  id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  amount        DECIMAL(15,2) NOT NULL,
  received_date DATE          NOT NULL,
  note          TEXT          DEFAULT '',
  created_at    TIMESTAMPTZ   DEFAULT NOW()
);

-- ── MANDATORY BILLS ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS mandatory_bills (
  id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name            VARCHAR(255)  NOT NULL,
  icon            VARCHAR(10)   DEFAULT '💡',
  amount          DECIMAL(15,2) DEFAULT 0,
  due_day         INT           DEFAULT 1 CHECK (due_day BETWEEN 1 AND 31),
  paid_this_month BOOLEAN       DEFAULT FALSE,
  paid_at         TIMESTAMPTZ,
  created_at      TIMESTAMPTZ   DEFAULT NOW(),
  updated_at      TIMESTAMPTZ   DEFAULT NOW()
);

-- ── MONTHLY BUDGETS ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS monthly_budgets (
  id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category_slug VARCHAR(50)   NOT NULL,
  amount        DECIMAL(15,2) DEFAULT 0,
  month         INT           CHECK (month BETWEEN 1 AND 12),
  year          INT,
  created_at    TIMESTAMPTZ   DEFAULT NOW(),
  updated_at    TIMESTAMPTZ   DEFAULT NOW(),
  UNIQUE(user_id, category_slug, month, year)
);

-- ── INVESTMENTS ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS investments (
  id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name            VARCHAR(255)  NOT NULL,
  monthly_amount  DECIMAL(15,2) DEFAULT 0,
  current_balance DECIMAL(15,2) DEFAULT 0,
  is_active       BOOLEAN       DEFAULT TRUE,
  created_at      TIMESTAMPTZ   DEFAULT NOW(),
  updated_at      TIMESTAMPTZ   DEFAULT NOW()
);

-- ── SUPPORT TICKETS ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS support_tickets (
  id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID         REFERENCES users(id) ON DELETE SET NULL,
  subject     VARCHAR(500) NOT NULL,
  description TEXT         NOT NULL,
  status      VARCHAR(50)  DEFAULT 'open',      -- open | in_progress | resolved | closed
  priority    VARCHAR(20)  DEFAULT 'medium',     -- low | medium | high | critical
  assigned_to UUID         REFERENCES users(id) ON DELETE SET NULL,
  category    VARCHAR(100),
  created_at  TIMESTAMPTZ  DEFAULT NOW(),
  updated_at  TIMESTAMPTZ  DEFAULT NOW(),
  resolved_at TIMESTAMPTZ
);

-- ── TICKET MESSAGES ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ticket_messages (
  id          UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_id   UUID    NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
  sender_id   UUID    REFERENCES users(id) ON DELETE SET NULL,
  message     TEXT    NOT NULL,
  is_internal BOOLEAN DEFAULT FALSE,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ── AUDIT LOG ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_log (
  id            UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID    REFERENCES users(id) ON DELETE SET NULL,
  action        VARCHAR(100) NOT NULL,
  resource_type VARCHAR(50),
  resource_id   TEXT,
  metadata      JSONB,
  ip_address    TEXT,
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- ── INDEXES ───────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_transactions_user_date   ON transactions(user_id, transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_category    ON transactions(user_id, category_slug);
CREATE INDEX IF NOT EXISTS idx_transactions_pending     ON transactions(user_id, is_pending) WHERE is_pending = TRUE;
CREATE INDEX IF NOT EXISTS idx_transactions_sms_id      ON transactions(user_id, sms_id) WHERE sms_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_sessions_refresh         ON user_sessions(refresh_token) WHERE is_revoked = FALSE;
CREATE INDEX IF NOT EXISTS idx_sessions_user            ON user_sessions(user_id, is_revoked);
CREATE INDEX IF NOT EXISTS idx_bank_accounts_user       ON bank_accounts(user_id) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_credit_cards_user        ON credit_cards(user_id) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_loans_user               ON loans(user_id) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_tickets_status           ON support_tickets(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tickets_assigned         ON support_tickets(assigned_to, status);
CREATE INDEX IF NOT EXISTS idx_audit_user               ON audit_log(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_action             ON audit_log(action, created_at DESC);

-- ── SEED CATEGORIES ───────────────────────────────────────────
INSERT INTO categories (slug, name, icon) VALUES
  ('food',          'Food & Dining',    '🍽️'),
  ('fuel',          'Fuel',             '⛽'),
  ('shopping',      'Shopping',         '🛍️'),
  ('bills',         'Bills & Utilities','💡'),
  ('emi',           'EMI / Loan',       '🏦'),
  ('entertainment', 'Entertainment',    '🎬'),
  ('health',        'Health',           '💊'),
  ('travel',        'Travel',           '✈️'),
  ('family',        'Family / Friends', '👨‍👩‍👧'),
  ('investment',    'Investment / SIP', '📈'),
  ('income',        'Income / Salary',  '💰'),
  ('savings',       'Savings',          '🏆'),
  ('waste',         'Wasteful',         '🗑️'),
  ('other',         'Other',            '📦')
ON CONFLICT (slug) DO NOTHING;

-- ── SEED DEFAULT ADMIN ────────────────────────────────────────
-- Password: Admin@SpendWise2025  (bcrypt hash, cost=12)
INSERT INTO users (email, password_hash, name, role, is_verified)
VALUES (
  'admin@spendwise.app',
  '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/lewXrwLd3qNMTBTJa',
  'SpendWise Admin',
  'admin',
  TRUE
) ON CONFLICT (email) DO NOTHING;
