## What and why

<!-- What does this change, and why? Link the issue: "Closes #123". -->

## How it was tested

<!-- Device or emulator, Android version, language, server. What you did, what you saw. -->

- [ ] `./gradlew spotlessCheck` (with `-x spotlessXml` if you have no Node.js)
- [ ] `./gradlew :core:testDebugUnitTest`
- [ ] `./gradlew :core:lintDebug`

## Checklist

- [ ] One topic; the commits start with `Feat:`, `Fix:`, `Perf:` or `Docs:`
- [ ] New strings are in all five `strings.xml` (en, fr, es, ru, ar)
- [ ] The tutorial (`ScreenTours`, `help_*` strings) is updated if a screen, setting or control changed
- [ ] The page in `docs/dev/` is updated if a documented behaviour changed
- [ ] `CHANGELOG.md` has an entry (what, why, how verified, what was not)
- [ ] No local edits committed (`app_config.xml`, `auth_config.xml`, `local.properties`, `build.gradle.kts`)

## Screenshots

<!-- For anything the user sees: before and after, and a small screen or a long language if it matters. -->
