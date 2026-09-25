# Technical Architecture — Offline-First Android Expense Tracker

Companion to `00-MASTER-DESIGN-SPEC.md`. Backend/capture module + app architecture for the locked stack (Kotlin + Compose + Room + Vico/custom Canvas). This is the engineering reference the UI assumes; detailed build plans come later.

## 1. Locked stack

| Layer | Choice |
|---|---|
| Language | Kotlin (latest stable) |
| UI | Jetpack Compose + Material 3 |
| Charts | Vico 2.x (line/column) + custom Compose Canvas (donut, gauges, heatmap, sparkline, story) |
| Motion | Compose Animation API + Lottie (selective) |
| Data | Room over SQLite; **paise integers** for all amounts |
| Background | `SMS_RECEIVED` BroadcastReceiver (+ later NotificationListenerService) |
| DI | Hilt (or manual DI if scope stays small — decide at scaffold time) |
| Navigation | Navigation Compose |
| Presentation | ViewModel + StateFlow; unidirectional data flow |
| Permissions | READ_SMS; NO INTERNET permission |
| Distribution | Sideloaded APK, two phones, no Play constraints |

**Zero-network guarantee:** the APK manifest declares no INTERNET permission — privacy claims in the UI (S3) are enforced by the OS, not by policy.

## 2. Module layout (single app, layered)

```text
app/
 ├─ ui/            compose screens, theme tokens, components, charts (custom Canvas)
 ├─ data/
 │   ├─ db/        Room: entities, DAOs, migrations
 │   ├─ capture/   SMS receiver, sender allowlist, parser funnel, template store
 │   ├─ rules/     rule engine, merchant dictionary, suggestion logic
 │   └─ repo/      repositories (TransactionRepo, BudgetRepo, InsightsRepo)
 ├─ domain/        models (Transaction, Category, Tag, Budget, …), use-cases
 │                (ConfirmReview, ApplyRule, DetectTransfer, MatchRefund, …)
 └─ diag/          detection-status aggregation for Settings/Advanced views
```

No multi-gradle-module split in v1 (YAGNI); package-level separation only.

## 3. Room schema (core entities)

```text
TransactionEntity
  id, timestamp (epochMillis), valueDate?, amountPaise (Long),
  type (EXPENSE | INCOME | TRANSFER | REFUND | ADJUSTMENT),
  accountId, merchantName?, vpa?, categoryKey?, note?,
  provenance (AUTO_CONFIRMED | AUTO_REVIEW | MANUAL),
  sourceSmsId?, refundOfTxnId?, transferGroupId?,
  utrRef?, ruleAppliedKey?

SmsRawEntity        id, sender, timestamp, body, digest, parsedState
AccountEntity       id, bankName, last4Mask, smsSenderAllowlist
CategoryEntity      key, name, icon, colorToken, parentId? (subcategories)
TagEntity + TxnTagCrossRef   many-to-many free-form tags
RuleEntity          predicate (sender/merchant/vpa contains …), action (category), source (USER|SYSTEM), hitCount
BudgetEntity        categoryKey?, amountPaise, period, rollover
TemplateEntity      bankId, signature (digit-masked pattern), fieldMap, version, hitCount
```

- Dedup uniqueness: `(accountId, utrRef)` where present; else digest of normalized body.
- `SmsRawEntity` retention governed by Settings (auto-purge N days post-parse).
- Migrations from day one (Room `ExportMode.REQUIRE`); schema history in repo.

## 3a. Money & locale

- All amounts `Long` paise; format at the edge only (Indian grouping `₹1,18,420`, `Locale("en", "IN")`).
- No floats anywhere in the ledger path (UI display models included).
- Dates: `valueDate` (from SMS body) preferred; `timestamp` (delivery) fallback; both stored.

## 4. Capture pipeline (the no-AI funnel)

```text
SMS_RECEIVED broadcast
 → 1 Sender allowlist (AD-HDFCBK, JD-PAYTM, VM-SBIBNK … from AccountEntity)
 → 2 Cheap screen: contains ₹/Rs amount + debit/credit keyword?
 → 3 Template match: digit-masked signature → TemplateEntity (named groups:
     amount, direction, account mask, UTR/ref, balance, merchant/VPA, date)
 → 4 Generic fallback: amount + direction + fuzzy date only → AUTO_REVIEW
 → 5 Write TransactionEntity (provenance= AUTO_CONFIRMED if template+category
     rule confident else AUTO_REVIEW) → Review Inbox
```

