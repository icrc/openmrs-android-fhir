# Several users on one device

A device can be shared by several people, each signing in with their own account. When the data of
a user is kept at log out (the "Delete data when logging out" option is off), the next user finds it on the
device. This page is what the app does about that.

With the default (data is deleted at log out) nothing stays for anyone and none of this shows.

## What a user sees

* **Their own patients, not the others'.** A patient is *owned* by the user who registered it or
  downloaded it. Another user does not see it in the patient list, in the group-session patient
  picker or in the list of unsynced resources, unless:
  * the owner turned on **Settings > Shared device > "Let other users see my registered data"**
    (per user, **off by default**), or
  * the user has access to the patient themselves: it is in a patient list they selected, or they
    opened it from the server (live mode), or they download it (sync) or register it.
* **Their own patient lists and live patients.** The selected patient lists and the patients
  brought by the live search are the user's: signing in as another user puts that user's in place
  (a new user starts with none) and keeps the previous user's for when they come back.
* **Their own location.** The location chosen after login belongs to the user too. A user who
  signs in on a device where another user chose a location is asked for theirs (they may have no
  access to the other one); the location a user used last is kept for them and put back when they
  return. **Settings > Shared device > "Remember my location"** (per user, on by default): on, a
  user goes straight back to their last location after login; off, they are asked for the location
  after every login. How: the location keys are swapped with the patient lists when the signed-in
  user changes, and removed at login for a user who turned the setting off, so the existing "no
  location yet" flow runs.
* **Who still has something to upload**, on the login page: a small cloud icon at the bottom, next
  to the server settings. A grey cloud with a tick means nothing is waiting; an amber cloud with an
  arrow and a red number means that many users have data on the device that has not reached the
  server. A tap opens a dialog listing exactly those users, each with how many patients and
  records, and a **Log in as this user** button that copies the name to the username field, closes
  the dialog and puts the cursor in the password field. A user with nothing waiting is not listed.

## How it works

```
signed in user  -> UserRegistry   (preferences: active user, known users, sharing, ...)
FhirEngine      -> TrackingFhirEngine -> UserDataLedger -> Room: patient_owner, pending_change
```

* `UserRegistry` is the small always-in-memory record of the device: the signed-in user, every user
  who ever signed in (the first one owns what was there before anyone else), who shares, and whose
  lists are the stored ones. It is a preferences file, so "is there more than one user?" costs a
  preference read.
* `TrackingFhirEngine` is the SDK's engine with `create`, `update`, `delete` and `syncDownload`
  wrapped (and `withTransaction`, so what runs inside a transaction is noted too). It is what the
  app injects *and* what the sync workers use (`BaseFhirSyncWorker.getFhirEngine`). Everything else
  (search, get, count, upload) is the SDK's own, untouched.
* `UserDataLedger` writes the notes, in two Room tables (database version 3, migration 2 -> 3):
  * `patient_owner(patientId, owner)`: who brought each patient. Written only once the device has
    **two or more users**; a device with one user pays nothing for it. When the second user arrives
    the patients already on the device are attributed to the first user (`attributeExistingPatients`,
    one pass, before the second user can download anything, repeated if interrupted).
  * `pending_change(resourceType, resourceId, owner, patientId, changedAt)`: a note that `owner`
    created, changed or deleted a record. It is **not** the upload queue (that stays the FHIR
    engine's); the login page asks the engine, record by record, whether it still has something to
    upload for each note, and drops the notes it has not. With no notes that is a single `COUNT`.
* **Visibility** (`DataVisibility.visibleAmong`): a patient is hidden from the signed-in user when
  it has owners, none of them is the user, none of them shares and the user has no access to it
  (selected list, live). Patients with no owner at all are the first user's until the attribution
  above has run, and everyone's afterwards (they came by a path that is not tracked). One query per
  500 candidates; with one user on the device nobody is hidden and no query is made.

## Cost

| | One user | Several users |
|---|---|---|
| Opening the patient list | + one preferences read | + one indexed query per 500 candidates (the list already looks at up to 1000 when a list is selected) |
| Saving a record | + one small insert in a second database (all the records of a save in one insert) | the same |
| Login page | one `COUNT` | the count, then one local-changes lookup per noted record (a few ms each), off the main thread; the icon is grey until it is known |

## Limits (by design of the SDK, or of the scope)

* **The upload queue is whole.** The SDK uploads every pending local change whoever is signed in.
  If user B syncs (or has live mode on) while user A's records are waiting, those are uploaded under
  B's session: the server then records B as their creator. The login-page list tells who has
  something waiting; it cannot make the SDK hold it back. The safe routine is: log in as the user,
  sync, log out.
* **Data from before this version** that is still waiting to upload is not attributed to anyone, so
  it is not listed on the login page (it is attributed to the first user for visibility). It
  uploads with the next sync as before.
* **Hiding is in the app, not on the disk**: the FHIR database is one for the whole device and is
  not encrypted per user. Import / Export exports everything that is waiting to upload, protected
  by the PIN.
* Signing in with single sign-on uses the name the provider sends in the ID token as the user.
  Names are compared without case and without surrounding spaces.
