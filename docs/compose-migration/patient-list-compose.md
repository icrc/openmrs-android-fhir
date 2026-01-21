# Patient list Compose layout

The patient list screen now renders fully in Jetpack Compose. `PatientListFragment` hosts a
single `ComposeView` and delegates all UI rendering to `PatientListScreen`, keeping the fragment
focused on navigation and lifecycle wiring while Compose owns the list, search bar, and floating
action button. The XML file `fragment_patient_list.xml` has been reduced to a `ComposeView` host
only.

## Architecture overview

* **Single UI state source**: `PatientListViewModel` owns a `PatientListUiState` flow with the
  current search query, patient list, and loading/refreshing flags. The fragment collects this
  state and passes it to Compose.
* **Search and filtering**: the search field updates the query in the ViewModel. The ViewModel
  performs the search and updates the same `PatientListUiState`, so the query and results stay in
  sync across configuration changes.
* **Loading and refresh overlays**: the list uses the shared `PatientListContainerScreen` and
  `SwipeRefreshListContainer` so loading and pull-to-refresh indicators remain consistent with
  other Compose list screens.
* **Actions**: the floating action button callback is wired back to the fragment to navigate to
  add-patient flows. Patient row taps still navigate to patient details.

## Tests

Compose UI tests are in `core/src/androidTest/java/org/openmrs/android/fhir/ui/patient/PatientListScreenTest.kt`
and cover:

* Search field updates.
* Pull-to-refresh callback.
* Empty state rendering.
* Floating action button interaction.
