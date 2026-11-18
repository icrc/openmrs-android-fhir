# Server compatibility

The app needs a few things from the server that standard OpenMRS does not give (patient lists that can be
written, two concepts for group sessions). **Settings > Advanced settings > Server compatibility**
(`ServerCompatibilityFragment`) checks them, and the app leaves out what the server cannot do, so a user is
never offered a step that would be refused later.

`<server-compatibility enabled="false" />` in `app_config.xml` (`configuration.md`) turns the whole feature off:
the page and the summary in Advanced settings are hidden, no check runs, a refusal is not learned from, and
`ServerCapabilities.current()` reports everything as supported.

## What is checked

| Check | Request | Found | What the app does without it |
|---|---|---|---|
| Connection and sign-in | `GET session` (REST) | 200 | Nothing else is checked, and what was found before stays |
| FHIR R4 | `GET metadata` | a CapabilityStatement of FHIR 4.x | Reported only |
| Patient lists can be read | `GET Group?_count=1` | 200 | Reported only |
| Patients can be added and removed | `POST Group/<unknown id>/$add-members` with one patient | 404 or 410: the operation exists and the list does not; 400, 405 or 501 saying the server does not know the operation: not supported | A patient's page, a list's page, the picker and a group session no longer change members: lists are read only for members, a session does not add its patients to the list |
| Patient lists can be created and changed | `PUT Group/<id>`, then `DELETE`, **only when the user taps Test writing** | 2xx; a 405, 501, or a 400 or 422 naming the Cohort Module or an unsupported feature: not supported | No **New patient list**, rename or delete; members cannot be changed either; what waits is not sent |
| Cohort Module | `GET cohortm/cohort?limit=1` (REST) | 200 or 404 | Information only: with the Cohort Module installed, standard FHIR refuses to write a Group, which is why the patient list module of the server is needed |
| The two group-session concepts | `GET concept/<id>` (REST) for each id of `<group-sessions>` | 200, or 404: missing | The observation is not recorded: a concept the server lacks would make it refuse the whole encounter |

Nothing is written by the checks that run on their own. The write check creates one temporary patient list, **reads it
back** (a server that says yes and keeps nothing is reported as not supporting it) and deletes it at once; it is the only way to know that the server accepts what the app sends, so the page asks
before running it. `ServerChecker` holds the logic and takes a `ServerProbe` (implemented by `ApiManager`), so
it can be tried with a fake one; `docs/user-guide/fake-backend/` has a `cohort-module`
scenario that behaves as a server with the Cohort Module and without the patient list module.

## When

* When the page opens, unless it was checked in the last 12 hours; **Check again** always checks.
* From what the server answers: a change to a patient list that it refuses (405, 501, or a 400 or 422 that
  names the Cohort Module or a missing feature) marks the feature unsupported at once
  (`ServerCapabilities.learnRefusal`), without waiting for the page. A refusal for another reason (a name
  taken, no permission) does not.
* Nothing waits for a check: until the server is found not to support something it is allowed, which is what
  an offline first app has to do. One report is kept, for the server in use (`ServerCapabilities`, preferences
  `server_capabilities`); a server that did not answer says nothing, and the earlier findings stay.

## Where it shows

`ServerFeatures` (`editLists`, `changeMembers`, `missingConcepts`) is read by `PatientListDetailViewModel`,
`SelectPatientListViewModel`, `PatientMembershipViewModel`, `GroupSessionPatientsViewModel`,
`AddPatientViewModel`, `GroupFormEntryViewModel` and `PatientListsRepository.send`. The page of a list says so
in a banner that opens the compatibility page.

## Advanced settings

The settings meant for administrators and support are in one section of the Settings page, closed until
opened and open while the search is looking through it (`AdvancedSettingsSection`): the server address and its
connection test, the token check delay, the periodic sync delay, and a summary of the server checks with the
button that opens this page.
