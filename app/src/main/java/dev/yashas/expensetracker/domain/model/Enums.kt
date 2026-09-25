package dev.yashas.expensetracker.domain.model

/** Debit/credit direction on a bank SMS. */
enum class Direction { DEBIT, CREDIT }

/** Five ledger types, day one (00-MASTER §4). Stored by enum name via Room's enum converter. */
enum class TxnType { EXPENSE, INCOME, TRANSFER, REFUND, ADJUSTMENT }

/** Data trust model: detected → needs confirmation → confirmed ledger (00-MASTER §4). */
enum class Provenance { AUTO_CONFIRMED, AUTO_REVIEW, MANUAL }

/** Lifecycle of a captured raw SMS inside the funnel (04-TECH §3). */
enum class SmsParsedState { UNPARSED, PARSED, IGNORED }

/** Who authored a categorization rule (04-TECH §4 — rules grow out of corrections). */
enum class RuleSource { USER, SYSTEM }

/** Budget cadence (v1: monthly only is realistic, enum kept for rollover/scope growth). */
enum class BudgetPeriod { MONTH }
