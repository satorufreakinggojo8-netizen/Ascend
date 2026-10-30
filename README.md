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
| `:data` | SQLite persistence (workout history, routines, profile, training calendar), preferences, backup file I/O. | `:domain` |
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
| Today's workout, one primary action, week strip, small summary | `ui/screens/Home.kt` |
| Training calendar: plan a week, reschedule, skip, link a session | `ui/screens/Schedule.kt`, `domain/schedule/SchedulePlanner.kt` |
| Plan templates (strength, muscle, general fitness, home) and active routine | `ui/screens/Plan.kt`, `domain/plan/PlanBuilder.kt` |
| Weekly split library, including the featured six-day split, with a preview you can edit before applying | `ui/screens/Splits.kt`, `domain/plan/SplitTemplates.kt` |
| Profile: optional age/height/weight, weight trend, current plan, export/delete coverage | `ui/screens/Profile.kt`, `domain/model/BodyProfile.kt` |
| Nutrition-app area that honestly says "Not connected yet" plus the future boundary | `ui/screens/Nutrition.kt`, `docs/nutrition-integration.md` |
| Create and edit routines, supersets, rep ranges, exercise swaps | `ui/screens/RoutineEditor.kt`, `domain/library/Substitutions.kt` |
| Fast set logging, warm-ups, rest timer, RPE/notes, undo | `ui/screens/Logger.kt`, `domain/workout/WorkoutLogger.kt` |
| Optional plate calculator with per-side loads, shortfall and next weight | `ui/components/PlateCalculatorSheet.kt`, `domain/units/PlateCalculator.kt` |
| Post-workout summary: volume, records, comparison, muscle split | `ui/screens/SessionSummary.kt`, `domain/insight/SessionSummaryBuilder.kt` |
| Exercise library with search, filters, tap-to-speak voice search, instructions, artwork | `ui/screens/Library.kt`, `ui/voice/VoiceSearch.kt`, `domain/library/*` |
| Progression suggestions you can accept, edit or ignore | `domain/progression/ProgressionEngine.kt`, `ui/screens/ExerciseDetail.kt` |
| Plateau and deload guidance | same engine, surfaced on Progress |
| Charts: strength, consistency, volume, with windows and tap-through to the week in history | `ui/components/Charts.kt`, `domain/insight/ChartData.kt`, `ui/screens/Progress.kt` |
| Muscle balance insight with an honest measured/estimate split | `ui/screens/Progress.kt`, `domain/math/WorkoutMath.kt` |
| Anatomical front/back muscle map with a text scale, hatch key and screen-reader summary | `ui/art/MuscleBodyMap.kt`, `ui/screens/BodyMap.kt` |
| Optional level tiers with an explained benchmark you can hide | `domain/insight/LevelAssessor.kt` |
| Coaches: minimal ambassador landing page, David Laid's profile, verified facts only, state-driven portraits, guidance from logged data | `ui/screens/Coaches.kt`, `domain/coach/*`, `docs/coach-assets.md` |
| Optional sleep context | `ui/screens/Sleep.kt`, `domain/sleep/SleepRepository.kt`, `docs/health-connect-nocta.md` |
| Privacy, export, delete | `ui/screens/Settings.kt`, `domain/backup/BackupCodec.kt`, `data/BackupService.kt` |

## Artwork and licensing

Nothing is downloaded or copied. The muscle map, the movement illustrations on exercise
pages and the app icon are drawn in code from primitives (`ui/art/MuscleBodyMap.kt`,
`ui/art/ExerciseArtwork.kt`, `res/drawable/ic_launcher.xml`). They are original to this
repository and carry no third-party licence obligations. Icons are hand-built Compose
`Path`s in `ui/components/CadenceIcons.kt` rather than a branded icon set.

