# Screen Catalog — 18 Surfaces

Companion to `00-MASTER-DESIGN-SPEC.md`. Every screen: purpose, contents, interactions, motion, states. Priority column feeds implementation ordering.

Navigation recap: 4 tabs (Home · Transactions · Analytics · Budgets) + center quick-add FAB; Settings behind Home avatar; Review Inbox = filtered mode of Transactions.

---

## First-run flow (5 screens)

### S1. Splash — `priority: P1`
**Purpose:** brand moment, load check.
**Contents:** logo mark with violet→cyan gradient draw.
**Motion:** ≤800 ms total, expressive-tier; reduced-motion → static fade.
**Notes:** never blocks; app state restores underneath.

### S2. Onboarding carousel — `P1`
**Purpose:** value proposition before permission ask.
**Contents:** 3 slides, one idea each — (1) "Know where your money goes", (2) "Detected automatically from your bank SMS — on this phone, nowhere else", (3) "You confirm. You tag. Beautiful charts do the rest". Lottie illustrations, progress dots, Skip.
**Motion:** slide parallax; dots spring.
**Notes:** Skip jumps straight to S3 (permissions still required there).

### S3. Permission education — `P1` · *the trust moment*
**Purpose:** honest explanation, then the system dialog.
**Contents:** what is accessed (transaction SMS), why (detect expenses automatically), where processed (entirely on this device — no INTERNET permission, nothing leaves the phone), retention setting preview. Plain language, zero jargon. Primary CTA → system READ_SMS dialog.
**Motion:** illustrative funnel animation (message → card), standard tier.
**Notes:** no privacy claim the build can't guarantee; UI mirrors actual architecture (no-internet build makes this literal).

### S4. Setup checklist (simplified) — `P1`
**Purpose:** confirm readiness without technical ceremony.
**Contents:** `✓ Bank access · ✓ Notifications(optional)` → "Everything is ready."
**Notes:** battery-optimization/OEM-killer handling is **reactive, not upfront**: if Android restricts background operation later, Home shows "Transaction detection may be delayed → [Fix]" card linking to the exemption flow. Only surfaces when actually needed.

### S5. Empty Home — `P1`
**Purpose:** friendly zero-state.
**Contents:** tasteful illustration/Lottie, "No expenses yet — your bank SMS will start appearing here automatically. Add your first expense anytime." + quick-add CTA.
**Motion:** entrance fade; Lottie idle loop.
**Notes:** educates the auto-capture expectation on first contact. Not an error state.

---

## Home tab (2 screens)

### S6. Home dashboard — `P1` · *most important screen*
**Purpose:** answer in seconds — How much? Am I okay? What changed?
**Hierarchy (top→bottom):**
1. **Greeting** ("Good evening, Ananya") + avatar → Settings
2. **Review banner** *(only when pending > 0)* — count + pulse; sits above hero; vanishes at inbox-zero so Home goes fully calm. Trust beats calm when action is needed.
3. **Hero card** — month total `₹18,420` (count-up), label "Spent this month", trend chip `↓ 8.4% vs last month` (green/coral + arrow icon, never color-only), sparkline backdrop, horizontal month swipe.
4. **Budget pulse** — mini radial ring: overall month usage vs pace.
5. **Spending trend** — line chart (visual summary only; detailed scrubbing lives in Analytics).
6. **Category breakdown** — donut + top-3 rows (icon, name, amount, %).
7. **Recent transactions** — last 5, chevron → Transactions.
**Motion:** signature moments #1 (hero count-up/month slide), #4 (ring sweep); data animations standard-tier.
**Intensity:** low→medium. Calm and immediately understandable.

### S7. Quick-add sheet (FAB) — `P1`
**Purpose:** fastest possible manual entry (cash + anything missed).
**Contents:** big custom numeric keypad (₹ amount, keypad-first), category icon grid (springy selection), optional note/date; primary CTA "Add".
**Motion:** spring sheet-up; keypad digits pop (micro); confirm → S8.
**Notes:** for SMS-invisible spending; SMS-covered spending arrives by itself. This is a secondary entry path by design.

