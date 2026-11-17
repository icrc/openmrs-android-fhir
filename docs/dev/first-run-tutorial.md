# Tutorial (spotlight tour)

An in-app tour of the screens (`ui/tutorial`). It is entirely local: no server, no account, no
analytics. There are three kinds of tour: the first run, the login page, and one per screen.

## Pieces

| File | Role |
|---|---|
| `TutorialStep` / `TutorialAction` | One step: the key of the element to highlight, a title and a body, and what the user does: `Tap` the element or `Acknowledge` the card |
| `TutorialController` | The state of the tour (one at a time) and where each element is, in window coordinates |
| `Modifier.tutorialTarget(key)` | Anchors a step to a composable: reports its bounds and watches taps |
| `TutorialOverlay` | Draws the tour above the whole window and enforces the flow. `MainActivity` hosts it in a `ComposeView` added with `addContentView`, to cover the app bar and the bottom bar |
| `FirstRunTutorial` | The first-run tour and its once-only flag |
| `ScreenTours` | The tour of each screen |

## Targeting

```kotlin
Card(
  modifier = Modifier
    .tutorialTarget(TutorialTargets.HOME_PATIENTS) // before the clickable
    .clickable(onClick = onOpenPatients),
)
```

The element works as before: the target only observes taps and consumes nothing. A step may point at
an element of a screen that is not open yet (the tap of the previous step opens it): until it appears
the overlay blocks the screen, and after 4 seconds the tour ends. A target below the fold is scrolled
into view.

## How a step is enforced

* `Tap`: the element is left uncovered and panels block everything else, so the tap both advances the
  tour and does its normal job.
* `Acknowledge`: the whole window is blocked and the card's button moves on.
* The card always has **Skip tour** and a close button, and Back ends the tour: there is no way to be
  stuck. The card is a live region, so TalkBack reads each step.

## The tours

**First run.** `FirstRunTutorial` keeps a flag in its own preferences file. `FhirApplication.onCreate`
sets it once, to `firstInstallTime == lastUpdateTime`: a fresh installation gets the tour and an update
of an app already in use does not. `HomeFragment` starts the tour once Home shows its data (the counts
are in) and nothing is syncing or loading (`TutorialGate`; the first sync covers the app and the
numbers change while it runs), and the flag is cleared as the tour starts, so it never repeats and
stays pending until then. The manifests set `allowBackup="false"`, so
reinstalling runs it again. To see it again on a device, write `<boolean name="pending" value="true" />`
in `shared_prefs/first_run_tutorial.xml`.

**Login page.** The "?" in the footer starts it, as often as wanted. `LoginScreen` builds the steps
from what the page offers (basic, SSO, guest) and hosts its own `TutorialOverlay`, being a separate
activity. All its steps are `Acknowledge`, so the page cannot be left during the tour.

**Screens.** `ScreenTours` maps each navigation destination to a `Tour`. `MainActivity` shows the "?"
action (`menu/help_menu.xml`) only when the screen on show has a tour, and `startScreenTour()` starts
it, unless a sync or a load is running (`TutorialGate`): it then says so and does not start. Its steps are all `Acknowledge`. A step is `optional` when its element may be absent (an empty
list, an action only some users have): it is skipped instead of ending the tour. Views that are not
composables are listed in `Tour.viewTargets` (key to view id). A screen without a tour (identifier
choice, unsynced resources) has no "?".

## The card

"Step n of m" does not count an optional step whose element is not on the screen. **Back** returns
to the closest earlier step that is a card and whose element is there; it is not offered to a step
that follows a `Tap` step, since the screen that step was on may have been left (`TutorialController`).
The steps of a tour follow the order of the elements on the screen, top to bottom: check that when a
step is added.

## When a feature changes

A change that adds, moves, renames or removes a feature, screen, setting or control updates the
tutorial in the same change:

1. Tag the element with `Modifier.tutorialTarget(TutorialTargets.X)` (add the key to
   `TutorialTargets`; one element per key, so tag the first item of a list). An XML screen lists the
   view id in the tour's `viewTargets`.
2. Add the step to the screen's tour in `ScreenTours.kt`. A new screen needs a new `Tour` and a
   `forDestination` entry. Use `optional = true` for an element that is not always there.
3. Add `help_<screen>_<element>_title` and `_body` in `values`, `values-fr`, `values-es`, `values-ru`
   and `values-ar` (`StringResourcesTest` fails if one is missing).
4. Mention the step in the CHANGELOG entry.
5. A removed or renamed feature: update or delete its step and strings.
6. Run the tour on a device: every step, and a missing `optional` step must not stop it.
