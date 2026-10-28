# Cadence

Cadence is an Android training app for planning and logging gym or home workouts,
reading progress honestly, and — only with explicit permission — borrowing a little
sleep context from a sleep app so the next session can be planned sensibly.

Everything below runs offline. There is no account, no server and no analytics.

## Why the name

A *cadence* is the rhythm you keep: how often you train, how hard a set is, how long
you rest. It is short, not tied to any one goal, and does not imply the app can judge
your body.

## Modules

The project is deliberately split so features can grow without the UI turning into a
pile of screens:

| Module | Contents | Depends on |
| --- | --- | --- |
| `:domain` | Models, plan building, workout math, progression engine, charts, recovery, levels, sleep state machine, JSON/backup codec. Pure Kotlin, no Android. | — |
| `:data` | SQLite persistence (workout history, routines, profile), preferences, backup file I/O. | `:domain` |
| `:integrations` | Health Connect sleep gateway and the adapter that reduces it to the domain's sleep contract. | `:domain` |
| `:app` | Compose UI, navigation, view model, theming, procedural artwork. | all of the above |

Rules that keep it modular:

- `:domain` has no Android or Compose dependency, so all the interesting logic is
  testable on the JVM.
- Only `AndroidHealthConnectGateway` touches the Health Connect SDK; everything above
  it talks to `HealthConnectGateway` / `SleepDataSource` interfaces.
- Screens read a `StateFlow` of immutable screen models from one view model and call
  intention-named methods back. No screen reaches into storage.

## Feature map

| Area | Where |
| --- | --- |
| First-run setup (Gym/Home, equipment, experience, goal, days, session length) | `ui/screens/Onboarding.kt` |
| Today's workout, one primary action, small summary | `ui/screens/Home.kt` |
| Plan templates (strength, muscle, general fitness, home) and active routine | `ui/screens/Plan.kt`, `domain/plan/PlanBuilder.kt` |
| Create and edit routines, supersets, rep ranges | `ui/screens/RoutineEditor.kt` |
| Fast set logging, warm-ups, rest timer, RPE/notes, undo | `ui/screens/Logger.kt`, `domain/workout/WorkoutLogger.kt` |
| Exercise library with search, filters, instructions, artwork | `ui/screens/Library.kt`, `domain/library/*` |
| Progression suggestions you can accept, edit or ignore | `domain/progression/ProgressionEngine.kt`, `ui/screens/ExerciseDetail.kt` |
| Plateau and deload guidance | same engine, surfaced on Progress |
| Charts: strength, consistency, volume, with windows and tap-through | `ui/components/Charts.kt`, `domain/insight/ChartData.kt`, `ui/screens/Progress.kt` |
| Front/back muscle map shaded by logged volume | `ui/art/MuscleBodyMap.kt`, `ui/screens/BodyMap.kt` |
| Optional level tiers with an explained benchmark you can hide | `domain/insight/LevelAssessor.kt` |
| Optional sleep context | `ui/screens/Sleep.kt`, `domain/sleep/SleepRepository.kt`, `docs/health-connect-nocta.md` |
| Privacy, export, delete | `ui/screens/Settings.kt`, `domain/backup/BackupCodec.kt`, `data/BackupService.kt` |

## Artwork and licensing

Nothing is downloaded or copied. The muscle map, the movement illustrations on exercise
pages and the app icon are drawn in code from primitives (`ui/art/MuscleBodyMap.kt`,
`ui/art/ExerciseArtwork.kt`, `res/drawable/ic_launcher.xml`). They are original to this
repository and carry no third-party licence obligations. Icons are hand-built Compose
`Path`s in `ui/components/CadenceIcons.kt` rather than a branded icon set.

## Build

`scripts/dev-env.sh` points `JAVA_HOME`/`ANDROID_HOME` at a project-local toolchain in
`.toolchain/` when one exists and otherwise leaves your environment alone. `.toolchain/`
is not committed, so a fresh clone needs JDK 17, Android SDK platform 36 and build-tools
installed (or that directory restored):

```bash
source scripts/dev-env.sh
./gradlew test assembleDebug
```

- `minSdk 28`, `targetSdk 36`, `compileSdk 36`
- Kotlin 2.4.21, Android Gradle Plugin 8.13.2, Compose BOM 2025.10.01
- Health Connect client 1.1.0

Dependency versions in `gradle/libs.versions.toml` are pinned deliberately: the newest
Compose (1.12.x) and `core-ktx` (1.19.x) releases require AGP 9.1 and `compileSdk 37`,
which this toolchain does not ship. Upgrading AGP is the prerequisite for moving those.

Debug builds use the application id `com.cadence.fit.debug` so they can sit beside a
release install.

## Tests

```bash
./gradlew test          # 228 tests: 200 domain, 13 data, 15 integrations
```

They cover the parts that are easy to get subtly wrong:

- workout logging: set completion, warm-up vs working sets, undo, supersets, volume
- progression: rep-range completion, deload and plateau detection, no automatic jumps
- units: kg/lb conversion and rounding, volume formatting, duration formatting
- charts and summaries: windows, bucketing, personal records, consistency counts
- levels and recovery wording, including the "not enough data" cases
- sleep: permission denied, revoked, unavailable SDK, update required, no data
- backup/export round-trip and JSON stability
- plan builder output for each goal and equipment set

## Data and privacy

- Workout history, routines and preferences are stored locally in SQLite and
  `SharedPreferences`. Nothing leaves the device.
- No account is required, and there is no network permission in the manifest.
- Export writes a plain JSON file through the system file picker. Delete removes local
  data and asks for confirmation.
- Sleep is off until you turn it on, reads only sleep sessions, and can be disconnected
  at any time. Nothing is uploaded, and no journal notes or free text are ever read.

## Sleep from Nocta

Cadence reads sleep sessions from **Android Health Connect**, not from another app's
storage. Whether that works depends on whether Nocta publishes sleep to Health Connect.
`docs/health-connect-nocta.md` explains the states Cadence handles, what it reads, and
the smallest change Nocta needs if it does not publish yet.

## What is verified, and what needs a device

Verified here: all unit tests pass, `assembleDebug` produces an installable APK, and the
whole app module compiles against the pinned dependency set.

Not verifiable in this environment (no emulator, no Health Connect provider, no Nocta
build): rendering on a physical screen, the real Health Connect permission dialog, and
reading real sleep sessions. The steps to check those are in the integration note.

## Publishing

This is a standalone local Git repository. To publish it:

```bash
cd <this directory>
git remote add origin git@github.com:<your-account>/cadence.git
git branch -M main
git push -u origin main
```

Create the empty repository on GitHub first; no credentials for a hosted remote were
available here.
