# Diagnosis picker

A form question that searches a long list of diagnoses and lets the user tick several. Each tick is
saved as one encounter diagnosis (`Condition`). No bundled form uses it yet.

## In a form

```json
{
  "linkId": "diagnoses",
  "type": "choice",
  "repeats": true,
  "text": "Diagnoses of this visit",
  "extension": [
    {
      "url": "http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl",
      "valueCodeableConcept": {
        "coding": [{"system": "http://fhir.openmrs.org/code-system/questionnaire-item-control",
                    "code": "diagnosis-picker"}]
      }
    },
    {"url": "http://fhir.openmrs.org/ext/diagnosis-catalog", "valueString": "diagnoses"}
  ]
}
```

There is no `answerOption`: the options come from the CSV named by `diagnosis-catalog` (`diagnoses`
when left out). The answers are `valueCoding`s (the concept uuid and the name), so the review page and
the check of required questions work as for any multiple-choice question.

### Primary and secondary

Add `http://fhir.openmrs.org/ext/diagnosis-picker-rank` (`@o/diagnosis-picker-rank` in the compact
format) with `valueCode` `primary` or `secondary`, and everything ticked in that question has that
rank. A form usually has a *primary* question (`repeats: false`) and a *secondary* one.

* A diagnosis ticked in both is saved once, as primary.
* A picker without the extension: the first diagnosis ticked is primary and the rest secondary (a hint
  says so). In a form that has a primary question, an unranked picker is secondary.
* Editing an encounter fills each question with the diagnoses of its rank. A diagnosis moved to the
  other question is replaced: the old `Condition` is dropped and one with the new rank is created.

## The list: a CSV in the build

The list is `assets/diagnoses/<id>.csv`. `core` has `diagnoses.csv`; a file of the same name in the
assets of `app` or `dev3-app` replaces it. There is no other format and no way to load a list at run
time.

```
uuid,name:en,name:fr,name:es,name:ru,name:ar,code
10AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,Acute bronchitis,Bronchite aiguë,bronquitis aguda,,,J20.9
```

| Column | |
|---|---|
| `uuid` | The concept's uuid on the target server: all the server needs |
| `name:<language>` | What the user reads, one column per language. A cell left empty falls back to the English name (`name:en`, else the first language of the file). Every language is searched |
| `code` | An ICD-10 code, searched and shown next to the name. May be empty |

Other columns are ignored. A concept with several codes has one row per code (same uuid); the rows
become one diagnosis. The translations of the bundled file are machine translations that no clinician
has reviewed; a name can be corrected in the file with no code change.

**Search**: every word must be in a code or a name. Ranked: the exact code, codes that start with the
text, names that start with it, words of the name, the rest. 100 results are shown, with the number of
matches. A blank search shows what is ticked and the recent diagnoses.

**Load**: the file is large (about 45,000 rows), so the parsed list is cached in the app's private
cache (`DiagnosisCatalogCache`, tagged with the install/update time, so an update rebuilds it) and
`FhirApplication` releases the list from memory when the system asks. **Recent diagnoses**: the last 12
saved go to the top (`RecentDiagnoses`, one uuid per line in `filesDir`, removed with the data).

## What is saved

`GenericFormEntryViewModel` (new encounter, group sessions) and `EditEncounterViewModel` (edit) save
each tick as a `Condition`: category `encounter-diagnosis`, patient, encounter, `recordedDate`,
certainty *provisional*, the concept as a coding with no system and the uuid as code, and the rank in
the `diagnosis-rank` extension (`1` primary, `2` secondary).

On edit, a diagnosis taken out is dropped if it was never uploaded and deleted on the server if it was.
A diagnosis the list does not know, one written as free text and the other conditions of the encounter
are left alone. Deleting an unsent encounter from the Sync screen takes its conditions with it.

The app makes no call to the server for this, so **the CSV has to come from the dictionary of the
server the app talks to**: OpenMRS finds a coding with no system by its concept uuid, refuses a
`Condition` without the rank extension (422), and **stores a uuid it does not know without any concept
and answers 201**.

Not done: the tutorial has no step for the question, and the certainty is not asked.
