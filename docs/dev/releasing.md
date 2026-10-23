# Releasing

## Before the build

1. `CHANGELOG.md` says what is in the release, and how each change was verified.
2. `./gradlew spotlessCheck :core:testDebugUnitTest :core:lintDebug` pass (the CI runs them).
3. A change to the database has a migration, the exported schema and a case in
   `AppDatabaseMigrationTest` (`./gradlew :core:connectedDebugAndroidTest` on a device or emulator).
4. `app_config.xml` of the module being released: server, authentication method, support e-mail
   and a **diagnostics password that is not the example value** (`validateAppConfig` warns).
5. Raise `versionCode` (a whole number that only goes up: the stores and the system compare it) and
   `versionName` in the module's `build.gradle.kts`.

## Building

```bash
./gradlew :app:assembleRelease        # or :dev3-app:assembleRelease
```

One APK per processor type is written to `<module>/build/outputs/apk/release/`:

| File | For |
|---|---|
| `...-arm64-v8a-release.apk` | nearly every phone and tablet since 2017 |
| `...-armeabi-v7a-release.apk` | older 32-bit devices |
| `...-x86_64-release.apk`, `...-x86-release.apk` | emulators |

### Smaller APK (optional)

`./gradlew :app:assembleRelease -PshrinkRelease=true` shrinks the code and resources with R8
(the `release` block of `app/build.gradle.kts` and `dev3-app/build.gradle.kts`): about 51 MB per ABI instead of 72 MB. It is **off by default**
because it was only checked partly: the shrunk release build starts, shows the login page, opens
the guest home and the registration form, and a shrunk *debug* build signed in with the single
sign-on, synced and navigated. The shrunk **release** build was not run against a server (it does not
allow plain http, and the test server is http), so sign-in, sync and saving in it are unverified.
Moshi models use generated adapters and are safe; the FHIR libraries use reflection. Before turning
it on for a release, run the sign-in, a sync, a patient with an encounter and a save with that APK,
and add keep rules to `proguard-rules.pro` for anything that fails.

## Signing

The release build is currently signed with the **debug key** of the machine that builds it
(`signingConfig = signingConfigs.getByName("debug")` in `app/build.gradle.kts` and
`dev3-app/build.gradle.kts`). That is enough to install it, but:

- an update only installs over a version signed with the same key, so APKs built on different
  machines do not update each other;
- it cannot be published on Google Play.

Before a real distribution, make a release key (`keytool -genkeypair ...`), keep it and its
passwords outside the repository, and read them from environment variables in a
`signingConfigs { create("release") { ... } }` block. Losing the key means users have to uninstall
(and lose the data of the device) to install a version signed with a new one.

## After

- Tag the commit `v<versionName>`: the CI builds on tags.
- Install the APK on one device that already has data from the previous version and check it opens
  (this is the test that catches a bad database migration).
- Keep the `mapping.txt` that R8 wrote (`<module>/build/outputs/mapping/release/`) with the release:
  without it a crash report from the shrunk build cannot be read.
