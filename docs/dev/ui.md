# UI structure

The UI is Jetpack Compose. Only the forms are not (see below).

## Activities and navigation

* `SplashActivity`, `LoginActivity` (password and single sign-on on one page, `AuthScreens.kt`) and
  `MainActivity` each set their content with Compose (`setContent {}`) and move between each other with
  intents.
* `MainActivity` is a `DrawerLayout` (`activity_main.xml`): a `FragmentContainerView` with the
  navigation graph (`reference_nav_graph`) and Compose containers for the top bar, bottom bar and
  overlay (`MainActivityScaffold`, `HeaderComponents.kt`, `MainBottomBar.kt`). A destination is a
  fragment.

## A screen

Each destination fragment hosts one composable screen (`composeView {}` in `ComposeExt.kt`, which sets
the theme and ties the composition to the view's lifecycle):

* the ViewModel holds a `UiState` flow and exposes intent-like functions (`onQueryChanged`, ...);
* the screen is stateless: state in, callbacks out;
* the fragment keeps what is not drawing: navigation, dialogs, messages, file and intent calls.

Screens are in `ui/screens` and `ui/patient`, shared pieces in `ui/components` (banner and drawer
header, list containers with pull to refresh, row composables, sync status, dialogs).

A screen drawn under the app bar takes its height from `rememberAppBarHeight()`
(`rememberActionBarContentPadding`); an XML view uses `keepBelowAppBar` (`InsetsExt.kt`).

## Forms

The form library draws a form in its own `QuestionnaireFragment`, which `AddPatientFragment`,
`EditPatientFragment`, `GenericFormEntryFragment` and `GroupFormEntryFragment` host in a
`FragmentContainerView` (`add_patient_fragment.xml`, `generic_formentry_fragment.xml`,
`group_formentry_fragment.xml`). The other layouts left are views the form library or a custom
question inflates (`questionnaire_titleless_group.xml`, `questionnaire_diagnosis_picker.xml`) and two
small ones of the group session and the read-only form (`patient_tab_item.xml`,
`view_encounter_edit_action.xml`).

## Tests

Compose tests are in `core/src/androidTest/.../ui` (`components`, `entrypoints`, `patient`). A screen
exposes its tags in an `object ...TestTags`. ViewModels have unit tests in `core/src/test`.

A new control or screen also needs its tutorial step: see `first-run-tutorial.md`.
