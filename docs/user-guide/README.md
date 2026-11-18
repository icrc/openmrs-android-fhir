# User guide of the tablet app

A guide for field agents and clinicians, in **English, French, Spanish, Russian and Arabic** (right to left),
written in Markdown with real screenshots that use fictional data, and converted to A4 PDFs. Every word of the
screens in the guide comes from the app's own `strings.xml`.

```
content/<lang>/NN-name.md      the chapters (English is the source; the other languages translate it)
screenshots/manifest.yml       what to capture, how to reach each screen, and the callouts
screenshots/raw/<lang>/        the captures as they came from the tablet (+ the callout positions); not committed,
                               `tasks.py screenshots` writes them and `tasks.py polish` redraws the images from them
screenshots/<lang>/            the polished images used by the guide
fake-backend/                  the fictional server (see its README), no real server is ever used
device-config/                 test-only app configuration and single sign-on settings, and the Gradle init script
                               that builds the test app with them (debug build only)
i18n/                          glossary.yml, translation-status.json, meta.json (titles of the PDF)
tasks.py                       the tasks below (Windows, macOS and Linux; Python 3.7+, no make or bash)
scripts/                       screenshots.py, postprocess.py, guide.py (checks and translation status),
                               smoke-test.py, make-glossary.py, gl.py (shared helpers)
pdf/                           build.mjs and guide.css (Markdown -> HTML -> Paged.js -> PDF printed by Edge/Chrome)
build/                         the PDFs: user-guide-<lang>-v<version>.pdf (not committed)
_internal/                     inventory, coverage matrix, backend contract, the prompt to update the guide
```

Commands (`python tasks.py <task> [--lang fr]`, from any folder; `python tasks.py --help` lists them all):

| Task | Command |
|---|---|
| Install the PDF tools once | `tasks.py deps` (needs Node 22 or newer; Edge or Chrome is used to print) |
| Start / stop the fake server | `tasks.py fake-backend-up` / `tasks.py fake-backend-down` |
| Build and install the test app | `tasks.py app [--auth both\|sso\|basic] [--app-config FILE] [--serial S]` |
| Capture one language / all | `tasks.py screenshots --lang fr [--auth sso]` / `tasks.py screenshots-all` |
| See what needs translating | `tasks.py status` and `tasks.py check-translations` |
| Check the guide | `tasks.py check` (after a capture also `tasks.py smoke-test`) |
| Rebuild the glossary from the app strings | `tasks.py glossary` |
| Build the PDF of one language / all | `tasks.py pdf --lang ar [--auth sso]` / `tasks.py pdf-all` |

## One guide per way of signing in: `--auth`

Organisations sign in in different ways, and the guide should only describe the one a reader has.
`--auth both` (the default) describes user name and password and single sign-on, `--auth sso` only single
sign-on, `--auth basic` only user name and password. The PDF is then `user-guide-<lang>-sso-v<version>.pdf`
(`both` keeps the old name).

* In the Markdown, a part that depends on it is wrapped in `<!-- auth: sso -->` ... `<!-- /auth -->`. The tags are
  `basic`, `sso` and `both` (a list such as `sso, both` is allowed): a block is kept in a build of that kind, and
  `both`-tagged blocks only in the `both` build. Blocks cannot be nested; markers are on their own lines (or
  inline, with the space *outside* the block). Headings in blocks need distinct ids.
* The "Figure N" numbers are counted again when the PDF is built, so a picture left out leaves no gap.
* A language whose chapters have no `auth` blocks yet (not translated since they were added) still builds with
  `--auth sso`, but says so and describes every way of signing in.
* `tasks.py check` fails on a block that is not closed, nested, or has an unknown tag. The translations must
  carry the same blocks as the English file (`tasks.py status` shows a different structure).
* The login page differs, so there is one capture per way of signing in: `login-01-page` (both),
  `login-01-page-sso`, `login-01-page-basic`. In `screenshots/manifest.yml` a step with `auth: [sso]` runs only
  when the installed app was built with that way (`tasks.py app --auth sso`, `tasks.py screenshots --auth sso`).
* `tasks.py app` builds the test app with the guide's configuration: the file of `device-config/` or the one given
  with `--app-config` (for example the local `app_config.xml` of the app module), pointed at the fictional server,
  without its `cohort-type`, with the sign-in methods of `--auth`. No project file changes.

## Words of the app: `{{string_key}}`

In the Markdown a label is written as `{{key}}`, for example `tap {{button_login}}`. The build replaces it with
the **bold** text of that string in the language of the guide, so a label always matches the screen. A key that
does not exist stops `tasks.py check`. To find a key: search the English text in
`core/src/main/res/values/strings.xml`.

## How to update this guide

1. **Change the English file first** (`content/en/...`). If a screen or a step changed, update
   `screenshots/manifest.yml` (the steps and callouts of that shot) and, if the app now asks the server for
   something new, `fake-backend/` and `_internal/backend-contract.md`.
2. **Capture again**: `tasks.py screenshots --lang en` (all shots) or only some:
   `tasks.py screenshots --lang en --ids home-01-start,patients-01-list --skip-reset`. Then do the
   same for the other languages (`tasks.py screenshots-all`). The emulator must be a 1600x2560 portrait tablet
   and the test app must be installed (`tasks.py app`).
3. **Find the stale translations**: `tasks.py status` lists, per language and chapter, what is up to date, stale,
   missing or built differently from the English (other heading ids, images, steps, labels, notes, tables).
   Translate only the stale files, and only the changed paragraphs, using `i18n/glossary.yml` (the words of the
   app are never translated by hand: they are `{{keys}}`). Keep the heading ids, the images and the number of
   steps identical to the English.
4. **Record the translation**: `tasks.py mark --lang fr 11-patients.md` (or without names for all).
5. **Check and build**: `tasks.py check` then `tasks.py pdf-all`. Open the PDFs: the cover, the contents, one chapter,
   and for Arabic that everything is mirrored.
6. Add a line to `CHANGELOG.md` of the guide.

## Rules of the guide

* Everything comes from the code. A fact that could not be confirmed is written as `<!-- TODO: verify -->`.
* Pictures use only fictional data. No real server address, account or password appears anywhere.
* Short sentences, one action per step, no jargon (explained in `99-glossary.md` when needed).
* The guide documents the `dev3-app` configuration (see `_internal/app-inventory.md`).
