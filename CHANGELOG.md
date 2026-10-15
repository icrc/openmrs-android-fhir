# Changelog (audit & cleanup branch `chore/audit-and-cleanup`)

Each entry: what changed, why, and how it was verified.

## Baseline (no code changes yet)

Recorded on `main` @ `cdd4117`, Windows, JDK 17 toolchain, no emulator.

| Gate | Result |
|---|---|
| `spotlessCheck` (Kotlin) | Pass |
| `spotlessCheck` (Gradle scripts) | Fail — `app/build.gradle.kts` uncommitted local edit (`include("x86_64","arm64-v8a",…)`) |
| `spotlessCheck` (XML) | Cannot run locally — Node/npm missing |
| `./gradlew test` | Pass — 7 suites, 16 tests, 0 failures |
| `./gradlew build -x test --continue` | Compiles/assembles; only spotless tasks fail (above) — 7m14s |

## Unreleased

- docs: add `docs/audit.md` (Phase 1 audit, ranked findings, plan), including a UI-layer section (G1–G9) and UI reuse steps 3a–3c.
- docs: add server verification log to `docs/audit.md` (read-only probes of the test backend; A6 closed as not reproducible).

### Fixes
- **Patient list search race and error handling** (`PatientListViewModel`): a slower earlier search could overwrite the newest result, and a failure left the screen stuck loading. Only the latest request now publishes; failures reset the flags. Two regression tests (both verified failing before the fix). Commit `613d399`.
- **Diagnostics export off the main thread** (`MainActivity`): log read + AES zip no longer run inside `runBlocking` on the UI thread. Commit `ceee5d3`.
- **Sync-info worker** (`SyncInfoDatabaseWriterWorker`): cancel the `shareIn` scope in `finally`, rethrow `CancellationException`, log with Timber instead of `println`. Commit `159113c`.

### Removed (dead code)
- 11 unreferenced RecyclerView adapter/view-holder files and the `activity_login`, `activity_basic_login`, `activity_splash` layouts. Verified by compiling main + unit-test + androidTest sources and assembling the debug app. Commit `8795853`.

### Audit corrections
- A6 (`+` in `_lastUpdated`) closed: server accepts it (verified).
- C1 not batchable via the public `FhirEngine` API; C2 negligible on this backend (no `RiskAssessment` resource).
- New A13: unscoped full download when no patient list is selected (observed on device).

## Measurements (baseline, before performance work)

Emulator `emulator-5554` (Android 13, x86_64), debug build, test backend.

| Metric | Value |
|---|---|
| Cold start (`am start -S -W`, 3 runs) | 5,610 / 5,440 / 5,492 ms |
| Debug APK (x86_64) | 86.8 MB |
| Release APK (x86_64, unminified) | 72.8 MB |
| First sync (Location + Questionnaire), then bounded patient sync (10 patients) | ≈ 2.5 min total; DB: 10 Patient, 197 Encounter, 1,890 Observation, 26 Questionnaire, 11 Location |
| Unscoped patient sync (no list selected) | 560 Patient + 5,016 Encounter in ~7 min (stopped) |
| `core` unit tests | 18 pass (16 baseline + 2 new) |
