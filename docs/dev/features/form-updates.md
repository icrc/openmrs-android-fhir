# Forms on the server

The app uses the questionnaires that ship with it (see Bundled questionnaires in `../configuration.md`) and does
not download any by itself: the setting **Use embedded forms only** is on by default. **Settings > Advanced settings >
Forms on the server > Check for new and updated forms** opens a page (`FormUpdatesFragment`) that compares the forms of
the device with what the server publishes and lets the user take the ones they choose.

## What it says

`FormUpdater` asks the server (`ApiManager.fetchQuestionnaires`: `GET Questionnaire`, read only) **only when the
user presses Check the server**, and gives each form one state (`FormState`, `FormVersions.status`):

| State | Means | Action offered |
|---|---|---|
| Up to date | the form in use says what the server's copy says | none |
| Update available | the server's copy is different from the one in use | **Update** |
| New on the server | the form is not on the device | **Add** |
| Not on the server | the device has it, the server does not publish it | none |
| The server's copy cannot be used | the copy is written with the app's own short codes (`@h/...`), which the form library cannot read | none |
| Not checked | the server was not asked yet (the sources are shown anyway) | none |

Each form also says which copy is in use (the app's, or the server's) and the version label of the server's
copy. **Update all** takes every form that is different or new. The last answer is kept while the app runs.

"The same" is decided by a fingerprint of what the form says (`Questionnaire.fingerprint`): the copy is written
the way the FHIR library writes it, without `meta` (version and time) and the narrative, and hashed, so the copy
that ships in the compact form and the plain one a server serves are equal when they say the same, whatever the
order of the keys or the spelling of a number.

## What takes a form

**Update** / **Add** stores the server's copy in the FHIR engine like a download (nothing is queued for upload) and
remembers its id (`AcceptedFormsStore`). A form is read from the engine when the app uses the server's forms, or
when it is one of those ids (`usesStoredForm`): the form lists (`allFormSummaries`, `storedQuestionnaires`) and
every screen that opens a form (`getQuestionnaireOrFromAssets`) follow that one rule. The other forms stay the
app's, and the sync still downloads no questionnaire while the setting is on. **Use the app's version** forgets the
id and removes the stored copy. With the setting off every form of the engine is in use, as before, and the page is
the way to fetch what is new or changed. **Reset to defaults** goes back to the app's forms (it clears the ids).

A form is not replaced by itself when the server changes it later: the page says "Update available" again and
the user decides.

The fake backend (`docs/user-guide/fake-backend`) serves the questionnaires of `fixtures/*.json`, so the page can be
tried against a server with changed, missing and new forms.
