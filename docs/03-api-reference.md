# API Reference

Base URL: `http://localhost:3000` (development)  
All authenticated endpoints require: `Authorization: Bearer <access_token>`  
All responses follow: `{ "success": true, "data": {...} }` or `{ "success": false, "error": "..." }`

---

## Auth Service (`/auth`)

### POST /auth/register
Create a new user account.

**Request:**
```json
{ "email": "user@example.com", "password": "Password@123", "name": "John Doe" }
```
**Response 201:**
```json
{ "userId": "uuid", "email": "...", "name": "...", "role": "user", "access_token": "...", "refresh_token": "..." }
```

---

### POST /auth/login
Authenticate and receive tokens.

**Request:**
```json
{ "email": "admin@spendwise.app", "password": "Admin@SpendWise2025" }
```
**Response 200:**
```json
{ "access_token": "eyJ...", "refresh_token": "eyJ...", "user": { "userId": "...", "email": "...", "role": "admin" } }
```

---

### POST /auth/refresh
Exchange refresh token for new access token.

**Request:**
```json
{ "refresh_token": "eyJ..." }
```

---

### GET /auth/me
Get current user profile. **Requires Auth.**

**Response:**
```json
{ "userId": "...", "email": "...", "name": "...", "role": "user", "is_active": true }
```

---

### POST /auth/logout
Revoke refresh token. **Requires Auth.**

**Request:**
```json
{ "refresh_token": "eyJ..." }
```

---

### POST /auth/change-password
Change password. **Requires Auth.**

**Request:**
```json
{ "current_password": "old", "new_password": "New@Pass123" }
```

---

## Transaction Service (`/transactions`)

All endpoints require auth.

### GET /transactions
List transactions with filtering and pagination.

**Query Params:**
| Param | Type | Description |
|---|---|---|
| `category` | string | Filter by category_slug |
| `is_pending` | boolean | Filter pending transactions |
| `is_credit` | boolean | Filter credit/debit |
| `start` | date | Start date (YYYY-MM-DD) |
| `end` | date | End date |
| `bank_account_id` | uuid | Filter by account |
| `search` | string | Search merchant / note |
| `page` | number | Page number (default 1) |
| `limit` | number | Results per page (max 200) |

**Response:**
```json
{
  "transactions": [...],
  "total": 248,
  "page": 1,
  "limit": 50,
  "pages": 5
}
```

---

### POST /transactions
Create a transaction.

**Request:**
```json
{
  "amount": 450.00,
  "merchant": "Swiggy",
  "category_slug": "food",
  "transaction_date": "2025-01-15",
  "note": "Dinner",
  "is_waste": false,
  "is_pending": false,
  "is_credit": false,
  "bank_account_id": "uuid",
  "sms_raw": "Rs.450 debited from A/c **1234 at SWIGGY",
  "sms_id": "12345"
}
```
**Response 201:** Created transaction object.

> **Auto-classification**: If `category_slug` is `"other"` or empty, the server automatically classifies using MerchantMatcher.

---

### PUT /transactions/:id
Update a transaction (partial update via COALESCE).

**Request:** Any subset of transaction fields.

---

### DELETE /transactions/:id
Delete a transaction.

**Response:** `{ "deleted": true }`

---

### POST /transactions/batch
Bulk insert SMS-parsed transactions with deduplication.

**Request:**
```json
{
  "transactions": [
    {
      "amount": 250, "merchant": "Zomato", "category_slug": "food",
      "transaction_date": "2025-01-15", "is_credit": false, "is_pending": true,
      "sms_raw": "Rs.250 debited...", "sms_id": "67890"
    }
  ]
}
```
**Response:** `{ "inserted": 1, "skipped": 0 }`

---

### GET /transactions/summary
Monthly spending summary.

**Query Params:** `month` (1-12), `year` (YYYY)

