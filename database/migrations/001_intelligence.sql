-- ============================================================
--  Migration 001 — Intelligence & Gmail integration
--  Run once against your Supabase / PostgreSQL database.
-- ============================================================

-- Auto-detected flag + category on bills
ALTER TABLE mandatory_bills
  ADD COLUMN IF NOT EXISTS is_auto_detected BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS category         VARCHAR(50) DEFAULT 'bills',
  ADD COLUMN IF NOT EXISTS is_paid_this_month BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_active        BOOLEAN DEFAULT TRUE;

-- Gmail OAuth tokens (stored per user, never exposed via API)
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS gmail_connected        BOOLEAN    DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS gmail_email            VARCHAR(255),
  ADD COLUMN IF NOT EXISTS gmail_access_token     TEXT,
  ADD COLUMN IF NOT EXISTS gmail_refresh_token    TEXT,
  ADD COLUMN IF NOT EXISTS gmail_token_expiry     TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS gmail_last_synced_at   TIMESTAMPTZ;

-- Financial goals
CREATE TABLE IF NOT EXISTS financial_goals (
  id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title           VARCHAR(255)  NOT NULL,
  description     TEXT          DEFAULT '',
  target_amount   DECIMAL(15,2) NOT NULL,
  current_amount  DECIMAL(15,2) DEFAULT 0,
  deadline        DATE,
  category_slug   VARCHAR(50)   DEFAULT 'savings',
  icon            VARCHAR(10)   DEFAULT '🎯',
  color           VARCHAR(7)    DEFAULT '#6C63FF',
  is_completed    BOOLEAN       DEFAULT FALSE,
  auto_contribute BOOLEAN       DEFAULT FALSE,   -- auto-deduct from salary
  monthly_target  DECIMAL(15,2) DEFAULT 0,
  created_at      TIMESTAMPTZ   DEFAULT NOW(),
  updated_at      TIMESTAMPTZ   DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_financial_goals_user  ON financial_goals(user_id);
CREATE INDEX IF NOT EXISTS idx_mandatory_bills_user  ON mandatory_bills(user_id);