Coach portraits are the exception: they are **not** in this repository. The Coaches
section needs four real, brand-approved photographs of David Laid, and until they are
supplied each expression renders a dashed placeholder that names the missing resource.
Cadence never substitutes a stock image, another person, or a generated expression.
The full list, framing rules and permission checklist are in `docs/coach-assets.md`.

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
./gradlew test          # 400 tests: 329 domain, 24 data, 15 integrations, 32 app
```

They cover the parts that are easy to get subtly wrong:

- workout logging: set completion, warm-up vs working sets, undo, supersets, volume
- training calendar: week generation, moving and skipping days, completed days immovable,
  session linking (including a workout finished after midnight), routine deletion
- substitutions: muscle/pattern relevance, equipment filtering, unavailable options last
- plate loading: kg and lb plate sets, bar weight, exact and unreachable targets,
  shortfall and next weight, plate caps, empty sets, unit conversion for the log
- session summary: measured volume, records only against previously logged values,
  comparison fallback, muscle shares
- home model: today's planned workout, rest days, resume, post-workout session link
- progression: rep-range completion, deload and plateau detection, no automatic jumps
- units: kg/lb conversion and rounding, converting a typed amount when the unit is
  switched mid-entry, volume formatting, duration formatting
- charts and summaries: windows, bucketing, personal records, consistency counts
- levels and recovery wording, including the "not enough data" cases
- coaches: which portrait expression each app state selects (topic reading, session in
  progress, work logged today, a ready suggestion, idle), which guidance topics appear
  only when their data exists, that pending profile details are never presented as
  claims, and that unapproved photo slots are marked missing
- sleep: permission denied, revoked, unavailable SDK, update required, no data
- backup/export round-trip and JSON stability, including optional body details and the
  current plan name, plus older backups importing with sensible defaults
- plan builder output for each goal and equipment set
- splits: the featured six-day mapping, no chest on Monday, Sunday rest, day toggles,
  renames, exact weekday scheduling, two-week planning, completed-day preservation,
  past-day exclusion, equipment filtering and future-week planning
- profile: optional fields, same-day weight replacement, clamping, trend maths, local
  codec round-trip and corrupt-preference fallback
- voice search: unavailable and permission-denied states, granted flow, editable
  transcript, empty-result fallback, error mapping and text normalisation
- body map: every group drawn on the correct side, conditioning excluded, and the
  intensity-band boundaries

## Data and privacy

- Workout history, routines, plan and preferences are stored locally in SQLite and
  `SharedPreferences`. Nothing leaves the device.
- Optional age, height and weight readings live in a local preference and are removed
  by "Delete all data". They are included in an export only because the export is the
  user's own file.
- No account is required, and there is no network permission in the manifest.
- The microphone is requested only when the library's voice-search control is tapped.
  Cadence listens only while that screen is in front of the user and stops on leave;
  speech recognition is handled by the system service. Text search always works.
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

Verified here: all 400 unit tests pass, `assembleDebug` produces an installable APK, and
the whole app module compiles against the pinned dependency set. The calendar is covered
by planner tests (generation, moving, skipping, session linking, midnight boundaries),
and the home model is tested against a fixed clock and timezone. The database schema is
unchanged in this upgrade (optional details use local preferences), so no new migration
is introduced; the existing v1→v2 calendar upgrade remains covered by its additive path.

Not verifiable in this environment (no emulator, no microphone, no Health Connect
provider, no Nocta build): rendering on a physical screen, the real microphone prompt
and on-device recognition, the Health Connect permission dialog, the SQLite upgrade on a
device that already has data, and reading real sleep sessions. The steps to check those
are in the integration notes. The David Laid portraits are still placeholders because no
approved photographs are in the repository.

## Repository

Cadence lives at the root of the `Ascend` repository:
`satorufreakinggojo8-netizen/Ascend`. `main` holds the complete app and the Android CI
workflow; work lands through feature branches and pull requests. This upgrade is on
`feature/premium-training-profile`, opened as a pull request into `main` for review.
No history is rewritten and nothing is force-pushed.

The CI workflow runs `./gradlew test assembleDebug` on JDK 17 and uploads the debug APK
as a build artifact. The four approved David Laid portraits are still missing before the
Coaches photo feature can be called complete; see `docs/coach-assets.md`.
