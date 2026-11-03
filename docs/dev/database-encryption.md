# Database encryption at rest

The patient data (the FHIR engine's database) is **not encrypted** on the device. It is protected by
the Android sandbox, the screen lock, the app lock and the "delete patient data" action, but a rooted
device or a backup can read it. The SDK setting exists (`FhirEngineConfiguration(enableEncryptionIfSupported
= ...)` in `FhirApplication`) and is `false`.

Turning it on makes the app crash at start (`lateinit property internalTracker has not been
initialized`): the SDK's encrypted-database code does not work with the Room version the app builds with
(2.8.5). The cause was not traced further.

## Options

| Option | Cost | Risk |
|---|---|---|
| Move the app to the Room version the SDK expects (2.6.x), then enable encryption | Downgrade Room, check the schema export and add a migration test, re-test everything | Medium: `AppDatabase` is Room too |
| Upgrade the FHIR SDK to a release built against Room 2.8 | Needs such a release, and API changes in sync and search | Medium to high: the SDK is a fork (`ohs-foundation/android-fhir`) |
| Leave it off | Nothing | The data is readable on a rooted device |

## Whichever way it goes

* **Existing installs must not lose data.** Enabling encryption does not convert an existing database:
  the SDK would open a new, empty one. A migration has to copy the data, and anything not uploaded must
  be exported or uploaded first (Import / Export).
* The key must live in the Android Keystore and be removed with the data.
* Test on a device with a database from the previous version and unsent changes.

Until one of the first two is chosen, the answer to "is the data encrypted?" is no.
