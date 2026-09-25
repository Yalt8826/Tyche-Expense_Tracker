# Expense Tracker — Master Design Specification

Status: **Approved direction** (source of truth for UI implementation planning)
Date: 2026-09-25
Replaces: all scattered discussion; supersedes nothing on disk (first canonical doc)

---

## 1. One-liner

A beautiful, premium, fully-offline Android expense tracker: bank-SMS auto-capture underneath, human-confirmed trusted ledger in the middle, calm-but-flashy visual analytics on top. **Simple on the surface, sophisticated underneath.**

## 2. Product principles

1. **Transactions are the single source of truth.** Home / Analytics / Budgets are views over one ledger — never independent data stores.
2. **Machine reads, human owns meaning.** SMS is parsed by deterministic rules (no AI); categories/tags are confirmed or corrected by the user; rules grow out of corrections.
3. **Trust is a visible part of the product.** Every transaction carries provenance (auto ✓ / needs-review ⚠ / manual ✎); raw SMS is one tap away; detection status is always understandable.
4. **Simple on the surface, sophisticated underneath.** SMS parsing, categorization, dedup, transfers, refunds, budgets — all real complexity handled underneath. None of it exposed unless it helps the user understand or correct something.
5. **Calm by default, expressive when something happens.** A small budget of signature moments; per-screen visual intensity is a deliberate scale.
6. **Charts are interaction, not decoration.** Every visualization is tappable, drillable, and answers a distinct question. No chart without a clear job.
7. **Zero-network is a feature.** No INTERNET permission; nothing leaves the device; privacy claims made in UI must match what the build actually guarantees.
8. **Sophisticated but enjoyable.** Premium, modern, personal, calm, slightly playful, data-rich, trustworthy. Never: gaming UI, crypto dashboard, corporate accounting, childish gamification.

## 3. Users

| | |
|---|---|
| **Primary** | Sister (design bar: "Ananya" in examples). Non-technical. Needs glanceable answers, zero jargon, gentle guidance. |
| **Secondary** | Yashas (power user). Wants filters, parser feedback loop, export, advanced door. |
| **Model** | Each on their own phone, own install, own local DB. No sync, no multi-profile in v1/v1.1 — "two phones" is solved by two installs. Multi-profile (one device, two users) is v2. |

## 4. Transaction model (ledger, complete)

Five types, day one:

```text
Expense · Income · Transfer · Refund · Adjustment
```

- **Transfers are not expenses.** Self-transfer detection (e.g., ₹2,000 out of HDFC, ₹2,000 into SBI within minutes) marks both legs as Transfer; excluded from spending totals and category charts by default.
- **Refunds reverse expenses; they are not income.** A refund links to its source expense, nets out of the original category's totals and budget usage, and appears in history with a refund badge. If the source expense is unknown/unmatched, the refund lands in Review Inbox rather than silently becoming income.
- Amounts stored as **paise integers**, never floats. Dates: parse in-body date preferentially; SMS delivery timestamp as fallback.
- Deduplication key: `(account, ref/UTR)` where available. Suspected duplicates surface as a `[Keep both] [Merge]` card in Review Inbox — only when the system has strong cause (same amount + same counterparty within a short window).
- **Data trust model:** `detected → needs confirmation → confirmed ledger`. A transaction is always visibly in one of these states; the user never wonders how an entry came to exist.

## 5. Capture pipeline (backend module, summarized)

```text
Bank SMS (READ_SMS / SMS_RECEIVED)
   → sender allowlist filter (AD-HDFCBK, JD-PAYTM, VM-SBIBNK …)
   → cheap screen (₹ amount? debit/credit keyword?)
   → per-bank template match (named-group regex: amount, direction,
     account mask, ref/UTR, balance, merchant/VPA, date)
   → generic fallback (amount + direction + fuzzy date only)
   → Review Inbox (human confirms/corrects once per unknown template)
```

- **No AI anywhere.** Bank SMS are machine-generated; masking digits yields stable template signatures. The user corrects one message; the template rule maps the whole family forever.
- Manual add exists for **cash** (SMS-invisible) and anything the funnel misses.
- Notification listener (GPay/PhonePe; catches UPI Lite) is a **later** secondary channel, not v1.
- **No historic import.** App starts at zero on first install; data builds live from day 0. (Decision: 2026-09-25.)
- Categories are **suggested deterministically**: bundled merchant dictionary (SWIGGY, ZOMATO, IRCTC, JIO…) + user's own tagging history ("last 6 times you tagged this VPA 'food'").
- Known gaps: cash (manual forever), SMS-disabled edge cases, UPI Lite SMS batching on some banks (Review Inbox + future notification channel catch these), delivery-vs-value dates (parse both, prefer in-body).

## 6. Tech stack (locked)

| Layer | Choice |
|---|---|
| Language / UI | **Kotlin + Jetpack Compose**, Material 3 |
| Charts | **Vico** (line, bar) + **custom Compose Canvas** (donut, radial gauges, heatmap, sparkline, story visuals) |
| Motion | Compose Animation (springs for finger-driven, curves for system-driven) + Lottie (selective: onboarding, success, empty states, celebrations) |
| Data | Room (SQLite), paise-integer amounts |
| Navigation | Navigation Compose; 4 tabs + center FAB |
| Presentation | ViewModel + StateFlow |
| Background | SMS_RECEIVED BroadcastReceiver → parser → Room |
| Permissions | READ_SMS (+ later NotificationListenerService). **No INTERNET permission.** |
| Distribution | Sideloaded personal APK (both phones). Not Play-published; no Play-policy constraints. |

