# Compose selection screens

The location, identifier, and patient list selection flows now render with Compose screens that mirror the legacy XML layouts. Each screen is hosted from its existing fragment via `ComposeView` so navigation and action bar logic remain in the fragment while the UI is rendered declaratively.

## Selection screen pattern

Each selection fragment now follows the same pattern:

1. **State holder in ViewModel**
   - Each selection ViewModel exposes a `UiState` `StateFlow` (query text, selection sets, loading flags, and content visibility).
   - The fragment still manages navigation events, dialog prompts, and toast messages.

2. **Compose screen**
   - The Compose screen is stateless: it receives the current lists, selection sets, and query text, and emits callbacks for selection changes.
   - The existing row composables (`LocationListItemRow`, `IdentifierTypeListItemRow`, and `SelectPatientListItemRow`) are reused to match the XML layout visuals.

3. **Filtering**
   - Search filtering is performed in Compose by recomputing the list from the latest items and the ViewModel-managed query text.

## ViewModel contracts

The Compose selection screens depend on the existing ViewModel contracts:

- **Location selection** uses `LocationViewModel.locations` for the full list and `LocationViewModel.pollState` for sync progress. Selection state lives in `LocationViewModel.LocationSelectionUiState`, and the ViewModel owns the preference updates for favorites and selected location IDs.
- **Patient list selection** uses `SelectPatientListViewModel.selectPatientListItems` and `SelectPatientListViewModel.pollState`. The ViewModel manages `SelectPatientListUiState` and writes selection changes to `PreferenceKeys.SELECTED_PATIENT_LISTS`.
- **Identifier selection** loads identifier types via `IdentifierSelectionViewModel` (backed by `AppDatabase` and `IdentifierTypeManager.fetchIdentifiers()`), while selection updates are stored in `PreferenceKeys.SELECTED_IDENTIFIER_TYPES`.

## UI event entry points

Each ViewModel exposes intent-like functions that update the `UiState` and persist the underlying preference state:

- `onQueryChanged(...)`
- `onLoadingChanged(...)`
- `onFavoriteToggle(...)` / `onPatientListToggle(...)` / `onIdentifierToggle(...)`
- `onShowContentChanged(...)` (location sync overlay)

This keeps view models focused on data retrieval and sync flows while the fragment and Compose screen manage the UI state and navigation transitions.
