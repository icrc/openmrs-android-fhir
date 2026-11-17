# Group sessions

A group session is held with the patients of **one patient list**. The flow differs from the
standard one in what comes first: the list, then the form, then who takes part.

1. **The list** (`GroupSessionListFragment`): the home card and the Group tab open it; the page of a
   list has **Start a group session**, which skips it (shown only for a downloaded list). It shows
   the lists of the chosen location (`atLocation`) that are **downloaded** (ticked on the Patient
   lists screen: the session is held with patients of the device) and asks the server once for
   new ones. One tap chooses the list.
2. **The form** (`CreateEncountersFragment`, argument `patient_list_id`): as for any encounter form,
   with the unfinished sessions to resume. A resume goes straight to the form (the draft knows its
   patients and its list: `GroupSessionDraft.patientListId`).
3. **Who takes part** (`GroupSessionPatientsFragment`, `GroupSessionPatientsViewModel`): the
   patients of the list, ticked; the user unticks the absent ones. A member the device does not
   have is greyed. The screen does not change the download. **Add patients** opens the list picker
   in session mode (`PatientListPickerFragment`, argument `session`): the patients of the device
   and, in live mode with a connection (`LiveSync.isAvailable`), those of the server found with 3
   letters. None is added to the list there; the chosen ones from the server are downloaded first
   (`LivePatients.download`, kept: the forms are filled in on this device; one that cannot be
   downloaded is left out and said so), and the ids come back through the saved state of the
   screen. In the picker, **Not in the list? Create a new patient** opens the registration
   (`AddPatientFragment`, argument `for_session`); once saved the patient comes back to the session
   (`popBackStack` to the session screen, the id in its saved state), ticked. **Identify later**
   records a placeholder patient (`UnidentifiedPatients.create`) and adds it to the session.
4. **Start** (`GroupSessionPatientsViewModel.begin`) adds the patients added to the session to the
   list (`patientsToJoinList`: those still ticked and not in the list already; one registered on
   the device and not uploaded is added **held for the upload**), then opens the form (`GroupFormEntryFragment`) with the ticked
   patients. Cancelling before Start leaves the list untouched.

The session details (the form's first step) cannot be skipped: with a required answer missing the
review offers "Fix questions" only (`allowSubmitAnyway = false` for the screener of
`GroupFormEntryFragment`; the patients' forms keep the library's default).

What each patient was given is read from their form when **Submit** is pressed (and when the user moves to
another patient), kept as JSON (`GroupFormEntryViewModel.patientResponses`) and saved from there. The form is
not read again after Submit (`submitAllEncounters`): a form that was just submitted answers with only its first
questions, and reading it again replaced everything the last patient had been given.

The record of the session names its list: each encounter carries the list's id and name as the two
internal observations (`GroupFormEntryViewModel.createInternalObservations`), read from the
patient lists of the device, so a list created on the device and not sent yet works too, also by a guest
(`GroupSessionListViewModel` offers the downloaded lists and the new ones).

## The cohort observations

Every encounter of a session records the id and the name of the list as two observations the user never sees
(`GroupFormEntryViewModel.createInternalObservations`). Their concepts are in the app configuration
(`<group-sessions cohort-identifier-concept=... cohort-name-concept=...>`, `configuration.md`): a blank or absent
one is not recorded, and one the server was found not to have (`server-compatibility.md`) is skipped, since a
concept the server lacks makes it refuse the whole encounter. With a server that does not let patients be added
to a list, the patients added to a session take part in it only.

## Limits

* A patient registered on the device and not uploaded takes part at once and joins the list after
  the upload (`patient-list-management.md`); the screen says so.
* An added patient to identify joins the list for good only when identified
  (`patient-list-management.md`).
* Without live mode (or without a connection) the picker of a session offers the patients of the
  device only: the forms are filled in on this device.
