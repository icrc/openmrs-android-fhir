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

* `SplashActivity`, `LoginActivity` (basic and SSO on one page), and `MainActivity` now call `setContent {}` to host Compose UI.
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

## Patient list screen

* `PatientListFragment` now hosts `PatientListScreen` via a `ComposeView`, mirroring the previous
  XML layout while centralizing state in `PatientListViewModel`.
* The screen renders the search field, list container, empty state, loading overlays, and
  floating action button in Compose.

See [Patient list Compose layout](patient-list-compose.md) for details.

## What is still XML (and why)

After the clean-up of unused layouts and menus, seven layouts remain in `core/src/main/res/layout`:

| Layout | Why it is still XML |
|---|---|
| `activity_main.xml` | The `DrawerLayout` that hosts the navigation graph (`FragmentContainerView`) and the Compose app bar, bottom bar, overlay and drawer. Structural. |
| `add_patient_fragment.xml`, `generic_formentry_fragment.xml`, `group_formentry_fragment.xml` | Host the form library's own fragment (`QuestionnaireFragment`) in a `FragmentContainerView`. Moving them needs `AndroidFragment` from `androidx.fragment:fragment-compose` and a re-test of every form flow. |
| `patient_tab_item.xml`, `view_encounter_edit_action.xml` | Item views of the group-session tabs and of the read-only form viewer, inflated by code the form library calls. |
| `questionnaire_titleless_group.xml` | A view holder layout the form library inflates for groups without a title. |

Every screen the user opens outside a form is Compose. The next candidates are the three form hosts.
