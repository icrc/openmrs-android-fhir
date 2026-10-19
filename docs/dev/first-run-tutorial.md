# First-run tutorial (spotlight tour)

A reusable onboarding tour (`org.openmrs.android.fhir.ui.tutorial`) and the tour that runs once
after a fresh installation. Everything is local: no server, no account, no analytics.

## Pieces

| File | Role |
|---|---|
| `TutorialStep` / `TutorialAction` | One step: the key of the element to highlight, a title and a body, and what the user must do: `Tap` the element, or `Acknowledge` the card |
| `TutorialController` | The state of the tour (a Compose `mutableStateOf`, one tour at a time, held by the process) and the registry of where each element is, in window coordinates |
| `Modifier.tutorialTarget(key)` | Anchors a step to any composable: reports its bounds and watches taps |
| `TutorialOverlay` | Draws the tour above the whole window and enforces the flow. Hosted once in `MainActivity` as a `ComposeView` added with `addContentView`, so it covers the app bar and bottom bar, which are not in the screens' compositions |
| `FirstRunTutorial` | The tour of the app and the once-only flag |

## Targeting

```kotlin
Card(
  modifier = Modifier
    .tutorialTarget(TutorialTargets.HOME_PATIENTS) // before the clickable
    .clickable(onClick = onOpenPatients),
)
```

The element keeps working exactly as before. `tutorialTarget` registers `boundsInWindow()` on every
layout and removes it when the element leaves the composition. The pointer observer runs in the
`Initial` pass and consumes nothing: it only tells the controller that the element was tapped (down
and up), and the controller advances only when the current step is a `Tap` on that key.

A step can point at an element of a screen that is not open yet (step 2 is in the Patients
screen, which the tap of step 1 opens): while the element is not there the overlay blocks the
screen, and if it never appears within 4 seconds the tour ends, so it cannot trap the user.

## Enforcing the flow

* `Tap` step: the hole is left empty and four panels (above, below, left, right of it) swallow every
  pointer event. The element underneath receives the touch natively, which is why a Tap both
  advances the tour and does its normal job (opens the screen, the form...). Nothing else on the
  window can be pressed, scrolled or swiped, the app bar and the bottom bar included.
* `Acknowledge` step: one panel blocks the whole window, the hole included; the button of the card
  moves on.
* The card is always reachable, and has **Skip tour** (a text button), a close button (an icon with
  its content description) and, in an Acknowledge step, the "Got it" button. **Back** ends the tour
  too (`BackHandler`). Skip, close and Back all end the whole tour; there is no way to be stuck.
* Accessibility: the card is a live region with a pane title, so TalkBack announces each step; every
  control has a text or a content description. The panels block touches, not TalkBack's own
  navigation of the screens below (Android does not allow hiding another view from the overlay): the
  card is read first.

## Drawing

A `Canvas` fills the window: `clipPath(hole, ClipOp.Difference)` paints the dim layer around a
rounded hole (no offscreen layer or blend mode), then a soft glow and an outline, pulsing with an
`infiniteTransition`, are drawn around the hole. The card goes below the element when it is in the
top half of the window, above it otherwise.

## First run, locally

`FirstRunTutorial` keeps one boolean, `pending`, in its own `SharedPreferences` file
(`shared_prefs/first_run_tutorial.xml`), in the app's private storage. The manifests set
`allowBackup="false"`, so nothing is restored from a backup: **uninstalling and installing again
starts from scratch and the tour runs again**, an update keeps the flag.

1. `FirstRunTutorial.init` (from `FhirApplication.onCreate`) decides once, the first time it runs:
   `pending = (firstInstallTime == lastUpdateTime)`: a fresh installation gets the tour, an update of
   an app that was already in use (installed before the tour existed) does not.
2. `HomeFragment` calls `FirstRunTutorial.startIfPending` once its screen is laid out (the screens
   before it, login, location and patient lists, are not part of the tour).
3. The flag is cleared **as the tour starts**, so it never comes back whether the user finishes it,
   skips it or closes it (or the app is killed in the middle).

To try it on a device that already has the app, write
`<boolean name="pending" value="true" />` in that file (debug builds: `run-as`) and start the app.

## The tour

1. Home: tap **Patients** (Tap).
2. Patients screen: the search field (Acknowledge).
3. Patients screen: tap **Register patient** (Tap), which opens the form and ends the tour.

## Using it elsewhere

```kotlin
// 1. anchor the elements
Button(modifier = Modifier.tutorialTarget("settings.sync")) { ... }

// 2. describe the steps
val steps = listOf(
  TutorialStep("settings.sync", R.string.t_title, R.string.t_body, TutorialAction.Tap),
)

// 3. start it (anywhere, once the screen is laid out)
TutorialController.start(steps)
```

`TutorialOverlay` is already hosted by `MainActivity`; a screen outside it (another activity) adds
its own `ComposeView { OpenMrsTheme { TutorialOverlay() } }` over its content.
