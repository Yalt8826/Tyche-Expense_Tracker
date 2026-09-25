# Animation & Motion System

Companion to `00-MASTER-DESIGN-SPEC.md`.

## 1. Two axes

**Purpose axis (what the animation communicates):**
- **Data animation** — change in data: count-ups, chart transitions, ring movement, progress changes.
- **Interaction animation** — what the user touched: slice selection, button press, category selection, sheet springs, swipe confirm.
- **Narrative animation** — movement through a story: Spending Story page transitions, recap sequencing.
Every animation must sit in exactly one bucket. No decoration bucket exists.

**Mechanics axis (how it moves):**
- **Springs** when a finger is involved (sheets, selection, swipe, donut slices) — the UI answers the hand.
- **Curves** when the system decided (chart draw-in, count-ups, fades) — the UI reports.
- Never fake momentum the user didn't impart.

## 2. Duration & easing tokens

| Token | ms | Use |
|---|---|---|
| micro | 120 | press feedback, chip toggles, digit pop |
| standard | 220 | fades, sheet content, list insertions, month crossfade |
| expressive | 350 | chart draw-ins, hero updates, ring sweep |
| story | 450 | Story page transitions, flagship moments |

- Nothing user-blocking exceeds **500 ms**.
- Standard easing: `EaseOutCubic`-family for entrances; `EaseInOutCubic` for moves; springs use Compose defaults tuned per component (stiffness/damping documented per component in token doc).

## 3. Per-screen choreography

| Screen | Intensity | What animates | What never does |
|---|---|---|---|
| Home | low→med | hero count-up, ring sweep, sparkline draw, review-banner pulse | recent-rows list (static), greeting |
| Transactions | low | row insert, month crossfade, swipe reveals | row contents, day headers |
| Review Inbox | medium | card entrances, chip-fly confirm, merge collapse | card contents while deciding |
| Analytics | med→high | chart draw-ins, donut morph, bar race, tooltip tracking | axis labels, period selector |
| Budgets | medium | ring sweep, state transition (haptic), pace indicator | budget list while idle |
| Spending Story (v1.1) | high | everything — pages, numbers, visuals | — |
| Settings | low | section reveals, toggle states | — |
| Quick-add | medium | sheet spring, keypad digit pop, category grid springy select | amount display |

## 4. Signature moments — the full budget (7)

1. **Hero number count-up + slide on month change** (Home). Data anim. Old month slides out, new slides in, number counts to new total. Expressive.
2. **Donut draw → tap-slice expand + center morph** (Analytics/Home). Interaction anim. Spring slice expansion; center number cross-morphs `TOTAL → FOOD ₹4,820 26%`.
3. **Add-expense ripple** (confirm overlay → Home). Data anim. Total, donut, budget ring tick upward in 80 ms stagger — action visibly causes data change everywhere.
4. **Budget ring sweep + state transition** (Budgets). Data anim + haptic tick when crossing into amber/red.
5. **Spending Story shared-axis transitions** (v1.1). Narrative anim. Horizontal shared-axis pager; numbers scale-in, visuals draw.
6. **Review-confirm chip-fly** (Review Inbox). Interaction anim. Selected category chip flies from chip-row into the transaction row and settles as its category badge.
7. **Row → detail shared-element** (Transactions). Interaction anim. Row icon grows into sheet header; sheet springs up underneath.

## 5. Hard rules

- **Number readability rule:** financial count-ups resolve quickly and unambiguously to the final value. The animation communicates change; it never obscures the amount. (§ revision 15)
- **Haptics only for:** expense confirmation, important selection, budget state transition, destructive action. Never every tap.
- **Reduced motion** (in-app toggle + system setting honored): draws become fades, particles/stories become static pages, count-ups become instant sets with a brief highlight pulse.
- **No continuous idle animation** except: review-banner pulse (action needed) and Lottie empty-state loops (calm, slow, ≤12 fps visual energy).
- Lottie usage is selective: onboarding, success confirmation, empty states, celebration moments (budget achieved). The majority of motion is native Compose.

## 6. Performance expectations

- All signature moments target 60 fps on a mid-range 2022 Android (sister's phone class); jank threshold: no frame > 32 ms during any signature moment (verified with Macrobenchmark during implementation).
- Charts render from snapshot state lists; no chart rebuilds on unrelated recomposition (stable keys, `derivedStateOf` for selections).
- Custom Canvas charts invalidate only their own draw scope.
