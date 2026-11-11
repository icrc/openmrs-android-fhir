# Retrying requests lost on the way

On a bad network the sync can fail with `Broken pipe`, `Connection reset`, `unexpected end of stream`
or a timeout. These are accidents of the network, not answers of the server, and sending the request
again usually works.

## Why it happens in the data source

The FHIR library (engine 1.2.0) counts **every** failure it hears of against the sync, and WorkManager
repeats a sync with one failure from the start (3 times, 30 s apart). Retrying later recovers the data
but not the status, so everything is downloaded again (about 5 minutes for the first download). The
request is therefore sent again *inside* the call, before the library hears of the failure.

## How

* `RetryingFhirSyncWorker` (Java) is the base of `BaseFhirSyncWorker`. It overrides
  `FhirSyncWorker.getDataSource()` and wraps the library's data source in `RetryingRequests`. It is
  Java because the library declares that method and `DataSource` `internal`, which Kotlin outside it
  cannot override, while the compiled methods are public. If a library upgrade changes that, the file
  stops compiling instead of the retries silently going away.
* `RetryingRequests` sends a request again up to 3 times, after 2, 5 and 10 s, and at most 20 times
  in one sync, so a server that is down does not hold the sync for every request. A cancelled sync is
  never retried.
* `SyncRetries` decides what is worth sending again:
  * **downloads** (read only): any request lost on the way, and the answers 408, 429, 500, 502, 503,
    504;
  * **uploads**: only failures that prove the server never got the request (`Broken pipe`, cannot
    connect, unknown host). After a reset in the middle of an exchange the server may have done the
    work, and a second POST could create it twice; the sync fails as before and the next one sends it.

Each retry is logged, so a bad network can be told from the diagnostics log. Tests: `SyncRetriesTest`,
`RetryingRequestsTest`.