### S8. Confirmation overlay — `P1`
**Purpose:** closure + connection between action and data.
**Contents:** ✓ burst, amount, category.
**Motion:** signature moment #3 — ripple update: total, donut, and budget ring tick up in 80 ms stagger across Home.
**Notes:** brief (≤1.2 s), then auto-dismiss to Home.

---

## Transactions tab (3 screens)

### S9. Transactions list — `P1`
**Purpose:** the ledger, scannable.
**Contents:** month strip (horizontal selector) · search + filter chips (category, tag, account, provenance, date) · day-grouped list with sticky headers · rows = category icon, merchant, amount, provenance chip.
**Search behavior:** free text + chips; result summary bar ("swiggy — 12 transactions · ₹4,820").
**Interactions:** swipe row → re-tag / delete+undo; tap row → S11.
**Motion:** low intensity; insertions animate in at top (standard tier); month switch crossfade.
**Notes:** must NOT look like a bank statement (grouped, iconified, colored).

### S10. Review Inbox (filtered mode) — `P1` · *first-class UX priority*
**Purpose:** the trust layer — "What did the app detect, and is it correct?"
**Contents per card:** parsed transaction (bank, amount, counterparty/VPA, time) + suggested category chips (from rules/history) + confirm affordance. Special card states: **possible duplicate** (`₹1,240 · 2 minutes earlier — [Keep both] [Merge]`), **possible transfer** (both legs shown, "Mark as transfer"), **unmatched refund** (no source found → ask).
**Interactions:** swipe-to-confirm; chip tap to correct category; edit → full editor; batch confirm for same-merchant runs; undo everywhere.
**Motion:** signature moment #6 — confirm: category chip flies from chip-row into the transaction row.
**Intensity:** medium. Feels like a simple inbox requiring occasional human confirmation — never a technical parser interface.

### S11. Transaction detail sheet — `P1`
**Purpose:** "What exactly is this transaction?"
**Contents:** amount (display), merchant, category, date/time, account, payment method (where available), tags, notes, provenance, transfer status, refund link (if any).
**Trust affordances:** **"Why this category?"** — plain-facts explainability: merchant matched (Swiggy → Restaurants), similar previous transactions (8), rule applied. No ML terminology, no raw confidence scores. **"View source message"** — expandable raw SMS, present but not dominant.
**Interactions:** edit all fields; tags editor; mark-as-transfer; delete (+undo); create-rule-from-edit ("Always categorize this as Food?").
**Motion:** signature moment #7 — shared-element entrance (row icon grows into sheet header).

---

## Analytics tab (4 screens)

### S12. Analytics dashboard — `P1`
**Purpose:** deep exploration — the flashy heart (medium→high intensity).
**Hierarchy:**
1. Period selector (1M / 3M / 6M / 1Y)
2. Overall comparison — income vs expense hero bars (animated race on period switch)
3. Spending trend — line chart, full scrub/drag tooltip
4. Category composition — interactive donut (see S12a)
5. Daily spending — bar chart, today highlight
6. Top merchants — ranked list with amounts
7. Insight cards — expandable ("Food ↑ ₹600 this week — ₹1,820 → ₹2,420")
8. Heatmap calendar entry → S14
**Rule:** each visualization answers a different question; no two charts saying the same thing; never a wall of charts.

### S12a. Interactive donut (within S12; also mini version on Home) — `P1` · *signature interaction*
**States:** Total (`₹18,420 TOTAL`) → tap Food → (`FOOD ₹4,820 26%`, slice expands, center morphs) → sub-breakdown reveals (Restaurants ₹2,140 / Groceries ₹1,680 / Coffee ₹620 / Snacks ₹380) → tap-away/back returns to total.
**Motion:** signature moment #2 — draw-in, slice expand, center number morph, smooth category↔subcategory transitions.
**Notes:** the donut center is an interactive information surface. Deliberately not over-complicated: tap in, tap out, that's it.

