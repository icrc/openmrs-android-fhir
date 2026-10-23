# Sign-in setup: password, single sign-on, offline login and the app lock

How the three ways to sign in are configured and what to check when one does not work.

## What the login page offers

`app_config.xml` (one per application module):

```xml
<server url="http://10.0.2.2:8080/openmrs" />
<auth method="both" guest-mode="true" />   <!-- basic | openid | both -->
```

- `basic`: user name and password, checked against `/ws/rest/v1/session`.
- `openid`: "Sign in with SSO" (OpenID Connect through AppAuth). The settings are in
  `res/values/auth_config.xml` (see below), not in `app_config.xml`.
- `both`: both on one page.
- `guest-mode="true"`: "Continue without signing in".

A server that only accepts the single sign-on answers a correct password as wrong
(`authenticated: false`). When the login page offers SSO, the failed message says to try
"Sign in with SSO" instead.

## Single sign-on (Keycloak)

`app/src/main/res/values/auth_config.xml` (an application module's file replaces the one in `core`):

| Resource | Meaning |
|---|---|
| `auth_client_id` | Client id in Keycloak (`openmrs-fhir`). |
| `auth_authorization_scope` | `openid`. |
| `auth_redirect_uri_host` + `auth_redirect_uri_path` | Together `org.openmrs.android.fhir.app:/oauth2redirect`, the redirect the provider sends the user back to. The manifest registers the scheme. |
| `auth_discovery_uri` | The provider's `.well-known/openid-configuration`. From the Android emulator the host machine is `10.0.2.2`. |
| `auth_https_required` | `false` lets a provider on plain http be used. Development only; a release build refuses to trust anything but the system certificates. |

### Keycloak client

In the realm, create (or check) the client:

- **Client ID**: the value of `auth_client_id`.
- **Client authentication**: off (public client). The app has no secret.
- **Standard flow**: on. Proof Key for Code Exchange is used by AppAuth.
- **Valid redirect URIs**: `org.openmrs.android.fhir.app:/oauth2redirect` (exactly).
- Tokens: the access token lives a few minutes, the refresh token as long as the session may be
  idle. The app renews the access token with the refresh token before it asks the user again.

### The host name Keycloak announces

The discovery document tells the app (and the phone's browser) which address to use for the login
page, often a name that only the server's network knows (for example `sso.example.org`).
The phone must resolve it:

- **Emulator**: `tools/emulator-hosts.sh sso.example.org 192.168.1.20` (needs an image that
  allows `adb root`). Then the discovery URL may use the same name.
- **Phone on a Windows hotspot / local network**: add the name to the network's DNS or router.
  There is no hosts file on a phone without root.
- **Windows PC** (to open the same page in its browser): add `192.168.1.20 sso.example.org`
  to `C:\Windows\System32\drivers\etc\hosts`, editing it as administrator.

## Offline login and the app lock

When the user signs in, the app asks once whether to enable the offline login (fingerprint, face or
screen lock). It then:

- asks at the start of the app (splash) and, with the app already open, **when the app comes back
  after being out of sight** for the chosen time;
- asks again before deleting data (Settings, "Delete patient data" of the drawer).

Settings > Security (shown when the offline login is on):

- **Ask again after**: every time / 30 seconds (default) / 1 minute / 5 minutes.
- **Lock when the screen turns off**: asks as soon as the screen is turned back on.
- **Hide the app in recent apps**: sets `FLAG_SECURE`, so the recent-apps list and screenshots show a
  blank screen.
- **Reset offline login**: removes the saved login and the answer; the user is asked again at the
  next sign-in.

The offline login is tied to the screen lock. If the device no longer has one, Settings says so and
the lock screen offers only "Sign in with my account instead".

The flow of the start of the app is in `offline_login_flow.mmd`.

## When something does not work

| Symptom | Check |
|---|---|
| "Sign in with SSO" does nothing or says it cannot reach the provider | The discovery URL opens in the phone's browser; the host name resolves on the phone; Wi-Fi is the same network as the server. |
| The browser opens but never comes back to the app | The redirect URI in Keycloak is exactly `org.openmrs.android.fhir.app:/oauth2redirect`. |
| Password rejected with a correct password | The server may accept only the single sign-on. Use "Sign in with SSO". |
| Asked to sign in again every few minutes | The refresh token is not issued or the SSO session is shorter than expected: check the realm's SSO session idle time. |
| "Session expired" strip under the connection strip | The server no longer accepts the session. Tap "Sign in"; what was done meanwhile stays on the device and is sent after. |
| The lock screen never appears | The offline login is off (Settings > Security), the user is a guest, or the app was away for less than the chosen time. |
