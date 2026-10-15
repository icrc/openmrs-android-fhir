# UI/UX and data-collection upgrade

Status: proposal + first slice. Companion to [audit.md](audit.md) (section G is the code-level UI audit).

## What the evidence says

Findings that shape this design (all verified; see audit §4 and the CHANGELOG):

1. **The deployment is concept-driven.** The test backend is a physical-rehabilitation deployment (observations such as *Device ID*, *Provision State*, *Planned Intervention*) and its observations carry no standard coding system. Hard-coding "vitals" or similar clinical forms would be wrong here. Clinical content must keep coming from **synced Questionnaires**; the app should improve *how* data is captured, found and protected, not *what* is captured.
2. **Field data loss is the dominant risk.** Back/cancel in the registration and edit forms leaves immediately with no confirmation; logout wipes everything after a generic warning; the unsynced screen can only be reached by hand.
3. **Finding a patient is name-only.** The list filters on `Patient.NAME`; identifiers and phone numbers are not searchable although they are what clinicians use in the field.
4. **Looks are inconsistent.** No app Compose theme; 83 hard-coded color lookups, 21 literal colors, 41 literal font sizes (audit G1/G2).
5. **The server supports** full create/update for `AllergyIntolerance`, `Immunization`, `Condition`, `Task`; read-only `MedicationRequest`, `Questionnaire`, `RelatedPerson`; no `Appointment` (audit §4).

## Principles

- Offline first: everything below works from the local FHIR store and syncs through the existing upload path.
- FHIR first; no new REST dependencies.
- No schema change and no sync-behaviour change unless explicitly approved.
- Each feature is small, covered by a unit test where logic exists, and reversible.

## Slice 1 — implemented in this branch (low risk, high value)

| # | Change | Why | Where |
|---|---|---|---|
| 1 | **At least one patient list is required** before continuing; the sync never downloads patients unscoped | Observed on the real server: with no list the app started pulling all 128k patients (≈1 day) | `SelectPatientListFragment`, `TimestampBasedDownloadWorkManagerImpl.applyPatientListScope` |
| 2 | **Search by identifier and phone**, not only name | What clinicians have on a card/referral slip; works offline | `PatientListViewModel` |
| 3 | **Unsaved-changes guard** on patient registration / edit | A field worker pressing back after typing 5 minutes loses everything | `AddPatientFragment`, `EditPatientFragment`, `QuestionnaireResponse.hasAnswers()` |
| 4 | **Consistent inset/padding and fragment hosting** | Less duplicated layout code, one place to fix edge-to-edge | `InsetsExt`, `ComposeExt` |

## Slice 1 verification (on the real server, emulator, basic auth)

| Change | Result |
|---|---|
| Patient list required / no unscoped download | 0 patients downloaded with no list (before: 560 in 7 min) |
| Identifier search | exact identifier finds the patient; no match shows the empty state |
| Unsaved-changes guard | untouched → no prompt; edited → prompt. The first version prompted on untouched forms, found on device and fixed |
| Instrumentation tests | all pass except one class that already failed at baseline |

Also found and fixed while testing: login was impossible with stored credentials because of a duplicate `Authorization` header (audit A14).

## Slice 2 — proposed, needs your decision

| # | Feature | Value | Cost / risk | Needs approval because |
|---|---|---|---|---|
| A | **Safe logout**: block logout (or require typing a confirmation) when unsynced local changes exist, and show an "N changes not uploaded" banner on Home | Prevents the worst data-loss path | Detecting unsynced data needs a scan (see perf note below); medium | Changes visible behaviour (logout) |
| B | **Draft autosave for all forms** (resume where you left) | Interrupted sessions are normal in the field | Needs storage for drafts; best as a Room table → schema change + migration | Schema change |
| C | **Duplicate-patient warning** at registration (same name + birth date, local check) | Data quality; cheap and offline | Low; name/dob matching rules need your input | New behaviour in registration |
| D | **App theme + dark mode** (audit G1/G2) | Visible consistency, readability outdoors/at night | Visual change on every screen | Visible change |
| E | **Patient details as a single list** (audit G6) | Smoother scrolling, ~330 lines less | Largest UI change | Visible change |
| F | **Allergies** (view + add, `AllergyIntolerance`) | Safety-critical info that is currently absent; the server supports it | New resource type on your backend | Writes a new clinical type |

## Performance note that affects A

The unsynced-resources scan loads **all** `Observation`, `Encounter` and `Patient` resources and calls `getLocalChanges` for each (`UnsyncedResourcesViewModel.fetchUnsyncedPatientsWithEncounterAndObservations`). `FhirEngine` has no batch "all local changes" call, so a cheap "is anything unsynced?" check is not available through the public API. **Measured:** ≈30 s for 10 patients / 197 encounters / 1,890 observations, of which 26 s is the SDK loading every Observation; the `getLocalChanges` lookups are only ≈4 s. Making it concurrent did not help. Real fixes are an SDK count/list API for local changes, or a small Room index of locally touched ids (schema change, needs your approval). Until then a cheap "anything unsynced?" banner (feature A) is not available.
