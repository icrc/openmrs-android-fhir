# Setup

Accessing artifacts hosted on GitHub necessitates the use of a GitHub account for downloading purposes:

- Duplicate the file `local.properties.default` and rename it to `local.properties`.
- Then, open the "local.properties" file and insert your GitHub account/token details.
- Other properties ( URLS, oauth2) should be changed only if you use a custom OpenMRS / Keycloak Setup

**To run the OpenMRS Server & Keycloak**
- Option 1: Use the docker  compose file in the root of this project. **Create the file `.env` from the template `.env.default`**
- Option 2: Follow the steps & apply change in the note below using the Project: https://github.com/icrc/openmrs-distro-sso/tree/main

To login, use one of this user: https://github.com/icrc/openmrs-distro-sso/blob/main/keycloak/users.csv

**Configuring fhir sync urls**

To enable FHIR synchronization, you must set the local property fhir_sync_urls with the required resource URLs. 

Each resource type should be separated by a comma (`,`. 

Example of how to set fhir_sync_urls:

`fhir_sync_urls=Location?_sort=_lastUpdated&_summary=data,Patient?_sort=_lastUpdated,Encounter?_sort=_lastUpdated,Observation?_sort=_lastUpdated`

# Common setup issues fixes:
1. Error 404 on clicking login button
    - Match the discovery_uri's port matches with your keycloak container's port in your file `local.properties`
2. After successful login sync is failing directly & in logs it's throwing error 404:
    - Match the BASE_URL's port with your gateway container's port in your file `local.properties`
3. The first sync is fetching too many resources:
    - Narrow down the scope of the download sync by modifying the urls [here]("https://github.com/icrc/openmrs-android-fhir/blob/1c0b93cbf14be3b32d12f7d5182d11b17085bc38/app/src/main/java/org/openmrs/android/fhir/data/TimestampBasedDownloadWorkManagerImpl.kt#L35")

# OpenMRS Notes

to logout: http://localhost:8080/realms/main/protocol/openid-connect/logout
There is an issue with current OpenMRS installation and SSO Logout process.

List of Users for OpenMRS: https://github.com/icrc/openmrs-distro-sso/blob/main/keycloak/users.csv

# Keycloak and `localhost` vs `10.0.0.2`

Keycloak can be accessed only from one URL but:
- `localhost` will be used to log into OpenMRS Web application
- `10.0.0.2` will be used from the android App

Thus the variable `KC_HOSTNAME` ( see `docker-compose.yml`, line 89) defining Keycloak hostname should be changed accordingly to the use case.

After a modification, Restart Keycloak with `docker compose up -d` that will restart only the service keycloak if you change that variable only or use `docker compose restart keycloak`.

# Development
See https://github.com/google/android-fhir/tree/openmrs as a custom code is made for openmrs


# Download 


# Android FHIR SDK with the FHIR Info Gateway
   - Based on  https://github.com/google/fhir-app-examples/tree/main/demo
   - See https://github.com/icrc/openmrs-android-fhir
