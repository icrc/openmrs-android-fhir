# Compose header utilities

New Compose primitives centralize banner and header rendering:

* `NetworkStatusBanner` – renders a centered, bold status label on the grey background formerly provided by `network_status_flag_layout.xml`. A `NetworkStatusText` test tag is attached for Compose UI assertions.
* `DrawerHeader` – stacks the "Last sync" label and timestamp vertically with the original spacing. Both rows expose test tags (`DrawerHeaderLabel` and `DrawerHeaderValue`).

These composables are defined in `core/src/main/java/org/openmrs/android/fhir/ui/components/HeaderComponents.kt` and are hosted from `MainActivity` via `ComposeView` to keep existing navigation and toolbar scaffolding intact.

## Compose patient selection dialog

`PatientSelectionDialogContent` replaces `dialog_patient_selection.xml` with a Compose-driven dialog surface that still lives
inside `PatientSelectionDialogFragment`. The fragment now uses a `ComposeView` to render the dialog contents and manages
selection state through Compose callbacks rather than RecyclerView adapters or layout inflation.

Key behaviors:

* The `Select all` checkbox is derived from `PatientSelectionUiState` and only enabled when the patient list is non-empty.
* Row selection is handled by the `SelectablePatientRow` composable, which is reused for every patient and exposes stable test tags for UI tests.
* Navigation continues through the fragment’s `onStartEncounter` handler; callers only need to navigate to the dialog destination—no extra inflation code is required.

# Patient list row

`PatientListItemRow` lives in `core/src/main/java/org/openmrs/android/fhir/ui/components/PatientRowComponents.kt` and replaces
`patient_list_item_view.xml` inside `PatientItemRecyclerViewAdapter`. The composable preserves the sync indicator, name label, and
age/gender chip while exposing test tags (`PatientSyncIcon`, `PatientName`, and `PatientAgeGender`) for Compose UI assertions. The
adapter now hosts the composable inside a `ComposeView`, keeping the existing fragment and navigation setup unchanged.

# Identifier and location rows

`IdentifierTypeListItemRow` and `LocationListItemRow` also live in
`core/src/main/java/org/openmrs/android/fhir/ui/components/PatientRowComponents.kt`. The identifier row keeps the checkmark and
required-state styling from `identifier_type_item_view.xml`, while the location row mirrors the favorite star and selection highlight
from `location_list_item_view.xml`. Both are hosted via `ComposeView` in their respective adapters so fragments can remain unchanged
while Compose UI tests target the exposed test tags (`IdentifierTypeIcon`, `IdentifierTypeName`, `LocationListItem`, and
`LocationFavorite`).

# Additional list rows

- Patient details rows now use Compose equivalents: `PatientPropertyRow` for patient properties/observations/conditions, `PatientDetailsHeaderRow` for section headers, `PatientUnsyncedCard` for the unsynced banner, and `EncounterListItemRow`/`VisitListItemRow` for encounters and visits.
- Selection and sync history screens now host `PatientSelectableRow`, `SelectPatientListItemRow`, `SyncSessionRow`, and the unsynced resource rows (`UnsyncedPatientRow`, `UnsyncedEncounterRow`, `UnsyncedObservationRow`) to mirror their original XML styling while remaining in RecyclerView-based flows.

# Patient list row

`PatientListItemRow` lives in `core/src/main/java/org/openmrs/android/fhir/ui/components/PatientRowComponents.kt` and replaces
`patient_list_item_view.xml` inside `PatientItemRecyclerViewAdapter`. The composable preserves the sync indicator, name label, and
age/gender chip while exposing test tags (`PatientSyncIcon`, `PatientName`, and `PatientAgeGender`) for Compose UI assertions. The
adapter now hosts the composable inside a `ComposeView`, keeping the existing fragment and navigation setup unchanged.

# Identifier and location rows

`IdentifierTypeListItemRow` and `LocationListItemRow` also live in
`core/src/main/java/org/openmrs/android/fhir/ui/components/PatientRowComponents.kt`. The identifier row keeps the checkmark and
required-state styling from `identifier_type_item_view.xml`, while the location row mirrors the favorite star and selection highlight
from `location_list_item_view.xml`. Both are hosted via `ComposeView` in their respective adapters so fragments can remain unchanged
while Compose UI tests target the exposed test tags (`IdentifierTypeIcon`, `IdentifierTypeName`, `LocationListItem`, and
`LocationFavorite`).

# Additional list rows

- Patient details rows now use Compose equivalents: `PatientPropertyRow` for patient properties/observations/conditions, `PatientDetailsHeaderRow` for section headers, `PatientUnsyncedCard` for the unsynced banner, and `EncounterListItemRow`/`VisitListItemRow` for encounters and visits.
- Selection and sync history screens now host `PatientSelectableRow`, `SelectPatientListItemRow`, `SyncSessionRow`, and the unsynced resource rows (`UnsyncedPatientRow`, `UnsyncedEncounterRow`, `UnsyncedObservationRow`) to mirror their original XML styling while remaining in RecyclerView-based flows.

# Patient list row

`PatientListItemRow` lives in `core/src/main/java/org/openmrs/android/fhir/ui/components/PatientRowComponents.kt` and replaces
`patient_list_item_view.xml` inside `PatientItemRecyclerViewAdapter`. The composable preserves the sync indicator, name label, and
age/gender chip while exposing test tags (`PatientSyncIcon`, `PatientName`, and `PatientAgeGender`) for Compose UI assertions. The
adapter now hosts the composable inside a `ComposeView`, keeping the existing fragment and navigation setup unchanged.

# Identifier and location rows

`IdentifierTypeListItemRow` and `LocationListItemRow` also live in
`core/src/main/java/org/openmrs/android/fhir/ui/components/PatientRowComponents.kt`. The identifier row keeps the checkmark and
required-state styling from `identifier_type_item_view.xml`, while the location row mirrors the favorite star and selection highlight
from `location_list_item_view.xml`. Both are hosted via `ComposeView` in their respective adapters so fragments can remain unchanged
while Compose UI tests target the exposed test tags (`IdentifierTypeIcon`, `IdentifierTypeName`, `LocationListItem`, and
`LocationFavorite`).

# Additional list rows

- Patient details rows now use Compose equivalents: `PatientPropertyRow` for patient properties/observations/conditions, `PatientDetailsHeaderRow` for section headers, `PatientUnsyncedCard` for the unsynced banner, and `EncounterListItemRow`/`VisitListItemRow` for encounters and visits.
- Selection and sync history screens now host `PatientSelectableRow`, `SelectPatientListItemRow`, `SyncSessionRow`, and the unsynced resource rows (`UnsyncedPatientRow`, `UnsyncedEncounterRow`, `UnsyncedObservationRow`) to mirror their original XML styling while remaining in RecyclerView-based flows.

## Sync status utilities

`SyncStatusLayout` mirrors `core/src/main/res/layout/sync_status_layout.xml` using Compose. It keeps the light-blue status row with grey icon/text, left-aligned percentage label, and in-card horizontal progress indicator using a `SyncStatusUiState` that distinguishes syncing, success, error, and idle states.

```kotlin
SyncStatusLayout(state = SyncStatusUiState.Syncing(completed = 5, total = 10))
```

Use `SyncInfoContent` to render the sync session list and delete/clear actions.
