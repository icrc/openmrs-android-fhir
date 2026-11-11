# Coverage matrix

Every screen (S) and function (F) of `app-inventory.md`, with the chapter that documents it and the screenshots
(`screenshots/<lang>/<id>.png`). "text" means the chapter explains it without a picture; "not done" is listed
at the end and in the final report.

## Screens

| Id | Screen | Chapter (file) | Screenshots |
|---|---|---|---|
| S01 | Splash | 02 Getting started (text) | text only (it is on screen for a moment) |
| S02 | Login page | 02 `02-getting-started` | login-01-page, login-03-wrong-password |
| S03 | Settings of the login page (language, appearance, server) | 91 `91-troubleshooting`, 02 | login-02-settings |
| S04 | Users with data waiting | 30 `30-security-and-data` (text) | not done: needs a second user with unsent data |
| S05 | Offline login question | 02 | login-04-offline-login |
| S06 | Lock screen | 30 (text) | not done: needs the fingerprint / screen-lock prompt of the system |
| S07 | Location | 02, 14 `14-location-and-lists` | location-01-choose |
| S08 | Patient lists | 02, 14 | lists-01-choose, lists-02-change |
| S09 | Home | 02, 03 `03-navigation-overview` | home-01-start |
| S10 | Menu | 03 | menu-01-drawer |
| S11 | Bottom bar | 03 | home-01-start |
| S12 | Patients list (search, sort, recent, identify later) | 11 `11-patients` | patients-01-list, patients-02-search |
| S13 | Patient file | 11 | patient-01-file |
| S14 | Registration form | 10 `10-register-patient` | register-01-form |
| S15 | Edit patient | 10, 11 (text) | text only (the same form with the saved answers) |
| S16 | Create Encounter (forms and sections) | 12 `12-record-encounter` | forms-01-sections |
| S17 | Encounter form | 12 | not done: form pages need many answers; described in text |
| S18 | Edit / view encounter | 12 (text) | not done |
| S19 | Group session form | 13 `13-group-and-visits` | group-01-forms |
| S20 | Select patients (group) | 13 (text) | not done |
| S21 | Sync page | 20 `20-sync-and-live-mode` | sync-01-page |
| S22 | Pending list (unsynced) | 20 (text) | not done: nothing is pending in the capture run |
| S23 | Import / Export | 21 `21-import-export` | importexport-01-page |
| S24 | Settings | 90 `90-settings` | settings-01-top, settings-02-middle, settings-03-form-sections |
| S25 | Form sections editor | 90 | settings-04-sections-editor |
| S26 | QR scanner and share settings | 90 (text) | not done: needs the camera |
| S27 | Identify patient dialog | 11 (text) | not done |
| S28 | Identify later note | 11 (text) | not done |
| S29 | Visit dialogs | 13 (text) | not done: visits are automatic in the sample configuration |
| S30 | Diagnosis picker | not documented | not done: no bundled form of the sample configuration uses it in the capture run |
| S31 | Send diagnostics | 30 | diagnostics-01-dialog |
| S32 | Delete patient data | 30 | delete-01-dialog |
| S33 | Log out | 30 | logout-01-dialog |
| S34 | Pending uploads / sessions dialogs | 20, 13 (text) | not done |
| S35 | Spotlight tour | 02, 03 (text) | not done: switched off in the capture run so that it does not cover the screens |
| S36 | Single sign-on (browser page of the organisation) | 02 `02-getting-started` | sso-01-browser, sso-02-filled |

## Functions

| Id | Function | Chapter | Screenshots |
|---|---|---|---|
| F01 | Sign in with a password | 02 | login-01-page, login-03-wrong-password |
| F02 | Single sign-on | 02, 91 | sso-01-browser, sso-02-filled |
| F03 | Work without an account | 02 (text) | login-01-page (callout 5) |
| F04 | Offline login and lock screen | 02, 30 | login-04-offline-login |
| F05 | Choose the server on the device | 91, 90 | login-02-settings |
| F06 | Choose the location | 02, 14 | location-01-choose |
| F07 | Choose patient lists | 02, 14 | lists-01-choose, lists-02-change |
| F08 | Register a patient | 10 | register-01-form |
| F09 | Find patients | 11 | patients-01-list, patients-02-search |
| F10 | Live search / download from the list | 11, 20 (text) | not done (live mode is off in the sample configuration) |
| F11 | Patient file | 11 | patient-01-file |
| F12 | Record an encounter | 12 | forms-01-sections |
| F13 | Resume a started session | 13 (text) | not done |
| F14 | Group session | 13 | group-01-forms |
| F15 | Visits | 13 (text) | not done |
| F16 | Identify later | 11 (text) | not done |
| F17 | Sync | 20 | sync-01-page |
| F18 | Live mode | 20, 90 (text) | not done |
| F19 | Import / Export | 21 | importexport-01-page |
| F20 | Settings | 90 | settings-01-top, settings-02-middle |
| F21 | Share settings (QR) | 90 (text) | not done |
| F22 | Several users on one device | 30 (text) | not done |
| F23 | Delete patient data, log out | 30 | delete-01-dialog, logout-01-dialog |
| F24 | Send diagnostics | 30 | diagnostics-01-dialog |
| F25 | Session expired strip | 20 (text) | not done |
| F26 | Connection strip | 03 | menu-01-drawer, home-01-start |
| F27 | In-app tour | 02, 03 (text) | not done |
| F28 | Language | 90, 91 | login-02-settings |
| F29 | Notifications | 02 (text) | system dialog, not captured |
| F30 | Empty, loading and error states | 91 (text) | not done: the fake backend supports the scenarios (`empty`, `error`, `slow`, `offline`), but they were not captured |

## Not done

Screens that need more than the capture run can give: a second user (S04), the system fingerprint or screen-lock
prompts (S06), the camera (S26), a form answered page by page (S17, S18), the group patient picker (S20), pending
changes (S22), identify later (S27, S28), user-managed visits (S29), the diagnosis picker (S30), the tour (S35) and
the scenario states (F30). Their chapters explain them in text from the code and the in-app help texts.
