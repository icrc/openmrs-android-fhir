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
