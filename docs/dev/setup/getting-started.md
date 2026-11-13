# Getting started

From a clean machine to a running app and a first pull request. The steps are the same on Windows,
macOS and Linux; where a command differs, both forms are shown.

## 1. Install what you need

| Tool | Needed for | Notes |
|---|---|---|
| Git | everything | |
| JDK 17 | Gradle | Gradle starts on a JDK 17 or newer (21 is what the project is tested with); the build compiles with a 17 toolchain and downloads one when none is found. Android Studio ships a JDK 17 (`jbr`). |
| Android Studio (current stable) | the IDE, the SDK, the emulator | Installs the Android SDK; the project compiles against API 36 and Gradle fetches what is missing once the licenses are accepted (`sdkmanager --licenses`). |
| Python 3.7 or newer | `tools/dev.py` and the questionnaire scripts | Optional; the standard library is enough, nothing to `pip install`. |
| Node.js 18 or newer | the XML formatting check (`spotlessXmlCheck`) | Optional; without it run the check as `./gradlew spotlessCheck -x spotlessXml` (the CI runs the XML step). |
| Docker | a local OpenMRS + Keycloak | Optional, see step 5. |

| | Install |
|---|---|
| **Windows** | `winget install Git.Git EclipseAdoptium.Temurin.17.JDK Google.AndroidStudio OpenJS.NodeJS.LTS Python.Python.3.12` |
| **macOS** | `brew install git node python` and `brew install --cask temurin@17 android-studio` |
| **Linux (Debian, Ubuntu)** | `sudo apt install git openjdk-17-jdk nodejs npm python3`, then Android Studio from [developer.android.com/studio](https://developer.android.com/studio). The emulator needs KVM (`sudo apt install qemu-kvm`, and your user in the `kvm` group). |

Open a new terminal afterwards and let the repository check your machine:

```bash
git clone https://github.com/<your-fork>/openmrs-android-fhir.git
cd openmrs-android-fhir
python tools/dev.py doctor        # Windows without `python` on the PATH: py -3 tools/dev.py doctor
```

`doctor` lists what is found and what is missing, with the fix next to each line. The command line
below uses `./gradlew`; in Windows PowerShell or `cmd` write `.\gradlew` or `gradlew.bat`.

## 2. Tell the build where the Android SDK is

Android Studio writes `local.properties` for you. Without it, copy the template and edit it:

```bash
cp local.properties.default local.properties      # Windows: copy local.properties.default local.properties
```

```properties
sdk.dir=/path/to/Android/Sdk
```

`local.properties` is the machine's file only (ignored by git). The environment variable `ANDROID_HOME`
works as well. No GitHub account or token is needed: the app builds from public artifacts.

## 3. Build

```bash
./gradlew :app:assembleDebug
```

The first build downloads Gradle and the dependencies and takes several minutes. `app` is the
application to work with; `dev3-app` is the same shell configured for the dev3 demo server, the one the
CI publishes.

## 4. Run on an emulator or a phone

Create a virtual device in Android Studio (Device Manager; a phone or tablet with a recent system image),
or plug a phone in with USB debugging on. Then either press Run in Android Studio, or:

```bash
python tools/dev.py install            # build `app`, install it on every connected device, open it
python tools/dev.py install --clean    # also erase the app data: a first start (login, first sync)
python tools/dev.py install --module dev3-app --serial emulator-5554
python tools/dev.py logcat             # the log of the app only
```

An emulator on a PC is x86_64, a processor the APK list of `app/build.gradle.kts` leaves out (phones and
tablets are arm). `install` asks Gradle for that APK for this build only and changes no file. The other
commands of `dev.py` are in [`tools.md`](tools.md).

## 5. Point the app at a server

Set the server in `app/src/main/res/xml/app_config.xml` ([`configuration.md`](../configuration.md) documents
the whole file):

```xml
<server url="http://10.0.2.2:8080/openmrs" />
```

From an emulator the machine that runs it is `10.0.2.2`. A phone needs the server's address on your network,
or plug it in and run `python tools/dev.py reverse` and use `http://127.0.0.1:8080/openmrs`. Debug builds
accept plain http (`core/src/debug/res/xml/network_security_config.xml`, never delete it); release builds do
not.

Your edits of `app_config.xml`, `auth_config.xml` or a `build.gradle.kts` are for your machine: do not
commit them (`git add <file>` or `git add -p` only for your change; `git checkout <file>` undoes a temporary
edit).

Where the server comes from, easiest first:

1. **An OpenMRS you already have** (a test server of your organisation): put its address in the file.
2. **The fake server of the user guide**, no Docker and no real data:
   `python docs/user-guide/fake-backend/ctl.py up` (sign-in and scenarios: `docs/user-guide/fake-backend/README.md`).
3. **OpenMRS + Keycloak in Docker**: `cp .env.default .env`, fill in the empty values (the passwords are yours
   to choose), `docker compose up -d`. OpenMRS answers on port 8080 and Keycloak on 8081 after a few minutes.
   The single sign-on users and the stack itself are documented by the
   [OpenMRS distro SSO project](https://github.com/icrc/openmrs-distro-sso). Single sign-on in the app:
   [`sign-in-setup.md`](../features/sign-in-setup.md).

## 6. Before the pull request

```bash
./gradlew :core:spotlessKotlinApply    # format the Kotlin you touched
./gradlew spotlessCheck                # formatting, license header, XML: the gate of the CI (-x spotlessXml without Node)
./gradlew :core:testDebugUnitTest      # one class: --tests "org.openmrs.android.fhir.viewmodel.SomeTest"
./gradlew :core:lintDebug              # the CI fails on lint errors
./gradlew build                        # the full build, as in the CI
```

**Do not run plain `./gradlew spotlessApply`**: its XML step rewrites many untouched XML files, a huge
whitespace-only diff. `./gradlew spotlessXmlApply` also runs over every XML file: run `git diff --stat`
afterwards and `git checkout` any file you did not touch. A new `.kt` file needs the header of
`license-header.txt`.

## Next

* Something fails: [`troubleshooting.md`](troubleshooting.md).
* The helper commands: [`tools.md`](tools.md).
* How the code is organised and the rules every change follows: [`architecture.md`](../architecture.md).
* How to send your change: [`CONTRIBUTING.md`](../../../CONTRIBUTING.md).
