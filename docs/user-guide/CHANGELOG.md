# Changelog of the user guide

## Unreleased

- `--auth both|sso|basic` for the PDF build, the test app and the screenshots: parts of a chapter are kept or left out
  with `<!-- auth: sso -->` blocks, figure numbers are counted at build time, `check` validates the blocks.
- `tasks.py app --app-config` builds the test app from another configuration file (the local one of the app module);
  `device-config/guide-overlay.init.gradle` was missing and is back.
- English chapters rewritten as a short guide of tips: a new chapter "Things that confuse people", patient lists you
  can make and send, group sessions that start from a list, the "Action needed" message of the sync page, advanced
  settings in one paragraph. The other languages are not translated yet (`tasks.py status` shows them as stale).

## 0.1.1 (first edition)

- Chapters: about this guide, introduction, getting started (password, single sign-on and guest sign-in, offline login,
  location, patient lists), finding your way around, registering a patient, finding and opening a patient (and
  "Identify later"), recording an encounter with a form, group sessions and visits, location and patient lists, sync and
  live mode, import and export, security / several users / delete data / diagnostics, settings (including the form
  sections), troubleshooting, FAQ, glossary.
- Languages: English (source), French, Spanish, Russian, Arabic (right to left).
- 29 screenshots per language, captured from the `dev3-app` debug build against a fictional backend with the test
  configuration in `device-config/` (password and single sign-on, guest mode, the three form sections of the sample
  configuration).
- Tools: fake backend, screenshot runner and polisher, translation status and checks, PDF build.
- Not covered yet (no screenshot or text): see "Not done" in `_internal/coverage-matrix.md`.
