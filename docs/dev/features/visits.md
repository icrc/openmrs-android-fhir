# Visits

A visit is an `Encounter` of type `http://fhir.openmrs.org/code-system/visit-type`; its encounters point
at it with `partOf`, and it is open while it has no `period.end`. **Settings > Forms, visits and identifiers > Create visits
automatically** chooses who manages them.

## Automatic (default)

Saving an encounter creates an already-closed visit for it (`GenericFormEntryViewModel.createWrapperVisit`,
start = end = the encounter's date) and links them. The user never sees a visit and a patient's page is
a flat list of encounters.

## Managed by the user

As in the OpenMRS web application, the user opens a visit, records encounters in it and ends it.

* A patient's page is grouped by visit (`PatientDetailsViewModel.addVisitsWithTheirEncounters`), then
  *Encounters without a visit*. A visit shows Active or Closed, its period, a marker while it waits for
  upload, and **End visit** or **Reopen**.
* Opening a visit asks when it started (not in the future), from *New visit* or from *Create
  encounter* when none is open (`OpenMRSHelper.startVisit`).
* **Only one visit is open at a time**: *New visit* and *Reopen* are offered only when none is open
  (`reopenVisit` refuses otherwise).
* Ending sets `period.end` to now (never before the start) and the status to `finished`.
* An encounter is saved into the open visit. A form asks for a day, so a date on the visit's first or
  last day is moved into the visit (`fitInVisit`); any other date, and saving with no open visit, is
  refused with a message.
* **Group sessions** do not ask each patient: a patient with an open visit (the date fits) gets the
  encounter in it, the others get a visit made for it, as in automatic mode.
* The *last encounter* shown in the patient list is never a visit.

A visit is a local change like any other (with live mode it goes at once). Changing the setting changes
nothing already stored.

Known gaps are in `known-gaps.md`.
