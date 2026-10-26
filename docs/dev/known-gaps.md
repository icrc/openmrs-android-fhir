# Known gaps

Things the code does not do yet, kept here instead of as `TODO` comments in the source.

- **Offline login, encryption key problem**: when the key for the offline login cannot be created,
  the app shows a toast and goes on without it. A dialog that explains the problem and offers to
  try again later from Settings does not exist (`BiometricPromptHelper`).
- **Offline login, no screen lock**: on Android 11 and later, when the device has neither a
  biometric nor a screen lock, nothing offers to set one up (`LoginActivityViewModel`).
- **Cohorts**: a patient registered on the device is not added to a cohort or patient list when
  saved (`AddPatientViewModel`); this waits for the OpenMRS cohort module.
- **Periodic sync**: a periodic background sync was written and switched off; whether the app
  should have one is undecided (the automatic sync in `MainActivityViewModel` is the timer that
  runs while the app is open).
