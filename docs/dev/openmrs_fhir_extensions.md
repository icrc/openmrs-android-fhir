# OpenMRS FHIR Extensions

| Extension URL | Description |
|--------------|-------------|
| `http://fhir.openmrs.org/ext/patient/identifier#location` | Attached to generated patient identifiers so each ID carries the location reference that produced it. |
| `http://fhir.openmrs.org/ext/person-attribute` | Used when converting questionnaire responses into person attribute extensions on Patient resources. |
| `http://fhir.openmrs.org/ext/person-attribute-type` | Nested under person-attribute extension to define the type of attribute. |
| `http://fhir.openmrs.org/ext/person-attribute-value` | Nested under person-attribute extension to store the value of the attribute. |
| `http://fhir.openmrs.org/ext/group/location` | Stored on Group resources to scope patient lists by location, enabling client-side filtering by site. |
| `http://fhir.openmrs.org/ext/observation-child` | Marks questionnaire items whose responses should be grouped under a parent Observation. |
| `https://openmrs.org/ext/show-screener` | Flags encounter questionnaire items that should appear in the screener add-on. |
