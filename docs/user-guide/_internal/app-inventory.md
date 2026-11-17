# App inventory (Phase 0)

What the app is, built from the code in this repository. The user guide documents only what is
listed here. Anything not confirmed from the code is marked `TODO: verify`.

## Tech stack

| Area | What the code uses |
|---|---|
| Language / build | Kotlin, Gradle Kotlin DSL, `minSdk 26` |
| Modules | `core` (library with nearly all the code), `app` (application shell), `dev3-app` (the same shell with its own configuration; the release APK of the CI) |
| UI | Jetpack Compose screens hosted by fragments (`ui/screens`, `ui/patient`); the forms are drawn by the form library's own `QuestionnaireFragment` (Android views) |
| Navigation | One navigation graph, `reference_nav_graph`, inside `MainActivity` (a `DrawerLayout`); `SplashActivity`, `LoginActivity`, `UnlockActivity` and `SettingsScanActivity` are separate activities |
| DI | Dagger 2 (`di/`) |
| Data | Room `AppDatabase`, DataStore and SharedPreferences, the Android FHIR SDK's own database (offline first) |
| Network | Retrofit + OkHttp; FHIR R4 under `/ws/fhir2/R4/` and the OpenMRS REST API under `/ws/rest/v1/`; the FHIR SDK's sync workers |
| Auth | Basic (user name and password) or OpenID Connect (single sign-on through AppAuth); offline login with fingerprint, face or screen lock |
| Languages | English (default), French, Spanish, Russian, Arabic (right to left), `values*/strings.xml`; the language is chosen inside the app (Settings) |
| Build variants | `debug` (application id suffix `.debug`, clear-text http allowed through `core/src/debug/res/xml/network_security_config.xml`) and `release`. No product flavors: the "flavors" are the application modules `app` and `dev3-app` |

## Configuration that changes what the user sees

`app_config.xml` (one per application module) decides: the server, the login method (`basic`,
`openid`, `both`), the guest mode ("Continue without signing in"), the location tag, whether the
patient lists are filtered by location, whether live mode starts on, the registration form, whether
identifiers are fetched, and optional form sections. This guide documents the **`dev3-app`**
configuration (password login, no guest mode, identifiers fetched, live mode on at first start) and
says where another configuration changes the screen.

## Screens

Entry point: the launcher starts `SplashActivity`, which decides where to go.

