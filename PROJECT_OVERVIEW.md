# SpendWise — Project Overview

**A privacy-first, automatic personal finance companion for India.**
Prepared for stakeholders & investors · Version 5.0

---

## 1. Executive Summary

SpendWise turns the messy reality of personal money management into an
effortless, automatic, and motivating daily experience. It **reads the bank
SMS and email receipts users already receive**, converts them into clean,
categorized transactions **without any manual entry**, and layers on budgets,
goals, a financial-health score, and gentle gamification to actually change
behavior.

Unlike account-aggregation apps that demand bank logins or screen-scraping,
SpendWise works from data the user already owns — **on-device, with no
bank credentials, no OAuth account linking, and encrypted local storage.**
The result is a finance app that is *zero-effort to maintain, private by
design, and genuinely useful from day one.*

---

## 2. The Problem

Personal finance is broken for the average earner — especially in India,
where spending is fragmented across UPI, cards, net-banking, wallets, and EMIs.

| # | Problem | Why it hurts |
|---|---------|--------------|
| **P1** | **Manual tracking fails.** Every popular app expects users to log expenses by hand. | 80%+ of users abandon manual logging within weeks. Data goes stale, insights become useless. |
| **P2** | **Money is fragmented.** A single person transacts via HDFC UPI, an ICICI card, an SBI account, NEFT, and wallet apps. | No single source of truth. People genuinely don't know where their money went. |
| **P3** | **Privacy fear.** Aggregator apps ask for net-banking credentials or read-only bank links. | Users (rightly) distrust handing over bank logins to a third party. |
| **P4** | **No proactive guidance.** Bank apps show balances, not behavior. | Users discover overspending *after* the month ends — too late to act. |
| **P5** | **Bills & dues slip.** Credit-card due dates, rent, subscriptions are scattered across inboxes. | Missed payments → late fees, interest, credit-score damage. |
| **P6** | **No motivation loop.** Budgeting feels like punishment, so people quit. | Without positive reinforcement, good habits don't stick. |
| **P7** | **Goals stay abstract.** "Save for a trip" never becomes a concrete monthly plan. | Saving is aspirational, not actionable. |

---

## 3. The Solution — SpendWise

SpendWise solves each problem with a concrete, shipped capability:

| Problem | SpendWise Solution | How it solves it |
|---------|--------------------|------------------|
| **P1 — Manual tracking fails** | **Automatic SMS + Gmail import.** A bank debit/credit SMS or email receipt becomes a categorized transaction within seconds — no typing. | A background engine parses 14+ Indian banks' SMS formats and bank emails, extracts amount/merchant/category, and de-duplicates. |
| **P2 — Fragmented money** | **One unified ledger.** UPI, cards, net-banking, NEFT/IMPS/RTGS, wallets — all captured into a single timeline. | Sender-pattern matching across HDFC, ICICI, SBI, Axis, Kotak, Yes, IndusInd, IDFC, Federal, Canara, PNB, RBL, HSBC, Citi, AmEx, Standard Chartered + more. |
| **P3 — Privacy fear** | **Zero bank credentials. On-device + encrypted.** Gmail uses an *App Password* (never the real password); SMS is read locally; tokens & secrets sit in `EncryptedSharedPreferences` (AES-256). No bank login, ever. | Nothing leaves the device except the user's own anonymized transactions to their own account. `allowBackup=false`, HTTPS-only, no analytics SDKs. |
| **P4 — No proactive guidance** | **Predictive alerts & daily pulse.** The app projects month-end spend, warns *before* overspending, and sends an 8 AM daily budget summary. | Background workers compute burn-rate, project month-end against the *discretionary* budget (salary × (1 − savings rate)), and push notifications at 80% / 100% category thresholds. |
| **P5 — Bills slip** | **Auto-detected bill reminders.** Credit-card statements and recurring bills are detected from email/SMS and surfaced with due-date reminders. | A parser classifies statement/due emails; a reminder worker notifies before the due date; outstanding balances update automatically. |
| **P6 — No motivation** | **Gamification that sticks.** XP, levels, daily challenges, spending streaks, and round-up savings turn discipline into a game. | A daily-challenge engine, streak tracking, and XP/level system reward staying under budget. |
| **P7 — Abstract goals** | **Goal planner with a concrete plan.** Set a target & deadline; SpendWise computes the required monthly contribution, ETA, on-track status, and milestone notifications (25/50/75/100%). | Each goal shows an animated progress ring, an achievement roadmap, and "on track / at risk" intelligence based on real savings capacity. |