Chart-library note (verified 2026-09-25): Vico 2.x for standard line/column charts (scrub, tooltips, markers). MPAndroidChart exists but is dated; RN chart libs (gifted-charts 1.4.78, victory-native 42.0.1) were evaluated and rejected when the stack was locked to Kotlin.

## 7. Navigation skeleton

```text
┌──────────────────────────────────────┐
│              [screen]                │
│               ( + )   ← quick-add FAB│
├──────────────────────────────────────┤
│  Home · Transactions · Analytics · Budgets │
└──────────────────────────────────────┘
```

- Settings behind the Home avatar (not a 5th tab).
- **Review Inbox is a filtered mode of Transactions**, surfaced by a Home banner + badge on the Transactions tab icon. Never its own tab.
- Search lives inside Transactions (merchant/category/account/provenance/date filters + free text; result summary "swiggy — 12 transactions · ₹4,820"). Designed so richer queries can be added later; NL search explicitly not v1.

Mental model: **Transactions = ledger · Home = overview · Analytics = exploration · Budgets = planning.**

## 8. Visual direction

Premium dark-mode-first finance aesthetic:

- Deep charcoal / near-black background; dark elevated cards
- **Violet / electric purple** primary accent; **cyan/teal** secondary
- Green positive, coral/red negative (never color-only — always paired with icon/text)
- White primary typography, muted-gray secondary
- Subtle gradients, rounded cards, soft borders, restrained blur/glass
- Large expressive financial numbers (visual hierarchy: `₹18,420` dominates `Spent this month`)
- **Not** neon, not gaming, not crypto, not corporate

**Visual intensity scale (adopted):** Home low→medium · Transactions low · Review Inbox medium · Analytics medium→high · Spending Story high · Settings low. Flash concentrates in Analytics, the donut, and Story; contrast is what makes them land.

## 9. Animation & motion system (summary)

**Taxonomy (purpose axis):** Data animation (count-ups, chart transitions, ring movement) · Interaction animation (selection, press, sheet springs, swipe confirm) · Narrative animation (Story page transitions).

**Mechanics axis:** springs when a finger is involved, curves when the system decided; never fake momentum the user didn't impart.

**Duration tokens:** micro 120 ms · standard 220 ms · expressive 350 ms · story 450 ms; nothing user-blocking > 500 ms. Reduced-motion support (in-app toggle + system setting): draws become fades, particles off, Story becomes static pages.

**Hard rule — number readability:** financial count-ups must resolve quickly and unambiguously to the final value (`₹17,920 → ₹18,420` settles fast; the animation communicates change, never obscures the amount).

**Signature moments (budget: 7, no more):**
1. Hero number count-up + slide on month change
2. Donut draw-in → tap-slice expand + center morph
3. Add-expense ripple (confirm → dependent numbers/charts tick up in 80 ms stagger)
4. Budget ring sweep + amber/red state transition (haptic tick)
5. Spending Story shared-axis page transitions (v1.1)
6. Review confirm: category chip flies from chip-row into the transaction row
7. Transaction row → detail sheet shared-element (icon grows into sheet header)

**Haptics:** only expense confirmation, important selection, budget state transition, destructive action. Never every tap.

Full system: `03-ANIMATION.md` in this doc set. (The later design-doc pipeline will produce `docs/UI/02-MOTION.md` with per-component spring constants — that file does not exist yet.)

## 10. Phasing

| Version | Scope |
|---|---|
| **v1** | Onboarding+permissions, setup (simplified, §11 of revision), Home, Transactions + search + Review Inbox (filtered mode), Analytics (core charts), Budgets, quick-add, manual add, SMS detection pipeline, provenance chips, refunds/transfers/duplicate UX, basic insights, data management settings |
| **v1.1** | Spending Story, notification-listener channel (UPI Lite), advanced pattern analysis, recurring detection, richer search, advanced chart interactions, polish |
| **v2** | Light theme, tablet optimization, multi-profile (one device), splits, budgets-per-tag |

Underlying dependency chain (Story waits for all of it): transactions → categorization → budgets → analytics → insights → **Spending Story**.

## 11. Companion docs

- `01-SCREENS.md` — every page/screen: purpose, contents, interactions, animations, empty/error states, priority
- `02-CHARTS.md` — chart system: every chart, its home, its job, its interactions
- `03-ANIMATION.md` — full animation system: taxonomy, tokens, per-screen choreography, signature moments
- `04-TECH-ARCHITECTURE.md` — stack, modules, data layer, capture pipeline detail, testing strategy

## 12. Provenance of decisions

| Date | Decision |
|---|---|
| 2026-09-25 | Sideload-only APK; bank-SMS primary channel; no-AI parsing; review-inbox flow; no historic import (day-zero start) |
| 2026-09-25 | Flashy interactive UI priority → stack evaluated; Kotlin+Compose+Vico locked 2026-09-26 after UI brief settled it |
| 2026-09-26 | Users: both siblings, own phones; sister = primary design bar. External revision doc adopted (refunds, explainability, intensity map, animation taxonomy, humanized parser status, simplified setup); nothing rejected; Advanced door kept for power-user parser feedback |
