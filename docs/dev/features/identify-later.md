# Identify patient after

The patient is on the server but not on this device, and the user is offline (or does not want to
wait). Registering them would create a **duplicate** on the server. Instead the user records the
visit, the encounters and the rest against a *patient to identify after*, with a note that says who
it is, and finds the real patient on the server later.

## What the user sees

* **Patients > "Identify later"** (next to "Register Patient"): asks for a note (what will help find
  the patient later: name, village, relative, phone), then opens the patient's page. The patient
  is listed like any other, with the chip "Identify later" and the note as its name.
* The page has a card "To identify after" with the note, **Identify patient** and **Edit note**.
  Create Encounter, visits, forms and the list of encounters work as for any patient; there is no
  registration to edit.
* **Sync**: when patients to identify exist, "Sync now" asks first: **Identify now** (the list of
  patients to identify, then the search), **Sync anyway** or Cancel. The automatic sync does not ask
  and does not touch them.
* **Identify**: the search is on the server (name or identifier, like the live search). Choosing a
  patient and confirming moves every record to them; the next sync (or the live upload) sends them.
* The logout confirmation says what happens to them (they are deleted when the data is, otherwise
  they stay on the device).

## How it works

* A **placeholder `Patient`** with a tag (`identify-patient-after`) and a row in the Room table
  `unidentified_patient` (patient id, note, created at; database version 4, migration 3 -> 4).
  `UnidentifiedStore` keeps the ids in memory.
* **Nothing about a placeholder is in the upload queue.** `TrackingFhirEngine` routes `create`,
  `update` and `delete` of the placeholder and of the records whose subject is a placeholder
  (`Encounter`, `Observation`, `Condition`) to `syncDownload` (the way a download is stored: not a
  local change; `delete` becomes a purge). The SDK's upload therefore has nothing to send for them,
  whoever starts it. Everything else is unchanged; with no placeholder the engine pays one empty-set
  check per save.
* `UnidentifiedPatients.identify(entry, serverPatientId)`: downloads the real patient first (if that
  fails nothing is touched), then, in one transaction, purges the records and creates them again for
  the real patient (same ids, visits first, then encounters, then observations), so they are
  ordinary local changes; finally the placeholder is purged and the row removed.

## Where they show

* The Home summary says how many patients wait to be identified (`HomeSummary.toIdentify`). "Everything is
  uploaded" is only said when nothing waits and nobody is to be identified: the sync does not send them, and an
  open action is not "uploaded". The row is a tonal pill (not a coloured text) whenever something is open; the
  changes waiting include those to patient lists.
* The status card of the Sync page does the same: with nothing to upload and patients to identify its title
  is **Action needed**, and a tonal notice under it says how many ("1 patient to identify"), that their records
  stay on the device, and **Review** opens the pending tab, where they are listed. With changes to upload the
  title stays the count of pending changes and the notice shows under it.
  The pending tab lists them apart, above the changes to upload, with their visits, encounters and
  observations folded under them (`UnsyncedResourcesViewModel.fetchPatientsToIdentify`). Each row has
  an **Identify now** link that opens the identification for that patient (`MainActivity.showIdentifyDialog`
  with the id). The rest is read only: nothing there can be downloaded or deleted, since none of it is in
  the upload queue. There is no separate card for them.

## Limits

* The records are only on this device until identified (they are not in the export of unsynced
  data either, since they are not unsynced).
* Records of a patient identified as someone already on the device are merged into that patient
  like any other new records.
* Group sessions and other places that pick patients by list also show the placeholders. A
  placeholder can be added to a patient list: the add waits (it is not sent) until the patient is
  identified, then goes for the real patient (`patient-list-management.md`).
