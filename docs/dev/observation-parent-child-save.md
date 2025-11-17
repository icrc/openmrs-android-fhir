# Observation parent/child save logic

*Last updated: 2025-11-14*

The observation grouping flow that powers `GenericFormEntryViewModel` was introduced in commit `0f8ed9d (integrated obs group save logic)`. The feature lets us faithfully persist complex OpenMRS observation groups that originate from structured questionnaires. This document breaks down the major components, explains the rules encoded in `ObservationGroupUtils`, and captures the rationale for the design so future contributors can reason about the behavior without spelunking through the ViewModel.

## Why does the save logic look so complicated?

FHIR questionnaires only return flat `Observation` resources when we run `ResourceMapper.extract()`. OpenMRS, however, expects many answers to be stored as parent/child obs groups, sometimes with repeated options or multi-select answers. We therefore need extra metadata—on both the questionnaire and the extracted observations—to rebuild the parent nodes, re-link each child, and avoid duplicating historical data during edits. The code accomplishes this by:

1. Annotating question items with the `http://fhir.openmrs.org/ext/observation-child` extension so we know which question is a child of which obs group.
2. Capturing any codings provided in the response (or in the question’s initial value) and hashing them into `CodingKey`s. These keys drive lookups for both new observations and previously saved ones.
3. Recording “expected value tokens” from sibling value widgets. These tokens help us disambiguate between multiple child definitions that share the same coding (for example, multiple vitals that all use the same SNOMED code but live under different parent groups).
4. Creating or reusing parent `Observation` entities before children are persisted. We ensure the parent’s `code`, `status`, and `effective` fields are updated consistently and we store the relationship via the child’s `partOf` reference.

Without this machinery, we would either lose grouping information altogether or corrupt parents when editing encounters that already contain observation trees.

## High-level flow inside `GenericFormEntryViewModel`

`saveEncounter()` orchestrates the entire save pipeline. After validation and encounter wrapping, we call `saveResources()` which:

1. **Persists the extracted Encounter shell** – we enrich the extracted encounter with OpenMRS-specific subject, type, visit, and location information before calling `fhirEngine.create()`.
2. **Builds a lookup for observation metadata** – `buildObservationGroupLookup(questionnaire, questionnaireResponse)` walks the questionnaire/response tree, returning `ObservationChildInfo` entries with:
   * `childLinkId`: the question that produced the observation
   * `childCodingKeys`: all codes seen in answers or defaults
   * `parentCoding` / `parentCodingKey`: the obs group definition supplied by the extension
   * `expectedValueTokens`: sibling answer tokens that can be used later for disambiguation
3. **Creates concrete observations** – `createObservationEntities()` normalizes each extracted observation so that:
   * Every saved observation has a generated UUID, subject, encounter, final status, and a UTC effective timestamp.
   * Multi-select `CodeableConcept` values with multiple codings become *multiple* observation rows (each with one coding and a copy of the textual value) so downstream analytics can treat them independently.
4. **Ensures a parent exists** – for each normalized child, we look up a matching `ObservationChildInfo`. If present, we call `createParentObservation()` to instantiate the group observation (one per unique `parentCodingKey`). Parents are cached in `parentObservationsByKey` so multiple children reuse the same parent instance.
5. **Links the child to the parent** – `Observation.updateParentReference()` rewrites the child’s `partOf` list to contain *only* the desired parent reference (plus any unrelated references we need to preserve). We store the IDs from `parentObservationsByKey` and write all parent resources to the database before saving the children.
6. **Handles Conditions** – extracted `Condition` resources get encounter-diagnosis categorization (`ConditionCategory.ENCOUNTERDIAGNOSIS`) and the standard subject/encounter references before being saved.

This flow guarantees that every child observation created from a questionnaire either stands alone (if no extension is provided) or sits under a freshly minted parent observation that matches OpenMRS’ expectations.

## Deep dive: `ObservationGroupUtils`

`ObservationGroupUtils.kt` centralizes all the tricky matching and reuse logic so both `GenericFormEntryViewModel` (new encounter) and `EditEncounterViewModel` (edit existing encounter) can share the same ruleset.

### Observation metadata extraction

