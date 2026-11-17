# Patient lists: how the patients are downloaded

Choosing patient lists (**Patient lists** on the home screen) only saves the choice. The sync
brings the patients of the chosen lists with their encounters and observations. It does not ask
the server for them in one search (see "Why not `_revinclude`" below) but in small pieces that are
remembered, in `PatientDownloadWorkManager`.

## How

1. **Ids.** `SyncUrls.patientIdSearches`: the patients of the lists, ids only (`_elements=id`,
   200 per page), at most `SyncUrls.LISTS_PER_SEARCH` lists in a search (all 150 lists of a server
   made a URL of 5.6 KB). Nothing is stored from these answers.
2. **Batches.** The patients are taken `SyncUrls.HISTORY_BATCH_SIZE` at a time. For each batch the
   patients (`Patient?_id=`), their encounters and their observations (`patient=`) are asked for in
   turn, `SyncUrls.HISTORY_PAGE_SIZE` resources a page, following `next`. No response has more than
   a page, whatever the history of a patient.
3. **Each patient carries the time its data is complete up to** (`DemoDataStore.patientHistoryDone`,
   set when a batch is complete). A patient without one is new: all of the data, and first. The
   others (in batches of `SyncUrls.KNOWN_BATCH_SIZE`) only what changed after that time: so new
   encounters of a patient who did not change are found too, which a search by the date of the
   patient never did.
4. **Nothing is trusted before it is stored.** The SDK stores a page before it asks for the next
   request, so progress is saved at the start of `getNextRequest`. A request that got no answer is a
   failure the SDK goes on after: its batch is not marked done and the times of the plain searches
   (locations, forms) do not move, so the next run does it again and nothing is skipped for good.
5. **A cut loses at most a page.** How far a chain of pages got is saved page by page
   (`historyProgress`, named by the patients of the batch), and the next run goes on from there
   (`_lastUpdated=ge`), also inside a very long history.

The same manager runs the download of lists just added:

1. Ticking a list remembers it in `PENDING_LIST_DOWNLOADS` (per user, swapped with the other
   personal preferences by `UserRegistry`) and schedules `PatientListDownloader.schedule`: 1.5 s
   after the last tick, in an application scope (leaving the screen does not stop it).
2. `ApiManager.downloadPatientsOfLists` runs the manager for the lists that are still selected, for
   the patients that have no data yet (the others belong to the sync). Each page is stored the way a
   sync stores it (`syncDownload` with `AcceptLocalConflictResolver`: nothing is a local change, and
   what waits to upload is kept). Patient lists on screen reload as the pages arrive.
3. Done: the lists are forgotten. The screen says "Downloading..." and then how many patients came.
4. Unticking a list that was not downloaded yet forgets it; at login nothing is scheduled (the first
   sync follows).
5. No connection or the server refuses: the lists stay remembered, the user is told the next sync
   will bring them, and `SyncInfoDatabaseWriterWorker` downloads them first in every sync that
   downloads (not in an upload-only one).

Read only: nothing is sent to the server. (Creating and changing the lists themselves is
`patient-list-management.md`.)

## Why not `_revinclude`

`Patient?_revinclude=Encounter:patient&_revinclude=Observation:patient` is one request, but the
server cannot page what it includes: a page of ten patients is the whole history of ten patients.
On a real server that was almost 2 MB, 982 resources and 25 s to build, and a phone on a real
network did not receive it before being cut, again and again, so the sync never got past the first
page. `_count` does not help: it counts patients, not what comes with them. Searching encounters and
observations by `patient=` pages properly. The patients are filtered by list with `_has:Group` but
the encounters and observations cannot be (`patient._has:Group...` is refused), hence the ids.

`$everything` per patient was tried: slower, brings other types, and `_since` was ignored.

## State, and what clears it

* `patient-history-done` (patient id and the time), `history-progress-*` (how far a batch got), and
  the plain searches' times: in `DemoDataStore`.
* "Delete patient data" forgets the patients it removes (`PatientDataEraser`) and signing out with
  the data deleted clears everything.
* An update from a version that downloaded with `_revinclude` has no such times, so every patient
  gets all of its data once more.
