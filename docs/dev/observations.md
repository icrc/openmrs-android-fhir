# Observation groups

OpenMRS keeps many answers as a parent observation (an obs group) with child observations.
`ResourceMapper.extract` returns flat `Observation`s, so the app rebuilds the groups itself, when a
new encounter is saved (`GenericFormEntryViewModel`) and when one is edited (`EditEncounterViewModel`).
The shared rules are in `util/ObservationGroupUtils.kt`.

## In the form

A child question carries the `observation-child` extension (`http://fhir.openmrs.org/ext/observation-child`)
with the coding of its parent. A question without it is saved as a plain observation.

To tell which child an observation belongs to the app matches, in this order:

1. the coding of the observation;
2. when several children share a coding, the answers of the sibling questions (`expectedValueTokens`);
3. when the same question sits under several parents (a count asked under "new cases", "cases
   closed"), the order of the form: `assignChildInfos` gives each observation the first equally good
   child that no other observation took. Create and edit use the same assignment.

## On a new encounter

* Each observation gets an id, the patient and encounter, status `final` and the time of the save.
* An answer with several codings becomes **one observation per coding**: OpenMRS stores each choice
  as its own obs.
* One parent is created per parent coding (`createParentObservation`) and the child points at it with
  `partOf` (`updateParentReference`, which leaves any other `partOf` alone).
* A `Condition` gets the category `encounter-diagnosis`, the patient and the encounter.

## On an edit

The new answers are reconciled with what is stored, so ids and history are kept where nothing
changed:

* Soft-deleted observations are ignored when the encounter is loaded.
* A parent that the new answers regenerate is removed first, with its children, so a form that
  regroups answers (a repeating group moved) leaves no stale parent.
* A stored observation that matches an answer is **amended in place** (status `amended`, new value and
  time) when the value or the parent changed, and left alone when not. A multi-coding answer is
  deleted and recreated, one observation per coding.
* Children no answer matched are soft-deleted. A parent no answer touched is soft-deleted too.
* A `Condition` keeps its id when the encounter already has one with the same coding.
* Every write of the edit is queued and flushed together: if one fails, none is applied.
