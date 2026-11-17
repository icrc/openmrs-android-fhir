# Tests

| Where | What | Run |
|---|---|---|
| `core/src/test/java/<package of the class under test>` | JVM unit tests: rules, mappings, parsing, retries and ordering; view models and repositories against fakes (`patientlists/PatientListTestSupport.kt`); every bundled questionnaire (`forms/BundledQuestionnairesTest`); the string resources (`resources/StringResourcesTest`) | `./gradlew :core:testDebugUnitTest` (`--tests "<class>"` for one) |
| `core/src/androidTest/java/.../ui` | Compose tests of one composable on a device or emulator, in the package of the composable: `components`, `screens`, `patient`, `entrypoints` | `./gradlew :core:connectedDebugAndroidTest` |

CI runs the unit tests (`./gradlew test`); the Compose tests need a device and are run by hand.

## What gets a test

* Behaviour that breaks without anyone noticing: a rule, a mapping between two formats, an order, a retry, a default,
  what is kept and what is dropped. The name says it as a sentence (`aRefusedCreateKeepsItsReasonAndIsNotSentAgain`).
* A fix for a bug that a test would have caught goes in the class of the thing that was wrong.
* One class per class or feature. Before adding a file, look for the class that already covers the area; a case that
  another test already makes is not repeated.

## What does not

* A constant or a list of values, a call that only hands over to another one (a mock that is asked "was it called"),
  a composable that shows the text it was given, a form's content (the questions of one questionnaire), and a screen
  that is only a layout.
* Forms are not unit tested; the helpers and view models around them are.
