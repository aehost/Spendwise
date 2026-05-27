# Database Schema Reference

Database: **PostgreSQL 17**  
Schema file: `database/schema.sql`

---

## Tables

### `users`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | gen_random_uuid() |
| `email` | TEXT UNIQUE | Lowercased |
| `password_hash` | TEXT | bcrypt cost=12 |
| `name` | TEXT | Display name |
| `phone` | TEXT | Optional |
| `role` | TEXT | `user` / `admin` / `support` |
| `is_active` | BOOLEAN | Default true |
| `is_verified` | BOOLEAN | Email verification |
| `currency_code` | TEXT | Default INR |
| `locale` | TEXT | Default en-IN |
| `last_login_at` | TIMESTAMPTZ | Updated on login |
| `created_at` | TIMESTAMPTZ | Auto |
| `updated_at` | TIMESTAMPTZ | Auto |

---

### `user_sessions`
Stores refresh tokens for revocation.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID FK → users | |
| `refresh_token` | TEXT UNIQUE | Hashed JWT |
| `expires_at` | TIMESTAMPTZ | |
| `is_revoked` | BOOLEAN | |
| `created_at` | TIMESTAMPTZ | |

---

### `bank_accounts`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID FK → users | |
| `bank_name` | TEXT | e.g. HDFC Bank |
| `account_type` | TEXT | savings / current / salary |
| `account_last4` | TEXT | Last 4 digits |
| `balance` | NUMERIC(15,2) | Current balance |
| `color` | TEXT | Hex color for UI |
| `name` | TEXT | User-given nickname |
| `is_active` | BOOLEAN | |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

---

### `credit_cards`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID FK → users | |
| `bank_name` | TEXT | |
| `card_name` | TEXT | e.g. HDFC Regalia |
| `card_last4` | TEXT | |
| `limit_amount` | NUMERIC(15,2) | Credit limit |
| `outstanding` | NUMERIC(15,2) | Current outstanding |
| `billing_date` | INT | Day of month |
| `due_date` | INT | Payment due day |
| `is_active` | BOOLEAN | |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

---

### `loans`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID FK → users | |
| `lender_name` | TEXT | Bank / NBFC name |
| `loan_type` | TEXT | personal / home / car / education / business |
| `principal` | NUMERIC(15,2) | Original loan amount |
| `outstanding` | NUMERIC(15,2) | Remaining balance |
| `emi_amount` | NUMERIC(15,2) | Monthly EMI |
| `interest_rate` | NUMERIC(6,2) | Annual % |
| `start_date` | DATE | |
| `end_date` | DATE | |
| `tenure_months` | INT | |
| `loan_account_no` | TEXT | Partial account number |
| `is_active` | BOOLEAN | |
| `created_at` | TIMESTAMPTZ | |

---

### `transactions`
Core table — all financial transactions.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID FK → users | |
| `amount` | NUMERIC(15,2) | Always positive |
| `merchant` | TEXT | |
| `category_slug` | TEXT | food / shopping / travel / etc. |
| `transaction_date` | DATE | |
| `note` | TEXT | User note |
| `is_waste` | BOOLEAN | Marked as wasteful spend |
| `is_pending` | BOOLEAN | Not yet settled |
| `is_credit` | BOOLEAN | True = money received |
| `loan_id` | UUID FK → loans | If EMI transaction |
| `credit_card_id` | UUID FK → credit_cards | If CC transaction |
| `bank_account_id` | UUID FK → bank_accounts | |
| `contact_name` | TEXT | For P2P transfers |
| `sms_raw` | TEXT | Original SMS (first 500 chars) |
| `sms_id` | TEXT | SMS message ID for dedup |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

**Indexes:**
- `(user_id, transaction_date DESC)` — list queries
- `(user_id, category_slug)` — category filter
- `(user_id, sms_id)` — deduplication

---

### `support_tickets`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID FK → users | Reporter |
| `subject` | TEXT | |
| `description` | TEXT | |
| `status` | TEXT | open / in_progress / resolved / closed |
| `priority` | TEXT | low / medium / high / urgent |
| `category` | TEXT | billing / technical / account / other |
| `assigned_to` | UUID FK → users | Support agent |
| `resolved_at` | TIMESTAMPTZ | |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

---

### `ticket_messages`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `ticket_id` | UUID FK → support_tickets | |
| `sender_id` | UUID FK → users | |
| `message` | TEXT | |
| `is_internal` | BOOLEAN | Internal agent note |
| `created_at` | TIMESTAMPTZ | |

---

### `audit_log`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID FK → users | Who performed the action |
| `action` | TEXT | CREATE / UPDATE / DELETE / LOGIN / etc. |
| `resource_type` | TEXT | transaction / user / ticket / etc. |
| `resource_id` | TEXT | ID of affected resource |
| `metadata` | JSONB | Additional context |
| `ip_address` | TEXT | |
| `created_at` | TIMESTAMPTZ | |

---

## Category Slugs

| Slug | Description |
|---|---|
| `food` | Restaurants, food delivery |
| `groceries` | Supermarkets, online grocery |
| `shopping` | E-commerce, retail |
| `travel` | Flights, trains, cabs, hotels |
| `fuel` | Petrol, diesel, CNG |
| `entertainment` | Streaming, movies, events |
| `health` | Pharmacy, doctor, gym |
| `bills` | Electricity, internet, telecom, DTH |
| `emi` | Loan EMI, credit card payment |
| `investments` | Mutual funds, stocks, SIP |
| `insurance` | Life, health, motor insurance |
| `education` | Courses, fees, tuition |
| `income` | Salary, refund, cashback |
| `family` | P2P transfer to family |
| `other` | Unclassified |

---

## Useful Queries

### Monthly spending by category
```sql
SELECT category_slug, SUM(amount) as total, COUNT(*) as count
FROM transactions
WHERE user_id = 'USER_ID'
  AND transaction_date BETWEEN '2025-01-01' AND '2025-01-31'
  AND is_credit = FALSE
GROUP BY category_slug
ORDER BY total DESC;
```

### Top merchants by spend
```sql
SELECT merchant, SUM(amount) as total, COUNT(*) as count
FROM transactions
WHERE user_id = 'USER_ID'
GROUP BY merchant
ORDER BY total DESC
LIMIT 10;
```

### Monthly trend (6 months)
```sql
SELECT DATE_TRUNC('month', transaction_date) as month,
       SUM(CASE WHEN is_credit=FALSE THEN amount ELSE 0 END) as debit,
       SUM(CASE WHEN is_credit=TRUE THEN amount ELSE 0 END) as credit
FROM transactions
WHERE user_id = 'USER_ID'
  AND transaction_date >= NOW() - INTERVAL '6 months'
GROUP BY month
ORDER BY month;
```
