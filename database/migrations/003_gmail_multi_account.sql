-- ── Gmail multi-account support ───────────────────────────────
-- Replaces the single-account gmail_* columns on users with a
-- per-account table so users can connect multiple Gmail addresses.
CREATE TABLE IF NOT EXISTS gmail_accounts (
  id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  gmail_email     VARCHAR(255) NOT NULL,
  access_token    TEXT,
  refresh_token   TEXT,
  token_expiry    TIMESTAMPTZ,
  last_synced_at  TIMESTAMPTZ,
  is_active       BOOLEAN      DEFAULT TRUE,
  created_at      TIMESTAMPTZ  DEFAULT NOW(),
  UNIQUE(user_id, gmail_email)
);

CREATE INDEX IF NOT EXISTS idx_gmail_accounts_user ON gmail_accounts(user_id);
