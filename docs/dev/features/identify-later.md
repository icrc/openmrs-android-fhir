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

## Limits

* The records are only on this device until identified (they are not in the export of unsynced
  data either, since they are not unsynced).
* Records of a patient identified as someone already on the device are merged into that patient
  like any other new records.
* Group sessions and other places that pick patients by list also show the placeholders.
