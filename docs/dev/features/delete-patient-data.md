# Delete patient data

The drawer action **Delete patient data** (above **Log out**, in the danger color) removes what the
signed-in user has on the device so that a new flow can start, without signing out. The code is
`data/PatientDataEraser.kt`; the dialogs are in `MainActivity`.

## What goes and what stays

| Removed | Kept |
|---|---|
| The patients the signed-in user can see, with their encounters, observations, conditions and risk assessments, including what was not uploaded yet | The signed-in session |
| The patients to identify later | The selected location |
| The group-session drafts | The forms, the locations and the patient lists themselves |
| The choice of patient lists, the lists still waiting to be downloaded, the patients brought by live mode | The settings |
| The "last downloaded" marks of those resource types, so the next download starts from the beginning | |

On a device shared by several users only the patients the signed-in user can see are removed
(`UserDataLedger` knows who owns what; see `multi-user.md`).

## The flow

1. The user taps the action. The app counts what would go and how much of it was **never uploaded**
   (`PatientDataEraser.preview()`); the confirmation dialog says both numbers, and warns that
   unsent changes are lost.
2. The user confirms in the dialog. When the offline login is on, the fingerprint, face or screen
   lock is asked next (`OfflineAuthPrompt.confirm`). A device with no screen lock goes ahead: the
   dialog was the confirmation.
3. A sync or live upload that is running is cancelled first (it would write what is being
   deleted). `erase()` then purges the records (references first: observations, conditions, risk
   assessments, encounters, then patients), forgets the ownership entries and clears the other
   stores above.
4. The app goes back to the home screen, with the session and the location kept, and says it is
   done. A failure is reported and written to the audit trail.

## "Log out and delete all data" (Settings)

A different action: it ends the session and removes everything on the device (`logoutAndDeleteAllData`
in `MainActivity`). It asks for the same confirmation and the same identity check. Whether a plain
log out also deletes the data is the setting "Clear data on logout".

## Testing

There is no unit test (it needs a FHIR engine and a database): check on a device with a few
patients, one with an unsent encounter, that the counts in the dialog are right and that the
location and the session are still there afterwards.
