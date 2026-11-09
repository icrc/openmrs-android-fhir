# Recent patients

The Patients screen lists the patients the user worked with last, above the list of all patients
(**Recent patients** / **All patients**). Settings > Patients > **Recent patients shown** says how
many (Off, 5, 10, 15 or 20; 5 until the user chooses). Off hides the section and stops noting
patients; the list already kept is shown again when the setting is raised.

## What counts

A patient goes to the top of the list when the user:

* opens the patient's page (also the way into it after registering a patient),
* saves an encounter for the patient (one form, or the patient's part of a group session),
* edits an encounter or the patient's registration,
* starts, ends, reopens or changes the period of a visit.

Background work never counts: the sync, the identifiers added before an upload, the completion of
guest data and the "identify later" moves write to the same records but are not the user's
interaction, which is why the notes are taken in the screens (`RecentPatients.record`) and not in
`TrackingFhirEngine`.

## Where it is kept

`RecentPatients` keeps only the ids, most recent first, in `PreferenceKeys.RECENT_PATIENT_IDS` (at most
20; the order is `RecentPatientsOrder`). It is personal like the location: on a device shared by
several users `UserRegistry` swaps it in and out at sign-in, and `PatientDataEraser` clears it. Nothing
is kept for a guest session.

The screen (`PatientListViewModel.loadRecents`) reads the patients themselves from the device, so a
patient who is not on it any more is not listed and is dropped from the ids. The same visibility rules
as the main list apply on a device shared by several users. The section is hidden while a search is
typed. The main list below is not changed (a recent patient is in it too), so its count, sort and
"Show more" stay what they were.

## Limit

A patient registered while offline gets another id from the server when it is uploaded (the device's
copy takes it). The note made under the old id then finds no patient and is dropped, so the patient is
back among the recent ones the next time their page is opened. In live mode the registration waits for
the upload and notes the new id (see `AddPatientViewModel`).
