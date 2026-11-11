# Documentation

| You want to | Read |
|---|---|
| Build and run the app on your machine | [`dev/setup/getting-started.md`](dev/setup/getting-started.md), then [`dev/setup/troubleshooting.md`](dev/setup/troubleshooting.md) if it fails |
| Send a change | [`../CONTRIBUTING.md`](../CONTRIBUTING.md) |
| Understand how the code is organised, and the rules every change follows | [`dev/architecture.md`](dev/architecture.md), [`dev/ui.md`](dev/ui.md) |
| Configure an app (server, login, forms) | [`dev/configuration.md`](dev/configuration.md) |
| Change a feature | The page for it in [`dev/features/`](dev/features/README.md) |
| Add or change a screen, setting or control | [`dev/first-run-tutorial.md`](dev/first-run-tutorial.md): the in-app tutorial has to follow |
| Use the helper scripts | [`dev/setup/tools.md`](dev/setup/tools.md) |
| Publish a version | [`dev/releasing.md`](dev/releasing.md) |
| Know what is missing or why something was not done | [`dev/decisions/`](dev/decisions/): [`known-gaps.md`](dev/decisions/known-gaps.md), [`database-encryption.md`](dev/decisions/database-encryption.md) |
| Write or update the user guide (five languages, PDFs) | [`user-guide/README.md`](user-guide/README.md) |

```
docs/
  dev/
    setup/        getting-started, troubleshooting, tools, emulator-hosts-file
    features/     one page per behaviour that differs from a standard OpenMRS client
    decisions/    known gaps and decisions not to do something
    architecture.md  ui.md  configuration.md  first-run-tutorial.md  releasing.md
  user-guide/     the guide for the people who use the app (not developer documentation)
```

Pages refer to each other by file name (`sign-in-setup.md`); names are unique.

## Writing here

* Say what differs from standard OpenMRS or the SDK, and why. Do not explain what they do.
* Name the code (`Class`, `file`) instead of copying it, so the page does not go stale.
* No server addresses, accounts, passwords or machine names: use a placeholder such as `<server-ip>`.
* No history ("it used to...") and no test narratives. How a change was checked goes in its
  `CHANGELOG.md` entry; a limit that stays goes in the page it belongs to.
* A gap that nothing in the page explains goes in `decisions/known-gaps.md`.
* A change that adds, moves or removes something a user sees also updates the tutorial
  (`dev/first-run-tutorial.md`).
* A change to a documented behaviour updates its page in the same change. A new page is added to the index
  of its folder; a removed one is removed from it.