| Id | Screen | How to reach it | Notes |
|---|---|---|---|
| S01 | Splash | Launch the app | Decides: login, lock screen or home |
| S02 | Login page | First launch, or after **Log out** | User name, password, **Log in**; server settings button; "users with data waiting" cloud; help "?" tour; SSO button and guest button only when the configuration offers them |
| S03 | Server settings dialog | Login page, server button | Address of the server, **Test**, save restarts the app |
| S04 | Users with data waiting (dialog) | Login page, cloud icon | Lists users with unsent data, **Log in as this user** |
| S05 | Offline login set-up prompt | After the first successful login | Asks whether to unlock the app with fingerprint, face or screen lock |
| S06 | Lock screen (`UnlockActivity`) | Back to the app after a while, when offline login is on | Unlock button; **Log in with account** instead |
| S07 | Location screen | After login (and from the drawer, "Location") | Search, starred locations first, tap to pick |
| S08 | Patient lists screen (`selectPatientListFragment`) | After the location, or **Patient lists** on Home | Search, tick lists; ticking downloads their patients |
| S09 | Home | Start destination | Status strip (location, last sync), summary, tiles: **Register patient**, **Patients**, **Patient lists**, **Group or family session**, **Sync**, **Import / Export** |
| S10 | Menu drawer | Menu button in the app bar | **Sync**, current location, **Settings**, **Send diagnostics**, **Delete patient data**, **Log out**, version |
| S11 | Bottom bar | Every main screen | Home, Patients, Group, Sync |
| S12 | Patients list | Home tile / bottom bar | Search, sort chips, **Recent patients**, **All patients**, cloud marks, **Show more**, **Register Patient**, **Identify later** |
| S13 | Patient file (`patient_details`) | Tap a patient | Overview card, **Edit Patient**, visits and encounters, **Create Encounter**, pull to refresh |
| S14 | Registration form (`addPatientFragment`) | **Register patient** | Paged form, page breadcrumbs, Next/Previous, Review, Submit |
| S15 | Edit patient (`editPatientFragment`) | **Edit Patient** | Same form with the saved answers |
| S16 | Create Encounter (`createEncounterFragment`) | **Create Encounter** | Search forms, list of forms, sections, favorites, sessions not finished |
| S17 | Encounter form (`genericFormEntryFragment`) | Pick a form | Form, Review, Submit |
| S18 | Edit / view encounter (`editEncounterFragment`) | Tap an encounter / the pencil | Read-only view, **Edit** |
| S19 | Group session form (`groupFormEntryFragment`) | **Group or family session** | Tabs per patient, form |
| S20 | Group session participants (`groupSessionPatientsFragment`) | Group session, after the list and the form are chosen | Untick the absent patients of the list, add others or a patient to identify later, start |
| S20b | Group session list (`groupSessionListFragment`) | Home card or Group tab | Choose the patient list the session is for |
| S21 | Sync page (`syncInfoFragment`) | Home tile, bottom bar, menu, summary | Status, Download/Upload/Both, **Sync now**, **Cancel sync**, Pending and History tabs, patients to identify |
| S22 | Unsynced resources (inside Sync, Pending tab) | Sync page | Patients and records waiting, expand, remove |
| S23 | Import / Export (`importExportFragment`) | Home tile, Sync page | **Export**, **Import**, PIN |
| S24 | Settings (`settings_fragment`) | Menu, **Settings** | Search, Language and appearance, General and security, Forms visits and identifiers, Live mode, Synchronization, Advanced settings (closed: server and connection test, token and sync delays, server compatibility) |
| S25 | Share settings dialog | Settings, QR icon | Shows a QR code; **Import** scans or pastes a code |
| S26 | QR scanner (`SettingsScanActivity`) | Share settings, scan | Needs the camera |
| S27 | Identify patient dialog | Patient file (patient to identify), Sync "Identify now" | Search on the server, confirm |
| S28 | Identify later note dialog | Patients, **Identify later** | Asks for a note |
| S29 | Visit dialogs | Patient file | Start a visit, **End visit**, **Reopen**, edit the period (only when visits are managed by the user) |
| S30 | Diagnosis picker | A form question about diagnoses | Search a long list and tick several |
| S31 | Send diagnostics dialog | Menu | Choose what goes in the report, builds an encrypted file, opens the mail app |
| S32 | Delete patient data dialogs | Menu | Count of what goes, confirm, then fingerprint or screen lock when set |
| S33 | Log out dialog | Menu | Explains what stays or goes, confirm |
| S34 | Pending uploads / pending sessions dialogs | Sync, Group | Warn before leaving with unsent work |
| S35 | Spotlight tutorial | First run, "?" in the app bar, "?" on the login page | One tour per screen |
| S36 | Server compatibility (`serverCompatibilityFragment`) | Settings, Advanced settings, **Open the checks** | What the server supports, **Check again**, **Test writing** (asks first), **Copy the report** |
| S37 | Patient list history and export dialogs | A patient list's page, **History**, **Export** | Who did what on this device; a warning before the CSV file is shared |

Tablet layout: see below.

## Functionalities

