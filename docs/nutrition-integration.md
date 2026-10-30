# Nutrition app connection: boundary and status

**Status: not connected.** Cadence does not calculate calories or micronutrients, does
not contain a nutrition service, and does not claim any integration exists. The
"Nutrition app" area in the profile says **Not connected yet** and has no sync button.

This note defines the smallest boundary a future integration should use so the two
apps can share only what the user explicitly approves.

## Why there is nothing to connect yet

Cadence is deliberately offline-first. The Android manifest has no `INTERNET`
permission and the app has no networking code. A real connection needs:

1. A documented transport that both apps implement.
2. A per-field consent screen in Cadence.
3. A read path in the nutrition app.
4. Tests for denial, revocation and unavailable states.

None of those exist yet, so the UI must keep saying "Not connected yet" until they do.

## Recommended first step: a read-only summary export

The smallest safe boundary is a one-way, user-initiated export. It avoids a background
service, an account, and a network permission.

- Cadence shows a preview of exactly which fields will be included.
- The user picks fields; nothing is preselected.
- Cadence hands a small JSON document to the Android sharesheet or the system file
  picker. The user chooses the nutrition app.
- The nutrition app imports it and shows what it received.
- The export is a snapshot; there is no automatic refresh, polling or background sync.
- Revocation is simply not exporting again. Any previously exported file remains the
  user's own file, which the consent copy must say.

### Proposed snapshot fields

All fields are optional and individually toggled.

| Field | Example | Why it helps | Default |
| --- | --- | --- | --- |
| `planName` | `"Personal 6-day"` | Context for training load | Off |
| `goal` | `"MUSCLE_GAIN"` | A nutrition app may adjust guidance | Off |
| `trainingDaysPerWeek` | `6` | Rough weekly energy expenditure context | Off |
| `sessionsCompletedLast7Days` | `4` | Consistency, not calories | Off |
| `lastSessionDate` | `"2026-10-29"` | Recency | Off |
| `bodyWeightKg` | `78.6` | Only if the user explicitly includes it | Off |
| `generatedAtMs` | `1761...` | Lets the receiver label the snapshot | Always |

Cadence must not derive calorie targets, macros or micronutrients from these fields.
It also must not claim that the nutrition app's output is medical advice.

### Explicitly excluded

- Nocta sleep sessions, duration, timing and journal notes.
- Workout set notes, RPE, exercise history and personal records.
- Any identifier from another app.
- Any data the user did not select.
- Any server, analytics or third-party SDK.

## Alternative: a platform API later

If both apps need live data, prefer a platform-backed, user-visible API (for example
Health Connect records for exercise sessions) over a private backend. That requires:

- A new Health Connect write permission in Cadence, explained before requesting it.
- A matching read permission in the nutrition app.
- Both apps handling partial grants, revocation and missing providers.
- Documentation that states plainly which records are written and read.

Do not add this until the export path is working and the permission copy has been
reviewed. Health Connect is not a shortcut around consent.

## Consent rules

1. Off by default. A fresh install shares nothing.
2. Ask at the moment of sharing, not during onboarding.
3. Explain the benefit in one sentence before the system prompt or export.
4. Field-level choice, with a preview of the exact payload.
5. One-way by default; no background reads or writes.
6. Revocable at any time from Cadence's profile or settings.
7. Denial and revocation leave the rest of Cadence fully usable.
8. Do not send sleep or profile data to the nutrition app by default.

## Work required before the UI may claim a connection

- [ ] Implement the consent screen with real toggles and a payload preview.
- [ ] Implement the export handoff and verify it on a device.
- [ ] Add tests for denied consent, partial fields, revocation and unavailable apps.
- [ ] Publish the receiving contract and a sample file.
- [ ] Update this note and the in-app copy from "Not connected yet".

Until every box is ticked, the connection area stays informational and the app keeps
saying that nothing is shared.
