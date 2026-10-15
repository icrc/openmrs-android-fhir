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

### Features and fixes added after the patient-list decision
- **A13 fixed** (`f305227`): at least one patient list must be selected; the sync never downloads patients unscoped. Verified on device.
- **A14 fixed** (`fab0b4a`): login no longer sends two `Authorization` headers when credentials are stored (400 → 200). Verified on device.
- **Search by identifier or phone** (`3750098`, hint text `85ec694`): verified on device.
- **Unsaved-changes guard** on registration/edit: first version (`9e474a4`, `82f9698`) **broke registration** (see below); rewritten in `2673803` to be tap-based and to never read the questionnaire response. Verified on device.
- **Regression found and fixed (`2673803`):** the first guard read the questionnaire response early, which made the form library drop conditional items and later answers; patients were saved without gender and rejected by the server (422). Caught by the offline-create → upload test, **not** by the unit or instrumentation suites. Offline create → upload now verified end to end (patient uploaded, identifier assigned).
- **UI reuse** (`3927482`): shared inset helper, `composeView {}` fragment hosting, Home card table. 51/51 instrumentation tests that pass at baseline still pass.
- Tests: 18 → 40 unit tests (sync URL helpers, patient-list scoping, auth header, answer signature, unsynced scan characterization).

### Tried and rejected (measured)
- Running the unsynced-resources local-change lookups concurrently: 37–46 s vs ≈31 s sequential. Reverted. Timing instrumentation showed the cost is `search<Observation>` (26 s), not the lookups (3.6 s).

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
| `core` unit tests | 16 baseline → 40 now, all pass |
| Instrumentation tests (52) | baseline: 35 pass, 17 fail (all from `MainNavigationContainerTest` onward, deterministic). Excluding that one class, 51/51 pass at baseline and 51/51 pass after the changes |
| Unsynced-resources screen (1,890 obs) | ≈30 s (26 s in `search<Observation>`) |

## Dependencies, settings and UI (branch `chore/upgrade-dependencies`)

- **Stable Open Health Stack**: FHIR engine 1.2.0 and data-capture 1.3.1 from Google Maven; the GitHub Packages repository and token are gone. AndroidX, coroutines, Retrofit, Moshi, Dagger, Room, Mockito and the Compose BOM (2026.06.01) are on current stable versions.
- **Settings**: one script (`gradle/app-config.gradle.kts`) turns `local.properties` into app resources; server URLs are derived from `openmrs_server_url`.
- **Look and feel**: OpenMRS 3 teal/Carbon palette (light and dark) for Compose, XML and the data-capture form; bottom navigation (Home / Patients / Group / Sync); Home status header; avatar patient rows; one rounded search field.
- **Top bar no longer covers content**: main and login activities are edge-to-edge on every API level and screens pad by the measured app-bar height (before, only API 35+ was right). The content also clears the navigation bar and keyboard.
- **Data capture**: `newQuestionnaireFragment` / `onQuestionnaireResult` replace five copies of the builder and result wiring; registration loads each identifier type once.
- **Bug fixed**: the bottom-bar "Group" tab crashed (wrong navigation argument keys, `patientId` / `isGroupEncounter` instead of `patient_id` / `is_group_encounter`); it now builds the arguments with the generated `CreateEncountersFragmentArgs`.
- **Verification note**: the UI, edge-to-edge and data-capture changes were checked by building, installing on the emulator and looking at the screens (home, patient list, settings, registration, group session flow). The automated suites were **not** re-run after these changes, at the owner's request; run them before merging.
