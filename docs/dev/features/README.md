# Features that differ from a standard OpenMRS client

One page per behaviour. Each says only what the app does differently from OpenMRS or the Android FHIR SDK,
and why. Read the page before changing the feature.

## Sync and data

| Page | About |
|---|---|
| [`sync-modes.md`](sync-modes.md) | Download only, upload only or both |
| [`sync-retries.md`](sync-retries.md) | Requests lost on a bad network are sent again inside the sync |
| [`patient-list-download.md`](patient-list-download.md) | Downloading the patients of selected patient lists |
| [`live-mode.md`](live-mode.md) | Uploading at once and searching the server, on top of the offline database |
| [`import-export.md`](import-export.md) | Handing unsent data to a colleague in a PIN-protected file |
| [`fhir-extensions.md`](fhir-extensions.md) | The FHIR extensions the app reads or writes |

## Patients and visits

| Page | About |
|---|---|
| [`recent-patients.md`](recent-patients.md) | The recent patients section of the Patients screen |
| [`visits.md`](visits.md) | Visits created automatically or managed by the user |
| [`identify-later.md`](identify-later.md) | Patients registered without an identifier |
| [`delete-patient-data.md`](delete-patient-data.md) | Removing the patient data from the device |

## Forms

| Page | About |
|---|---|
| [`forms.md`](forms.md) | Keeping a form fast; the extensions the forms use |
| [`observations.md`](observations.md) | How answers become observation groups, and how an edit reconciles them |
| [`diagnosis-picker.md`](diagnosis-picker.md) | The form question that searches a long list of diagnoses |
| [`form-page-breadcrumbs.md`](form-page-breadcrumbs.md) | The row that jumps between the pages of a form |

## Users, sign-in and settings

| Page | About |
|---|---|
| [`sign-in-setup.md`](sign-in-setup.md) | Password, single sign-on, offline login and the app lock (the flow: [`offline_login_flow.mmd`](offline_login_flow.mmd)) |
| [`multi-user.md`](multi-user.md) | Several people on one device |
| [`share-settings.md`](share-settings.md) | Sharing the server settings by QR code |
