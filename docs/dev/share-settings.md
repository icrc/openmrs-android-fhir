# Share settings (QR code)

Passes the app's settings from one device to another without typing them: **Export** shows a QR
code, **Import** scans it (or takes the code pasted), lists what would change and applies only what
the user approves.

## Where

* **Settings** page: the QR icon in the app bar (next to "Reset settings").
* **Login page**: the QR icon in the footer, next to the settings gear (a new device can be set up
  before anyone signs in).

## What is in a code

A short JSON text (`OPENMRS-SETTINGS:{"v":1,"settings":{...}}`), drawn as a QR code and also
available as text ("Copy code", and "Or paste the code" on the importing side):

* notifications, network status, token check delay, periodic sync delay, automatic sync (and what it does: upload only, or upload and download), live mode
  and "keep patients", automatic visits, theme, color vision, embedded forms only, delete data when
  logging out, language and **the server**.
* Never patients, passwords, tokens or the account, and not what belongs to a user (sharing,
  remember my location).

## Import

1. The code is checked value by value (`SettingsShare.parse`): a wrong prefix or version is refused,
   and a value outside its setting (a delay that is not one of the options, an unknown theme, an
   address that cannot be a server) is dropped.
2. It is compared with this device's settings: only the **differences** are listed, each with this
   device's value and the new one.
3. Each difference has a switch (**Take** / **Keep mine**): turning it off reverts that one, so it
   is not applied. Nothing changes until **Apply** (the button says how many).
4. The **server** starts as *Keep mine* with a warning, because taking it signs the user out and
   points the app at another host (a code from someone untrusted could otherwise redirect the
   login). On the Settings page it goes through `MainActivity.changeServer` (sign-out, restart); on
   the login page through the login page's own restart.
5. After applying, the screens are recreated to show the new values.

## Scanner

`zxing-android-embedded` (ZXing, no Google Play services needed): `SettingsScanActivity` is the
library's capture screen, free to rotate. The camera permission is asked when the scanner opens.
Draw and read use the same text, so Copy code / Paste behave exactly like the QR code.
