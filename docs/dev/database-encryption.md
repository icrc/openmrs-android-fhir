# Database encryption at rest: why it is off and what it would take

The patient data (the FHIR engine's database) is **not encrypted** on the device. It is protected
by the Android sandbox, the screen lock, the app lock and the "delete patient data" action, but a
rooted device or a device backup can read it. The setting exists in the SDK
(`FhirEngineConfiguration(enableEncryptionIfSupported = ...)`, in `FhirApplication`) and is `false`.

## What was tried

Turning it on made the app crash at start (`lateinit property internalTracker has not been
initialized`). The SDK's encrypted-database code does not work with the Room version this app
builds with (2.8.5); the cause was not traced further than that. The change was reverted; nothing
is half enabled.

## Options (a decision for the project, not for a quick change)

| Option | What it costs | Risk |
|---|---|---|
| Move the app to the Room version the SDK expects (2.6.x), then enable encryption | Downgrade Room, check the schema export and the migrations (`AppDatabaseMigrationTest`), re-test everything | Medium: the app's own database (`AppDatabase`) is also Room |
| Upgrade the FHIR SDK to a release built against Room 2.8 | Depends on a release that exists and on API changes in sync and search | Medium to high: the SDK is a fork (`ohs-foundation/android-fhir`) |
| Leave it off and document it | Nothing | The data is readable on a rooted device |

## What has to be true whichever way it goes

- **Existing installs must not lose data.** Turning encryption on for a database that was created
  without it does not convert it: the SDK would open a new, empty database. A migration has to
  copy the data (or the user has to sync again), and anything not uploaded yet must be exported
  or uploaded first. The app has "Import / Export" for that.
- The key must live in the Android Keystore and be removed with the data ("delete patient data",
  logout).
- A test on a device with a database from the previous version, with unsent changes in it.

Until one of the first two is chosen, the answer to "is the data encrypted?" is no.
