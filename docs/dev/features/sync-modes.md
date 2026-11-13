# Sync modes (upload / download / both)

A sync can send what was recorded on the device, bring the latest data down, or both (`SyncMode`).

## Where it is chosen

* **Settings > Synchronization > What the automatic sync does**: *Upload and download* (default) or
  *Upload only*. Applies to the automatic (periodic) sync, and to "Sync now" when the Sync page's
  choice is left alone. Only the two options are offered: an automatic sync that only downloads is
  not useful, and is never stored (a stored `download` reads as both). Also part of the settings
  QR code (`auto_sync_mode`).
* **Sync page**: *Download*, *Upload*, *Both* above "Sync now". They start on the setting's value
  and are for that run only; the setting is never changed from here. The choice is kept while the
  page is open (rotation included), not after it is left.

## What each one does

| Mode | Worker | Notes |
|---|---|---|
| Both | `FhirSyncWorker` | as before |
| Upload | `UploadFhirSyncWorker` | nothing is downloaded; is not a "last sync" |
| Download | `DownloadOnlyFhirSyncWorker` | its engine's `syncUpload` returns an empty flow, so the SDK has nothing to send; what is waiting stays on the device; does not take `UploadGate`, because it sends nothing |

`SyncInfoDatabaseWriterWorker.enqueue(context, mode)` carries the mode in its input data;
`MainActivityViewModel.startSync` resolves it (`mode ?: the setting`). A download skips the
preparation of what would be uploaded (provider, guest data completion, identifiers in patients)
and the "patients to identify" question, because nothing is sent.

## Deleted on the server

The sync asks only for what changed since the last run, and a patient's record read again only returns
what exists: neither can tell that an encounter was deleted (or voided) on the server, so it would stay on
the device for ever. `ServerDeletions` removes it, in two places:

* **A patient's record read again** (pull to refresh, the read when a page opens in live mode, a patient
  brought by the live search): the encounters and observations the device holds for the patient and the
  answer does not contain. Only when every page of the answer was read.
* **After a full sync** (not an upload-only one, not a guest session): every encounter on the device is
  asked for in batches (`Encounter?_id=a,b,...&_elements=id`); the ones missing go through the same check
  below, and their observations are removed with them. An observation deleted on its own, without its
  encounter, is only noticed by the first case.

What is missing from an answer is only a suspect. It is removed when the server then says so for that
resource alone (`GET` answers 404 or 410), so a partial answer, a search that ignored its filter, a
refusal or a server error never costs a record. Anything waiting for upload is kept (checked before the
question), an encounter too when one of its observations waits. If more than 20 suspects turn out to
exist, or more than 500 questions would be needed, the answers are not trusted and nothing is removed.
Lists and the open patient page reload after a removal (`LiveSync.notifyUploaded`).

Code: `data/sync/ServerDeletions.kt` (`ServerPresence`), `ApiManager` (`existingIds`, `isGone`,
`fetchPatientRecord`), `LivePatients.download`, `MainActivityViewModel.handleSyncWorkInfos`.
