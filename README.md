# Project Setup Guide

Welcome to the project! Follow the instructions below to set up the environment and get started.

---

## 🛠️ Setup Instructions

The app builds from public artifacts (Google Maven, Maven Central); **no GitHub account or token is needed**.

1. **Duplicate and rename the machine file:**
   - Copy `local.properties.default` to `local.properties` and set `sdk.dir` to your Android SDK path (Android Studio does this for you). Nothing else goes in this file.

2. **Configure the app:**
   - Everything the app needs (server, login, what to download, forms) is in **one file per app**: `app/src/main/res/xml/app_config.xml` (and `dev3-app/src/main/res/xml/app_config.xml` for the dev3 build). Open it and set `<server url=...>`; see [Configuration](#️-configuration) below. The file in `core` is only the fallback and the reference.

---

## 🚀 Running OpenMRS Server & Keycloak

You can run the OpenMRS Server and Keycloak using one of the following options:

### Option 1: Using Docker Compose
1. Use the `docker-compose` file available in the root of this project.
2. **Create a `.env` file** from the provided template `.env.default`.

### Option 2: Using OpenMRS Distro SSO Project
1. Follow the steps provided in the [OpenMRS Distro SSO repository](https://github.com/icrc/openmrs-distro-sso/tree/main).
2. Apply any necessary changes as per your setup.

#### Keycloak Login
- To log in using Keycloak, use one of the predefined users listed [here](https://github.com/icrc/openmrs-distro-sso/blob/main/keycloak/users.csv).

---

## ⚙️ Configuration

An app is configured by one file, `res/xml/app_config.xml`, in its module. It is read when the app starts; a missing or contradictory setting stops the app with a message that says what to fix. Every attribute that is not listed here has a default, so a small file is enough:

```xml
<app-config>
    <server url="http://10.0.2.2:8080/openmrs" />
    <auth method="both" guest-mode="false">
        <sso discovery-url="http://10.0.2.2:8081/realms/main/.well-known/openid-configuration"
             client-id="openmrs-fhir" />
    </auth>
</app-config>
```

| Element | Attribute | Meaning |
| --- | --- | --- |
| `<server>` | `url` | The OpenMRS server. The FHIR, REST and connectivity-check URLs are derived from it (from the emulator the host is `10.0.2.2`). On the login screen the gear can point a device at another server without rebuilding. |
| `<auth>` | `method` | What the login page offers: `basic` (username and password), `openid` (single sign-on) or `both` (the form, an **or** divider and the SSO button). With `both` the app remembers which one the session was opened with. |
| | `guest-mode` | `true` adds **Continue without signing in**: patients and forms are filled offline and syncing asks the user to sign in; what was entered is kept. |
| `<sso>` | `discovery-url`, `client-id` | The OpenID Connect provider (needed for `openid` and `both`). The scope (`openid`), the redirect (`<application id>:/oauth2redirect`), http vs https and the endpoints are worked out from these. |
| | `provider-name` | Optional label: the button reads **Sign in with <name>**. |
| `<sync>` | `location-tag` | Tag of the locations offered at login (default `Login Location`). |
| | `cohort-type` | Optional cohort type (UUID): only patient lists of that cohort are downloaded. |
| | `filter-patient-lists-by-location` | `true` (default): only the lists of the location the user picked. |
| | `patients-since` | Optional ISO date: patients last updated before it are not downloaded. |
| `<patients>` | `registration-form` | Id of a synced questionnaire, or the file name of one in `assets`, used to register patients. |
| | `fetch-identifiers` | `true`: the device fetches auto-generated identifiers and adds them to new patients; `false`: the server creates them. |
| `<support>` | `email` | Where **Send diagnostics** and exported unsynced data go. |
| | `diagnostics-password` | Optional password of the diagnostics archive. |
| `<form-sections>` | | The Create Encounter screen, see below. |

### What is downloaded

It is the same everywhere, so it is not configured URL by URL: the locations with the tag above, the patient lists (Groups), the questionnaires, and the patients of the lists the user selects together with their encounters and observations. Patient data is only ever downloaded for the selected lists; with no list selected none is. Later syncs fetch only what changed since the last one.

### Form sections

The Create Encounter screen lists forms in the sections declared in `<form-sections>`; a form is identified by the encounter type code its questionnaire declares (code system `http://fhir.openmrs.org/code-system/encounter-type`), and questionnaires come from the server or from `assets`:

```xml
<form-sections>
    <section name="MHPSS Forms">
        <name lang="fr">Formulaires MHPSS</name>
        <name lang="es">Formularios de MHPSS</name>
        <form code="encounter.assessment" />
        <form code="encounter.followup" />
    </section>
</form-sections>
```

`name` is the section title; a `<name lang="xx">` child translates it. Form titles come from the questionnaire (and its own title translations). Sections show in the order declared, can be collapsed, and a search box appears when there are many forms. A code without a questionnaire is skipped. Leave `<form-sections>` out (or empty) and every available form is listed alphabetically in one **All forms** section. Each user can also pick up to five favorites in Settings; they are shown first.

### Bundled questionnaires

The forms that ship with the app (`core/src/main/assets/questionnaires/<id>.json`, plus the registration form) are the offline fallback for forms that have not been synced; a synced form always wins. They are stored in a compact form that the app turns back into plain FHIR JSON before parsing it, so rendering, translations, show/hide rules, obs groups and extraction behave exactly as with the original file:

- translations read `"text:i18n": {"fr": "...", "es": "..."}` instead of the verbose `"_text": {"extension": [...]}`;
- `url` / `system` values can start with a prefix name (`"@h/questionnaire-itemControl"`), listed under the first key, `"$compact"`.

To bundle a new form, put the plain FHIR JSON in the folder and run `python tools/compact_questionnaires.py core/src/main/assets/questionnaires`; each file is expanded again and compared with the original before it is written.

### Understanding the Encounter Screener Template

Encounter forms can optionally show the shared screener questionnaire before the main form renders. To enable this behavior:

1. Flag any encounter questionnaire item that should surface in the screener with the `https://openmrs.org/ext/show-screener` extension.
2. At runtime, `GroupFormEntryViewModel` inspects each encounter form, extracts those flagged items, and merges them into the screener template before handing the combined questionnaire to the UI layer.
3. The base template lives at `core/src/main/assets/screener-questionnaire-template.json`; customize this file (or replace it entirely) to adjust the screener layout or wording.

### Example Questionnaire
```json
{
"code": [
    {
      "system": "http://fhir.openmrs.org/code-system/encounter-type",
      "code": [{"code":"<Observation code>"}],
      "display": "display"
    }
}
```
---

## 📝 OpenMRS Notes

### Logout URL
To log out of the OpenMRS web application:
```plaintext
http://localhost:8080/realms/main/protocol/openid-connect/logout
```
**Note:** There is an issue with the current OpenMRS installation and the SSO logout process.

### List of Users

A list of predefined users for OpenMRS can be found here:\
[Keycloak Users CSV](https://github.com/icrc/openmrs-distro-sso/blob/main/keycloak/users.csv)

* * * * *

🔧 Common Setup Issues and Fixes
--------------------------------

1.  **Error 404 on clicking login button:**

    -   Ensure that the `discovery_uri`'s port matches your Keycloak container's port in the `local.properties` file.
2.  **Error 404 after successful login, and sync is failing:**

    -   Verify that the `BASE_URL`'s port matches your Gateway container's port in the `local.properties` file.
3.  **First sync is fetching too many resources:**

    -   Narrow down the scope of the download sync by modifying the `fhir_sync_urls` property in the `local.properties` file.

* * * * *

🌐 Keycloak and Hostname Configuration (localhost vs 10.0.0.2)
--------------------------------------------------------------

Keycloak can be accessed through different URLs depending on the use case:

-   **localhost** is used to log into the OpenMRS web application.
-   **10.0.0.2** is used by the Android app.

To configure this:

1.  Update the `KC_HOSTNAME` variable in `docker-compose.yml` (line 89).
2.  After modifying, restart using:
    `docker compose up -d`
    Alternatively to restart only the Keycloak service, you can use:
    `docker compose restart keycloak`

* * * * *

🔨 Development Notes
--------------------

### OpenMRS Development

Refer to the custom code created for OpenMRS:\
[Google Android FHIR OpenMRS Code](https://github.com/google/android-fhir/tree/openmrs)

### Android FHIR SDK with the FHIR Info Gateway

-   This project is based on [Google FHIR App Examples](https://github.com/google/fhir-app-examples/tree/main/demo).
-   For more details, see:\
    [OpenMRS Android FHIR Project](https://github.com/icrc/openmrs-android-fhir)


### Create a release

To initiate the build workflow:

- From Git:  Just create and push a tag `vX.Y.Z`
- From Web UI:  Create a release and the related tag `vX.Y.Z` and Delete immediately the Realse

the `Build` action should be triggered and it will create a new release containing the apk configured to use dev3.openmrs.org as the OpenMRS Backend.