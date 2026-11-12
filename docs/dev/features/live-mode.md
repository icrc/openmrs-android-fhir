# Live mode

Settings > Live mode (it starts as `live-mode-default` of `app_config.xml` says, off when the attribute
is missing) is for a user who is connected and wants what they do to be on the server at once, as in
the OpenMRS web application, without giving up offline work. It **never replaces the local database**:
every screen still reads and writes the device.

| | Without live mode | With live mode |
|---|---|---|
| A patient, encounter or visit is saved | Waits in the local changes until the next sync | Uploaded a moment later |
| Patients you can find | Those of the selected patient lists | Those, plus anyone on the server |
| Opening a patient | What the last sync brought | The record is read again from the server |

## Sending: `LiveSync`

```
save (ViewModel) -> LiveSync.requestUpload() -> [0.8 s] -> LiveUploadWorker
                                                            |- same checks as a sync (location, guest data)
                                                            |- ensureIdentity() + UploadPreparer (identifiers)
                                                            `- Sync.oneTimeSync<UploadFhirSyncWorker>()  (upload only)
```

* A save is written to the device first (a local change), then `requestUpload` is called. Saves that
  follow each other (a visit and its encounter, the patients of a group session) are sent together:
  the request is debounced by 0.8 s.
* `LiveUploadWorker` needs a network, so a save made offline is sent by itself when the connection
  returns.
* The upload is the engine's own sync with a download manager that asks for nothing
  (`UploadFhirSyncWorker`): same strategy, authentication and queue as a sync.
* **What did not go stays queued.** WorkManager retries (3 attempts, 20 s apart, one try each, unlike
  the engine's own retries that wait minutes), then it is left for the next sync. The connection
  banner reads **Live**, **Sending...** or **Not sent yet, will retry**.
* **One upload at a time** (`UploadGate`): a live upload and a full sync read the same queue, so they
  take turns. Live uploads leave no entry in Sync > History.
* "Last sync" is the last *full* sync (`DemoDataStore.saveLastFullSync`), because the engine also
  stamps it after an upload-only run.
* **The automatic (periodic) sync does not run** while live mode is on: its countdown is not started
  (`MainActivityViewModel.observeAutoSync`), so the banner shows no "next sync in" time. It starts
  counting from the full interval when live mode is turned off. "Sync now" and the Sync page still work.
* A guest session never uploads, nor does anything while the data entered as a guest waits to be
  completed.

## Finding patients: `LivePatients`

With 3 or more characters in the patient search, and a network, the app also asks the server (FHIR,
read only) after a 450 ms pause: `Patient?name=` and `Patient?identifier=`, 20 results each. A text of
several words sends one `name` parameter per word (`name=Ann&name=Smith`, ANDed), because no single name
holds all of them; the device search does the same (`applyPatientTextQuery`: every word must match the
name). The server takes name prefixes and the exact identifier, not `telecom`, so a phone number only
finds patients on the device.

* Patients not on the device appear under **On the server**. Tapping one reads it with its encounters
  and observations (the request the sync uses), stores it with `FhirEngine.syncDownload` (so it is not
  a local change and never overwrites what waits for upload), and opens it. The **cloud button** at
  the end of the row does the same without opening: the patient joins the list and is kept on the
  device whatever the "keep patients" setting says (`LivePatients.download(keep = true)`). From then on it is an
  ordinary patient, offline too. Its id is kept in `PreferenceKeys.LIVE_PATIENT_IDS` so the list shows
  it although it is in none of the selected patient lists.
* Opening any patient re-reads the record in the background (once per 30 s per patient).
* **Pull to refresh** on the patient's page (`PatientDetailsViewModel.refresh`) reads the record again
  at once, whether live mode is on or not (it is not limited by the 30 s). It does nothing but reload
  the device's data for a guest session, for a patient created on the device and not sent yet, and
  offline (a message says the server could not be reached). What waits for upload is kept.
* The app searches instead of downloading because the server holds far more patients than a device
  should.

### Keeping or dropping them

Settings > Live mode > **Keep patients from the server on this device** (on by default, used only while
live mode is on).

* **On**: the patient stays like any other, until the data is deleted. There is no age-based clean-up.
* **Off**: the patient is temporary (`LIVE_TEMPORARY_IDS`). When their page closes, after a live upload,
  after a full sync and at app start, `LivePatients.discardTemporary` purges their record, encounters,
  observations and conditions (`FhirEngine.purge`). It never removes a patient with anything waiting
  for upload, one whose page is open, one just downloaded whose page has not opened, or one that was
  already on the device before the search. Switching the setting on keeps the waiting ones.

Patients from the live search are in the patient list but not in the group session's picker, which
lists only the selected patient lists.

## Code

* `data/sync/LiveSync.kt`: `LiveSync`, `LiveUploadWorker`, `UploadGate`
* `data/sync/UploadPreparer.kt`: what runs before an upload; shared with the full sync
* `data/sync/LivePatients.kt`, `data/remote/ApiManager.kt` (`searchPatients`, `fetchPatientRecord`)
* `PatientListViewModel` (`searchServer`, `openServerPatient`)
* `LiveSync.requestUpload` is called by `AddPatientViewModel`, `EditPatientViewModel`,
  `GenericFormEntryViewModel`, `EditEncounterViewModel` and `OpenMRSHelper.startVisit/endVisit`
