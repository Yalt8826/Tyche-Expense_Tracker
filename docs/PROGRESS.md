# Progress Log

Plain-language notes, 3 lines per milestone (newest first).

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
