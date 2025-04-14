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
---

## 🔗 Configuring Patient Registration Questionnaire

To Configure **Patient Registration Questionnaire**, set the `registration_questionnaire_name` property to either of the following: 
1. `resourceId` of the registration questionnaire synced from the server.
2. `file name` of the registration questionnaire in the assets folder.

---

## 🔗 Configuring Create Encounter Questionnaire

To Configure the questionnaire list on click of **Create Encounter** button in the patient screen, update the `questionnaires` & `encounter_type_system_uri` property with the encounter type of Encounter's questionnaire:
Similar to registration questionnaire, it either takes the questionnaire from assets folder or after sync from the server.

Note:
In the `questionnaires` property, write the code for `encounter type` instead of the `questionnaire.id` & make sure use the correct `url`
Reference: [Example property](https://github.com/parthfloyd/openmrs-android-fhir/blob/integrate-create-encounter-screen/core/src/main/res/values/server.xml)

### Example Questionnaire
```json
{
"code": [
    {
      "system": "http://fhir.openmrs.org/code-system/encounter-type",
      "code": "<This Code value should be part of questionnaire>",
      "display": "display"
    }
}
```
---

## 📧 Configuring the Developer Email for Application Diagnostics

To ensure application diagnostics are sent to the correct developer email upon user request:

1. Open the `local.properties` file.
2. Update the `support_email` property with the desired email address.

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