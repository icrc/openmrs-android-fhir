# Forms

## Keeping a form fast

A long form is redrawn and re-evaluated at every answer, so how it is written matters:

* Do not use `%resource.repeat(item)` in an expression that runs often (`variable`,
  `calculatedExpression`, `enableWhenExpression`): each run walks the whole response. Read the answer
  once instead. `tools/optimize_questionnaire_expressions.py` rewrites the scoring expressions of the
  bundled forms that way.
* Prefer `enableWhen` to `enableWhenExpression`.
* A group with `text` or `prefix` is drawn as a header. A group that only holds questions should have
  neither: the app draws a group with no title with a cheap view (`CompactGroupViewHolders`).
* Put a long single-choice list in a drop-down.

The app side of this: the lists that only need the name and encounter type of a form read a small
`FormSummary` (`forms/FormSummaries.kt`) instead of parsing every bundled form, and a bundled form is
read with the translations of the device language only (`expandCompactQuestionnaire(json, locale)`).

## The form in the saved state

Android saves the state of the screen when the app goes to the background and refuses a state over
about 500 KB (`TransactionTooLargeException`, the app crashes when it is left). The form library keeps
the questionnaire it is given as text twice in that state, so a large form (the assessment form is
330 KB) was enough. `newQuestionnaireFragment` therefore gives the library the address of a file
(`forms/QuestionnaireFiles`, `files/questionnaires`, named after the content, unused files removed after
two weeks) and not the text; the page breadcrumbs read the questionnaire from the same file. Patient
answers (`responseJson`) are never written to a file: they stay in the state, which is small. Any new
screen that shows a form must go through `newQuestionnaireFragment`.

## The bundled forms

`core/src/main/assets/questionnaires` holds the forms in a compact format
(`tools/compact_questionnaires.py`; the README lists the shortcuts). A synced form of the same id wins.

## Form sections

The Create Encounter screen is configured in `<form-sections>` of `app_config.xml`: see the README.
Section and form names are translated by the questionnaire's own translations, a section name by a
`<name lang="xx">` child.