**Response:**
```json
{
  "total_debit": 15000,
  "total_credit": 5000,
  "pending_count": 3,
  "by_category": [
    { "category_slug": "food", "total": 4500, "count": 15 }
  ],
  "month": 1, "year": 2025,
  "start_date": "2025-01-01", "end_date": "2025-01-31"
}
```

---

## User Service (`/users`)

### GET /users/profile
Get own profile.

### PUT /users/profile
Update name, phone, currency_code, locale.

### GET /users/bank-accounts
List bank accounts.

### POST /users/bank-accounts
Create bank account.
```json
{ "bank_name": "HDFC", "account_type": "savings", "account_last4": "1234", "color": "#6C63FF" }
```

### GET /users/credit-cards
List credit cards.

### POST /users/credit-cards
```json
{ "bank_name": "ICICI", "card_name": "Coral", "card_last4": "5678", "limit_amount": 100000, "billing_date": 15, "due_date": 5 }
```

### GET /users/loans
List loans.

### POST /users/loans
```json
{ "lender_name": "HDFC", "loan_type": "personal", "principal": 500000, "emi_amount": 12000, "start_date": "2024-01-01", "tenure_months": 48 }
```

---

## Analytics Service (`/analytics`)

### GET /analytics/dashboard
Personal spending analytics.

**Query Params:** `month`, `year`

---

## Admin Routes (`/admin`) — role: admin

### GET /admin/stats
Platform KPIs: total users, transactions today, active users, open tickets.

### GET /admin/users
All users with pagination, search, role, active filters.

### GET /admin/users/:id
Full user profile with account count, card count, loan count.

### PUT /admin/users/:id/status
`{ "is_active": false }` — suspend/activate user.

### PUT /admin/users/:id/role
`{ "role": "user|support|admin" }` — change role.

### GET /admin/users/:id/transactions
Last N transactions for user. Query: `?limit=20`

### GET /admin/users/:id/accounts
User's bank accounts.

### GET /admin/users/:id/cards
User's credit cards.

### GET /admin/users/:id/analytics
Per-user analytics: monthly spending, by-category, AI insights.

### GET /admin/transactions
All transactions across platform. Filters: `user_email`, `category`, `start`, `end`.

### GET /admin/analytics
Platform-wide analytics: user growth, transaction volume, category breakdown.

### GET /admin/audit
Audit log. Filters: `search` (email), `action`, `resource_type`.

### GET /admin/tickets
All support tickets. Filter: `status`.

### PUT /admin/tickets/:id
`{ "status": "...", "priority": "...", "assigned_to": "uuid" }` — update ticket.

### GET /admin/tickets/:id/messages
Ticket message thread.

### POST /admin/tickets/:id/messages
`{ "message": "..." }` — send message as admin.

### POST /admin/support-agents
Create support agent: `{ "email": "...", "name": "...", "password": "..." }`.

---

## Support Routes (`/support`) — role: admin|support

### GET /support/tickets
All tickets sorted by priority. Filters: `status`, `priority`.

### GET /support/tickets/:id
Single ticket detail.

### GET /support/tickets/:id/messages
Message thread.

### POST /support/tickets/:id/messages
Send reply: `{ "message": "...", "is_internal": false }`.

### PUT /support/tickets/:id
Update status/priority/assigned_to.

### GET /support/users
Search users: `?search=email_or_name`.

### GET /support/users/:id
User profile (limited fields — privacy compliant).

### GET /support/users/:id/transactions
Last transactions (max 50).

---

## Error Codes

| Code | HTTP | Meaning |
|---|---|---|
| `UNAUTHORIZED` | 401 | Missing or invalid token |
| `TOKEN_EXPIRED` | 401 | JWT expired — refresh required |
| `FORBIDDEN` | 403 | Insufficient role |
| `NOT_FOUND` | 404 | Resource doesn't exist |
| `SERVICE_UNAVAILABLE` | 502 | Downstream service unreachable |
