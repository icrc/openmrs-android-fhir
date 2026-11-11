# Architecture overview and contributor guide

## Modules

| Module | What it is |
|---|---|
| `core` | Android library with nearly all the code (`org.openmrs.android.fhir`). |
| `app` | The application shell. Its `res/xml/app_config.xml` and `res/values/auth_config.xml` configure one build. |
| `dev3-app` | The same shell with its own configuration (the release APK of the CI). |

## How the code is laid out (in `core`)

- **UI**: Compose screens hosted by fragments and activities, see `ui.md`. Strings are in
  `res/values*/strings.xml`, in five languages: English (default), French, Spanish, Russian and
  Arabic.
- **Injection**: Dagger 2 (`di/`). A new ViewModel is registered in `ViewModelModule` (or
  `AssistedViewModelModule` when it takes arguments).
- **Sync** (`data/sync`): the FHIR SDK's download managers build the searches (`SyncUrls`); uploads
  of what was created on the device are tracked in `UnsyncedResource`. The automatic sync runs in
  `MainActivityViewModel` (timer, then `startSync`); it waits for the conditions in
  `AutoSyncGate` (unmetered network, battery). Modes (both / upload only / download only):
  `sync-modes.md`. Live mode: `live-mode.md`.
- **Data**: Room `AppDatabase` (schemas exported to `core/schemas`), preferences in `DataStore`
  (`PreferenceKeys`) and small `SharedPreferences` stores for settings read before anything else.
- **Auth** (`auth/`): `AuthMethod` (OpenID through AppAuth, or Basic), the offline login
  (`BiometricUtils`, `OfflineAuthPrompt`, `AppLock`, `AppLockSettings`) and the stored session
  (`AuthStateManager`). The Keystore and encryption helpers live here too. See `sign-in-setup.md` and `offline_login_flow.mmd`.
- **Several users on one device**: `multi-user/` (`UserRegistry`, `UserDataLedger`); see
  `multi-user.md`. Deleting a user's data: `delete-patient-data.md`.
- **Forms**: questionnaires come from the server or `core/src/main/assets`; the viewer of an
  encounter whose form is not on the device is `EncounterFallbackForm`. The helpers around forms (compact
  questionnaires, summaries, the fragment helpers) are in `forms/`; the log file and the crash
  handler are in `logging/`.
- **Configuration**: one `app_config.xml` per application module, read by `config/AppConfig`. The
  build checks it (`validateAppConfig`, `gradle/validate-app-config.gradle.kts`).
- **Audit trail**: `audit/AuditLog` keeps the last steps (sign-ins, syncs, setting changes) for the
  support team's diagnostics.

## Building and checking

Set-up, the commands to build, run and check, and the helper scripts: `setup/getting-started.md` and
`setup/tools.md`. Do not run plain `spotlessApply` (its XML step rewrites unrelated files).

## Rules of the house

1. **Translations**: a new string goes into all five `strings.xml` files. `StringResourcesTest`
   fails when a tutorial text (`help_*`) is missing in a language or a translation changes the
   placeholders.
2. **Tutorial**: a change that adds, moves or removes a user-facing feature updates the tutorial in
   the same change (`ScreenTours.kt`, `TutorialTargets`, the `help_*` strings). See
   `first-run-tutorial.md`.
3. **Database**: `AppDatabase` is at version 1, the baseline from which the app is installed fresh
   (the earlier migrations were removed). The first time an entity changes after a release exists on
   real devices, bump the version, write a `Migration`, commit the new exported schema and test the
   migration (`androidx.room:room-testing` and the schemas as test assets, as `git log` shows for the
   removed `AppDatabaseMigrationTest`). A migration that does not produce the schema Room expects
   crashes the app of every user who updates. Until then, changing an entity needs a fresh install
   of the devices that hold the old database.
4. **Settings**: a new setting is shown in `SettingsScreen`, wired in `SettingsFragment`, and
   audited (`AuditAction.SETTINGS_CHANGED`) when it changes.
5. **Changelog**: `CHANGELOG.md` says what changed, why, and how it was verified (including what was
   not). The pages in `docs/` describe how things are, not how they were checked: see `docs/README.md`.
6. **Small screens**: check a 320 dp wide screen, a 1.3 times font and a long language (French,
   Russian) before calling a screen done; dialogs wrap their buttons.
7. **The server is not yours to change**: the app works with OpenMRS as it is. A provider or an
   encounter type the signed-in role cannot see is refused by the server, and the app handles it.
8. **Debug network configuration**: `core/src/debug/res/xml/network_security_config.xml` is what lets
   debug builds reach an http server. Never delete it.
