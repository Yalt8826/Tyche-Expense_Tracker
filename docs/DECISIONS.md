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
