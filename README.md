# Setup

Accessing artifacts hosted on GitHub necessitates the use of a GitHub account for downloading purposes:

- Duplicate the file `local.properties.default` and rename it to `local.properties`.
- Then, open the "local.properties" file and insert your GitHub account/token details.

# OpenMRS Notes

to logout: http://localhost:8080/realms/main/protocol/openid-connect/logout
There is an issue with current OpenMRS installation and SSO Logout process.

List of Users for OpenMRS: https://github.com/icrc/openmrs-distro-sso/blob/main/keycloak/users.csv

# Keycloak and `localhost` vs `10.0.0.2`

Keycloak can be accessed only from one URL but:
- `localhost` will be used to log into OpenMRS Web application
- `10.0.0.2` will be used from the android App

So the variable `KC_HOSTNAME` ( see `docker-compose.yml`, line 89) defining Keycloak hostname should be changed accordingly to the use case.

Restart Keycloak with `docker compose up -d` that will restart only the service keycloak if you change that variable only or use `docker compose restart keycloak`.

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
