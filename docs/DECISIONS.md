# Decision Log

One line per call. Unspecified-in-docs choices made during implementation; approved docs are never redesigned here.

| Date | Phase | Decision | Why |
|---|---|---|---|
| 2026-09-26 | P0 | Package `dev.yashas.expensetracker` | No package stated in docs; reverse-DNS convention, app-specific, no collision. |
| 2026-09-26 | P0 | minSdk 29 (Android 10) | Unspecified; both family phones are modern; 99%+ device coverage vs API cost of SMS/scoped-storage edge handling below 29. |
| 2026-09-26 | P0 | targetSdk 36, compileSdk 37 | latest stable Android 16; navigation 2.10.2 + Vico 3.3.1 require compileSdk ≥ 37 (AAR metadata check). |
| 2026-09-26 | P0 | AGP 9.4.1 + Gradle 9.6.0 + Kotlin 2.4.20 + KSP 2.3.12 | Current stable; AGP 9.4 tested default is Gradle 9.6; KSP is decoupled-versioned (KSP2), latest pairs with Kotlin 2.4.x. |
| 2026-09-26 | P0 | AGP built-in Kotlin (no `kotlin-android` plugin) | AGP 9.0+ errors if `org.jetbrains.kotlin.android` is applied; built-in Kotlin is the supported path. |
| 2026-09-26 | P0 | JDK 26 for Gradle/AGP; bytecode target 17 | athena system JDK 26 accepted by AGP 9.4 (min 17); 17 bytecode keeps Kotlin/AGP toolchains aligned. |
| 2026-09-26 | P0 | Launcher icon = adaptive vector (violet→cyan gradient disc + abstract ₹) | Docs specify palette, not icon art; placeholder-grade premium mark, revisited at P6 polish. |
| 2026-09-26 | P0 | `org.gradle.vfs.watch=false` | Repo lives on NFS (/mnt/storage); inotify does not cross NFS (same class of issue as WATCHFILES_FORCE_POLLING on other NFS dev servers). |
| 2026-09-26 | P0 | app label "Expense Tracker" | Working label; not specified in docs. |
| 2026-09-26 | P1 | Instrumented-test AVD uses system-images;android-36;google_apis;x86_64 (mission suggested android-35) | android-35 image is not installed on athena; android-36 google_apis already present (servgrid uses it) — avoids a ~1.5 GB download; v1 minSdk 29 / target 36 unaffected. |
| 2026-09-26 | P2 | `valueDate` stored as epochDay (`Long?`), timestamp as epochMillis | Date-only granularity is what aggregates group by; epochDay keeps daily SQL integer division trivial; in-body date preferred, delivery fallback (04 §3a). |
| 2026-09-26 | P2 | Dedup = hard unique index `(accountId, utrRef)` + soft digest on `sms_raw`; same-UTR inserts are rejected, near-dupes surface in Review | 00 §4: (account, ref/UTR) key where present; Review cards carry the ambiguous cases; SQLite NULLs distinct → manual rows unconstrained. |
| 2026-09-26 | P2 | Room POJO aliases avoid `key` (SQLite reserved word): `CategoryTotalRow.label` | Room's query parser rejects `key` as a column alias even though SQLite engine allows it quoted. |
| 2026-09-26 | P2 | Robolectric tests pinned `@Config(sdk = [34])` | Robolectric's stable Android framework level; independent of the API-36 instrumented AVD. |
| 2026-09-26 | P3 | Manual DI via `AppGraph` object (no Hilt) | 04 §1 explicitly allows it at small scope; v1 has one DB and two consumers — Hilt would be ceremony. Revisit if a third scope appears. |
| 2026-09-26 | P3 | All SMS-captured rows land as AUTO_REVIEW in v1 | Suggestion engine (P4) supplies categories; AUTO_CONFIRMED activates when a confident rule/template+category path exists (00 §4 trust model). |
| 2026-09-26 | P3 | Sender allowlist checked BEFORE any persistence | Data minimization: non-bank SMS bodies are never written to disk, only counted as IGNORED. |
| 2026-09-26 | P3 | Template signature = digit-masked body (letters + digit-run lengths) | Deterministic family key per 04 §4; same-mask SMS with different merchant letters are distinct families by design — induction ties signature+field map. |
| 2026-09-26 | P3 | "credit card" excluded from credit-direction keywords (negative lookahead) | ICICI/HDFC card-debit fixtures read "spent on Credit Card" — the product noun must not flip direction detection (fixture-caught). |
| 2026-09-26 | P4 | Icon-name indirection (`IconsAuto` map) between DB `icon` strings and Material icons | Category icons stay data (Room-friendly, seedable); Compose keeps compile-time vector safety at the mapping edge. |
| 2026-09-26 | P4 | Manual quick-add stores rupee input ×100 at the repository edge | S7 keypad collects rupees as the user thinks; the ledger sees only paise Longs (04 §3a no-float rule preserved). |
| 2026-09-26 | P5 | Vico charts plot rupees (`paise/100`), donut + gauges stay custom Canvas | 02-CHARTS engine split kept; axis labels read naturally in rupees; exact paise math remains ledger-only. |
| 2026-09-26 | P5 | Overall pace "on track" verdict within ±5pp of calendar fraction | S15 asks for plain verdicts; small tolerance avoids nagging every single day (tunable constant in BudgetMath). |
| 2026-09-26 | P5 | Vico 3.x imports: everything under `com.patrykandpatrick.vico.compose.cartesian.*` | Sample-app sources used as ground truth; guide blog posts still show old `core.*` paths that no longer resolve. |
| 2026-09-26 | P5 | Demo seeder: 40 days of deterministic synthetic rows, DEBUG-only, tagged `ruleAppliedKey='demo'` for wholesale wipe in P6 data management; real SMS capture untouched | Lets the UI be evaluated with a full ledger; deterministic (seed per epochDay) so screenshots/repro are stable. |
| 2026-09-26 | P5 | Fixed on-device demo findings: donut drawn in centered square (was ellipse) + rotating palette; DAO daily queries group by `valueDate` directly (epochDay ÷ 86400000 collapsed all days to 1970-01); budget spend rolls subcategories into parents | All three caught only by looking at populated screens — demo seeding doubled as an integration test. |
