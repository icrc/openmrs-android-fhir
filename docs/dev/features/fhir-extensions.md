# FHIR extensions

The extensions of `http://fhir.openmrs.org/ext/` belong to the OpenMRS FHIR module. Those of
`http://openmrs.org/android-fhir/` and `https://openmrs.org/ext/` are the app's own.

| Extension | On | Used for |
|---|---|---|
| `.../ext/patient/identifier#location` | Patient identifier | Location that produced the identifier |
| `.../ext/person-attribute` (with `-type` and `-value` inside) | Patient | A person attribute built from a questionnaire answer |
| `.../ext/group/location` | Group | Scopes a patient list to a location (filtered on the device) |
| `.../ext/observation-child` | Questionnaire item | The parent coding of an observation group: `observations.md` |
| `.../ext/diagnosis-catalog`, `.../ext/diagnosis-picker-rank` | Questionnaire item | Diagnosis picker: `diagnosis-picker.md` |
| `.../R4/StructureDefinition/diagnosis-rank` | Condition | Primary (1) or secondary (2) diagnosis |
| `https://openmrs.org/ext/show-screener` | Questionnaire item | The item is shown in the screener add-on |
| `http://openmrs.org/android-fhir/StructureDefinition/original-provider` | Encounter participant | Provider of an imported encounter, kept until upload: `import-export.md` |
| `http://openmrs.org/android-fhir/StructureDefinition/export-user`, `export-provider`, `export-location` | Bundle meta | Who exported a file: `import-export.md` |
