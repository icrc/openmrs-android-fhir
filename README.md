# Project Setup Guide

Welcome to the project! Follow the instructions below to set up the environment and get started.

---

## 🛠️ Setup Instructions

Accessing artifacts hosted on GitHub requires a GitHub account for downloading purposes. Please follow these steps:

1. **Duplicate and Rename Configuration File:**
   - Copy the file `local.properties.default` and rename it to `local.properties`.
   - Open the `local.properties` file and insert your **GitHub account/token details**.

2. **Modify Additional Properties:**
   - Modify other properties (e.g., URLs, OAuth2) **only if** you are using a custom OpenMRS or Keycloak setup.

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

## 🔒 Authentication Methods

This project supports the following authentication methods:
- **Basic Auth**
- **OAuth2**

Update the `auth_method` property in your configuration to:
- `"basic"` for Basic Auth
- `"openid"` for OAuth2

---

## 🔗 Configuring FHIR Sync URLs

To enable **FHIR synchronization**, set the `fhir_sync_urls` property with the required resource URLs in your `local.properties` file.

Each resource type should be separated by a comma (,).

### Example:
```properties
fhir_sync_urls=Location?_sort=_lastUpdated&_summary=data,Patient?_sort=_lastUpdated,Encounter?_sort=_lastUpdated,Observation?_sort=_lastUpdated
```

### Controlling first-time downloads

Use the optional `first_fhir_sync_url` property when you need a different search scope for the very first download. The app always queues all URLs from `first_fhir_sync_url` and `fhir_sync_urls`, then deduplicates them by resource type so subsequent syncs do not re-run broader first-time queries. This behavior is implemented in `TimestampBasedDownloadWorkManagerImpl` where the two lists are merged and filtered with `distinctBy` on the resource type derived from each URL.【F:core/src/main/java/org/openmrs/android/fhir/data/sync/TimestampBasedDownloadWorkManagerImpl.kt†L61-L82】

### Scoping patient list downloads

Two properties help control how patient lists are filtered during syncs driven by group resources:

- `filter_patient_lists_by_group` (boolean) limits Group queries to the user’s selected location by adding a `location` search parameter when enabled.【F:core/src/main/java/org/openmrs/android/fhir/data/sync/TimestampBasedDownloadWorkManagerImpl.kt†L66-L93】【F:core/src/main/java/org/openmrs/android/fhir/data/sync/GroupDownloadWorkManagerImpl.kt†L46-L54】
- `cohort-type` (UUID) appends a `cohort-type` parameter to Group requests when provided, allowing you to scope patient lists to a specific cohort definition on both initial and incremental sync paths.【F:core/src/main/java/org/openmrs/android/fhir/data/sync/TimestampBasedDownloadWorkManagerImpl.kt†L95-L101】【F:core/src/main/java/org/openmrs/android/fhir/data/sync/GroupDownloadWorkManagerImpl.kt†L57-L60】

Define these properties in `local.properties` (see `local.properties.default` for examples and defaults).【F:local.properties.default†L20-L31】
---

## 🌐 Server connectivity checks

Configure the lightweight connectivity probe using two properties:

- `check_server_url` is the endpoint the app pings to verify the server is reachable before launching sync or login flows.【F:core/src/main/java/org/openmrs/android/fhir/FhirApplication.kt†L135-L135】
- `server_connectivity_timeout_seconds` defines how long the probe waits before failing; values below one second are automatically clamped to a one-second timeout before being converted to milliseconds.【F:core/src/main/java/org/openmrs/android/fhir/FhirApplication.kt†L137-L140】

Both settings live in `local.properties` (see `local.properties.default` for their default values) so you can point to custom health-check endpoints or adjust the responsiveness of the status indicator.【F:local.properties.default†L8-L12】

---

## 🔗 Configuring Patient Registration Questionnaire

To Configure **Patient Registration Questionnaire**, set the `registration_questionnaire_name` property to either of the following:
1. `resourceId` of the registration questionnaire synced from the server.
2. `file name` of the registration questionnaire in the assets folder.

Use the `show_review_page_before_submit` toggle to decide whether a review screen is displayed before users submit questionnaire responses. The fragment builders read this boolean and call `showReviewPageBeforeSubmit(...)` so disabling it skips the intermediate review page for workflows such as patient registration or encounter capture.【F:core/src/main/java/org/openmrs/android/fhir/fragments/GenericFormEntryFragment.kt†L80-L138】 Configure the flag in `local.properties`; the default value is documented in `local.properties.default`.【F:local.properties.default†L18-L24】

---

## 🔗 Configuring Fetch Identifiers flag

To configure **patient identifier creation**, set the `fetch_identifiers` property to either of the following:
1. `true`: fetch auto-generated identifier values on the Android app and embed them on the patient before uploading to the server.
2. `false`: skip fetching auto-generated identifier values so they can be created entirely on the backend (or when you do not rely on autogenerated identifiers).

---

## 🔗 Configuring Create Encounter Questionnaire

To configure the questionnaire list on click of the **Create Encounter** button in the patient screen, update the `questionnaires` & `encounter_type_system_url` property with the encounter type of the encounter's questionnaire:
Similar to registration questionnaire, it either takes the questionnaire from assets folder or after sync from the server.

Note:
In the `questionnaires` property, write the code for `encounter type` instead of the `questionnaire.id` & make sure you use the correct `url` value.
Reference: [Example property](https://github.com/parthfloyd/openmrs-android-fhir/blob/integrate-create-encounter-screen/core/src/main/res/values/server.xml)

### Understanding the Encounter Screener Template

Encounter forms can optionally show the shared screener questionnaire before the main form renders. To enable this behavior:

1. Flag any encounter questionnaire item that should surface in the screener with the `https://openmrs.org/ext/show-screener` extension.
2. At runtime, `GroupFormEntryViewModel` inspects each encounter form, extracts those flagged items, and merges them into the screener template before handing the combined questionnaire to the UI layer.【F:core/src/main/java/org/openmrs/android/fhir/viewmodel/GroupFormEntryViewModel.kt†L120-L158】
3. The base template lives at `core/src/main/assets/screener-questionnaire-template.json`; customize this file (or replace it entirely) to adjust the screener layout or wording.

### Example Questionnaire
```json
{
"code": [
    {
      "system": "http://fhir.openmrs.org/code-system/encounter-type",
      "code": [{"<Observation code>"}],
      "display": "display"
    }
}
```
---

## 📧 Configuring the Developer Email for Application Diagnostics

To ensure application diagnostics are sent to the correct developer email upon user request:

1. Open the `local.properties` file.
2. Update the `support_email` property with the desired email address.

You can also protect exported diagnostic archives by setting the optional `diagnostics_password` property. When populated, the app passes the value to the `FileLoggingTree` initializer so generated ZIP files require that password to be opened.【F:core/src/main/java/org/openmrs/android/fhir/FhirApplication.kt†L64-L75】 Add the secret to `local.properties`; a placeholder entry is available in `local.properties.default`.【F:local.properties.default†L48-L49】

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