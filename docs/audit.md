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
| `./gradlew build` (tests excluded, `--continue`) | **Compiles and assembles; fails only on the spotless tasks above** | 315 tasks, 7m14s. Only `spotlessXml` (×4, no npm) and `app:spotlessKotlinGradleCheck` (local `x86_64` edit) failed. |
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
| A6 | Timestamp is formatted with offset (`XXX` → `+02:00`) and appended to the URL unencoded. A literal `+` in a query string is decoded as a space by most servers. Only positive-UTC-offset devices are affected (`Z` and `-05:00` are fine). | `TimestampBasedDownloadWorkManagerImpl.kt:251-256`, `affixLastUpdatedTimestamp` | Med | Low (`%2B` or format in UTC) | **Not reproducible on the test server (2026-10-15):** raw `+02:00`, `%2B02:00` and the UTC form all returned the same result for a boundary query (see §4). Downgraded to informational; no change planned. |
| A7 | Resource type is derived by prefix: `resourceTypeList.find { url.startsWith(it) }` / `findAnyOf`. Enum order is alphabetical, so `QuestionnaireResponse`, `MedicationRequest`, `PractitionerRole`… resolve to `Questionnaire`, `Medication`, `Practitioner`. Effects: `distinctBy` silently drops one of two URLs; wrong timestamp looked up (full re-download or skipped updates). | `TimestampBasedDownloadWorkManagerImpl.kt:78-80, 128` | Med | Low | **Latent** (default URLs don't collide) |
| A8 | `findAnyOf(...)!!` throws NPE (crashing the sync worker) on any URL not containing a known resource name. | `TimestampBasedDownloadWorkManagerImpl.kt:128` | Low | Low | Confirmed |
| A9 | `SyncInfoDatabaseWriterWorker`: (a) `workerScope` is never cancelled and `shareIn(..., Eagerly)` keeps collecting after `doWork` returns → leak per sync; (b) `catch (e: Exception)` swallows `CancellationException` and logs nothing; (c) enqueued with `ExistingWorkPolicy.REPLACE`, so a second trigger can cancel a run mid-flight, leaving an `ONGOING` session row that later runs reuse; (d) uses `println` instead of Timber. | `SyncInfoDatabaseWriterWorker.kt:77, 99, 131-132, 295-315, 336` | Med | Low (a, b, d) / Med (c) | (a,b,d) Confirmed; (c) Verify |
| A10 | `Dao.getOrCreateInProgressSyncSession` returns the new entity with `id = 0` (insert's rowId discarded). Callers currently ignore the return value and re-query. | `Dao.kt:110-128` | Low | Low | **Latent** |
| A11 | HTTP logger is hard-wired to `Level.BODY`, and a file logger is planted in every build type. Full FHIR bodies (patient data) are written to a log file that the "send diagnostics" action zips and emails. The zip password is the `diagnostics_password` string resource, which ships inside the APK (obfuscation, not secrecy). On device the log file is encrypted per line (`IV:ciphertext`), so it is not readable at rest, but the key material is that APK-resident password and the file is capped at 5 MB, so it is mostly a CPU/IO cost; whether the `Authorization` header is logged is unconfirmed. | `FhirApplication.kt:69-86`, `MainActivity.kt:769-800` | Med (privacy) / perf | Low | Partly mitigated (encrypted at rest); rest *Verify* — **ASK** (support relies on these logs) |
| A12 | Conflict resolution is `AcceptLocalConflictResolver` — local always wins, server-side edits to the same resource are overwritten with no user visibility. Possibly intended; worth a documented decision. | `BaseFhirSyncWorker.kt` | Med | — | Design note |
| A13 | **Unscoped full download when no patient list is selected.** `TimestampBasedDownloadWorkManagerImpl` removes the `_has:Group:member:id=` filter if the user has selected no patient list, turning `fhir_sync_urls` into an all-patients query with `_revinclude` of every Encounter/Observation. **Observed on the test server (128,505 patients): 560 patients + 5,016 encounters + 71 observations after ~7 minutes, with no end in sight (≈1 day extrapolated); I stopped it.** A new user who simply taps "Submit" on an empty patient-list screen triggers this. | `TimestampBasedDownloadWorkManagerImpl.kt:105-117` (`replace("_has:Group:member:id=&", "")`) | **High** (device storage, battery, server load, hours of sync) | Low code change, but it changes sync behaviour | **Fixed** in `f305227` per your decision (a list is required; the sync skips an unscoped request). **Verified on device:** placeholder URL + no list → 0 patients after 80 s (before: 560 patients in 7 min). |
| A14 | **Login impossible once credentials are stored: two `Authorization` headers.** `ApiManager`'s interceptor used `addHeader` for the stored access token and for the stored basic credentials, so every REST request, including the login session check that already carries its own Basic header, went out with two `Authorization` headers. The nginx proxy in front of OpenMRS answers 400. **Observed on device** after a reinstall; the same session check returns 200 after the fix. | `ApiManager.kt:59-74` | **High** (user locked out until app data is cleared) | Low | **Fixed** in `fab0b4a`, unit tested |

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
| C1 | **Per-patient `getLocalChanges` is *not* the bottleneck.** I first suspected an N+1 pattern here; measured on device (see C11) the lookups for all 2,100 resources cost about 4 s in total (~2 ms each). The patient list performs at most 100 of them. `FhirEngine` has no batch API in any case. | `PatientListViewModel.kt`, `PatientDetailsViewModel.kt`, `UnsyncedResourcesViewModel.kt` | None | — | **Closed (measured)**; concurrency was tried and gave no gain (37-46 s vs 31 s), so it was not kept |
| C2 | `getRiskAssessments()` loads **every** `RiskAssessment` in the DB and groups in memory on each refresh, regardless of which patients are shown. Grows with data; runs even when the list is empty. **The test server exposes no `RiskAssessment` resource (§4), so on this deployment the table is empty and the cost is negligible; left unchanged.** | `PatientListViewModel.kt:177, 191-199` | Negligible on this backend | — | Confirmed pattern; **not worth changing here** |
| C3 | Hard cap `count = 100, from = 0`; no paging. Patients beyond 100 are unreachable except by search, while the displayed total (`count()`) is the real total. | `PatientListViewModel.kt:163-166` | Functional gap + memory | Med | Confirmed |
| C4 | Search fires a new coroutine per keystroke with no debounce and no cancellation → out-of-order results possible (slow older query overwrites newer). No `try/catch`: an exception crashes via `viewModelScope` and leaves `isLoading = true`. | `PatientListViewModel.kt:93-120, 150-185` | Bug + jank | Low | Confirmed |
| C5 | Diagnostics zip is built with `runBlocking` on the main thread. | `MainActivity.kt:769` | ANR risk | Low | Confirmed |
| C6 | `PatientItem` is a data class with `var` fields and a mutable HAPI `List<Identifier>` plus an HTML string per row → Compose treats it as unstable; extra recompositions. | `PatientListViewModel.kt:~205-222` | Unknown — measure | Low | Verify |
| C7 | Lists: missing item keys (`ListContainerComponents.kt:89`, `PatientSelectionDialog.kt:136`, `UnsyncedResourcesScreen.kt:161`); `HomeScreen` rebuilds its row list every recomposition and keys by `hashCode()` (`HomeScreen.kt:188`); `collectAsState` (13) and `collectAsStateWithLifecycle` (12) are mixed — the former keeps collecting in the background. | see left | Minor | Low | Confirmed |
| C8 | Release builds are unminified/unshrunk (`isMinifyEnabled = false` in `core`; `app` release is signed with the **debug** key). Large APK (HAPI FHIR + Compose + ABI splits). | `core/build.gradle.kts`, `app/build.gradle.kts` | APK size, startup, distribution risk | **High** (needs keep rules for HAPI/Room/Moshi) | Confirmed — **ASK** |
| C9 | Startup: `FhirApplication.onCreate` initializes the file logger, FHIR engine config and Keystore synchronously on the main thread, and emits a `Timber.e("…Test Error log…")` on every launch. | `FhirApplication.kt:65-105` | Unknown — measure | Low | Confirmed (test log) / Verify (cost) |
| C10 | Sync: `fhir_sync_urls` are built from first-time URLs + sync URLs per run; `runBlocking` inside URL loading. Over-fetching risk depends on configuration (README already documents scoping). | `TimestampBasedDownloadWorkManagerImpl.kt:70-110` | Config-dependent | — | Needs real-server measurement |
| C11 | **Unsynced-resources screen takes ≈30 s with only 10 patients / 197 encounters / 1,890 observations** (debug build, emulator). Phase timings: `search<Observation>` 26.0 s (≈14 ms per resource, SDK/HAPI deserialization of every row), local-change lookups 3.6 s, `search<Encounter>` 2.5 s, rest < 0.5 s. On a field device with thousands of patients this scales into minutes. | `UnsyncedResourcesViewModel.fetchUnsyncedPatientsWithEncounterAndObservations` | **High** | Med–High | **Measured.** Cheap in-app fixes do not exist: the cost is loading all resources. Real options: (a) an SDK/fork API that lists or counts local changes, (b) keep a small index of locally-touched resource ids (needs a Room table → schema change, **ASK**), (c) progressive display (patients first). Not changed yet. |

Room tables owned by the app (`Identifier`, `IdentifierType`, `SyncSession`, `GroupSessionDraft`) are tiny and only queried by primary key or with `LIMIT 1`; I found no missing-index problem there. The FHIR SDK manages its own indexes.

### D. Organization and maintainability

| ID | Finding | Evidence |
|---|---|---|
| D1 | **Dead code — removed in commit `8795853`:** the `IdentifierType`, `LocationItem`, `ObservationItem`, `PatientItem`, `PatientSelection` and `SelectPatientListItem` adapters and view holders (11 files) and the `activity_login`, `activity_basic_login`, `activity_splash` layouts. *Correction:* my first scan wrongly listed `EncounterFormSectionAdapter.kt` as dead; it defines `CreateFormSectionAdapter`, which `CreateEncountersFragment` uses (the compiler caught it, and `form_item`/`form_section_item` layouts stay). Verified by compiling main, unit-test and androidTest sources and assembling the debug app. |
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

### G. UI layer (added at your request — layout simplification and reuse)

Scope: `ui/` (3,810 lines of Compose), `adapters/`, the 9 Compose-hosting fragments, and the 12 remaining XML layouts. Findings come from the code index and targeted reads; nothing has been run on a device yet, so visual claims (dark mode, jank) are marked *Verify*.

| ID | Finding | Evidence | Benefit | Fix risk | Status |
|---|---|---|---|---|---|
| G1 | **No app-level Compose theme.** Each entry point wraps content in a bare `MaterialTheme { }` (default M3 palette), while the XML theme (`AppTheme`, `Theme.Material3.DayNight`, with a `values-night` override) carries the real colors. Compose screens therefore don't pick up the brand palette or dark mode. | no `fun *Theme(` in `core`; `MaterialTheme {` in each ViewHolder/fragment; `res/values/styles.xml`, `values-night/styles.xml` | Consistent look, dark mode, one place to restyle | Med (visual change) | Confirmed in code; visual effect *Verify* |
| G2 | **Hard-coded styling bypasses the theme**: 83 `colorResource(R.color.*)` (many `white`/`black`) vs 12 `MaterialTheme.colorScheme`; 21 literal `Color(0x…)`; 41 literal `fontSize = …sp`. Example: `PatientUnsyncedCard` uses `Color(0xFFfef3e6)` etc. | `ui/components/ListRowComponents.kt:213-222`, `PatientRowComponents.kt:73,119` | Prerequisite for G1; removes dark-mode glitches (white rows on dark background) | Low–Med | Confirmed |
| G3 | **Same row built several ways.** Selectable list row = rounded `Surface`/`Row` + selected background + label + trailing control, re-implemented in `SelectPatientListItemRow`, `IdentifierTypeListItemRow`, `LocationListItemRow`, `PatientSelectableRow`. Card rows (`PatientUnsyncedCard`, `ObservationCardRow`, sync session card) repeat the same `Card` + padding + `testTag` boilerplate. | `ui/components/ListRowComponents.kt:511-545`, `PatientRowComponents.kt:106-170` | One `SelectableRow` / `InfoCard` primitive; fewer lines, uniform spacing/touch targets | Low (androidTest covers rows) | Confirmed |
| G4 | **`actionBarSize` lookup copy-pasted 6 times** (HomeScreen, PatientListScreen, UnsyncedResourcesScreen, SelectionScreens, SettingsFragment) even though `extensions/InsetsExt.kt:43-59` already provides the helper + a `remember`ed composable. Top content padding is computed by hand per screen. | `HomeScreen.kt:112`, `PatientListScreen.kt:229`, `UnsyncedResourcesScreen.kt:107`, `SelectionScreens.kt:404-420`, `SettingsFragment.kt:82,188` | ~60 lines removed, one place to fix inset bugs (edge-to-edge) | Low | Confirmed |
| G5 | **Search + list + empty-state scaffold repeated** across location, identifier and patient-list selection screens (`SelectionSearchField` → `SelectionEmptyState` → `LazyColumn` with `items(key=…)`), and again in patient list/selection dialog. | `ui/screens/SelectionScreens.kt:84-330`, `ui/patient/*` | One `SearchableListScaffold`; fewer divergent behaviours | Low–Med | Confirmed |
| G6 | **Patient details is a RecyclerView of 8 view types, each a separate `ComposeView`** with its own composition and `MaterialTheme`. Per-row ComposeViews are costlier than a single `LazyColumn` and force the XML `patient_detail` layout + view binding + DiffUtil + 8 ViewHolders to stay. | `adapters/PatientDetailsRecyclerViewAdapter.kt:56-330`, `fragments/PatientDetailsFragment.kt:100-115` | Simpler (one composable, ~330 lines of adapter code gone), likely smoother scrolling | Med (largest UI change; needs screenshot/behaviour tests first) | Confirmed structure; perf gain *Verify* — measure first |
| G7 | **Dead XML / adapters.** Layouts with zero references: `activity_login.xml`, `activity_basic_login.xml`, `activity_splash.xml` (activities already use `setContent`). Six unreferenced RecyclerView adapters (see D1) whose only purpose was hosting Compose rows (`ObservationCardRow` is used only by one of them). `MainActivity` still calls `setContentView(R.layout.activity_main)` (`MainActivity.kt:147`) alongside Compose. | `res/layout/*`, `adapters/*` | Less code/APK, faster builds | Low (Lint-confirmed removal) | Confirmed by reference search; confirm with Android Lint |
| G8 | **Nine fragments each hand-build `ComposeView(requireContext()).apply { setContent { MaterialTheme { … } } }`** and each re-implements insets/padding and test-tag naming. A small `ComposeFragment` base (or `composeContent {}` helper wrapping the app theme + insets) removes the boilerplate and is the natural home for G1/G4. | `fragments/{Home,Identifier,Location,PatientList,PatientSelectionDialog,SelectPatientList,Settings,SyncInfo,UnsyncedResources}Fragment.kt` | ~15 lines × 9 removed; single theme/inset entry point | Low | Confirmed |
| G9 | Smaller: `HomeScreen` hand-writes six near-identical `HomeCardConfig(...)` blocks and rebuilds the list on every recomposition; table-driven + `remember` would halve it (`HomeScreen.kt:124-190`). `FormEntry` screens remain XML because the SDC questionnaire is a Fragment — that part should **stay** XML. | see left | Minor | Low | Confirmed |

What I would deliberately **not** touch: the questionnaire/form entry fragments (SDC library is fragment-based and form handling must not change), and the login/auth UI logic (only its theme/layout wrappers).

## 3. Proposed plan, safest → riskiest

| Step | Content | Gate |
|---|---|---|
| 0 | Commit this report. Keep your local config out of commits. | — |
| 1 | **Characterization tests** (no behaviour change): download-manager URL building and timestamps (A6–A8), `PatientListViewModel` list/search/sync-flag behaviour (C1–C4), identifier embedding (A3), sync-info worker. Reinstate or delete `LocationViewModelTest`. | Tests green on current code |
| 2 | **Zero-behaviour-change fixes**: C4 (search cancel/debounce/try-catch), C5 (zip off main thread), A8 (null-safe), A9 a/b/d (scope cancel, rethrow cancellation, Timber), A10, C9 test log, D1/G7 dead code, dead adapters and the 3 unused activity layouts (each confirmed with Lint), D5 typos, D6 duplicate `packaging`. Each with a regression test where possible. | spotlessApply + tests + build per commit |
| 3 | **Measured performance**: C1 (batch local-change lookup), C2 (scope risk query), C7 list keys. Record before/after. | Keep only if measurable |
| 3a | **UI reuse, no visual change**: G4 (one inset/action-bar helper), G3 (`SelectableRow` + `InfoCard` primitives, existing androidTest tags preserved), G5 (shared searchable-list scaffold), G8 (`ComposeFragment` helper), G9 (table-driven Home cards). One screen per commit, same test tags. | Compose androidTests green on emulator; before/after screenshots identical |
| 3b | **App Compose theme (G1+G2)**: add `OpenMrsTheme` mapped from the existing XML palette, replace literal colors/sizes with theme tokens. *Visible change (dark mode, colors)* — **ASK**. | Your approval + side-by-side screenshots |
| 3c | **Patient details → single `LazyColumn` (G6)**, removing the RecyclerView, DiffUtil, 8 ViewHolders and `patient_detail.xml`. Largest UI change — **ASK**; first add a characterization test of the item list the ViewModel produces, and measure scroll/composition time before and after. | Your approval + measured benefit |
| 4 | **ASK first** (each is a behaviour/sync/auth/schema decision): A1, A2, A3 fix, A4, A5, A6, A11, B1–B5, C3 paging, C8 minification/signing. | Your approval per item |
| 5 | **Features** from section F, one at a time, behind config flags. | Your approval per feature |

## 4. Server verification log (test backend `192.168.1.20:8080`)

Ground rules: the backend is for testing only. I do not change its configuration or software and use no admin/maintenance endpoints. Everything below was read-only (`GET`). Any data written later is created by the app through its normal upload path and tagged so it can be recognised as test data.

| Date | Check | Result |
|---|---|---|
| 2026-10-15 | `GET /ws/rest/v1/session` (unauthenticated) | 200, `authenticated:false` — matches the app's `check_server_url` |
| 2026-10-15 | `GET /ws/fhir2/R4/metadata` | 200; HAPI FHIR 5.7.9, FHIR 4.0.1, 21 resource types (below) |
| 2026-10-15 | `GET /ws/rest/v1/session` with Basic auth (credentials from CLAUDE.md) | 200, `authenticated:true` → REST **and** FHIR accept Basic auth on this server, so API-level tests don't need the OpenID browser flow. The app itself is configured `auth_method=openid`, so the OpenID path still needs a device. |
| 2026-10-15 | `GET Patient?_summary=count` | 128,505 patients on the server — a full unscoped `Patient` download is not viable; the `_has:Group:member:id=` scoping in `fhir_sync_urls` matters |
| 2026-10-15 | **A6** boundary query on `_lastUpdated` with raw `+02:00` / `%2B02:00` / `Z` | All three: `total=3`. Server accepts the unencoded offset → A6 closed as informational |
| 2026-10-15 | App on emulator, basic auth: login → first sync → location → home | Works; first sync ≈ 1–2 min |
| 2026-10-15 | **A13** scoped URL with no patient list selected | 0 patients downloaded (was 560 patients / 5,016 encounters in 7 min) |
| 2026-10-15 | **A14** login session check with stored credentials | 400 before the fix, 200 after |
| 2026-10-15 | Identifier search `JAL100305-1010YG` | Returns the one matching patient; non-matching text returns the empty state |
| 2026-10-15 | Unsaved-changes guard | Untouched form leaves without prompt; typed form prompts (a first version prompted wrongly; fixed in `82f9698`) |
| 2026-10-15 | Unsynced-resources screen timing | ≈30 s; 26 s of it in `search<Observation>` (see C11) |

Server FHIR capabilities (interactions) relevant to section F:

| Resource | Interactions | Note |
|---|---|---|
| Patient, Encounter, Observation, Condition, Location, Group | create, read, update, patch, delete, search | Already used by the app |
| AllergyIntolerance, Immunization, Task, DiagnosticReport, Medication, MedicationDispense, Person, Practitioner | create, read, update, patch, delete, search | Candidates for new features, FHIR-only |
| MedicationRequest | read, patch, search (**no create**) | View-only on FHIR; ordering would need REST |
| Questionnaire, ServiceRequest, RelatedPerson, ValueSet | read, search | Read-only |
| EpisodeOfCare | read only | Programs/enrolment not writable via FHIR |
| Appointment | **absent** | Would have to be REST (if the module is installed) |

`PATCH` is supported for the types above, which matches `UploadStrategy.forIndividualRequest(update = PATCH)` in `BaseFhirSyncWorker`.

## 5. What I have not verified

- Only read-only API probes have been run against the backend (§4). Still to do, and needing a device/emulator: the app's OpenID login, a real app-driven full sync, and offline create → upload, to confirm baseline behaviour before changing anything. B4 (token refresh on 401) and A5 (timestamp persisted before storage) also need this.
- No device/emulator was used: `androidTest` suites, startup time, scroll jank and APK size are unmeasured.
- Server-side FHIR capability for section F items.