* `collectObservationChildInfos()` walks every questionnaire item. When it finds the observation-child extension it:
  * Resolves child coding keys from response answers, falling back to `initial` codings when the response is empty.
  * Builds `expectedValueTokens` from sibling questions in the same group by harvesting answer tokens (`collectSiblingValueTokens`). Token extraction is recursive, so nested items or repeated groups still contribute their values.
  * Stores all of this in an `ObservationChildInfo` record.
* `buildObservationGroupLookup()` materializes three caches:
  * `childInfos`: all known child definitions.
  * `childInfosByCodingKey`: a dictionary to quickly locate candidate definitions by `CodingKey`.
  * `parentCodingKeys`: a set of all parent codes. This is later used by `ExistingObservationIndex` to identify parent rows among persisted observations.

### Matching algorithm

When `GenericFormEntryViewModel` needs to decide which parent to attach to a newly extracted observation it calls `ObservationGroupLookup.findChildInfo()` which internally invokes `findObservationChildInfo()`:

1. We first match by coding – every coding on the observation is mapped to a `CodingKey` and used to gather candidate `ObservationChildInfo` entries.
2. If only one candidate exists, we use it. If multiple candidates share the same coding we then compare the child observation’s value tokens (string, number, code, quantity, date, etc.) against `expectedValueTokens`. The candidate with the most overlapping tokens wins. This protects us from repeated codings used for different siblings.
3. If no tokens match we fall back to candidates that do not expect any tokens. When everything still ties, we choose the first candidate deterministically.

### Parent creation and reuse

`createParentObservation()` generates a fresh `Observation` with a brand new UUID, the advertised parent `Coding`, the subject/encounter references, and a default `FINAL` status. Parents are timestamped with `nowUtcDateTime()` so edits always produce a new effective time.

`ExistingObservationIndex` (used heavily in `EditEncounterViewModel`) keeps three indexes:

* `parentObservationsByKey`: map of `ParentKey` → parent observation so we can reuse a still-active parent when editing.
* `parentObservationsById`: needed to resolve `partOf` references and to delete parents that no longer have children.
* `childObservationsByKey`: keyed by both `CodingKey` and “parent context” (none, parent ID, or parent coding). This lets us locate and “consume” matching child observations so we only update what changed.

Although `GenericFormEntryViewModel` only uses the lookup for new records, documenting `ExistingObservationIndex` is important because the same rules inform how we sync edits with historical data.

### Updating the parent reference

`Observation.updateParentReference()` is deliberately cautious:

1. It derives the `desiredReference` from the selected parent’s ID.
2. It filters the current `partOf` list to preserve any unrelated references (e.g., if the child was already linked to another resource type) but removes stale parent observation references.
3. If no parent should be set it ensures we remove any existing obs parent links.

The method returns `true` when a change was made, which helps the edit flow decide whether a child observation needs to be persisted.

## Reasoning behind the rules

* **Why rely on extensions?** We cannot infer obs group membership purely from questionnaire structure because multiple groups can use the same question text or coding. The extension gives us an unambiguous mapping that survives translation and schema tweaks.
* **Why tokenize values?** Questionnaires often emit a generic code for repeated answers (e.g., the same LOINC code for two blood pressure rows). The sibling value tokens (numbers, units, coded answers) allow us to detect which row is which when editing or when multiple observation definitions share the coding.
* **Why split CodeableConcept answers into multiple observations?** OpenMRS treats each selected option as its own obs row. Splitting ensures search queries and analytics align with legacy expectations and avoids storing multi-value codings that the OpenMRS backend cannot index individually.
* **Why dynamically create parents?** Parent observations need OpenMRS-specific metadata (UUIDs, timestamps, encounter references). Creating them on the client keeps the sync payload consistent and prevents the server from guessing parent state based on partially filled children.

## Practical guidance for contributors

* When designing a questionnaire that should result in grouped observations, attach the `observation-child` extension to the child question and specify the parent coding (system + code).
* If you need disambiguation between repeated children, ensure the relevant sibling value questions use distinct link IDs or values so the token extractor can tell them apart.
* Before touching `GenericFormEntryViewModel` or `ObservationGroupUtils`, read through this document and the source to understand how parent references are computed. Small changes in tokenization or coding resolution can have large downstream effects.
* Use `buildObservationGroupLookup()` whenever you need to reason about questionnaire-defined obs groups—reimplementing the logic elsewhere is error-prone.

By encoding the above rules we can confidently support complex observation hierarchies during both create and edit flows, keeping the mobile client aligned with OpenMRS’ data model.