---

## 4. Key Features

**Automatic capture**
- Real-time bank SMS parsing (14+ banks, all rails: UPI/card/NEFT/IMPS/RTGS/ATM)
- Gmail receipt & statement import every 30 minutes (IMAP, App Password)
- Smart categorization (merchant database + fuzzy matching + keyword engine)
- Duplicate detection across SMS and email sources

**Plan & control**
- Per-category monthly budgets with color-coded progress (green → amber → red)
- Budget alerts via push notification at 80% and 100%
- Net-worth tracker (assets − liabilities)
- Cash-flow & what-if simulator, debt-payoff (snowball/avalanche), tax planning

**Insight & motivation**
- Home dashboard: daily budget, savings rate, EMI burden, burn-rate, projections
- Financial Health Score (0–100) with color-banded gauge and pillar breakdown
- Monthly report: category breakdown, top merchants, waste analysis, cost-reduction tips
- XP / levels, daily challenges, spending streaks, round-up savings
- Home-screen widget for at-a-glance daily budget

**Goals & obligations**
- Goal planner with rings, ETA, required-monthly-savings, milestone alerts
- Auto-detected bill reminders and credit-card due tracking
- IOU tracker, loans, credit cards

---

## 5. Why It Wins — Differentiation

| | SpendWise | Manual apps (Walnut-style) | Aggregators (account-link) |
|---|-----------|----------------------------|----------------------------|
| Effort to maintain | **Zero** (auto-import) | High (manual logging) | Low |
| Bank credentials needed | **None** | None | **Yes** (high friction + fear) |
| Privacy | **On-device, encrypted** | On-device | Cloud-linked to bank |
| Proactive alerts | **Yes (predictive)** | Rare | Limited |
| Motivation loop | **Yes (gamified)** | No | No |
| India-first (UPI, EMI, lakhs format) | **Yes** | Partial | Partial |

**Core moat:** the combination of *zero-effort automatic capture* + *no bank
credentials* + *proactive, gamified guidance* — privacy and effortlessness at
the same time, which the incumbents trade off against each other.

---

## 6. Benefits to the User

- **Saves time:** no manual logging — ever.
- **Saves money:** proactive alerts stop overspending *before* it happens; waste analysis and cost-reduction tips surface leaks.
- **Builds wealth:** goals become concrete monthly plans; round-up and savings-rate nudges compound.
- **Reduces stress:** bills never slip; one screen shows true financial health.
- **Protects privacy:** no bank logins, encrypted on-device storage, no data resale.
- **Keeps users engaged:** streaks, XP, and challenges make good habits feel rewarding.

---

## 7. Technical Foundation (high level)

- **Platform:** Native Android (Kotlin, Jetpack Compose, MVVM, Hilt DI).
- **Automation:** WorkManager background workers (SMS sync, Gmail sync, daily pulse, predictive alerts, weekly review, bill reminders, goal milestones).
- **Privacy & security:** `EncryptedSharedPreferences` (AES-256-GCM), HTTPS-only with certificate validation, JWT with silent refresh, `allowBackup=false`, no third-party analytics.
- **Reliability:** retry caps and de-duplication on every ingestion path; 1,000+ documented test cases and a passing unit-test suite covering the parsing and view-model layers.
- **Backend:** REST API (auth, transactions, budgets, goals, intelligence) with month-scoped queries so reporting is always period-accurate.

---

## 8. Status & Quality

- **Feature-complete v5.0** across capture, budgets, goals, reports, health score, gamification, and obligations.
- **Quality:** 1,006-case master test suite; 115 automated unit tests passing; a full senior-architect bug audit closed **25 defects with 0 open**.
- **Design:** a unified, premium dark UI with a shared component library (animated progress rings, consistent cards and bars) across all screens.

---

## 9. Roadmap (indicative)

- **Near term:** recurring monthly budgets (auto-carry-forward), richer charts, on-device ML categorization, multi-currency.
- **Mid term:** shared/family budgets, investment & SIP tracking, credit-score insights, iOS app.
- **Long term:** proactive savings automation, marketplace of money-saving offers, optional advisor layer.

---

## 10. The Ask / Opportunity

India has 300M+ digitally-active earners drowning in fragmented, manual
money management. SpendWise is the rare app that is **effortless *and*
private** — the two qualities users refuse to trade off. With automatic
capture already working across the country's major banks and a motivation
loop that drives retention, SpendWise is positioned to become the default
financial companion for the UPI generation.

*Prepared by the SpendWise team. Contact for a live demo and detailed metrics.*
