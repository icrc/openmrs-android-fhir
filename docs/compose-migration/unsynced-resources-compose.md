﻿# Unsynced resources Compose layout

The Unsynced Resources screen now renders fully in Compose and replaces the legacy
`fragment_unsynced_resources.xml`. The `UnsyncedResourcesFragment` hosts a `ComposeView` and
passes `UnsyncedResourcesViewModel` state and callbacks into the `UnsyncedResourcesScreen`
composable.

## UI structure

`UnsyncedResourcesScreen` is responsible for:

* Rendering a `LazyColumn` of `UnsyncedResource` entries with the existing row composables
  (`UnsyncedPatientRow`, `UnsyncedEncounterRow`, `UnsyncedObservationRow`).
* Displaying an empty state with an icon and message when the list is empty.
* Showing a loading indicator overlay when `isLoading` is true.
* Showing confirmation dialogs for per-item delete and delete-all actions.

Action callbacks (`onDeleteResource`, `onDeleteAll`, `onDownloadResource`, `onDownloadAll`) stay in
the Fragment layer so side effects like toasts and file exports remain outside the composable.

## Tests

* Compose UI tests live in `core/src/androidTest/java/org/openmrs/android/fhir/ui/` and validate the
  empty state, list rendering, and loading indicator using the `UnsyncedResourcesTestTags`.
* Fragment tests launch `UnsyncedResourcesFragment` inside a test activity to confirm the
  Compose surface renders.
* ViewModel tests verify expansion behavior and resource ID collection logic in
  `UnsyncedResourcesViewModelTest`.
