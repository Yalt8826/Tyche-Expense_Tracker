# Progress Log

Plain-language notes, 3 lines per milestone (newest first).

## 2026-09-27 — Home redesign (greeting, proportion bar, tags)

1. Home rebuilt per request: greeting + name + avatar (tap to rename, DataStore-backed) at top; review banner moved BELOW the hero; hero amount + "after refunds" label kept.
2. Tag proportion bar under the hero: thin multi-color divider (segment width = tag share, confirmed expenses only) + legend with display names, percentages and amounts, "Share of confirmed spending" caption and an "N more · x%" aggregate row; tags are color-based, "+ New tag" creates a user_* category with a chosen palette color; existing tags render as a chip row.
3. On-device verification caught a flow race (slices computed before categories loaded → all-violet legend) — fixed by combining flows; 44 tests green (home slices + slug tests added); verified via UI dump on the A05.

## 2026-09-27 — Audit fixes R1–R5, R7 implemented

1. Review Inbox rebuilt: payee-grouped cards ("84 pending · 50 payees"), initial avatars, latest-day line, group totals, always-visible quick chips (Food/Transport/Shopping/Bills), category-picker sheet for "Other", batch confirm with UNDO snackbar, and confirm-chips inside the transaction detail sheet.
2. Transactions: month strip (All/Sep/Aug…), friendly sticky day headers (Today/Yesterday/18 Sep), merchant-initial avatars on unconfirmed rows, exact-paise amounts; Budgets: category names + icons + state-colored progress bars, "4× of budget" instead of "388%"; hero relabeled "after refunds"; FAB clearance padding everywhere.
3. 42 tests green (5 new for grouping/day-labels/month-options/decimal policy); installed and visually verified on the A05; chip-row overflow fixed with horizontal scroll.

## 2026-09-27 — REAL SMS CAPTURE WORKING (KotAK)

1. Yashas's missed transaction diagnosed via adb DB pull: bank is Kotak (AD-/AX-/VM-KOTAKB-S senders) — not in the seeded allowlist, and its "Sent Rs.X … to <Payee> … UPI Ref Y" format needed "sent" as a debit verb + "to" merchant anchor. All fixed + fixture-tested.
2. Allowlist now contains-matches (DLT prefix variants), account resolution prefers sender over mask, bank seeding is per-bank idempotent, and a debug-only inbox reprocessor back-fills history (adb one-liner; stripped from release).
3. Result: 83 real transactions over 21 days captured into Review Inbox (91 raw SMS stored, 102 promos/OTPs correctly ignored); live SMS_RECEIVED capture now active for future transactions.

## 2026-09-26 — Demo data pass + on-device fixes

1. DEBUG-only demo seeder added: 40 days of realistic transactions (13 merchants, salaries, a self-transfer pair, a refund, 2 review items) + sample budgets; deterministic, wipeable via the `demo` tag.
2. Populated screens exposed 3 real bugs, all fixed & tested (41 green): donut ellipse→circle with per-slice palette, daily-series SQL collapsing to day 0 (epochDay double-division), budget cards ignoring subcategory spend (roll-up added).
3. Fixed APK built and waiting on athena — phone dropped off adb mid-install; reinstall pending.

## 2026-09-26 — P5 analytics + budgets GREEN

1. Analytics dashboard live: 1M/3M/6M/1Y period selector, income-vs-expense hero bars + spending trend line (Vico), custom Canvas donut with tap-to-drill center readout (signature interaction #2), top merchants, deterministic insight cards (no ML).
2. Budgets live: dual-progress radial gauge (spend vs calendar pace) with plain verdicts + end-of-month projection, per-category budget cards with On track / Almost there / Over budget states, tap-to-edit sheet (overall + per-category), all persisted in Room.
3. 36 tests green (5 new: month bucketing, insights, budget states, pace projection); APK installed on the physical A05 via adb — phone becomes the real-SMS test bed from here.

## 2026-09-26 — P4 core UI GREEN

1. Transactions ledger screen live: free-text search with live result summary, day-ready list with provenance chips (✓ auto / ⚠ review / ✎ manual), detail sheet with plain-facts explainability + delete, friendly empty states.
2. Review Inbox shipped as a filtered Transactions mode: suggested-category cards with one-tap confirm (reason text included), [Keep both]/[Merge] duplicate cards, "Mark as transfer" pair cards — plus Home review banner with live count.
3. Quick-add sheet (₹-first, category chips) + Home hero (September 2026 · ₹0 · "Spent this month") verified ON-DEVICE via screenshot: install, cold launch, tab navigation all healthy; suggestion engine wired (rules → dictionary → history).

## 2026-09-26 — P3 capture pipeline GREEN

1. Funnel live end-to-end: SmsReceiver (SMS_RECEIVED, goAsync-safe) → sender allowlist (before any disk write) → cheap screen → field extraction (amount/mask/UTR/balance/merchant/VPA/date) → Room as AUTO_REVIEW; no INTERNET permission, receiver gated by BROADCAST_SMS.
2. Template induction working: digit-masked signatures stored per confirmed message; learnTemplate + sibling ingest carries the template key; seeded redacted fixtures for HDFC/SBI/ICICI/Axis.
3. Dedup layered: raw-SMS digest (carrier redeliveries) + ledger (account, UTR) unique index; 31 tests green including the "credit card" direction trap (fixture-caught, fixed with lookahead).

## 2026-09-26 — P2 data layer GREEN

1. Room schema v1 live: 9 entities (transactions ledger, accounts, categories, tags+cross-ref, rules, budgets, templates, raw SMS), paise-Long amounts, exported schema committed at `app/schemas/`, DB tests via Robolectric.
2. Ledger invariants unit-tested (8 tests): transfer pairing/exclusion (±30 min window, cross-account), refund netting into source category (partial + unmatched cases), paise exactness, duplicate suspicion (UTR-aware), Indian ₹ grouping.
3. DAO/Room layer verified against real SQLite: (account, UTR) unique dedup enforced, NULL-UTR rows never collide, SQL aggregates exclude transfers and net refunds; 12 tests total green + APK builds.

## 2026-09-26 — P1 scaffold GREEN

1. Navigation skeleton live: 4 tabs (Home/Transactions/Analytics/Budgets) with state-preserving switching + quick-add FAB in the dark violet/cyan M3 theme; MainActivity hosts the Compose app shell.
2. Emulator gate passed on athena: created headless AVD `test` (API 36 x86_64), connectedDebugAndroidTest green (instrumented smoke), app installed, cold-launched (PID confirmed), screenshot shows correct dark shell.
3. Placeholder bodies on all four tabs; real screens arrive P4 (Home/Transactions) and P5 (Analytics/Budgets).

## 2026-09-26 — P0 toolchain GREEN

1. Athena toolchain verified end-to-end: Gradle 9.6.0 wrapper + JDK 26 + AGP 9.4.1 + KSP 2.3.12; `./gradlew --version`, `assembleDebug` and unit tests all pass; `app-debug.apk` produced.
2. Project scaffold in place: version catalog, Compose/M3 dark theme tokens (violet/cyan per 00 §8), launcher icon, edge-to-edge MainActivity placeholder, JVM + instrumented smoke tests.
3. Gotchas hit & fixed: KSP is decoupled-versioned (2.3.12, not kotlin-suffixed); AGP 9 forbids the `kotlin-android` plugin (built-in Kotlin now); navigation/Vico need compileSdk 37.
