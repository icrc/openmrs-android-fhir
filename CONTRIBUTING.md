# Contributing

Thank you for helping. This page is about how to send a change; the rest is in [`docs/`](docs/README.md).

1. **Set up** your machine and run the app: [`docs/dev/setup/getting-started.md`](docs/dev/setup/getting-started.md).
   `python tools/dev.py doctor` checks it.
2. **Understand the area** you change: [`docs/dev/architecture.md`](docs/dev/architecture.md), then the page for the
   feature in `docs/dev/features/`. Several features behave differently from a standard OpenMRS client on purpose.
3. **Open an issue first** for anything bigger than a fix, and say what you plan, so the work is not wasted.
4. **Branch from `main`** in your fork: `feat/<topic>`, `fix/<topic>` or `docs/<topic>`.
5. **Follow the rules of the house** in [`architecture.md`](docs/dev/architecture.md#rules-of-the-house): strings in
   all five languages, the in-app tutorial, the database version, settings, the changelog, small screens. The
   pull request template repeats them as a checklist.
6. **Check** with the commands of step 6 of the getting-started page (format, `spotlessCheck`, unit tests, lint).
   These are what the CI runs.
7. **Commit**: one topic per commit and per pull request; the subject starts with `Feat:`, `Fix:`, `Perf:` or
   `Docs:` followed by a plain sentence, as in `git log`. Do not commit the edits of your machine
   (`app_config.xml`, `auth_config.xml`, `local.properties`, a temporary `build.gradle.kts`).
8. **Open the pull request** and fill in the template: what and why, how you tested it (device, language,
   server), screenshots for anything a user sees. Answer review comments with new commits.

## Reporting a problem

Use the issue forms. Say the app version, the device and Android version, what you did, what you expected and
what happened. Never paste passwords, tokens or patient data.

## Security

Do not report a vulnerability in a public issue. Write to the maintainers privately (a GitHub security
advisory on the repository).

## License

BSD 3-Clause, see [`LICENSE`](LICENSE). New `.kt` files carry the header of `license-header.txt`.
