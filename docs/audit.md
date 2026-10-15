# Audit report (Phase 1 — read-only)

Branch: `chore/audit-and-cleanup` (from `main` @ `cdd4117`). No production code was changed to produce this report.

## How to read this

- **Impact** = what happens to users/data if the problem triggers. **Fix risk** = how likely the fix is to break something.
- **Status**
  - *Confirmed* — verified by reading the code path end to end.
  - *Latent* — real defect, but nothing in the current configuration triggers it.
  - *Verify* — plausible from the code, but depends on SDK/server behaviour that I have not yet tested.
- Items marked **ASK** change sync, auth, schema or visible behaviour and need your approval before I touch them.

## 1. Baseline (before any change)

| Gate | Result | Notes |
|---|---|---|
| `spotlessCheck` — Kotlin sources | **Pass** | `spotlessKotlin` / `spotlessKotlinCheck` clean. |
| `spotlessCheck` — Gradle scripts | **Fail** | `app/build.gradle.kts:71` — the *uncommitted local edit* `include("x86_64","arm64-v8a",…)` is missing a space after the comma. Not on `main`. |
| `spotlessCheck` — XML | **Cannot run locally** | Prettier step needs Node/npm, which is not installed on this machine. Environment issue; CI is unaffected. |
| `./gradlew test` | **Pass** | 7 suites, 16 tests, 0 failures, 0 skipped (`core` only; `app`/`dev3-app` have no test sources). |
| `./gradlew build` (tests excluded) | see `CHANGELOG.md` | Run after the audit commit; result recorded there. |
| `androidTest` (19 Compose/activity UI tests) | **Not run** | Needs an emulator/device. |

Working tree already contained your uncommitted local config (server URL, `x86_64` ABI, cleartext domain for `192.168.1.20`, regenerated `core/schemas/.../2.json`). I am **not** committing those.

## 2. Findings

### A. Data safety and sync (highest priority for an offline-first app)

