# Backend contract (Phase 1a)

What the app asks its server for, and what the fake backend (`fake-backend/server.py`) answers. The
rows were found in two ways: by reading the code (`ApiService`, `FhirApiService`, `ApiManager`,
`RestApiManager`, `SyncUrls`, `ServerSettings`, the sync workers) and by running the app against a
fake that logged every request and returned 404 for what it did not know (`/__control/log` lists the
requests nobody answered; it was empty after the flows below).

Base address: `<server>/openmrs`. The server is chosen on the device (`ServerSettings`, preference
`server_settings/server_url`); the guide's scripts write it before the first start, so the app never
asks any other server.

## Authentication

| Header | Who sends it | Notes |
|---|---|---|
| `Authorization: Basic <base64(user:password)>` | The FHIR SDK's own requests | The fake accepts the fictional user only |
| `Authorization: Bearer <base64(user:password)>` | The app's own requests (`ApiManager`: patient lists, search) | The app sends the **encoded password credentials under the `Bearer` scheme** (`resolveAuthorizationHeader`, pinned by `ResolveAuthorizationHeaderTest`). The fake accepts both schemes. `TODO: verify` against a real server which one it relies on |
| `Authorization: Bearer <access token>` | After single sign-on | The fake provider issues an unsigned demo token that only the fake reads |

## REST (`/ws/rest/v1`)

| Method | Path | Purpose | Screens |
|---|---|---|---|
| GET | `session` | Is the user signed in; who (user, person, current provider, session location). Without or with wrong credentials the answer is `{"authenticated": false}` (status 200) | Login, every start, connection banner |
| POST | `session` | Sets the working location (`{"sessionLocation": "<uuid>"}`) | Location screen |
| GET | `provider/<uuid>` | Can the account see this provider (404 when not) | Import (encounters of another provider) |
| GET | `idgen/autogenerationoption?v=default` | Identifier types and their sources | Registration (identifier types) |
| POST | `idgen/identifiersource/<uuid>/identifier` | Next identifier value (`{"identifier": "..."}`) | Registration, sync (only when `fetch-identifiers` is on) |

## FHIR (`/ws/fhir2/R4`)

| Method | Path and query | Purpose | Screens |
|---|---|---|---|
| GET | `Location?_sort=_lastUpdated&_summary=data&_tag=<location-tag>` (also `_summary=count`) | Locations that can be chosen | Location, sync |
| GET | `Location/<id>` | One location | Location |
| GET | `metadata`, `Group?_count=1&_elements=id`; POST `Group/<unknown id>/$add-members`; REST `session`, `cohortm/cohort?limit=1`, `concept/<uuid>` | The server compatibility checks (`server-compatibility.md`); the scenario `cohort-module` of the fake server refuses Group writes | Settings, Advanced |
| GET | `Group?location=<id>[&cohort-type=<uuid>]&_count=100` | Patient lists of the location (extension `http://fhir.openmrs.org/ext/group/location` holds the location id as a string) | Patient lists |
| GET | `Patient?_has:Group:member:id=<ids>&_elements=id&_count=200` | The ids of the patients of the chosen lists (ids only) | First sync, "Patient lists" |
| GET | `Patient?_id=<ids>&_count=50&_sort=_lastUpdated[&_lastUpdated=gt..]` | The patients, in batches | Sync |
| GET | `Encounter?patient=<ids>&_count=50&_sort=_lastUpdated` | Their encounters | Sync |
| GET | `Observation?patient=<ids>&_count=50&_sort=_lastUpdated` | Their observations | Sync |
| GET | `Patient?_id=<id>&_revinclude=Encounter:patient&_revinclude=Observation:patient` | One patient with the history | Live mode, opening a patient |
| GET | `Patient?name=<text>&_count=20&_elements=...`, `Patient?identifier=<text>...` | Search on the server | Live search, identify later |
| GET | `Questionnaire?_sort=_lastUpdated` and `Questionnaire?_count=100` | The server's forms (the fake answers with none: the app's own forms are used) | Sync, Settings "fetch forms" |
| POST | `/` (a `transaction` Bundle) | Upload of what was created on the device | Sync, live mode |
| PUT | `<Type>/<id>` | Update of one resource (visits, imports) | Visits, import |

Searches answer a `searchset` Bundle with `link.next` for more pages. The fake filters on `_id`,
`patient`, `_has:Group:member:id`, `name`, `identifier`, `location` (Group), `_tag` (Location) and
`_lastUpdated` (`gt`, `ge`, `lt`, `le`); it ignores `_sort`, `_summary` and other parameters.

## Single sign-on (served by the fake under `/realms/main`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/realms/main/.well-known/openid-configuration` | Discovery document (the address is in `auth_config.xml`, a build resource) |
| GET / POST | `/realms/main/protocol/openid-connect/auth` | The sign-in page (a fictional organisation page, in the language being captured); a correct form redirects to the app's redirect address with a code |
| POST | `/realms/main/protocol/openid-connect/token` | Exchanges the code (or a refresh token) for an access token. No `id_token` is returned: the app does not need one |
| GET | `/realms/main/protocol/openid-connect/userinfo`, `/certs`, `/logout` | Present for completeness |

## Control (fake only)

`/__control/lang?code=fr`, `/__control/scenario?name=empty|error|validation|slow|offline|default`,
`/__control/reset`, `/__control/log`, `/__control/uploads`, `/__control/shutdown`.

## Findings worth knowing

* The application id of `dev3-app` (`org.openmrs.android.fhir.dev3_app`) contains an underscore. The
  single sign-on redirect address is `<application id>:/oauth2redirect`, and a browser does not
  accept an underscore in a scheme, so the browser treats the redirect as a path on the provider's
  own site and the app is never reopened. For the guide's test build the redirect scheme is replaced
  by a valid one (`org.openmrs.android.fhir.guide`) through the init script in `device-config/`.
  `TODO: verify` how the released build is configured on the real identity provider.
* Items that need real infrastructure: the QR scanner (camera) and the fingerprint / face prompts.
