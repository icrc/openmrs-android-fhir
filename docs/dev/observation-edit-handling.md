# Observation editing rules in `EditEncounterViewModel`

This note captures the edit-only behaviors we apply when reconciling questionnaire answers
back into existing Observations during an encounter edit. It complements the parent/child
save design described in `observation-parent-child-save.md` and focuses on the cases that
only appear when we are **editing** (not creating) encounters.

## High-level edit flow

1. Load the encounter's existing Observations and Conditions, excluding soft-deleted
   Observations. [`getObservationsEncounterId` filters out `CANCELLED` entries before we
   create the merge index.][edit-load]
2. Build an `ObservationGroupLookup` from the questionnaire + response to infer parent
   groupings for child answers (see companion doc for the grouping rules).
3. Pre-emptively soft-delete any parent Observations (and their children) that would be
   regenerated from extracted children in the new bundle. This guarantees that regrouping
   does not leave behind stale parents. [`removeReplacedParentObservations` takes the
   bundle + lookup and removes any matching on-disk parents before indexing children.
   ][edit-remove-parents]
4. Index remaining Observations into an `ExistingObservationIndex`, keeping track of parent
   IDs so we can later remove untouched parents. [`ExistingObservationIndex` catalogs both
   parents and children with parent-context keys so we can match extracted answers back to
   stored Observations during upsert.][edit-index]
5. Iterate through the extracted bundle (Observations + Conditions) and upsert each item,
   reusing IDs and amending values where possible. Parent Observations are ensured/reused
   via the index so child `partOf` chains remain stable.
6. Soft-delete any remaining children that were not matched, and soft-delete untouched
   parents at the end. [`deleteRemainingChildObservations` handles children while amending
   touched parents; any parent IDs never touched are soft-deleted afterward.][edit-delete]
7. Flush queued DB operations together to avoid partial writes. [`pendingResourceOperations`
   batches creates/updates so all edits succeed or are rolled back by clearing the queue on
   error.][edit-flush]

## Edit-only cases and rationale

### 1) Parents regenerated from children
When the incoming bundle carries a child Observation that declares a parent coding, we may
need to recreate the parent Observation even if it already exists. `removeReplacedParentObservations`
finds on-disk parents with matching coding keys and soft-deletes them **before** we build the
matching index, along with any children pointing to that parent. This prevents us from
reusing a now-invalid parent/child cluster when the questionnaire response has been
re-grouped (e.g., moving a vitals set into a different repeating group instance).
[Code][edit-remove-parents]

### 2) Reusing parents but marking them touched
`ExistingObservationIndex.ensureParentObservation` reuses an existing parent unless it is
soft-deleted; every reuse marks the parent as "touched" so we know it still belongs to the
edited encounter. Parents not touched are later soft-deleted to avoid orphaned aggregates.
[Code][edit-index]

### 3) Child matching prefers stability and preserves unchanged data
For scalar (string/quantity) and single-coding observations we try to **amend** in place:
- If a matching child exists and neither value nor parent changed, we skip updates entirely
  and still mark the parent touched so we do not delete it.
- If value or parent changes, we set status to `AMENDED`, update `value` and `effective`,
  and persist under the same ID.
- For multi-coding answers, we delete prior matches and recreate one Observation per coding.
These behaviors avoid churn in IDs while ensuring historical entries reflect the latest edit
semantics. [Code][edit-upsert-single] [Code][edit-upsert-codeable]

### 4) Cascading deletions when answers are removed
After processing the bundle we soft-delete any child Observations left unmatched in the
index. If the child belonged to a parent that was touched during the edit, we mark that
parent as `FINAL` with a refreshed `effective` timestamp so audit trails show the parent was
changed by the edit. [Code][edit-delete]

### 5) Parent cleanup after regrouping
Parents that existed before the edit but were never touched during upsert are soft-deleted
at the end, ensuring that regrouping or deleting entire repeat groups does not leave behind
old parent containers. [Code][edit-parent-cleanup]

### 6) Condition reconciliation (edit-specific IDs)
Conditions extracted from the questionnaire reuse IDs when a condition with the same coding
already exists for the encounter; otherwise a new random ID is assigned. This keeps stable
condition references across edits without forcing exact object identity checks.
[Code][edit-conditions]

## References
- `EditEncounterViewModel.saveResources` — orchestrates all edit-specific merging steps.
- `ObservationGroupUtils` — shared parent/child matching utilities used here and in the
  creation flow; see companion doc for deeper rules.

[edit-load]: ../..//core/src/main/java/org/openmrs/android/fhir/viewmodel/EditEncounterViewModel.kt#L561-L590
[edit-remove-parents]: ../..//core/src/main/java/org/openmrs/android/fhir/viewmodel/EditEncounterViewModel.kt#L253-L343
[edit-index]: ../..//core/src/main/java/org/openmrs/android/fhir/util/ObservationGroupUtils.kt#L130-L246
[edit-upsert-single]: ../..//core/src/main/java/org/openmrs/android/fhir/viewmodel/EditEncounterViewModel.kt#L398-L437
[edit-upsert-codeable]: ../..//core/src/main/java/org/openmrs/android/fhir/viewmodel/EditEncounterViewModel.kt#L439-L499
[edit-delete]: ../..//core/src/main/java/org/openmrs/android/fhir/viewmodel/EditEncounterViewModel.kt#L501-L525
[edit-parent-cleanup]: ../..//core/src/main/java/org/openmrs/android/fhir/viewmodel/EditEncounterViewModel.kt#L294-L300
[edit-flush]: ../..//core/src/main/java/org/openmrs/android/fhir/viewmodel/EditEncounterViewModel.kt#L259-L310
[edit-conditions]: ../..//core/src/main/java/org/openmrs/android/fhir/viewmodel/EditEncounterViewModel.kt#L527-L559
