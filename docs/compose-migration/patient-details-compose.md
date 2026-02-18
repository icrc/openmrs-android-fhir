# Patient details Compose layout

The patient details screen (`PatientDetailsFragment`) still hosts a RecyclerView, but its header, section headers, and cards now render via Compose. The adapter (`core/src/main/java/org/openmrs/android/fhir/adapters/PatientDetailsRecyclerViewAdapter.kt`) owns the layout structure by mapping each `PatientDetailData` entry to a Compose-backed view holder.

## Layout structure

1. **Patient summary header** – `PatientDetailsOverviewHeader` renders the name, avatar icon, and identifiers.
   - Displays each identifier as `"<type>: <value>"`.
   - Filters out identifiers with type text equal to `"unsynced"`.
   - Test tags: `PatientDetailsOverviewHeader`, `PatientDetailsName`, `PatientDetailsIdentifier`.
2. **Unsynced banner** – `PatientUnsyncedCard` appears immediately after the header when the patient has local changes.
   - Test tag: `PatientUnsyncedCard`.
3. **Section headers** – `PatientDetailsHeaderRow` labels encounter/visit sections.
   - Test tag: `PatientDetailsHeader`.
4. **Detail rows** – `PatientPropertyRow`, `EncounterListItemRow`, and `VisitListItemRow` render remaining details.

## ComposeView state flow

Each RecyclerView view holder owns a small `mutableStateOf` instance holding its current model. The `ComposeView` is configured once in the holder’s initializer, and `bind()` only updates the state. This avoids re-calling `setContent()` during list updates while still allowing Compose to recompose when new data arrives.

When adding a new row type:

- Create a composable that accepts the display data as parameters.
- Add a view holder with a `mutableStateOf<YourModel?>` that sets content once and updates state in `bind()`.
- Ensure you expose stable `testTag` values for UI tests.
