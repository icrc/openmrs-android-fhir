# Known gaps

What the code does not do yet, kept here instead of as `TODO` comments in the source.

* **Offline login, key problem**: when the key for the offline login cannot be created, the app shows
  a toast and goes on without it. No dialog explains it or offers to retry from Settings
  (`BiometricPromptHelper`).
* **Offline login, no screen lock**: on Android 11 and later, a device with neither a biometric nor a
  screen lock is not offered to set one up (`LoginActivityViewModel`).
* **Patient lists**: the id of a patient registered on the device changes at the upload; the add that
  waits for it follows the id read from the upload's answer. Without that note it finds the patient by what
  was entered (name, birth date, sex) and, when several fit, asks the user to add the patient again rather
  than guess. The history of a list is what was done on this device; what other devices did is not known. The
  export file is not protected.
* **Periodic sync**: a periodic background sync was written and switched off; whether the app should
  have one is undecided. The automatic sync (`MainActivityViewModel`) is the timer that runs while the
  app is open.
* **Visits**: *Reopen* sends a `PATCH` that removes `period.end`; how a server treats it is untested.
  Only the visit type *Facility Visit* can be chosen.
* **Diagnosis picker**: no tutorial step; the certainty is not asked; a free-text diagnosis read back
  from the server is not shown in the picker.
* **Database encryption**: see `database-encryption.md`.
