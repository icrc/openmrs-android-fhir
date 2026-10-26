# Form performance

What made the data capture slow, what was changed, and how to keep it fast. Measured on the
emulator with the ART sampling profiler (`am profile start --sampling 1000 --streaming`), on the
largest bundled form (Assessment, 311 items), warm process, with the profiler's overhead.

| What | Before | After |
|---|---|---|
| Opening a form: main thread busy | 2124 ms | about 1310 ms |
| Opening a form: loading it (worker) | 391 ms | about 205 ms |
| One tap on an answer of a scale form: FHIRPath | 511 ms for 3 taps (2 variables per total) | 59 ms |
| A form with 30 score variables, one evaluation of all of them | 17.7 times slower | (reference) |
| Opening the patient list / patient details / form list: reading every bundled form | 1.3 s of CPU each time | about 1 ms (read once per run) |

## The causes and the changes

1. **Score variables** (`variable` extensions of the scale forms). Each was a chain of five tests
   that each ran `%resource.repeat(item)`, which collects every item of the response and removes
   duplicates by comparing them one by one. The form library re-evaluates every variable a total
   depends on at each answer, on the main thread. They now read the answer once, straight through
   its place in the form (`%resource.item.item.item.item.item.where(linkId='q1')...`).
   `tools/optimize_questionnaire_expressions.py` does the rewrite (run it again after adding a
   scale form written the old way). Same result for every answer: checked on 15,120 random
   evaluations with the FHIRPath engine, and the 26 bundled questionnaires differ in nothing but
   these 252 expressions.
2. **Groups without a title.** Forms following the OpenMRS conventions wrap each question in two
   groups that only carry the `definition` used for extraction. The form library drew a header
   view for each (`GroupHeaderView`, the most expensive view of a form). `CompactGroupViewHolders`
   gives such groups a view that draws nothing. The screens look the same and what is extracted is
   untouched.
3. **Translations of languages nobody sees.** The form library parses and renders everything it is
   given. The bundled files are now read with the translations of the device language only
   (`expandCompactQuestionnaire(json, locale)`), instead of expanding all of them, parsing them and
   pruning afterwards. The result is identical to pruning (checked on all forms and 7 locales).
4. **Reading every form to read a name.** The lists (patients, patient details, create encounter,
   favorites) parsed all bundled questionnaires only to get their names and encounter types. They
   use `FormSummary` (`forms/FormSummaries.kt`) instead: a streaming read of five fields,
   done once per run. Editing an encounter reads the one form for its type instead of all of them.

## Keeping a form fast

- Do not use `%resource.repeat(item)` in an expression that runs often (a `variable`, a
  `calculatedExpression`, an `enableWhenExpression`): go through the items by their level.
- Prefer `enableWhen` to `enableWhenExpression` when a plain comparison is enough.
- A group with a `text` or a `prefix` is drawn as a header; one without is free.
- Long single-choice lists belong in a drop-down (the guideline says so from about ten options).
