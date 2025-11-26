# Encounter form section configuration and translations

The app reads encounter form groups from the `questionnaires` JSON string inside `core/src/main/res/values/server.xml`. This configuration is parsed into `FormData`, which currently supports two top-level keys:

- `formSections`: Ordered list of form groupings shown in the **Create Encounter** screen. Each section contains a `name` and the encounter type `forms` (codes) to display.
- `Translation overrides`: Optional map of locale overrides for section titles and questionnaire titles. Keys are [IETF language tags](https://developer.android.com/reference/java/util/Locale#getLanguage()) (for example `"fr"` or `"es"`), and each value is a map from the original label (`formSections.name` or `Questionnaire.title`) to its translated value.

At runtime `CreateEncounterViewModel` loads this JSON, looks up the device language (for example `Locale.getDefault().language`), and replaces each section name or questionnaire title with the translated value when an override exists. When no override is provided the original label is shown. The same translation data is also reused in **Patient Details** to localize visit and encounter names in the lists.

For questionnaire names the view model first checks if the Questionnaire resource includes `_title` translation extensions with the current device language (FHIR `http://hl7.org/fhir/StructureDefinition/translation`). If a match is found that localized value is shown; otherwise the translation overrides map is used, falling back to the raw `title`.

## Example configuration

Below is a minimal example of the `questionnaires` string value inside `server.xml` using multiple sections and two locale overrides:

```json
{
  "formSections": [
    {
      "name": "MHPSS Forms",
      "forms": [
        "encounter.mhpss.assessment",
        "encounter.mhpss.followup"
      ]
    },
    {
      "name": "Physiotherapy Forms",
      "forms": [
        "encounter.pt.intake",
        "encounter.pt.progress"
      ]
    }
  ],
  "Translation overrides": {
    "fr": {
      "MHPSS Forms": "MHPSS Formulaires",
      "Physiotherapy Forms": "Formulaires de physiothérapie",
      "Psychosocial Assessment": "Évaluation psychosociale",
      "Physiotherapy Intake": "Évaluation initiale de physiothérapie"
    },
    "es": {
      "MHPSS Forms": "Formularios de MHPSS",
      "Physiotherapy Forms": "Formularios de fisioterapia",
      "Psychosocial Assessment": "Evaluación psicosocial",
      "Physiotherapy Intake": "Ingreso de fisioterapia"
    }
  }
}
```

Paste the JSON above (with escaped quotes) into the `questionnaires` string resource to ship default sections and translations, or supply your own structure through a custom `server.xml` at build time.