| ID | Finding | Evidence | Impact | Fix risk | Status |
|---|---|---|---|---|---|
| A1 | FHIR DB is opened with `DatabaseErrorStrategy.RECREATE_AT_OPEN`: if the database fails to open, it is silently recreated, destroying any unsynced local patients/encounters/observations. | `FhirApplication.kt:75` | **High** | Low–Med | Verify (SDK semantics) — **ASK** |
| A2 | Logout wipes the FHIR DB, Room DB and files with only a generic "all data will be deleted" dialog. It never checks for unsynced local changes (`getLocalChanges` is only used for display). A connected user with offline-created data can lose it in two taps. | `MainActivity.kt:650-716`; string `logout_message` | **High** | Low | Confirmed — **ASK** (behaviour change) |
| A3 | Identifier embedding before sync: if `fetchIdentifierFromEndpoint` returns `null`, the identifier value is set to `null`, the `"unsynced"` marker is removed with `removeAt(0)` and the patient is saved. The patient no longer matches the `unsynced` search, so it is never retried and uploads with a null identifier. | `MainActivityViewModel.kt:198-243` (esp. 236, 239) | **High** | Med | Confirmed in code; needs characterization test first — **ASK** |
| A4 | `navigateToLogin` uses `invokeOnCompletion`, which fires even if `clearAppData()` threw or was cancelled → partial wipe followed by navigation to login. | `MainActivity.kt:695` | Med | Low | Confirmed |
| A5 | The per-resource `lastUpdated` timestamp is persisted inside `processResponse`, i.e. *before* the SDK has stored that page. If storing fails or the process dies in between, the next incremental sync starts after resources that were never saved. | `TimestampBasedDownloadWorkManagerImpl.kt:196-208` | Med–High | **High** (changes sync) | Verify (SDK ordering) — **ASK** |
| A6 | Timestamp is formatted with offset (`XXX` → `+02:00`) and appended to the URL unencoded. A literal `+` in a query string is decoded as a space by most servers. Only positive-UTC-offset devices are affected (`Z` and `-05:00` are fine). | `TimestampBasedDownloadWorkManagerImpl.kt:251-256`, `affixLastUpdatedTimestamp` | Med | Low (`%2B` or format in UTC) | Verify against the real server with device TZ set to e.g. +02:00 — **ASK** |
| A7 | Resource type is derived by prefix: `resourceTypeList.find { url.startsWith(it) }` / `findAnyOf`. Enum order is alphabetical, so `QuestionnaireResponse`, `MedicationRequest`, `PractitionerRole`… resolve to `Questionnaire`, `Medication`, `Practitioner`. Effects: `distinctBy` silently drops one of two URLs; wrong timestamp looked up (full re-download or skipped updates). | `TimestampBasedDownloadWorkManagerImpl.kt:78-80, 128` | Med | Low | **Latent** (default URLs don't collide) |
| A8 | `findAnyOf(...)!!` throws NPE (crashing the sync worker) on any URL not containing a known resource name. | `TimestampBasedDownloadWorkManagerImpl.kt:128` | Low | Low | Confirmed |
| A9 | `SyncInfoDatabaseWriterWorker`: (a) `workerScope` is never cancelled and `shareIn(..., Eagerly)` keeps collecting after `doWork` returns → leak per sync; (b) `catch (e: Exception)` swallows `CancellationException` and logs nothing; (c) enqueued with `ExistingWorkPolicy.REPLACE`, so a second trigger can cancel a run mid-flight, leaving an `ONGOING` session row that later runs reuse; (d) uses `println` instead of Timber. | `SyncInfoDatabaseWriterWorker.kt:77, 99, 131-132, 295-315, 336` | Med | Low (a, b, d) / Med (c) | (a,b,d) Confirmed; (c) Verify |
| A10 | `Dao.getOrCreateInProgressSyncSession` returns the new entity with `id = 0` (insert's rowId discarded). Callers currently ignore the return value and re-query. | `Dao.kt:110-128` | Low | Low | **Latent** |
| A11 | HTTP logger is hard-wired to `Level.BODY`, and a file logger is planted in every build type. Full FHIR bodies (patient data) are written to a log file that the "send diagnostics" action zips and emails. The zip password is the `diagnostics_password` string resource, which ships inside the APK (obfuscation, not secrecy). Whether the `Authorization` header is also written depends on the SDK logger; not yet checked. | `FhirApplication.kt:69-86`, `MainActivity.kt:769-800` | **High** (privacy) / perf | Low | Verify what is written — **ASK** (product decision: support needs these logs) |
| A12 | Conflict resolution is `AcceptLocalConflictResolver` — local always wins, server-side edits to the same resource are overwritten with no user visibility. Possibly intended; worth a documented decision. | `BaseFhirSyncWorker.kt` | Med | — | Design note |

### B. Authentication and offline login

| ID | Finding | Evidence | Impact | Fix risk | Status |
|---|---|---|---|---|---|
| B1 | `AuthStateManager.current`, `AuthConfiguration`, and several `LoginRepository` methods use `runBlocking` to read DataStore from property getters/callbacks. Some run on the main thread (AppAuth callbacks) → ANR/jank risk, and `current` re-parses JSON on every access. | `AuthStateManager.kt:77-81`, `AuthConfiguration.kt:52`, `LoginRepository.kt:154, 226, 250, 254, 264` | Med | Med | Confirmed |
| B2 | `updateAfterAuthorization` / `updateAfterTokenResponse` read `current`, mutate, then `replace` — not atomic. A token refresh racing a sync's auth read can lose an update. | `AuthStateManager.kt:84-100` | Med | Low (`dataStore.edit {}` transform) | Confirmed (race window real; frequency unknown) |
| B3 | `performTokenRequest` swallows `UnsupportedAuthenticationMethod`, so the callback never fires and the `suspendCoroutine` caller hangs forever; `suspendCoroutine` is also not cancellable. | `LoginRepository.kt:~190-215` | Med | Low | Confirmed |
| B4 | `getAuthenticationMethod()` returns `Bearer(accessToken)` with no freshness check, and token refresh is only triggered from a UI dialog (`MainActivity.kt:576`). Background periodic sync with an expired token therefore fails until the user opens the app. | `LoginRepository.kt:~237-247`, `MainActivity.kt:555-600` | Med | **High** (auth) | Verify (SDK retry-on-401 behaviour) — **ASK** |
| B5 | `KeystoreHelper.getKey()` does an unchecked `as SecretKey`; `initialize()` swallows exceptions with `printStackTrace`. If the Keystore key is invalidated (lock-screen change, OS reset) every authenticated request throws on an OkHttp thread. | `KeystoreHelper.kt:50, 73` | Med | Low–Med | Confirmed |
| B6 | `isAuthenticated()` has a side effect (wipes basic credentials when expired); `isLockedOut()` writes on read; the "AUTH_STATE" DataStore doubles as the app preference store (e.g. `LOCATION_ID`) and `clearAuthDataStore()` also resets the lockout counters. | `AuthStateManager.kt:~118-130, 188-195` | Low | Med | Confirmed (design smell) |

### C. Performance

No measurements exist yet; these are code-level hypotheses to be measured in Phase 3 before any change is kept.

| ID | Finding | Evidence | Expected effect | Fix risk | Status |
|---|---|---|---|---|---|
| C1 | **N+1 queries**: one `getLocalChanges()` per patient (up to 100 sequential engine queries per list load). Same pattern in patient details and unsynced-resources screens. | `PatientListViewModel.kt:170-174`, `PatientDetailsViewModel.kt:143-158`, `UnsyncedResourcesViewModel.kt:352-420` | Large on list load | Low | Confirmed |
| C2 | `getRiskAssessments()` loads **every** `RiskAssessment` in the DB and groups in memory on each refresh, regardless of which patients are shown. Grows with data; runs even when the list is empty. | `PatientListViewModel.kt:177, 191-199` | Large as data grows | Low–Med | Confirmed |
| C3 | Hard cap `count = 100, from = 0`; no paging. Patients beyond 100 are unreachable except by search, while the displayed total (`count()`) is the real total. | `PatientListViewModel.kt:163-166` | Functional gap + memory | Med | Confirmed |
| C4 | Search fires a new coroutine per keystroke with no debounce and no cancellation → out-of-order results possible (slow older query overwrites newer). No `try/catch`: an exception crashes via `viewModelScope` and leaves `isLoading = true`. | `PatientListViewModel.kt:93-120, 150-185` | Bug + jank | Low | Confirmed |
| C5 | Diagnostics zip is built with `runBlocking` on the main thread. | `MainActivity.kt:769` | ANR risk | Low | Confirmed |
| C6 | `PatientItem` is a data class with `var` fields and a mutable HAPI `List<Identifier>` plus an HTML string per row → Compose treats it as unstable; extra recompositions. | `PatientListViewModel.kt:~205-222` | Unknown — measure | Low | Verify |
| C7 | Lists: missing item keys (`ListContainerComponents.kt:89`, `PatientSelectionDialog.kt:136`, `UnsyncedResourcesScreen.kt:161`); `HomeScreen` rebuilds its row list every recomposition and keys by `hashCode()` (`HomeScreen.kt:188`); `collectAsState` (13) and `collectAsStateWithLifecycle` (12) are mixed — the former keeps collecting in the background. | see left | Minor | Low | Confirmed |
| C8 | Release builds are unminified/unshrunk (`isMinifyEnabled = false` in `core`; `app` release is signed with the **debug** key). Large APK (HAPI FHIR + Compose + ABI splits). | `core/build.gradle.kts`, `app/build.gradle.kts` | APK size, startup, distribution risk | **High** (needs keep rules for HAPI/Room/Moshi) | Confirmed — **ASK** |
| C9 | Startup: `FhirApplication.onCreate` initializes the file logger, FHIR engine config and Keystore synchronously on the main thread, and emits a `Timber.e("…Test Error log…")` on every launch. | `FhirApplication.kt:65-105` | Unknown — measure | Low | Confirmed (test log) / Verify (cost) |
| C10 | Sync: `fhir_sync_urls` are built from first-time URLs + sync URLs per run; `runBlocking` inside URL loading. Over-fetching risk depends on configuration (README already documents scoping). | `TimestampBasedDownloadWorkManagerImpl.kt:70-110` | Config-dependent | — | Needs real-server measurement |

Room tables owned by the app (`Identifier`, `IdentifierType`, `SyncSession`, `GroupSessionDraft`) are tiny and only queried by primary key or with `LIMIT 1`; I found no missing-index problem there. The FHIR SDK manages its own indexes.

### D. Organization and maintainability

| ID | Finding | Evidence |
|---|---|---|
| D1 | **Dead code candidates** (no references outside their own file): `EncounterFormSectionAdapter`, `IdentifierTypeRecyclerViewAdapter`, `LocationItemRecyclerViewAdapter`, `ObservationItemRecyclerViewAdapter`, `PatientSelectionAdapter`, `SelectPatientListItemRecyclerViewAdapter` — and their ViewHolders/layouts once confirmed with Android Lint. Remaining adapters (`PatientDetails…`, `PatientItem…`) are referenced and need a closer look. |
| D2 | `LocationViewModelTest.kt` is entirely commented out (0 tests); a large commented-out block sits in `MainActivity.onSyncPress` (`MainActivity.kt:444`). |
| D3 | Oversized classes: `GroupFormEntryFragment` (880 lines), `MainActivity` (863), `ObservationGroupUtils` (777), `ListRowComponents` (670), `EditEncounterViewModel` (633), `MainActivityViewModel` (584 — sync, identifiers, connectivity, logout, token state in one class), `UnsyncedResourcesViewModel` (521). |
| D4 | Duplicated URL-building logic between `TimestampBasedDownloadWorkManagerImpl` and `GroupDownloadWorkManagerImpl` (location/cohort filtering); `BaseDownloadWorkManagerImpl` is only used by the simpler managers. |
| D5 | Typos in identifiers: `embeddIdentifierToUnsyncedPatients`, `showInProgressNotifiation`, `getLasUpdateTimestamp`. |
| D6 | `setResValue` silently skips properties missing from `local.properties` → a misspelled/missing config quietly falls back to defaults; `app/build.gradle.kts` declares `packaging {}` twice. |
| D7 | Dependencies on `1.2.0-SNAPSHOT` / `1.3.0-SNAPSHOT` (FHIR engine, data capture) → non-reproducible builds. |
| D8 | Committed `core/schemas/.../2.json` carries a hand-written identity hash that differs from what Room generates (visible in your local diff). Harmless at runtime, but the committed file should be the Room-generated one. |
| D9 | XML/Compose mixing is in progress as documented (`docs/compose-migration/`); not a defect, but 12+ fragments still wrap Compose via XML nav graph. |

### E. Test coverage

16 tests exist across ViewModels for patient list, settings, sync info and unsynced resources, plus two fragment tests. There is **no** coverage for: download work managers (URL building, timestamps), `AuthStateManager` / `LoginRepository` / offline login, `SyncInfoDatabaseWriterWorker`, identifier embedding (A3), logout/`clearAppData`, any form ViewModel (`Add/Edit Patient`, `EditEncounter`, `GroupFormEntry`, `GenericFormEntry`), `MainActivityViewModel`. These are exactly the areas with the highest-impact findings, so characterization tests come before any change there.

### F. Gaps vs. OpenMRS capabilities (candidates for Phase 4 — not decided)

The app reads/writes Patient, Encounter, Observation, Condition (display), Questionnaire, Group (cohorts) and Location. Candidate additions, each to be checked against the target server's FHIR capability statement (`/ws/fhir2/R4/metadata`) before I propose it for real:

1. **Patient search by identifier/phone** — current search filters on `Patient.NAME` only. Offline, FHIR-native, low risk, high daily value.
2. **Unsynced-data safety net** — block or strongly warn on logout/sign-out with pending local changes (A2); show per-patient sync state consistently.
3. **Allergies** (`AllergyIntolerance`) — view/record; FHIR-supported in OpenMRS FHIR2.
4. **Medications** (`MedicationRequest`) — read-only view first.
5. **Appointments / programs** — likely REST-only in OpenMRS; would be behind a flag and follow the REST rules in your constraints.
6. **Conflict visibility** (A12) — surface when a local edit overwrote a newer server version.

## 3. Proposed plan, safest → riskiest

| Step | Content | Gate |
|---|---|---|
| 0 | Commit this report. Keep your local config out of commits. | — |
| 1 | **Characterization tests** (no behaviour change): download-manager URL building and timestamps (A6–A8), `PatientListViewModel` list/search/sync-flag behaviour (C1–C4), identifier embedding (A3), sync-info worker. Reinstate or delete `LocationViewModelTest`. | Tests green on current code |
| 2 | **Zero-behaviour-change fixes**: C4 (search cancel/debounce/try-catch), C5 (zip off main thread), A8 (null-safe), A9 a/b/d (scope cancel, rethrow cancellation, Timber), A10, C9 test log, D1 dead code (confirmed with Lint), D5 typos, D6 duplicate `packaging`. Each with a regression test where possible. | spotlessApply + tests + build per commit |
| 3 | **Measured performance**: C1 (batch local-change lookup), C2 (scope risk query), C7 list keys. Record before/after. | Keep only if measurable |
| 4 | **ASK first** (each is a behaviour/sync/auth/schema decision): A1, A2, A3 fix, A4, A5, A6, A11, B1–B5, C3 paging, C8 minification/signing. | Your approval per item |
| 5 | **Features** from section F, one at a time, behind config flags. | Your approval per feature |

## 4. What I have not verified

- Nothing has been run against the backend at `192.168.1.20:8080` yet (login, full sync, offline create → upload). I'd do this at the end of step 1 to confirm the baseline behaviour before changing anything, and for A6/B4 specifically.
- No device/emulator was used: `androidTest` suites, startup time, scroll jank and APK size are unmeasured.
- Server-side FHIR capability for section F items.
