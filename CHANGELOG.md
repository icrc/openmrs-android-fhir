# Changelog (audit & cleanup branch `chore/audit-and-cleanup`)

Each entry: what changed, why, and how it was verified.

## Baseline (no code changes yet)

Recorded on `main` @ `cdd4117`, Windows, JDK 17 toolchain, no emulator.

| Gate | Result |
|---|---|
| `spotlessCheck` (Kotlin) | Pass |
| `spotlessCheck` (Gradle scripts) | Fail — `app/build.gradle.kts` uncommitted local edit (`include("x86_64","arm64-v8a",…)`) |
| `spotlessCheck` (XML) | Cannot run locally — Node/npm missing |
| `./gradlew test` | Pass — 7 suites, 16 tests, 0 failures |
| `./gradlew build -x test --continue` | Compiles/assembles; only spotless tasks fail (above) — 7m14s |

## Unreleased

- docs: add `docs/audit.md` (Phase 1 audit, ranked findings, plan).
