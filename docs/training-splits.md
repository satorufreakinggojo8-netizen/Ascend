# Training splits

Cadence ships a small split library rather than a fixed programme. A split sets the
**weekly shape**; the exercise builder fills each day from the user's equipment,
experience and goal.

## The featured personal split

The featured template is a six-training-day week with one rest day:

| Day | Session | Working focus |
| --- | --- | --- |
| Monday | Shoulders & arms | Shoulders, biceps, triceps |
| Tuesday | Legs | Quads, hamstrings, glutes, calves |
| Wednesday | Chest & back | Chest, lats, upper back, rear delts |
| Thursday | Shoulders & arms | Shoulders, biceps, triceps |
| Friday | Legs | Quads, hamstrings, glutes, calves |
| Saturday | Chest & back | Chest, lats, upper back, rear delts |
| Sunday | Rest | Recovery |

Chest is deliberately **not** scheduled on Monday. The two shoulder-and-arm days and
the two chest-and-back days rotate exercise choice, so they are not identical sessions.

## Other templates

| Template | Training days |
| --- | --- |
| Full body · 3 days | Mon, Wed, Fri |
| Upper / lower · 4 days | Mon, Tue, Thu, Fri |
| Push / pull / legs · 3 days | Mon, Wed, Fri |
| Home · 3 days | Mon, Wed, Fri (bodyweight and bands) |

Every template is a complete Monday-to-Sunday skeleton, so rest days are explicit
rather than missing.

## Preview and edits

Before applying, the preview allows:

- turning any training day into a rest day;
- turning a rest day back into its session;
- renaming a session label.

Edits are local to the preview until **Apply** is tapped. Applying:

1. Creates one new routine per training day, with goal-appropriate sets, rep ranges and
   rests, and exercises filtered by the profile's equipment and experience.
2. Replaces **planned** days in the current and next week with the split's weekdays.
3. Keeps every completed and skipped day, including its session link.
4. Keeps every pre-existing routine. It never deletes a routine or rewrites history.
5. Records the split as the current plan on the profile.

Future weeks are filled from the same split, matching routines by the day label used at
apply time. A day with no matching routine is left empty rather than guessed at.

## What a split never does

- It does not set a starting weight.
- It does not accept or apply a progression suggestion.
- It does not change an in-progress or completed session.
- It does not claim a calorie burn, body-composition change or medical benefit.

## Tests

`domain/src/test/.../plan/SplitTemplatesTest.kt` covers the featured mapping, the
no-chest-on-Monday rule, Sunday rest, day toggles, renames, exact weekday generation,
two-week planning, preservation of completed days, replacement of old planned days,
past-day exclusion, empty splits, equipment filtering and future-week planning.
