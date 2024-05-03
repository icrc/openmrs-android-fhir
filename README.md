# Setup

Accessing artifacts hosted on GitHub necessitates the use of a GitHub account for downloading purposes:

- Duplicate the file `local.properties.default` and rename it to `local.properties`.
- Then, open the "local.properties" file and insert your GitHub account/token details.

# OpenMRS Notes

to logout: http://localhost:8080/realms/main/protocol/openid-connect/logout
There is an issue with current OpenMRS installation and SSO Logout process.

List of Users: https://github.com/icrc/openmrs-distro-sso/blob/main/keycloak/users.csv



# Temporary Situation

A PR is currently under validation here:
https://github.com/google/android-fhir/pull/2525
If validated, SNAPSHOT will be available on officiel github project.

In the meantime, artifacts are https://github.com/icrc-fdeniger/android-fhir



# Download 


# Android FHIR SDK with the FHIR Info Gateway
- Based on  https://github.com/google/fhir-app-examples/tree/main/demo
- See https://github.com/icrc/openmrs-android-fhir
