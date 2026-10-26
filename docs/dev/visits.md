# Visits

In OpenMRS FHIR a visit is an `Encounter` of type `http://fhir.openmrs.org/code-system/visit-type`;
the encounters recorded during it point at it with `Encounter.partOf`. It is open while it has no
`period.end`. The app can handle visits in two ways (Settings > Visits > **Create visits
automatically**).

## Automatic (default)

What the app has always done. Saving an encounter creates a visit for it
(`GenericFormEntryViewModel.createWrapperVisit`: `period.start = period.end` = the encounter's date,
so it is already closed) and links the encounter to it. The user never sees a visit; a patient's page
is one flat list of encounters.

## Managed by the user (setting off)

Like the OpenMRS web application: the user opens a visit, records encounters in it, and ends it.

* **A patient's page** is grouped by visit (`PatientDetailsViewModel.addVisitsWithTheirEncounters`):
  the *Visits* header (with the count and, when no visit is open, a **New visit** action), then each
  visit, newest first, with its encounters indented under it, then *Encounters without a visit*
  (what the server holds that belongs to none). A visit shows whether it is **Active** (green
  outline) or **Closed**, its period, a marker while it waits for upload, and **End visit** (open) or
  **Reopen** (ended).
* **Opening a visit**: *New visit* in the header, or *Create encounter* when none is open (it asks
  first: "No open visit"). Both ask when the visit started (date, then time; not in the future)
  and create it (`OpenMRSHelper.startVisit`, status `in-progress`, no end).
* **Only one visit is open at a time** (the server does not take two): *New visit* is offered only
  when none is open, and *Reopen* only when none is. `OpenMRSHelper.reopenVisit` refuses (and the
  screen says why) if another one is open.
* **Ending**: *End visit* asks to confirm, then sets `period.end` to now (never before the start) and
  the status to `finished`.
* **Saving an encounter** (`GenericFormEntryViewModel.saveEncounter`) goes into the open visit
  (`partOf`). The form only asks for a day while a visit has a time, so a date on the visit's first or
  last day is moved to that moment (`fitInVisit`); a date on any other day is refused with a
  message ("The encounter date is outside the open visit"), and so is saving with no open visit.
* **Group sessions** do not ask each patient for a visit: a patient with an open visit (the date
  fits) gets the encounter in it, the others get a visit made for the encounter, as in automatic
  mode.
* A patient's *last encounter* in the patient list never is a visit (a visit is the container, not
  an encounter).

Both modes upload like everything else (a visit is a local change; with live mode it goes at once).
Changing the setting changes nothing already stored: visits created either way show in the grouped
page, and the flat page shows the encounters.

## Not covered

* Reopening a visit sends a `PATCH` that removes `period.end`. It was checked on the device and in
  what the app stores and sends, not against a real OpenMRS server (nothing is written to the test
  server): how the server treats it should be tried before relying on it.
* A visit type other than *Facility Visit* cannot be chosen yet.
