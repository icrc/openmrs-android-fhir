# Managing patient lists

The **Patient lists** screen of the home screen is where the user both chooses which lists the device
downloads and manages them. Each row has a tick (download the list's patients, as before), an arrow
(open the list) and, under the rows, a **New patient list** button. A list opens to its description,
location, who created and last changed it, the server's other attributes, a **Keep its patients on
this device** switch (the same choice as the tick) and its members, who can be added and removed.
Offline first: what the user does is saved on the device at once, marked as waiting, and sent later.
After login the screen only chooses (no arrows, no button): that is the first sync's business.

## Words

The app says *patient list*. On the wire it is a FHIR `Group` (`Group.member`, `Group.quantity`,
`managingEntity` for the creator, `type: person`). *Cohort* is OpenMRS's word and lives only on the
server: the Cohort Module's entities, and the server module that maps a `Group` to them. The code
follows that: `GroupMapper`, `GroupServer` and `ServerGroups` talk FHIR; everything else is
`PatientList...` (`PatientListsRepository`, `PatientListEntry`, the `patient_list...` tables). The one
cohort left in the app is the extension `.../ext/group/cohort-type` (the OpenMRS cohort type, as the
`cohort-type` setting of the configuration already names it).

## The server

What the server of a device supports is checked, and the app leaves out what it cannot do: `server-compatibility.md`.

The OpenMRS FHIR module cannot write a Group when the Cohort Module is installed. The module
`openmrs-module-fhir2extension` does (its README, "Group (patient list) support", is the contract).
The app relies on:

| Request | Used for |
|---|---|
| `GET Group?_elements=id,name,quantity,extension,managingEntity,meta,active&_count=100` | The lists, light (no members) |
| `GET Group/<id>` | A list with its members (read when it is opened) |
| `GET Patient?_id=a,b,...&_elements=...` | The names of members the device does not know |
| `PUT Group/<id>` with the id chosen on the device, for an id the server lacks | Create. A `PUT`, not a `POST`: HAPI replaces the id of a created resource, so only a `PUT` keeps the id that the changes queued for the list carry; sent again it is an update, so a lost answer is safe to repeat |
| `PUT Group/<id>` without `member` | Rename or describe; a Group without `member` leaves the members alone |
| `POST Group/<id>/$add-members`, `$remove-members` | Members, by what changes: two devices do not undo each other |
| `DELETE Group/<id>` | Voids the list; 404 and 410 count as done |

A read adds the creator, the dates, who changed it last, the location, the type and the server's own
attributes (extensions `.../ext/group/...`: `fhir-extensions.md`), shown as they come, read only.

The module has not been run against a real server (that HAPI merges a second provider of Group, and replaces the
id of a `POST`ed resource, was read in the library, not run). The fake server of the user guide
(`docs/user-guide/fake-backend/groups.py`) follows the same contract, and `contract_check.py` in that folder sends
the contract to any server: run it against the real one first.

## Why not the FHIR engine

The engine does hold the Groups (the Patient lists screen stores them, and they say which patients
to show), but a Group written through it would be uploaded by the SDK, which sends an update as a
patch (`BaseFhirSyncWorker`), and Group patch is refused (400): every sync would fail. So changes
have tables of their own (`PatientListsDao`):

| Table | Holds |
|---|---|
| `patient_list`, `patient_list_member` | What the server last said; the screen reads its rows from here |
| `patient_list_change` | At most one change per list: `CREATE`, `UPDATE` or `DELETE`; later edits fold into it |
| `patient_list_member_change` | One `ADD` or `REMOVE` per patient; adding and removing the same patient cancels out |

The screens show `PatientListOverlay`: the rows with the changes on top. Nothing is written back to
the rows, so a change the server refuses and the user discards just stops showing. The Patient lists
screen stores the full Groups it reads for the downloads in both places (engine and rows) from the
one answer (`PatientListsRepository.store`).

## Sending and reading (`PatientListsRepository`)

* **`send`**: list changes in the order made (a server that gives the list another id than the
  device's makes what waits for it follow that id), then members (adds before removes, 100 a
  request). A change the server **refuses** (400, 403, 422...) keeps its reason, holds back the
  members of that list, and is not sent again by itself: "Send now" retries it, "Discard changes"
  drops it. A patient the server does not accept does not hold back the others. 404 or 410 for a list
  means it is gone: what was changed in it is dropped and counted. A server that cannot be reached
  (no answer, 5xx, 408, 429) or that says the session ended (401) stops the send and loses nothing.
* **A "yes" is checked.** After a create or an edit that the server accepts, the patient list is read
  back: one that is not there (a gateway that answers a write with its own page, a server without the
  patient list module) is **not created**, and the change stays with the reason "The server said yes but
  did not create the patient list" until "Send now" tries again; an edit the server did not keep is the
  same ("did not keep the change"). A server that cannot be asked afterwards leaves the change to go
  again (a `PUT` is safe to repeat). What the server kept of a create is compared with what was sent
  (name, description, location, cohort type) and anything missing is written in the log. A write answered
  with a web page is treated as not answered.
* **The log.** Every request about a patient list is logged (`Patient list create <id>: answer=...`, with
  the code and the server's reason) and recorded in the audit trail (`PATIENT_LIST_SENT`, with the same
  facts and never a patient's name), so a list that does not arrive can be followed in a diagnostics
  report.
* **`refreshList` / `refreshMembers` / `store`**: the lists are read page by page; a list the server no
  longer lists is removed from the device, but never one something waits for, and nothing is removed
  when a page could not be read or the lists are those of one location. The members are read when a
  list is opened (and again when the list says its count or last change moved).
* **When**: after a sync (`MainActivityViewModel`): a download-only run sends nothing, an upload-only
  run reads nothing; with the live upload (`LiveUploadWorker`); on "Send now"; the Patient lists
  screen reads when it opens. A guest session does none of it (`PatientListsSync`).

## Without a session (guest mode)

A guest creates and manages lists like anyone: everything is saved on the device and nothing is asked of the
server (`PatientListsRepository` with `signedIn = false`: no read, no send). The changes are made by the
guest owner (`""`, `multi-user.md`). When someone signs in, `adoptGuestChanges` makes them that user's
(`LoginActivityViewModel.signInUser`; a change the user already has for the same list or patient wins), and they
are sent with the first sync like the user's own.

A list is created with the location chosen on the device. One made before a location was chosen (a guest, or a
user who has not picked one) has none, and it is not sent until it has: it takes the location chosen later when
the user picks it (`LocationFragment` > `MainActivityViewModel.completeGuestDataForNewLocation`), when guest data
is completed (`GuestDataCompleter`) and whenever the lists are sent (`PatientListsRepository.send` calls
`assignSavedLocation`, so a sync, the live upload and **Send now** all do it). The lists of other locations that
were chosen before keep theirs. A guest can add patients of the device only: the server is not searched.

A patient registered on the device and not uploaded yet can be added to a list: the add is **held for
the upload** (`PatientListMemberChange.heldForUpload`, shown as "Waiting for the upload"), because the
server gives the patient its own id. The id is read from the answer of the upload itself
(`VisitUploadingEngine` notes (id on the device, id on the server) in `patient_uploaded_id` for the patients
something waits for, `UploadedIds`). After an upload (a sync, or the live upload: `PatientListsSync.run`
calls `PatientListsRepository.releaseUploaded` first) the add is moved to that id (`UploadedPatientFinder`)
and sent like any other. Only when nothing was noted (an upload from before this was kept, a download that
replaced the patient) is the patient looked for by name, birth date and sex: one match is the patient, none
and the add is dropped, **several and nothing is guessed**: the add stays, marked "Could not tell which
patient this is", and the user adds the patient again from the list (`Uploaded.Ambiguous`).

## Other places that use the lists

* **A patient's page** has a "Patient lists" card (`PatientListMembershipCard`, `PatientMembershipViewModel`):
  the lists the patient is in and **Manage**, a dialog with a tick per list. A tick is the same
  queued add or remove as on the page of the list. A list whose members have not been read yet
  cannot be ticked (`Membership.UNKNOWN`). Not shown when the device knows no list.
* **The Patients screen** can be limited to one list (`PatientListFilter`, a chip under the sort
  chips). Only the lists ticked for download are offered, since only their patients are on the
  device. The members come from `PatientListsRepository.memberIds` (the adds and removals not yet
  sent included), are fetched by id (`patientsById`), and the text search and the sort are applied in
  memory (`matchesText`, `patientOrder`); the server is not searched while a list is the filter, but
  in live mode the members of the list that are only on the server (added from a search, not downloaded)
  are shown under "On the server", to download (`offerListMembersOnServer`).
* **Patients to identify later** can be put in a list (the list's picker, a group session, the page
  of the patient). The add is **held**: it is not sent and not counted as waiting
  (`PatientListOverlay`, `PatientListMember.toIdentify`), because the server does not know the
  placeholder. When the patient is identified (`UnidentifiedPatients.identify`) the held add is
  re-keyed to the real patient (`replacePlaceholder`) and sent like any other; when the records are
  linked to a newly registered patient it goes to that patient, held for the upload.
* **The Home screen** shows the lists in use next to the location: the downloaded ones (`HomeViewModel.selectedLists`),
  as the name when there is one and as a count otherwise, with an eye that opens them all (and Change, which opens
  the Patient lists screen).
* **Registering a patient** from the Patients screen with a list as the filter, or from the **Create a new
  patient** row of a list's picker, adds the new patient to that list (`AddPatientFragment.ARG_JOIN_LIST`,
  `AddPatientViewModel.joinList`): held for the upload when the patient has not been sent.
* **Group sessions** start from one list: `group-sessions.md`.

## Adding many patients, the history and the export

* **Select all** in the picker chooses every patient shown that is not in the list yet (search first to
  narrow it down); **Add the members of another list** chooses everyone in a list picked from those of the
  device (a list whose members were not read yet is read first, which needs the server). The chosen patients
  can be on the device or only on the server (`PatientListPickerViewModel.addFromList`); the add is the same
  queued add, for all of them.
* **History** (a button of the list's page, `PatientListHistoryDialog`): what was done to the list on this
  device and by whom, newest first (`patient_list_event`: the list created, renamed, deleted, a patient added or
  removed, with the user and the time). It stays after the changes are sent, keeps the last 500 events of a
  list, and says nothing about what other devices did.
* **Export** (a button of the list's page): the members as a CSV file shared through the system's share sheet
  (`PatientListExport`, `FileProvider` of the app). One line per member (name, sex, birth date, identifier,
  patient id, status); a patient removed and not sent is left out, one added and not sent is marked, and a
  text a spreadsheet would run as a formula is made plain text. The file is **not** protected (Import / Export
  is, with a PIN): the user is warned before, and the export is in the audit trail (`DATA_EXPORTED`, with the
  list and the number of patients, never a name).

## Limits

* The changes to lists are the **user's** (`multi-user.md`): each carries who made it
  (`UserScopedPatientLists`), only that user sees and sends it, and it goes to the server under their own
  session. The server's word, the history and the notes of uploads are the device's.
* "Delete patient data" removes the cached members and what waits for sending; the lists stay. "Log
  out and delete all data" clears the tables. Import / Export does not carry list changes.
* A list created here shows no creator until it has been sent and read back.
* Removing a patient voids the membership, and the downloads of patient lists ask the server for
  `_has:Group:member`, which may still match a voided membership: a removed patient may come back
  with the next download of that list, until the server answers it right.
* A list created here carries the location extension as a `Location/<id>` reference; the screen reads
  either form, but whether the server's `Group?location=` finds it was not checked.
* A created list is sent with its name, description, the location chosen on the device and, when the
  configuration names one (`<sync cohort-type=...>`), that type as a `CohortType/<id>` reference
  (`GroupMapper.toCreate`); without it the server's default applies. Attributes are shown, not edited.

The tutorial: the Patient lists tour (`help_lists_*`) has the steps for the waiting changes and the new
list button, and the page of a list has its own tour (`help_plist_*`).

See also `patient-list-download.md`: how the patients of the ticked lists are downloaded.
