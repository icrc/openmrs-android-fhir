# Import / Export

Not an OpenMRS feature: the app can pass the data that waits to be uploaded to another user in a
PIN-protected file. The other user imports it (also as a guest, who signs in when they sync) and
uploads it with their own account.

* **File**: a FHIR `Bundle` (`transaction`), gzipped and sealed with a 4-digit PIN (`TransferCrypto`).
  An entry is the whole resource, `POST` if it was created on the device, `PUT` if it edits a record
  the server has. `Bundle.meta` says who exported it and from which location.
* Changes to patient lists that were not sent (`patient-list-management.md`) are not in the file.
* **Import**: records are stored as local changes. A `PUT` is only stored if the device has the record;
  the others are reported, with what to do. Importing a file twice does nothing.
* **Upload**: as recorded, location included. The server notes the uploader as creator, as always. The
  one difference is the provider: OpenMRS refuses an encounter whose provider the uploading account
  cannot see ("provider_id cannot be null"). So, right before an upload and for **imported encounters
  only** (`ImportedEncounters`), the app asks `GET /provider/<uuid>`: if the account can see it the
  encounter keeps it, otherwise it is sent under the account's own provider and the original is kept
  on the participant (`original-provider` extension) until the upload; if it cannot be told (offline,
  guest not signed in) it is asked again at the next upload.
  (`OpenMRSHelper.useOwnProviderWhereTheOriginalIsNotVisible`)
* **Identifiers**: the number of an auto-generated identifier is fetched from the source of the
  identifier's own type, not of the types the uploader selected (`UploadPreparer`).
* **The uploading account must be able to see the encounter type** of each form, or the server refuses
  the encounter ("encounter type is required").
* **Live mode** uploads at once, so there is nothing left to export: turn it (and the automatic sync)
  off for a hand-over, or work offline.
