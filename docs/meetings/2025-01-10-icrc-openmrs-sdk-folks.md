# Support used in the meeting:
- [Meeting Presentation](https://docs.google.com/presentation/d/1ZdXugdDnpGZSt1r-4fQZwLXax6Zq2HFJ0wLRsapuba8/edit?usp=sharing)
- [Canva Presentation of app](https://www.canva.com/design/DAGKaf4ihqg/2gaJ1x7vbZoaCPfCM4v7-g/edit?utm_content=DAGKaf4ihqg&utm_campaign=designshare&utm_medium=link2&utm_source=sharebutton)
- [OpenMRS FHIR Documentation on registering patients](https://openmrs.atlassian.net/wiki/spaces/docs/pages/228294725/Handling+Registration+Patient+Flow+in+OpenMRS+using+FHIR)

# Main topics:
- overview of the current status of ICRC Development
- ICRC objective is to create a white label application usable by all OpenMRS implementers. ICRC has a private repository to create

# ICRC project Timeline:
(To be added)..

# Topics Discussed in the meeting

## FHIR Info Gateway Usage

### Status
- Currently not in use.

### Concerns
- Avoiding the introduction of new components to simplify the system.
- However, the FHIR Info Gateway could enhance architecture flexibility by decoupling authentication from a single backend.

---

## Identifier Generation

### Current Approach
- Use **identifiers placeholder** on patient resource when created offline.

### More Info
- [Handling Registration Patient Flow in OpenMRS using FHIR](https://openmrs.atlassian.net/wiki/spaces/docs/pages/228294725/Handling+Registration+Patient+Flow+in+OpenMRS+using+FHIR)

---

## Edit Questionnaire Functionality

### Current Approach
- Using extracted resources from **questionnaireResponse** & utilizing **populate API** to edit the questionnaire.

### More Info
- Needs to be tested in cases when resources are created offline & also on the server, and how it behaves after synchronization.

---

## OpenMRS Locations & Security

- Patient identifiers linked to locations in ICRC’s OpenMRS implementation for **security purposes.**

### Common Setup Issue
- Linking identifiers to locations is extensively used by ICRC.

---

## FHIR Patient Representation in OpenMRS by Ian

> At least in our FHIR representation, we add the location in Patient.identifier field as an extension (at least for identifier types that are associated with a location). Maybe it's possible to create a "placeholder" identifier with an extension identifying the location.
 
> Alternatively, FHIR patients can be associated with a "Managing Organization", which isn't technically a location, but it's one of the concepts OpenMRS uses locations to express.

---

## Broader Goals
- Create an easy-to-integrate solution for broader community adoption.
- Foster feedback and maintain openness in the system design for better usability across different organizations.

---

## Next Steps
- technical meeting on "Patient identifier and location in OpenMRS"

