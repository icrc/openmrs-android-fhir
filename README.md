# Setup

Accessing artifacts hosted on GitHub necessitates the use of a GitHub account for downloading purposes:

- Duplicate the file `local.properties.default` and rename it to `local.properties`.
- Then, open the "local.properties" file and insert your GitHub account/token details.

To run the OpenMRS Server & Keycloak
- Option 1: Use the docker file in the root of this project.
- Option 2: Follow the steps & apply change in the note below using the Project: https://github.com/icrc/openmrs-distro-sso/tree/main

Note Following changes are required for Option 2:
- Update the ANDROID_SDK_REDIRECT_URIS value to "org.openmrs.android.fhir:/oauth2redirect" on this [line]("https://github.com/icrc/openmrs-distro-sso/blob/500feeb524831b7e3128e0a4e675dab5d6d1d380/keycloak/initialize-my-realm-and-add-users.sh#L35")

# Common setup issues fixes:
1. Error 404 on clicking login button
    - Match the discovery_uri's port matches with your keycloak container's port [here]("https://github.com/icrc/openmrs-android-fhir/blob/1c0b93cbf14be3b32d12f7d5182d11b17085bc38/app/src/main/res/raw/auth_config.json#L6")
2. After successful login sync is failing directly & in logs it's throwing error 404:
    - Match the BASE_URL's port with your gateway container's port [here]("https://github.com/icrc/openmrs-android-fhir/blob/1c0b93cbf14be3b32d12f7d5182d11b17085bc38/app/src/main/java/org/openmrs/android/fhir/FhirApplication.kt#L82")
3. The first sync is fetching too many resources:
    - Narrow down the scope of the download sync by modifying the urls [here]("https://github.com/icrc/openmrs-android-fhir/blob/1c0b93cbf14be3b32d12f7d5182d11b17085bc38/app/src/main/java/org/openmrs/android/fhir/data/TimestampBasedDownloadWorkManagerImpl.kt#L35")

# OpenMRS Notes

to logout: http://localhost:8080/realms/main/protocol/openid-connect/logout
There is an issue with current OpenMRS installation and SSO Logout process.

List of Users: https://github.com/icrc/openmrs-distro-sso/blob/main/keycloak/users.csv

# Development
See https://github.com/google/android-fhir/tree/openmrs as a custom code is made for openmrs

# Temporary Situation

A PR is currently under validation here:
https://github.com/google/android-fhir/pull/2525
If validated, SNAPSHOT will be available on officiel github project.

In the meantime, artifacts are https://github.com/icrc-fdeniger/android-fhir



# Download 


# Android FHIR SDK with the FHIR Info Gateway
- Based on  https://github.com/google/fhir-app-examples/tree/main/demo
- See https://github.com/icrc/openmrs-android-fhir