| Id | Functionality | Where |
|---|---|---|
| F01 | Log in with user name and password | S02 |
| F02 | Log in with single sign-on (OpenID) | S02, only when the configuration says `openid` or `both` |
| F03 | Work without signing in (guest) | S02, only with `guest-mode="true"` |
| F04 | Offline login (fingerprint, face, screen lock) and the lock screen | S05, S06, Settings |
| F05 | Choose the server on the device | S03, Settings |
| F06 | Choose the working location | S07, menu |
| F07 | Choose patient lists to download | S08, Home |
| F08 | Register a patient (paged form, required answers, review) | S14 |
| F09 | Find patients: search, sort, recent patients, show more | S12 |
| F10 | Live search of the server and download from the list (cloud button) | S12, live mode |
| F11 | Patient file: overview, edit, pull to refresh, call a phone number | S13, S15 |
| F12 | Record an encounter with a form, review, submit, edit, view | S16, S17, S18 |
| F13 | Resume a started form (drafts of group sessions) | S16, S19 |
| F14 | Group or family session | S19, S20 |
| F15 | Visits: automatic, or managed by the user | S13, S29, Settings |
| F16 | Identify later and identify | S12, S27, S28 |
| F17 | Sync (download, upload, both), cancel, history, pending, automatic sync | S21, S22, Settings |
| F18 | Live mode (send at once, search the server) | Settings, S12 |
| F19 | Import / Export a PIN-protected file | S23 |
| F20 | Settings (language, appearance, security, forms, sync...) and reset | S24 |
| F21 | Share settings by QR code | S25, S26 |
| F22 | Several users on one device (who sees which patients, who has data waiting) | S02, S04, Settings |
| F23 | Delete patient data, log out | S32, S33 |
| F24 | Send diagnostics to support | S31 |
| F25 | Session expired strip and sign in again | Home |
| F26 | Connection banner (connected, live, offline, sending) | App bar |
| F27 | In-app tutorial | S35 |
| F28 | Language switch (5 languages, right to left for Arabic) | Settings |
| F29 | Notifications for syncs | Android notification, Settings "Enable Notifications" |
| F30 | Empty, loading and error states | Lists, Sync, login |
| F31 | The app leaves out what the server cannot do (patient list changes, concept observations) | S36, patient lists, group sessions |
| F32 | Add many patients to a list at once (select all, the members of another list), register a patient into a list, export a list, history of a list | Patient list pages, Patients, S37 |

## Permissions

`INTERNET`, `ACCESS_NETWORK_STATE` (the connection banner and the sync conditions), `POST_NOTIFICATIONS`
(asked on Android 13 and newer, for the sync notification), `USE_BIOMETRIC` (offline login). The camera
is used by the QR scanner (`SettingsScanActivity`; the library asks the permission).
No location permission: the "location" in the app is a place of work chosen from a list, not GPS.

## Roles

No role selection in the app. What a user can see is decided by the server (the account's privileges:
for instance an encounter type the account cannot see is refused on upload).

## i18n mechanism

`core/src/main/res/values/strings.xml` (English) and `values-fr`, `values-es`, `values-ru`,
`values-ar`. The language is chosen in Settings (AppCompat per-app locales). Form texts come from the
questionnaires (their own translations). `StringResourcesTest` checks that the tutorial texts exist in
all five languages.

## Tablet-specific layouts

None. `values-sw600dp/dimens.xml` is empty; there is no list-detail, navigation rail or split pane.
A tablet shows the phone layout, wider, and the app rotates freely (no orientation lock on the
main activities). The guide says so in the introduction and shows landscape screenshots.

## Needs real infrastructure (and how the guide handles it)

| Item | Handling |
|---|---|
| A real OpenMRS server | A local fake backend (`fake-backend/`) |
| Single sign-on (Keycloak) | Not in the `dev3-app` configuration; described only in the text, no screenshot |
| QR scanning (camera) | The scanner screen is captured only if the emulator camera works; otherwise a placeholder |
| Fingerprint / face prompts | System dialogs; described in the text, captured only if the emulator can show them |
| Notifications | Android system UI, not captured |
| The mail app (diagnostics) | Not captured |
