# Several users on one device

By default the data of a user stays on the device at log out ("Delete data when logging out" is off),
so the next person to sign in finds it. This page is what the app does about that. With the option on,
nothing stays and none of this shows.

## What a user sees

* **Their own patients.** A patient is owned by whoever registered or downloaded it. Another user does
  not see it in the patient list, the group-session picker or the unsynced resources, unless the owner
  turned on **Settings > General and security > "Let other users see my registered data"** (per user, off by
  default), or the user has the patient themselves (in a patient list they selected, opened from the
  server in live mode, downloaded or registered).
* **Their own patient lists, live patients and location.** Signing in as another user puts that user's
  in place (a new user starts with none) and keeps the previous user's for when they come back. The
  location chosen after login is asked again of a user who has none for themselves. **Remember my
  location** (per user, on by default): off, they are asked after every login.
* **Who still has something to upload**, on the login page: a cloud icon beside the server settings.
  Grey with a tick: nothing waits. Amber with a number: that many users have data that has not reached
  the server. A tap lists them, with how many patients and records, and **Log in as this user** fills
  in the user name.

## How it works

```
signed in user  -> UserRegistry   (preferences: active user, known users, sharing)
FhirEngine      -> TrackingFhirEngine -> UserDataLedger -> Room: patient_owner, pending_change
```

* `UserRegistry` is the record of the device: the signed-in user, every user who ever signed in (the
  first one owns what was there before), who shares, and whose lists are the stored ones.
* `TrackingFhirEngine` wraps `create`, `update`, `delete`, `syncDownload` and `withTransaction` of the
  SDK's engine. It is what the app injects and what the sync workers use; search, get, count and upload
  are the SDK's own.
* `UserDataLedger` keeps two tables. `patient_owner` says who brought each patient; it is written only
  once the device has two or more users (the patients already there are then attributed to the first
  user). `pending_change` notes that a user created, changed or deleted a record; it is **not** the
  upload queue (that stays the engine's): the login page asks the engine, record by record, whether
  each note still has something to upload.
* **Visibility** (`DataVisibility.visibleAmong`): a patient is hidden when it has owners, none is the
  signed-in user, none shares, and the user has no access to it. A patient with no owner belongs to the
  first user until the attribution has run, and to everyone afterwards. With one user on the device
  nobody is hidden and no query is made.

## Limits

* **The upload queue is whole.** The SDK uploads every pending local change, whoever is signed in: if
  user B syncs (or has live mode on) while user A's records wait, they are uploaded under B's session
  and the server records B as creator. The login-page list says who has something waiting but cannot
  hold it back. The safe routine: log in as the user, sync, log out.
* **The patient list changes are the user's** (`patient-list-management.md`): each one carries who made it
  (`UserScopedPatientLists`); only that user sees it, and it is sent only in that user's session, so the
  server records the right person. The login page lists the users with changes to lists waiting next to
  those with records. Two users can have the same patient waiting for the same list without one replacing the
  other. What follows a patient (the id the server gave a registered patient, the identification of a patient
  to identify) works on the changes of every user and keeps their owner.
* **A group session that was started and not finished** (`GroupSessionDraft`) is kept per user: a user who
  signs in finds their own, never another user's. The patients to identify follow the visibility of patients:
  another user does not see them on Home, on the Sync page or in the list of patients to identify, and
  "Delete patient data" removes only those of the signed-in user.
* **Data waiting from before multi-user tracking** is attributed to no one, so it is not listed on the
  login page; it uploads with the next sync.
* **Hiding is in the app, not on the disk**: the database is one for the whole device. Import / Export
  protects what it exports with a PIN.
* With single sign-on the user is the name in the ID token. Names are compared without case or
  surrounding spaces.
