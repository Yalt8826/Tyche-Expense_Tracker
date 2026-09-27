# UI Audit — 2026-09-27 (real data, physical device A05)

Scope: all shipped screens audited via screenshots + accessibility-tree dumps after the Kotak back-fill (84 real pending reviews, ₹77.5k month). Vision-verified Home/Analytics/Budgets; tree-verified Review/Transactions.

## What's working (keep)

- **Capture accuracy**: 83/83 well-formed rows from real Kotak SMS (amount, date, payee, UPI ref); 102 promos/OTPs correctly ignored; dedup held.
- **Stability**: zero crashes across the full navigation pass; tab state preserved.
- **Honest math**: over-budget gauge (388%), projection (₹86.4k), net-vs-gross separation all correct.
- **Home hierarchy** lands per 00 §8: banner → hero → recent; review banner correctly disappears at inbox-zero.
- Dark theme consistent; empty states on Analytics/Budgets/Inbox exist and are friendly.

## Findings (ordered by user impact)

### R1. Review Inbox can't actually be reviewed fast — CRITICAL
Cards show merchant/amount/"Other"/"Edit" only. No suggestion fires (dictionary lacks real payees; no history yet) so there is **no one-tap confirm** — with 84 pending, every item needs 3+ taps.
**Fix**: always render quick-confirm chips (top 6 categories) even without a suggestion; group cards by payee (84 → ~30 groups, confirm-all-per-group); add date+account line; replace "Other" dead-end with a category picker; per-card undo snackbar; progress ("12/84 reviewed"); shorten the repeated "bank capture · needs your confirmation" line.

### R2. Transactions has no day grouping — HIGH
Flat reverse-chron list; a row's date is unknowable (spec S9 requires sticky day headers + month strip + filter chips).
**Fix**: sticky `Today / Yesterday / 24 Sep` headers; horizontal month strip; provenance filter chip with count.

### R3. Every row shares one placeholder icon — HIGH (visual identity)
Category icon only exists post-confirmation; unconfirmed rows get the generic glyph. The ledger looks unfinished.
**Fix**: merchant-initial avatar fallback (colored circle + "V" for Vaishnavi); keep category icon when present.

### R4. Budget cards show raw keys and no progress bar — MEDIUM
"food / transport" lowercase keys, text-only "Over budget" (spec S15: name + icon + shifting progress bar).
**Fix**: display name lookup + Material icon + thin progress bar under each card; add delete-budget in editor; "3.9× of budget" copy instead of bare 388%.

### R5. Paise display inconsistent — MEDIUM
"₹70" vs "₹212.43" (trailing .00 dropped). Pick one policy (recommend: always show paise on transaction rows, never on hero/gauge).

### R6. Hero net vs banner/budget gross mismatch — LOW but confusing
Home hero ₹77,481 (net) vs Budgets ₹77,767 (gross). Label hero "after refunds" or show both.

### R7. FAB overlaps last list row — LOW
Scrollables need bottom content padding (≈96dp) on Home/Analytics/Budgets/Transactions.

### R8. Analytics chart polish — MEDIUM
No month labels under bars; trend line has no date axis formatting/scrub tooltip (spec C2 requires scrub on Analytics); donut mixes subcategory slices (flatten to top level for v1; drill is v1.1).

### R9. Missing spec surfaces — scheduled
Settings (S17) incl. demo-data wipe + export; first-run permission education (S3) — real users must grant READ_SMS via dialog, not adb; signature moments 1–7; Transactions-tab review badge; greeting/avatar (S6).

### R10. A11y pass — MEDIUM
Verify 48dp targets on review chips; content descriptions on all icons; contrast for amber-on-dark chips.

## Priority plan

| When | Items |
|---|---|
| This week (pre-beta) | R1 quick-confirm + grouping, R2 day headers + month strip, R3 initial avatars, R4 names/progress, R5 decimal policy, R7 padding |
| P6 as planned | R9 settings/export/demo-wipe/onboarding-lite/signature moments |
| v1.1 backlog | R8 scrub+drill, batch ops, budget drill-through, heatmap, story |

*Grounded in: 8 device screenshots + 3 accessibility dumps + DB queries (97→180 rows during audit window).*
