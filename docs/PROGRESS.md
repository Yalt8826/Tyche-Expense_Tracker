# Progress Log

Plain-language notes, 3 lines per milestone (newest first).

## 2026-09-26 — P1 scaffold GREEN

1. Navigation skeleton live: 4 tabs (Home/Transactions/Analytics/Budgets) with state-preserving switching + quick-add FAB in the dark violet/cyan M3 theme; MainActivity hosts the Compose app shell.
2. Emulator gate passed on athena: created headless AVD `test` (API 36 x86_64), connectedDebugAndroidTest green (instrumented smoke), app installed, cold-launched (PID confirmed), screenshot shows correct dark shell.
3. Placeholder bodies on all four tabs; real screens arrive P4 (Home/Transactions) and P5 (Analytics/Budgets).

## 2026-09-26 — P0 toolchain GREEN

1. Athena toolchain verified end-to-end: Gradle 9.6.0 wrapper + JDK 26 + AGP 9.4.1 + KSP 2.3.12; `./gradlew --version`, `assembleDebug` and unit tests all pass; `app-debug.apk` produced.
2. Project scaffold in place: version catalog, Compose/M3 dark theme tokens (violet/cyan per 00 §8), launcher icon, edge-to-edge MainActivity placeholder, JVM + instrumented smoke tests.
3. Gotchas hit & fixed: KSP is decoupled-versioned (2.3.12, not kotlin-suffixed); AGP 9 forbids the `kotlin-android` plugin (built-in Kotlin now); navigation/Vico need compileSdk 37.
