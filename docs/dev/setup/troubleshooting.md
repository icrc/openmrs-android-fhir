# Troubleshooting the development set-up

Run `python tools/dev.py doctor` first: it checks the machine and says what is missing.

| Symptom | Fix |
|---|---|
| `python` is not found (Windows) | Use `py -3 tools/dev.py ...`, or install Python and tick "Add to PATH". |
| `BindException: Address already in use` when Gradle starts (Windows) | Set `GRADLE_OPTS=-Djava.net.preferIPv4Stack=true` (`dev.py install` does it for you). |
| Gradle runs out of memory, or the machine freezes | `gradle.properties` gives the daemon 8 GB. With less than 16 GB of RAM override it for yourself in `~/.gradle/gradle.properties`: `org.gradle.jvmargs=-Xmx4g`. |
| `Unsupported class file major version` or the toolchain cannot be found | Gradle was started with a JDK older than 17 (or a very new one): point `JAVA_HOME` at a JDK 17. |
| `SDK location not found` | Create `local.properties` (step 2) or set `ANDROID_HOME`. |
| `spotlessXmlCheck` fails with "npm" or "node" not found | Install Node.js, or skip that step locally: `-x spotlessXml`. |
| `spotlessCheck` lists many XML files you did not touch | Someone ran `spotlessApply`. Revert those files with `git checkout`; format only your own. |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | The APK is for another processor than the device: use `python tools/dev.py install`, which picks it. |
| `adb` shows `offline` or no device | `python tools/dev.py adb-restart`; check the USB debugging prompt on the phone. |
| "Sign in with SSO" cannot reach the provider, or the browser never returns to the app | [`sign-in-setup.md`](../features/sign-in-setup.md), table "When something does not work". |
| Login answers 404, or the sync fails after a good login | The port or path of `<server url>` is not that of the gateway of your server. |
| The first sync downloads too much | Narrow it in `<sync>` of `app_config.xml`: `location-tag`, `cohort-type`, and choose few patient lists. |
| Shell scripts fail with `\r: command not found` (Windows) | A shell script was checked out with Windows line endings. `.gitattributes` keeps `*.sh` and `gradlew` in LF; fetch the file again with `git checkout -- <file>`. |
