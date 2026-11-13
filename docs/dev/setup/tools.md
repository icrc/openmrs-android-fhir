# Tools

The `tools/` folder holds small helpers. They are Python 3 scripts that use only the standard library, so
they behave the same on Windows, macOS and Linux (on Windows without `python` on the PATH write `py -3`).

| Script | What it is for |
|---|---|
| `tools/dev.py` | Day-to-day commands for building, installing and debugging on a device. |
| `tools/compact_questionnaires.py` | Rewrites bundled questionnaires into the compact form (see `forms.md`). |
| `tools/optimize_questionnaire_expressions.py` | Makes the score variables of bundled questionnaires cheap to evaluate (see `forms.md`). |

## `tools/dev.py`

```bash
python tools/dev.py <command> --help      # every command has its own options
```

| Command | What it does |
|---|---|
| `doctor` | Checks the JDK (17 or newer), the Android SDK, `local.properties`, adb, git, and the optional Node.js and Docker, then the connected devices. Each missing item comes with the fix. Run it first on a new machine, and when a build fails for no clear reason. |
| `install [--module app\|dev3-app] [--serial S] [--clean] [--no-launch]` | Builds the debug app and installs it over the one on every connected device (or `--serial`), with the APK that fits each device's processor, and opens it. The app data is kept unless `--clean` is given. An x86_64 emulator needs an APK that `app/build.gradle.kts` does not list; the command adds it for that build only, no file changes. |
| `logcat [--module app\|dev3-app] [--serial S] [--clear]` | The log of the running debug app only (found by its process id). |
| `reverse [port ...]` | `adb reverse` for ports 8080 and 8081 (or the ports given): a phone on USB reaches a server on this machine as `127.0.0.1`. |
| `hosts <host-name> <ip>` | Maps a host name to an address inside an emulator, so the browser of a single sign-on reaches a provider that announces its own host name. It lays a hosts file over `/system/etc/hosts` (a bind mount), which needs an emulator image that allows `adb root` (not the "Google Play" ones). The mapping is gone when the emulator restarts: run it again. A real phone needs the name in its network's DNS. For an image that needs a system-wide hosts file see `emulator-hosts-file.md`. |
| `adb-restart` | Restarts the adb server (a device shown as `offline` or missing) and lists the devices. |

## Questionnaire scripts

Both take the folder of the bundled questionnaires and are safe to run twice:

```bash
python tools/compact_questionnaires.py core/src/main/assets/questionnaires
python tools/optimize_questionnaire_expressions.py core/src/main/assets/questionnaires
```

Each file is expanded again and compared with the original before it is written; see the head of each
script and `forms.md` for what they change and what they leave alone.

## Adding a tool

A script goes in `tools/` only when it is used more than once by more than one person. Write it in Python
3 with the standard library, make it work on all three systems (no shell pipelines, no `grep`, `awk` or
`sed`), give it `--help`, and add it to this page. A step that is a single Gradle command belongs in
Gradle, not in a script.
