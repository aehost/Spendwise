# SpendWise — Project Overview

## What is SpendWise?

SpendWise is an intelligent personal finance management platform built for the Indian market. It automatically reads bank SMS messages, classifies transactions using a 300+ merchant database, and presents a comprehensive spending dashboard — all with minimal manual intervention.

## Core Features

| Feature | Description |
|---|---|
| **Intelligent SMS Parsing** | Reads bank SMS, extracts amount, merchant, card last-4, loan account, UPI VPA automatically |
| **Auto Merchant Tagging** | 4-tier classification (Exact → UPI Domain → Fuzzy → Keyword) covering 300+ merchants |
| **Transaction Management** | Add, edit, delete, filter, and export transactions |
| **Bank Account Tracking** | Multiple bank accounts with balance tracking |
| **Credit Card Management** | Due dates, outstanding, utilization % |
| **Loan Tracking** | EMI schedule, outstanding balance |
| **Analytics** | Monthly spending breakdown, category insights, 6-month trends |
| **AI Insights** | Rule-based spending insights with personalized recommendations |
| **PDF Export** | Download analytics reports as PDF |
| **Admin Dashboard** | Platform-wide analytics, user management, audit trail |
| **Support Dashboard** | Ticket management, user lookup, agent assignment |

## Target Audience

- Primary: Indian retail banking customers (25–45 age group)
- Languages: English UI, supports Indian banks (HDFC, ICICI, SBI, Axis, Kotak, etc.)
- Platform: Android 8.0+ (API 26+)

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Android App                           │
│   Kotlin + Jetpack Compose + Hilt + Retrofit            │
└────────────────────┬────────────────────────────────────┘
                     │ HTTPS (JWT Bearer)
┌────────────────────▼────────────────────────────────────┐
│                  API Gateway :3000                        │
│   Express.js + JWT Auth + Rate Limiting + CORS          │
└─┬──────────┬──────────┬──────────┬──────────────────────┘
  │          │          │          │
:3001      :3002      :3003      :3004
Auth    Transactions  Users   Analytics
Service   Service    Service   Service
  │          │          │          │
  └──────────┴──────────┴──────────┘
                     │
          ┌──────────▼──────────┐
          │   PostgreSQL 17      │
          │  (Local / Supabase) │
          └─────────────────────┘

  ┌───────────────────┐  ┌────────────────────┐
  │  Admin Dashboard  │  │ Support Dashboard  │
  │  React+Vite:5173  │  │  React+Vite:5174   │
  └───────────────────┘  └────────────────────┘
```

## Technology Stack

### Android App
- **Language**: Kotlin 1.9
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM + Clean Architecture + Repository Pattern
- **DI**: Hilt
- **Networking**: Retrofit 2 + OkHttp
- **Storage**: DataStore (preferences), Room (local cache)
- **SMS**: BroadcastReceiver + WorkManager

### Backend
- **Runtime**: Node.js 20 LTS
- **Language**: TypeScript 5
- **Framework**: Express.js 4
- **Auth**: JWT (access 15m + refresh 30d)
- **Database**: PostgreSQL 17
- **ORM**: raw pg (no ORM — full SQL control)
- **Password**: bcrypt cost=12

### Dashboards
- **Framework**: React 18 + Vite 5
- **Language**: TypeScript
- **Styling**: Tailwind CSS (dark theme)
- **Charts**: Recharts
- **PDF Export**: jsPDF + html2canvas
- **HTTP**: Axios

## Default Credentials

See [06-credentials-and-config.md](06-credentials-and-config.md) for all credentials.

| Role | Email | Password |
|---|---|---|
| Admin | admin@spendwise.app | Admin@SpendWise2025 |
| Support | support@spendwise.app | Support@SpendWise2025 |
| Demo User | demo@spendwise.app | Demo@12345 |
