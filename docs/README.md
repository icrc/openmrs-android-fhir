# Developer documentation

These pages describe what is specific to this app. They assume the reader knows how OpenMRS and the
Android FHIR SDK normally work, and say only where the app does something else, and why. How to build
and run is in the root `README.md`; the rules for working on the code are in `CLAUDE.md`.

## Where to start

| Page | About |
|---|---|
| `dev/architecture.md` | Modules, where things live, the rules every change follows |
| `dev/ui.md` | Activities, the screen pattern, how forms are hosted |
| `dev/releasing.md` | Version, build, signing, what to check before and after |

## Behaviour that differs from a standard OpenMRS client

| Page | About |
|---|---|
| `dev/sync-modes.md` | Download only, upload only or both |
| `dev/sync-retries.md` | Requests lost on a bad network are sent again inside the sync |
| `dev/patient-list-download.md` | Downloading the patients of selected patient lists |
| `dev/live-mode.md` | Uploading at once and searching the server, on top of the offline database |
| `dev/visits.md` | Visits created automatically or managed by the user |
| `dev/observations.md` | How answers become observation groups, and how an edit reconciles them |
| `dev/diagnosis-picker.md` | The form question that searches a long list of diagnoses |
| `dev/forms.md` | Keeping a form fast; the extensions the forms use |
| `dev/import-export.md` | Handing unsent data to a colleague in a PIN-protected file |
| `dev/identify-later.md` | Patients registered without an identifier |
| `dev/multi-user.md` | Several people on one device |
| `dev/delete-patient-data.md` | Removing the patient data from the device |
| `dev/sign-in-setup.md` | Password, single sign-on, offline login and the app lock |
| `dev/share-settings.md` | Sharing the server settings by QR code |
| `dev/form-page-breadcrumbs.md` | The row that jumps between the pages of a form |
| `dev/first-run-tutorial.md` | The tutorial, and what to update when a feature changes |
| `dev/fhir-extensions.md` | The FHIR extensions the app reads or writes |

## Decisions and gaps

| Page | About |
|---|---|
| `dev/database-encryption.md` | Why the database is not encrypted and what it would take |
| `dev/known-gaps.md` | What the code does not do yet |

## Tools

| Page | About |
|---|---|
| `dev/emulator-hosts-file.md` | Changing the hosts file of an emulator by hand |
| `dev/offline_login_flow.mmd` | Diagram of the start of the app |

## Writing here

* Say what differs from standard OpenMRS or the SDK, and why. Do not explain what they do.
* Name the code (`Class`, `file`) instead of copying it, so the page does not go stale.
* No server addresses, accounts, passwords or machine names: use a placeholder such as `<server-ip>`.
* No history ("it used to...") and no test narratives. How a change was checked goes in its
  `CHANGELOG.md` entry; a limit that stays goes in the page it belongs to.
* A gap that nothing in the page explains goes in `known-gaps.md`.
* A change that adds, moves or removes something a user sees also updates the tutorial
  (`dev/first-run-tutorial.md`).
