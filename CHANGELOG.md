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

- **Stable Open Health Stack**: FHIR engine 1.2.0 and data-capture 1.3.1 from Google Maven; the GitHub Packages repository and token are gone. AndroidX, coroutines, Retrofit, Moshi, Dagger, Room and Mockito are on current stable versions. The Compose BOM stays at 2024.12.01, the generation data-capture 1.3.1 is built against: a newer BOM crashed forms on the second page (PEDS: `layout state is not idle before measure starts`).
- **Sync that never finished**: the download crashed (`NullPointerException`) whenever a downloaded resource had no `meta.lastUpdated`, which dropped the page and ended the sync as failed with no message while the progress bar sat at 100%. The newest-timestamp step now skips resources without one. The progress was also misleading: the total the sync library gives (e.g. 28) only counts the matching patients, while the download also brings their encounters and observations (914), so the bar pinned at 100%; it now becomes indeterminate with a live "N items synced so far" count (popup, pill, Sync screen, notification, history of a running sync). A session left open by a sync that was cut short is closed as interrupted when the next one starts, so its numbers no longer carry over. Verified on the emulator: a sync that failed after 914 items now completes (940/940 in 48 s).
- **Form check on the emulator** (fresh install, UI-driven, basic auth): each form that is selectable in the picker (22) was created with the first and with the last answer of every question (dropdown options picked from the list, show/hide rules and required fields followed) and submitted; all 22 submit without a crash. Edit mode was run for each of them (open an existing encounter, answers come back filled in, change every answer, submit, reopen). The four forms with `observation-child` groups (Closure, Assessment, Follow-up, Activity) keep consistent parent links after editing. WHODAS 2.0, Brief Cope and the second DASS-21 are bundled but not listed in the sections of `app_config.xml`, so they are not selectable until a section lists them. The project test suites were not run.
- **Settings**: one file per app, `res/xml/app_config.xml`, replaces `local.properties` settings, the generated resources and the per-module `server.xml` / `auth_config.xml` (see README, *Configuration*). Sync URLs, SSO endpoints and the constants nobody changed are no longer settings; form sections are declared with XML tags.
- **Look and feel**: OpenMRS 3 teal/Carbon palette (light and dark) for Compose, XML and the data-capture form; bottom navigation (Home / Patients / Group / Sync); Home status header; avatar patient rows; one rounded search field.
- **Top bar no longer covers content**: main and login activities are edge-to-edge on every API level and screens pad by the measured app-bar height (before, only API 35+ was right). The content also clears the navigation bar and keyboard.
- **Data capture**: `newQuestionnaireFragment` / `onQuestionnaireResult` replace five copies of the builder and result wiring; registration loads each identifier type once.
- **Bug fixed**: the bottom-bar "Group" tab crashed (wrong navigation argument keys, `patientId` / `isGroupEncounter` instead of `patient_id` / `is_group_encounter`); it now builds the arguments with the generated `CreateEncountersFragmentArgs`.
- **Verification note**: the UI, edge-to-edge and data-capture changes were checked by building, installing on the emulator and looking at the screens (home, patient list, settings, registration, group session flow). The automated suites were **not** re-run after these changes, at the owner's request; run them before merging.
- **Patient lists**: the patient list and the group-session picker only show members of the selected patient lists (taken from the Group resources on the device). Patients registered on the device stay visible until uploaded; with no list selected, or lists not downloaded yet, nothing is hidden. Not exercised on the emulator because its config downloads no Group resources.
- **Bundled questionnaires**: `core/src/main/assets/questionnaires/<id>.json` holds the 26 questionnaires published by the test backend (read-only download). Settings has **Fetch more questionnaires**: it lists the server's questionnaires, compares ids with the engine and the bundled files, and stores only the missing ones (as a download, so nothing is queued for upload). Verified on the emulator: "Questionnaires are up to date."
- **No list, no patient data**: patient requests are scoped to the selected lists whatever the configured sync URLs look like. A `Patient` URL without the `_has:Group:member:id=` placeholder (as in a plain `fhir_sync_urls`) used to download every patient; the filter is now added automatically, and with no list selected all patient-data requests (Patient, Encounter, Observation, ...) are skipped, also in the first-time sync. Verified on the emulator: submit with no list leaves 12 locations and 26 questionnaires, no patients.
- **Patient lists by location**: the group location is read from the string extension the server publishes (it only accepted a reference before), so lists show up for their location when `filter_patient_lists_by_group=true`; the list screen has a "Change location" action.
- **Offline login answer is remembered**: after login the opt-in dialog is shown only until the user has answered it ("enable" or "not now"); the answer survives later logins. Settings > Security shows the state and **Reset offline login** removes the saved offline login and the answer so the next login asks again. If the password changes, reset it so the saved offline credential is refreshed.
- **Light / dark / system** (Settings > Appearance): saved on Save, applied at startup to the XML and Compose screens.
