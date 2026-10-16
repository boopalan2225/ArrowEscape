# Daily Mode - plain board, mean puzzles

**Status:** proposal, nothing built yet.
**Owner:** TBD · **Prereq:** none - sits on the existing curved engine.

## The idea in one line

A third mode where the *screen* is boring on purpose and the *puzzle* does all
the work: one dense arrow tangle a day, the same tangle for every player,
three lives, no decoration.

## Why it is worth doing

Classic and Curved both sell the picture - a swan, a spiral, a pretty palette.
That is the hook, but it caps difficulty: a shape has to stay recognisable, so
the generator cannot pack arrows as tightly as it would like.

A mode with no picture to protect removes that ceiling. It also costs almost
no new code, because `CurvedLevelGenerator` already separates *art* from
*tightness* (see the header comment in `DifficultyTuning.kt`) - this mode just
turns the art knob down and the tightness knob up.

And "today's puzzle" is the cheapest retention mechanic that exists. One
deterministic seed per calendar day means every player gets the same board,
which makes it shareable without any server.

## What the screen looks like

Everything that is not the board is a row of plain text:

```
  <     October 16      (gear)
  [ 93 arrows ]      [ Normal ]

            ( the board, as big as it fits )

            (hint x2)        (undo)
```

Rules for the chrome:

- **White/neutral background.** No gradients, no drifting scenery, no theme
  persona art. Themes still set the *ink* colour, nothing else.
- **No shape name, no stars, no score.** Finished is finished.
- **The board gets every pixel left over.** Minimum 70% of screen height.
- **Two tools, no more.** Hint (already exists) and Undo (new, see below).
- One celebration at the end - the same `ConfettiBurst` the other modes use.

## What makes the levels interesting

Difficulty here comes from four levers, none of which is "more arrows":

| Lever | What it means | Where it lives today |
| --- | --- | --- |
| Tightness | How few legal moves exist per turn | `DifficultyTuning.targetTightness` |
| Dependency depth | Longest forced chain before a free choice | `DependencyTracker` |
| Red herrings | Arrows that look free but are blocked one step later | new scoring in `searchOrientations` |
| Density | Arrows per unit of board | `GenerationConfig.arrowSpacing` |

Target profile for a daily: tightness near the Expert band, density higher
than any journey level (there is no silhouette to respect), and a **guaranteed
single-mistake-tolerant solution** - the solver must find at least one path
that survives one wrong tap, or the board is regenerated.

Shape of the week-long rhythm, so Monday is not Saturday:

| Day | Difficulty | Arrow count |
| --- | --- | --- |
| Mon-Tue | Easy | 30-45 |
| Wed-Thu | Normal | 50-70 |
| Fri-Sat | Hard | 80-100 |
| Sun | Expert | 100-120 |

## Determinism

`seed = yyyyMMdd` in the device's local timezone, nothing else. Same date,
same board, everywhere, offline, forever. No server, no config fetch.

The generator is already pure given a seed, so this is a one-line change to
how the seed is chosen - not a new generator.

## Progress and the calendar

A month grid of dates: cleared days filled in, today outlined, future days
greyed. Backfilling an older day is allowed (it is the same deterministic
board) but marked differently from a same-day clear, so a streak stays honest.

Stored as a set of cleared `yyyyMMdd` ints plus a current-streak counter.
Per-mode progress already exists in `GamePreferences`; daily needs its own
small record rather than `ModeProgress`, because "level 37" means nothing here.

## Undo, the one new mechanic

A daily puzzle you lose on a misclick is a daily puzzle you stop playing.
Undo returns the last escaped arrow to the board and costs nothing; it cannot
undo a *mistake* (that is what the lives are for). Two per board.

## Build order

1. **Mode plumbing.** `GameMode.DAILY`, its own progress record, mode switcher
   entry. Nothing visual.
2. **Generator profile.** `DifficultyTuning.forDaily(dayOfWeek, random)` plus
   the "survives one mistake" check in the solvability pass.
3. **Screen.** A stripped `DailyScreen` reusing `CurvedGameBoard` as-is. The
   board component already knows nothing about chrome, so this is layout only.
4. **Calendar.** Month grid, streak counter, backfill.
5. **Undo.** Engine support plus the second tool button.
6. **Notification** (optional, later) - one a day, off by default, because a
   daily puzzle nobody is reminded of is a daily puzzle nobody plays.

Steps 1-3 are the playable slice and should ship on their own.

## Deliberately not doing

- Leaderboards or any server. Offline-first is the whole personality.
- A separate generator. If the curved one cannot make these boards, the fix
  belongs in the curved one.
- Timers. Pressure plus density is just stress.
- Decorative themes on this screen. The point is that there is nothing to look
  at except the problem.