### S13. Spending Story — `v1.1` 🌟
**Purpose:** monthly recap as a visual story — the flagship flashy experience.
**Format:** full-screen snap-pager; one idea per page — overall spending → top category → biggest change → budget status → simple conclusion. Each page: one major number, one custom visual, one short sentence.
**Motion:** signature moment #5 — shared-axis page transitions; maximum visual intensity in the app: larger typography, custom Canvas visuals, richer transitions, Lottie.
**Constraint:** same typography/colors/component language as the rest of the app — "the same app, entering presentation mode", never a different app. Depends on the full data chain existing first.

### S14. Heatmap calendar — `v1.1`
**Purpose:** spend-intensity per day; pattern recognition at a glance.
**Contents:** month grid, intensity-shaded cells; tap day → "September 18 · ₹1,240 spent" + that day's transactions sheet.
**Notes:** visually elegant, NOT a GitHub-contribution-graph look. Custom Canvas.

---

## Budgets tab (2 screens)

### S15. Budget overview — `P1`
**Purpose:** "Am I okay?" with precision.
**Contents:**
- **Hero radial gauge** — overall month usage with dual-progress readout: `Month progress 58% / Spending progress 62%` + plain verdict ("You're slightly ahead of your usual spending pace") + projected end-of-month total.
- **Category budget cards** — progress that shifts green→amber→red WITH icon + text change (never color alone).
**Motion:** signature moment #4 — ring sweep + state transitions with haptic tick.

### S16. Budget editor sheet — `P2`
**Purpose:** create/edit budgets.
**Contents:** amount (keypad), category picker, rollover toggle, create-rule hook.
**Motion:** standard sheet spring.

---

## Utility (2 screens)

### S17. Settings — `P2`
**Purpose:** preferences + data management + the power-user door.
**Sections:**
- Profile — greeting name, per-user (no accounts, local only)
- App lock — biometric on cold start
- Banks & accounts — SMS sender allowlist management
- **Detection status (humanized)** — "ICICI Bank ✓ Working · HDFC Bank ✓ Working · SBI ⚠ 3 need review · Transactions detected: 247" + entry to **Advanced** section (parser/template detail, unmatched signatures — the power-user feedback loop; hidden behind an explicit Advanced door)
- Data management — export (CSV/JSON), delete transaction data, clear parsed data, raw-SMS retention (auto-purge N days)
- Reduced motion toggle · theme (dark-first)
**Notes:** important financial-data controls never buried.

### S18. App-lock overlay — `P2`
**Purpose:** biometric gate on cold start.
**Notes:** standard BiometricPrompt; falls back to bypass if none enrolled.

---

## Screen relationship map

```text
Splash → Onboarding → Permission edu → Setup → Empty Home
                                                    ↓
              ┌──────────── Home dashboard ←────────┘
              ↓         ↓           ↓            ↓ (avatar)
     Quick-add sheet  Review     Transactions   Settings
              ↓       Inbox ↓        ↓            ↓
     Confirm overlay        Txn detail ←— search  App-lock
                              ↓
Analytics dash ── donut drill ── heatmap ── (v1.1: Story)
              ↓
       Budget overview → Budget editor
```

## Cross-cutting rules

- **Provenance chips everywhere rows appear:** ✓ auto-detected · ⚠ needs-review · ✎ manual — icon + text + subtle treatment, never color alone.
- **Transfers excluded** from expense totals/charts by default; visible in ledger with transfer badge.
- **Refunds** net against source category/budget; badge in history.
- **Indian number formatting** everywhere: ₹1,18,420 grouping.
- Empty states for every list/chart (friendly, Lottie, never error-toned).
- Touch targets ≥ 48dp; screen-reader labels; reduced-motion honored.
