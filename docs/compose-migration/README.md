# Compose migration: entry points and header/navigation UI

The main entry-point activities now render their UI with Jetpack Compose while preserving the multi-activity architecture and intent-based routing. XML fragments (for example `fragment_home`, `fragment_location`, and related navigation destinations) are still hosted from Compose via a `FragmentContainerView`. 

## Network status banner

* Composable: `NetworkStatusBanner(text: String)` in `core/src/main/java/org/openmrs/android/fhir/ui/components/HeaderComponents.kt`.
* Hosted from `MainActivityScaffold` in Compose (`core/src/main/java/org/openmrs/android/fhir/ui/screens/MainActivityScreen.kt`).
* Text updates are driven by `MainActivity.observeNetworkConnection`, which writes into a Compose `mutableState` for recomposition.

## Drawer header

* Composable: `DrawerHeader(label: String, lastSyncValue: String)` in `core/src/main/java/org/openmrs/android/fhir/ui/components/HeaderComponents.kt`.
* Rendered inside the Compose drawer in `MainActivityScaffold`.
* The last sync text uses Compose state so updates from `MainActivityViewModel.lastSyncTimestampLiveData` automatically recompose the header.

## Activity-level routing

* `SplashActivity`, `LoginActivity`, `BasicLoginActivity`, and `MainActivity` now call `setContent {}` to host Compose UI.
* Each activity owns a small Compose `NavHost` for its route (`splash`, `login`, `basic_login`, `main`) and preserves intent-based transitions between activities.
* Remaining XML fragments stay in the `reference_nav_graph` navigation graph and are hosted from Compose.

## Settings screen

* `SettingsFragment` now hosts a Compose-based `SettingsScreen` and no longer relies on `settings_page.xml`.
* Location, identifier, and patient list selection now use Compose screens hosted from their fragments. See `selection-screens.md` for the UI/state pattern and ViewModel contracts.
* `SettingsViewModel` holds the UI state, emits UI events (save/discard/sync status), and delegates persistence to a `SettingsRepository` backed by `DemoDataStore`.
* The initial sync action is routed through `InitialSyncRunner` so the ViewModel stays testable and follows dependency inversion.

## Testing

* `core/src/androidTest/java/org/openmrs/android/fhir/ui/NetworkBannerAndDrawerHeaderTest.kt` covers the banner and drawer header state updates.
* `core/src/androidTest/java/org/openmrs/android/fhir/ui/entrypoints` contains Compose UI/instrumentation tests for splash timing, login flows, and the main navigation container.

## Unsynced resources screen

* `UnsyncedResourcesFragment` now hosts Compose via `UnsyncedResourcesScreen`, which replaces
  `fragment_unsynced_resources.xml`.
* The screen renders existing unsynced rows, empty state, and confirmation dialogs in Compose while
  keeping side effects inside the Fragment.
* Compose and fragment tests live in `core/src/androidTest/java/org/openmrs/android/fhir/ui/`.

See [Unsynced resources Compose layout](unsynced-resources-compose.md) for details.