- **Template induction:** mask digits/amounts in an unknown SMS → signature; user corrects one message → app generalizes field mappings to every SMS sharing that signature. Rules grow from corrections (`RuleEntity.source=USER`).
- **Transfer detection:** same amount, opposite direction, two accounts, ±30 min window (configurable) → both legs marked TRANSFER via `transferGroupId`; excluded from expense totals by default.
- **Refund matching:** credit SMS matching a recent expense (merchant + window) → REFUND linked via `refundOfTxnId`; nets category/budget. Unmatched credit flagged for review instead of auto-income.
- **Duplicate detection:** same amount + counterparty within short window with differing/duplicate UTR → Review card `[Keep both] [Merge]`.
- **Explainability:** the parser records which path fired (merchant dict / template field / rule / similar-history count) into `ruleAppliedKey` + a light reason record; "Why this category?" reads plain facts from it — no ML, no confidence scores.
- Parser templates + merchant dict ship as versioned seed data; regression-tested against a fixture corpus of redacted real SMS (grows from live corrections).

## 5. Suggestion engine (deterministic)

Order of precedence: explicit RuleEntity (user rules first) → merchant dictionary (bundled, versioned) → user history ("last 6 times this VPA was tagged food"). Suggested category ships with the review card; one tap confirms.

## 6. OEM background-kill strategy

Design decision: **reactive, not upfront** (setup screen stays clean). A lightweight health check (BroadcastReceiver registered? last processed SMS age?) drives the Home "detection may be delayed → [Fix]" card; Fix links to battery-optimization exemption + OEM autostart intents (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, vendor-specific autostart pages where detectable).

## 7. Analytics queries

- All aggregates computed via Room DAO queries (raw SQL) — monthly totals, category breakdowns, daily series, merchant rankings, budget pace (spend progress vs calendar progress), insights (period-over-period deltas).
- Derived state exposed as `StateFlow<AnalyticsUiState>`; charts consume stable lists (no recomposition storms).
- Insights are deterministic period comparisons — no ML (e.g., "Food ↑₹600 this week", "largest category: Food 26%").

## 8. Testing strategy

| Layer | Tests |
|---|---|
| Parser | fixture corpus per bank template; golden in/out; unknown-signature induction test |
| Rules/suggestions | precedence tests; correction-creates-rule tests |
| Ledger invariants | transfer exclusion, refund netting, duplicate merge idempotence, paise math |
| Repos/DAO | Room in-memory tests incl. migration tests |
| ViewModels | StateFlow state machines (review confirm flow, add-expense ripple data prep) |
| UI | Compose tests for Review Inbox flow, quick-add, donut drill; screenshot tests for tokens |
| Perf | Macrobenchmark on signature moments (frame budget §6 of 03-ANIMATION) |

## 9. Build & tooling

- Gradle (KTS), version catalog, minimal deps (Compose BOM, Room, Vico, Lottie-compose, Biometric, DataStore for prefs).
- Baseline profile in v1 (cheap win for chart jank).
- Release builds: R8, signed with personal key; sideload via `adb install` / direct APK share to sister's phone.
- CI not required (personal); a local `./gradlew check` gate before install.

## 10. Known risks / open engineering questions

| Risk | Mitigation |
|---|---|
| Unknown bank SMS formats at launch | template induction + review flow is the mechanism; ship templates for the family's actual banks (collect samples early) |
| OEM kills receiver (Xiaomi/Oppo/Vivo/OnePlus) | reactive Fix card + setup deep-links; document per-OEM |
| SMS delivery-vs-value date skew | store both; prefer valueDate in aggregates |
| UPI Lite / small-value batching at some banks | review flow catches; notification channel (v1.1) completes |
| Custom Canvas donut/gauge complexity | prototype behind a chart-spike before wiring analytics (spike skill) |
| Room migration mistakes corrupting ledger | REQUIRE export + migration tests from day one |

## 11. Sequencing hint (full phased plan comes separately)

Rough dependency order: tokens/theme + scaffold → Room schema + DAO tests → capture pipeline + parser fixtures → review flow UI → transactions list/search → home dashboard → budgets → analytics + charts → polish + signature moments → (v1.1: story, heatmap, notification channel).
