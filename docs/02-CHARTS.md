# Chart System — Every Visualization, Its Job, Its Interaction

Companion to `00-MASTER-DESIGN-SPEC.md`. Rule zero: **no chart without a job.** Each visualization answers exactly one question; no two charts on a screen answer the same question.

## Implementation split

| Engine | Used for | Why |
|---|---|---|
| **Vico 2.x** | line charts, column/bar charts | Battle-tested scrub, markers, tooltips, axes — don't hand-roll the hard parts |
| **Custom Compose Canvas** | donut, radial budget gauges, sparkline, heatmap calendar, Story visuals, income-vs-expense hero | These are the product — interaction is the differentiation, so we own the code |

Version note (verified 2026-09-25): Vico 2.x is current; MPAndroidChart evaluated and rejected (dated); RN libs (gifted-charts, victory-native) rejected with the stack decision.

---

## The chart inventory

### C1. Hero sparkline — *Home hero card backdrop*
- **Question:** "What shape has this month been?"
- **Data:** daily cumulative spend, current month.
- **Engine:** custom Canvas. Passive (no touch); sits behind the hero number at low opacity.
- **Motion:** draws in with hero count-up (expressive tier).

### C2. Spending trend line — *Home (summary), Analytics (full)*
- **Question:** "Where is spending going?"
- **Data:** daily totals over selected period; gradient fill beneath the line.
- **Engine:** Vico line chart.
- **Interactions:** Home = tap-free summary; Analytics = full scrub/drag with tooltip, selected-point highlight, amount readout updates.
- **Motion:** animated entry (line draws left→right, standard tier); selection changes animate.

### C3. Interactive donut — *Home (mini, top-3), Analytics (full)* · **signature interaction**
- **Question:** "Where is my money going?" → tap → "What inside Food?"
- **Data:** category proportions → subcategory breakdown on drill.
- **Engine:** custom Canvas.
- **States:** Total (`₹18,420 TOTAL`) → tap slice (`FOOD ₹4,820 26%`, slice expands outward, center morphs) → sub-breakdown list reveals → tap-away returns to total.
- **Motion:** signature moment #2 — draw-in, slice expand (spring, finger-driven), center number morph, smooth category↔subcategory transitions.
- **Notes:** center of the donut is an interactive information surface. Tap in, tap out — deliberately nothing fancier.

### C4. Income vs expense bars — *Analytics hero*
- **Question:** "Am I earning more than I spend?"
- **Data:** per-month grouped bars over period.
- **Engine:** custom Canvas (grouped comparison with animated race) — or Vico grouped columns if custom proves heavy; decision at implementation.
- **Motion:** bars race on period switch (expressive tier).
- **Notes:** transfers/refunds excluded per ledger rules — this shows true in/out only.

### C5. Daily spending bars — *Analytics*
- **Question:** "Which days were heavy?"
- **Data:** daily totals, current period; today highlighted.
- **Engine:** Vico column chart.
- **Interactions:** tap day → day sheet (same one heatmap uses).

### C6. Radial budget gauge — *Budgets hero + Home mini pulse*
- **Question:** "Am I okay versus plan?"
- **Data:** dual progress — spending progress vs month (calendar) progress; projected end-of-month.
- **Engine:** custom Canvas sweep with pace indicator.
- **States:** healthy (green + check icon) → approaching (amber + warning icon) → exceeded (red + alert icon) — always icon+text paired with color; haptic tick on state transition (signature moment #4).
- **Home mini version:** thumb-sized ring, tap → Budgets tab.

### C7. Heatmap calendar — *Analytics (v1.1)*
- **Question:** "What's my daily rhythm?"
- **Data:** spend intensity per day, month grid.
- **Engine:** custom Canvas.
- **Interactions:** tap cell → "September 18 · ₹1,240 spent" + day sheet.
- **Notes:** elegant, NOT GitHub-contribution-graph styling.

### C8. Story visuals — *Spending Story (v1.1)*
- One custom visual per page (animated ring, arc, flowing graph) — each page = one number + one visual + one sentence. Maximum intensity, same design language.

---

## Chart interaction rules (all charts)

1. **Touch first.** Every chart supports at least tap; line charts support drag-scrub; donut supports drill. No static-only chart on a primary surface.
2. **Selection always has a readable readout** — tooltip or center-number; never rely on color highlight alone.
3. **Transitions animate data changes** — chart morphs between states rather than redrawing cold (standard tier).
4. **Honest data:** transfers excluded from expense charts by default; refunds netted; `needs-review` items optionally included via filter chip with visible count.
5. **Color-blind safety:** category series colors distinguishable under deuteranopia/protanopia (verified palette in token doc later); pattern/label redundancy on donut slices and bar groups.
6. **Empty & single-datapoint states** designed per chart (e.g., donut with one category → full ring + message, not a broken pie).

## Series colors (initial — final ramp in token doc)

Violet primary, cyan secondary, then a rotating distinguishable set for categories; green reserved for positive/income/under-budget, coral for negative/over-budget — semantic colors never reused as category colors.
