-- ── Password-reset OTP fields ─────────────────────────────────
-- Added for forgot-password / reset-password flow.
-- OTP is a 6-digit code stored hashed or plain; expires in 15 min.
ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_otp            VARCHAR(6);
ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_otp_expires_at TIMESTAMPTZ;
