# Patient lists: download when they are added

Choosing patient lists (**Patient lists** on the home screen) only saves the choice. The sync
brings the patients, but it asks the server only for what changed since its last run (a
`_lastUpdated` timestamp per resource type, `TimestampBasedDownloadWorkManagerImpl`), so the
patients of a list added later, which were on the server long before, would not come.

## What happens

1. Ticking a list remembers it in `PENDING_LIST_DOWNLOADS` (per user, swapped with the other
   personal preferences by `UserRegistry`) and schedules `PatientListDownloader.schedule`: 1.5 s
   after the last tick, in an application scope (leaving the screen does not stop it).
2. `ApiManager.downloadPatientsOfLists` asks for the patients of the remembered lists that are still
   selected (`SyncUrls.patients` with the lists filled in, no timestamp, 50 per page, following
   `next`), with their encounters and observations, and each page is stored the way a sync stores
   it (`syncDownload` with `AcceptLocalConflictResolver`: nothing is a local change, and what waits
   to upload is kept). Patient lists on screen reload as the pages arrive.
3. Done: the lists are forgotten. The screen says "Downloading..." and then how many patients came.
4. Unticking a list that was not downloaded yet forgets it; at login nothing is scheduled (the first
   sync follows and has no timestamp yet).
5. No connection or the server refuses: the lists stay remembered, the user is told the next sync
   will bring them, and `SyncInfoDatabaseWriterWorker` downloads them first in every sync that
   downloads (not in an upload-only one).

Read only: nothing is sent to the server.
