# Live mode

Live mode is a setting (Settings > Synchronization > Live mode, off by default) for a user who is
connected to the server and wants what they do to be there at once, like in the OpenMRS web
application, without giving up what the app is for: working with no connection.

It does two things, and **never replaces the local database**: every screen still reads and writes
the device, so the UI does not wait for the network and everything keeps working offline.

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

* A save is written to the device first (`fhirEngine.create/update`, so it is a *local change*), then
  `LiveSync.requestUpload` is called. Saves that follow each other (a visit and its encounter, the
  patients of a group session) are sent together: the request is debounced by 0.8 s.
* `LiveUploadWorker` is a WorkManager job that needs a network, so a save made offline is sent by
  itself when the connection comes back (checked: about 4 s after reconnecting).
* The upload itself is the engine's own sync with a download manager that asks for nothing
  (`UploadFhirSyncWorker`): same upload strategy (`POST` to create, `PATCH` to update, squashed),
  same authentication, same queue. The order is the engine's (patient, visit, encounter,
  observations), the provider and the location are what a normal sync sends.
* **What did not go stays queued.** A failed upload is tried again by WorkManager (3 attempts, 20 s
  apart, one attempt each: the engine's own retries wait minutes), then left for the next sync. The
  connection banner says "Not sent yet, will retry" until something gets through. Nothing is lost
  and nothing is sent twice.
* **One upload at a time** (`UploadGate`): the live upload and a full sync both read the same queue,
  so they take turns. The full sync's own history is not touched; live uploads leave no entry in
  Sync > History (a failure shows in the banner and the audit trail).
* "Last sync" is the last *full* sync (`DemoDataStore.saveLastFullSync`), because the engine also
  stamps its own "last sync" after an upload-only run, which has downloaded nothing.
* A guest session never uploads (there is no server session), and neither does anything while
  the data entered as a guest is waiting to be completed (the sync does that first).

The banner next to the connection status reads **Live**, **Sending...** or **Not sent yet, will
retry**.

## Finding patients: `LivePatients`

With 3 or more characters typed in the patient search, and a network, the app also asks the server
(FHIR, read only) after a 450 ms pause:

* `Patient?name=...` and `Patient?identifier=...`, in parallel, 20 results each, with `_elements`
  (about 40% smaller than the whole resource). The server takes name prefixes of each word and the
  exact identifier; it does not take `telecom`, so a phone number only finds patients on the device.
* The patients that are not on the device appear under **On the server**. Tapping one reads
  `Patient?_id=...&_revinclude=Encounter:patient&_revinclude=Observation:patient` (the request the
  sync uses), stores it with `FhirEngine.syncDownload` (stored like a sync stores it: not a local
  change, and what waits for upload on the device is never overwritten), remembers the id
  (`PreferenceKeys.LIVE_PATIENT_IDS`, so the patient list keeps showing it although it is in none of
  the selected lists) and opens the patient. From then on it is an ordinary patient, offline too.
* Opening any patient re-reads their record from the server in the background (once per 30 s per
  patient), so someone else's encounters show up.

The server holds a lot more patients than a device should (the test server has about 128,000),
which is why the app searches instead of downloading, and only brings the patients that are opened.

### Limits

* Patients brought by the live search stay on the device until the data is deleted (logout with
  data deletion). There is no automatic clean-up yet.
* They are in the patient list but not in the group session's patient picker, which only lists the
  selected patient lists.
* Search by phone number works on the device only.

## Code

* `data/sync/LiveSync.kt` - `LiveSync`, `LiveUploadWorker`, `UploadGate`
* `data/sync/UploadPreparer.kt` - what runs before an upload (session location, identifiers); shared with
  the full sync (`MainActivityViewModel`)
* `data/sync/LivePatients.kt`, `ApiManager.searchPatients` / `fetchPatientRecord` - the search side
* `viewmodel/PatientListViewModel.kt` (`searchServer`, `openServerPatient`), `ui/patient/PatientListScreen.kt`
* the calls to `LiveSync.requestUpload`: `AddPatientViewModel`, `EditPatientViewModel`,
  `GenericFormEntryViewModel`, `EditEncounterViewModel`, `OpenMRSHelper.startVisit/endVisit`

## How it was checked

On the emulator, against the test server through a local proxy that let every read through and
answered every write with a fake success (nothing was written to the server): the order and content
of the uploads (user as participant of the visit, provider of the encounter, observations pointing at
the encounter and the patient), a save while offline sent when the connection returned, a server that
refuses writes (banner, bounded retries, the next full sync sends the queue and clears the banner),
a patient registration, and the search and opening of a patient that was not on the device.
