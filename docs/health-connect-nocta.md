# Sleep from Nocta via Health Connect

This note records what Cadence actually does, what it needs from a sleep app, and what
could not be verified in this environment.

## The decision

Cadence reads sleep through **Android Health Connect**, and only through Health Connect.

That is the documented, user-consented Android path: the user grants one permission,
sees every read in Health Connect's own log, and can revoke it at any time without
talking to either app. Reading another app's private storage or database would be
neither documented nor consent-based, so it is not an option.

## What Cadence reads

- Record type: `SleepSessionRecord` only.
- Permission: `android.permission.health.READ_SLEEP` — one permission, nothing else.
  `WRITE_SLEEP` is never requested; Cadence does not write sleep data.
- Fields used: session start/end, and (when the writer provides them) coarse stage
  totals so "time asleep" can exclude recorded awake time. See
  `integrations/.../SleepSummaryMapper.kt`.
- Window: the last 14 days, because that is enough for a short trend
  (`SleepRepository.DEFAULT_WINDOW_DAYS`).
- Never read: journal entries, notes, free text, subjective ratings, heart rate, or any
  other record type. None of it is stored or transmitted. There is no network permission
  in the manifest at all.

The data is reduced to `SleepSummary` before it leaves `:integrations`, so the rest of
the app cannot accidentally depend on a Health Connect type.

## What the user sees in each state

`SleepConnectionStatus` is explicit so the screen can explain itself rather than fail
silently. Tested in `SleepRepositoryTest` and `HealthConnectSleepDataSourceTest`:

| State | What Cadence shows |
| --- | --- |
| Health Connect missing / device unsupported | "Not supported on this device", sync disabled, rest of the app unaffected |
| Health Connect needs an update | "Update required" with the store link, sync disabled |
| Sync off (the default) | Benefit explained, one "Turn on" action, nothing is read |
| Permission denied | Stays off, explains the app keeps working without it, offers retry |
| Permission revoked later | Refresh reports `PermissionMissing`, sync switches off, stored sleep is kept |
| Permission granted, no sessions in the window | "Connected, no sleep sessions yet" — explicitly *not* an error |
| Permission granted, sessions found | Duration, midpoint, and a gentle note; a source label if Health Connect reports one |

Recovery wording is deliberately modest (`RecoveryAdvisor`): it describes the sleep it
saw, offers "keep the plan as it is" as the default, and says it is not medical advice.
It never claims sleep caused a result. Turning sync off, or deleting local data, removes
the stored context immediately.

## One switch, not two

The settings page and the sleep page now drive the same stored opt-in
(`SleepRepository`'s enabled flag), and the settings value is mirrored from it after
every change. Before this, the settings switch only changed a preference while the sleep
page read a different flag, so the home screen could show recovery context for a
connection the sleep page reported as off. The home card is now gated on the real
repository state, which also covers permission revocation on resume.

## What Nocta needs to do

**This environment had no Nocta checkout** — no repository, source tree, APK or
documentation for Nocta was present on this machine (searched the workspace, the other
local project directories, and the home tree). So whether Nocta already publishes sleep
sessions to Health Connect **could not be inspected and is not claimed here**.

Cadence therefore implements only the reading side, and works fully without any sleep
data: the sleep page says sync is unavailable and points at what is missing instead of
pretending a connection exists.

If Nocta does not publish yet, the smallest change on the Nocta side is:

1. Declare the write permission in Nocta's manifest (it is a normal install-time
   permission on Health Connect, granted through the same UI):

   ```xml
   <uses-permission android:name="android.permission.health.WRITE_SLEEP" />
   ```

2. When a sleep session is finished and saved, insert one `SleepSessionRecord` with the
   session's start and end `Instant`s. Stage segments are optional; if Nocta has them,
   include them, otherwise a plain session window is enough for Cadence.
3. Ask for `WRITE_SLEEP` once, with the user's consent, and skip the insert quietly if
   it was denied. Nothing else in Nocta needs to change, and no Cadence code changes.

```kotlin
val record = SleepSessionRecord(
    startTime = sessionStart,           // java.time.Instant
    endTime = sessionEnd,
    title = null,                       // no free text crosses apps
    notes = null,
)
HealthConnectClient.getOrCreate(context).insertRecords(listOf(record))
```

Sessions Nocta writes become visible to Cadence after the user grants `READ_SLEEP`.
Because Health Connect tracks the data origin, the Sleep screen can name the source
instead of implying Cadence measured anything itself.

**Nocta's repository was not modified.** Nothing in this project depends on it, and no
change to it is required for Cadence to run.

## Verification steps that need a real device

These were not run here — there is no emulator, no Health Connect provider, and no Nocta
build in this environment:

1. Install the debug APK (`app/build/outputs/apk/debug/app-debug.apk`) on Android 9+.
2. Open **Sleep & recovery** and confirm the benefit text appears *before* any permission
   prompt, and that nothing is requested until sync is turned on.
3. Turn sync on, deny the Health Connect dialog, and confirm the screen stays off and the
   app remains usable.
4. Grant permission, confirm the state changes to connected, then open Health Connect's
   own app data screen and revoke `Sleep` for Cadence; reopen the screen and confirm it
   reports the revocation instead of showing stale data.
5. With a writer app (Nocta, if it publishes) log a sleep session, refresh, and check that
   duration and the source label match what the writer reported.
6. On a device without Health Connect, confirm the "not supported" state and that every
   other feature still works.

Until step 5 is done with real data, "connected" should be read as "permission granted
and Health Connect reachable", not as "Nocta sleep confirmed flowing".
