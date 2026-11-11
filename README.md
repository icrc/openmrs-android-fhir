# OpenMRS Android FHIR

An offline-first Android app for [OpenMRS](https://openmrs.org), built on the
[Android FHIR SDK](https://github.com/google/android-fhir). Clinicians register patients, record
encounters with FHIR questionnaires and work without a connection; the data is synchronised with the
OpenMRS server when there is one. Languages: English, French, Spanish, Russian and Arabic.

## Quick start

```bash
git clone https://github.com/<your-fork>/openmrs-android-fhir.git
cd openmrs-android-fhir
python tools/dev.py doctor        # checks your machine (Windows without `python`: py -3 tools/dev.py doctor)
./gradlew :app:assembleDebug      # first build: several minutes
python tools/dev.py install       # install on the connected emulator or phone and open it
```

Windows, macOS and Linux are supported. The full walk-through (what to install, an emulator, a server to
talk to, the checks before a pull request) is in
[`docs/dev/setup/getting-started.md`](docs/dev/setup/getting-started.md). No GitHub account or token is needed to build.

## Repository

| Path | What it is |
|---|---|
| `core/` | Android library with nearly all the code (`org.openmrs.android.fhir`): screens, ViewModels, sync, auth, database. |
| `app/` | The application shell. Its `res/xml/app_config.xml` configures the build. |
| `dev3-app/` | The same shell configured for the dev3 demo server; the CI publishes its APK on a release. |
| `tools/` | Developer helper scripts, see [`docs/dev/setup/tools.md`](docs/dev/setup/tools.md). |
| `docs/dev/` | Developer documentation, indexed in [`docs/README.md`](docs/README.md). |
| `docs/user-guide/` | The user guide of the app in five languages. |
| `docker-compose.yml` | A local OpenMRS + Keycloak for development (`.env.default` is its template). |

Contributing: [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Configuration

An app is configured by one file, `res/xml/app_config.xml`, in its module (server, login method, what to
download, registration form, form sections), plus `res/values/auth_config.xml` for single sign-on. A small
file is enough:

```xml
<app-config>
    <server url="http://10.0.2.2:8080/openmrs" />
    <auth method="both" guest-mode="false" />
    <support email="support@mail.com" />
    <diagnostics password="changeit" />
</app-config>
```

Every element and attribute is documented in [`docs/dev/configuration.md`](docs/dev/configuration.md).

## Running a server

The app needs an OpenMRS server. A test server you already have, the fake server of the user guide, or the
OpenMRS + Keycloak stack of `docker-compose.yml` all work: see step 5 of
[`docs/dev/setup/getting-started.md`](docs/dev/setup/getting-started.md). Single sign-on set-up and the host name problems
(`localhost` against `10.0.2.2`) are in [`docs/dev/features/sign-in-setup.md`](docs/dev/features/sign-in-setup.md).

## Releases

Push a tag `vX.Y.Z`: the `Build` workflow runs the checks and publishes a release with the signed `dev3-app`
APKs. What to check before, how to sign and how to build a smaller APK:
[`docs/dev/releasing.md`](docs/dev/releasing.md).

## Background

* This project grew from the [Google FHIR app examples](https://github.com/google/fhir-app-examples/tree/main/demo)
  and the [OpenMRS branch of the Android FHIR SDK](https://github.com/google/android-fhir/tree/openmrs).
* The OpenMRS back end with single sign-on used for development:
  [icrc/openmrs-distro-sso](https://github.com/icrc/openmrs-distro-sso).

## License

BSD 3-Clause, see [`LICENSE`](LICENSE).
