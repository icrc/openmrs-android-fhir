# Fake backend for the user guide

A small server that answers what the app asks for, with **fictional** data, so the screenshots of the guide
can be made without any real server. It has no connection to any real server and needs only Python 3.7+.

## Start and stop

```bash
python fake-backend/ctl.py up          # or: python tasks.py fake-backend-up (from docs/user-guide)
python fake-backend/ctl.py down        # or: python tasks.py fake-backend-down
python fake-backend/ctl.py status
```

The emulator reaches the host as `10.0.2.2` (the capture scripts use a host name that maps to it, see
`scripts/screenshots.py`). Cleartext http is allowed by the debug build only.

## Fictional sign-in

| | |
|---|---|
| User name | `demo.agent` |
| Password | `demo1234` |

Both are made up for the guide. The single sign-on page of the fake provider (`/realms/main`) takes the same ones.

## Data

`data.py` holds one set of personas: 12 patients (with a very long name and a patient without phone or address
as edge cases), 5 locations, 4 patient lists. Ids and dates are fixed. The names, places and list names are written
in the five languages, and `python ctl.py lang fr` makes the server answer in French (the capture script does this
for every language). `fixtures/*.json` (optional) can add Encounter and Observation resources.

## Scenarios

`python ctl.py scenario <name>`

| Scenario | What the server does |
|---|---|
| `default` | answers normally |
| `empty` | no patient lists, patients, encounters or observations |
| `error` | answers 500 to every FHIR and REST request (sign-in still works) |
| `validation` | refuses uploads with a 400 and a message |
| `slow` | waits 4 seconds before every answer |
| `offline` | drops the connection without an answer |

## What it answers

See `../_internal/backend-contract.md`. `GET /__control/log` lists the last requests and the ones that nothing
answered (empty after a normal run).

## Stubbed

Questionnaires (the app's own forms are used), the identifier source (a counter), the single sign-on provider
(an unsigned demo token that only this server accepts), the QR scanner and the fingerprint prompts (not available).
