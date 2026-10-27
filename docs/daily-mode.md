# Daily Mode - plain board, mean puzzles

**Status:** playable slice built (steps 1-3 below). Calendar, Undo and
notifications still open.
**Owner:** TBD - **Prereq:** none - sits on the existing curved engine.

## What exists today

| Piece | Where |
| --- | --- |
| Date to puzzle, streak rules | `game/daily/DailyPuzzle.kt` (pure Kotlin) |
| Solved days + streak storage | `GamePreferences.daily`, `markDailyCleared` |
| Session switch in the engine | `CurvedGameViewModel.startDaily`, `CurvedSession` |
| The bare screen | `ui/daily/DailyScreen.kt` |
| Way in | Home - "Daily Puzzle", route `daily` |

The deviation from the plan below: the first cut reuses the journey's
generator and artwork, picking a *level seed inside the right difficulty
band* for the weekday instead of a bespoke silhouette-free generator. Same
determinism, same difficulty curve, none of the new generation code - and
the board still arrives with no name, no stars and no decoration around it.
The denser silhouette-free profile stays on the list.

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

## Telling the three modes apart

`BoardSkin` (classic / curved / daily) owns the board's *surface* and nothing
else - arrow rendering, hit-testing and layout are identical everywhere,
because those are the rules of the game and a skin has no business touching
them.

| Skin | Surface |
| --- | --- |
| Classic | Paper, under the familiar dot grid |
| Curved | Soft centre glow tinted with the arrow hue, no grid |
| Daily | **Dark slate** with a pool of light over the middle |

The daily being dark is the point: previously all three were the same
rectangle in the same colour, so the only thing telling you which mode you
were in was a word in the header. The slate keeps a trace of the active
persona colour mixed in, so theme switching is still visible.

## The shuffle: what makes a daily different

Every **15 seconds** the board re-arranges itself. The arrow count never
changes - the same set is still there, pointing new ways. A draining ring in
the header shows when it is coming and flushes red for the last five seconds.

A fixed interval rather than a random gap, on purpose: a rhythm the player can
feel is a rhythm they can play against. "Time for one more move" is a
decision; an unpredictable timer is just an interruption. Fifteen seconds is
deliberately tight - there is no time to plan a long clear-out, only to read
the board and grab whatever is free right now.

This is the whole reason to play a daily instead of another journey level.
The journey rewards reading a board carefully; the daily rewards reading it
*fast*, because the plan you are building has a shelf life.

Three rules keep it a challenge rather than a punishment:

1. **The count is a promise.** Only the arrows still on the board are
   shuffled, and none are ever added or dropped. If the search cannot find a
   solvable re-arrangement, the shuffle is *skipped* - a board that
   occasionally stays put is a smaller lie than a board that quietly loses an
   arrow.
2. **Nothing is lost.** No lives, no progress, no failure state. You lose
   your plan, and that is the entire cost.
3. **Never mid-flight.** A shuffle waits for any escaping or bumping arrow to
   land first, and taps are ignored while the board is folded.

Mechanically it is two moves stacked:

1. **A rigid transform of the whole board** (`BoardTransform` - mirror X,
   mirror Y, or a 180 rotation), applied to every arrow at once. This is the
   part that actually matters: an early version reused only the generator's
   per-arrow orientation flip, which swaps an arrowhead to the *other end of
   the same curve* - the line on screen never moved, so the shuffle read as
   almost nothing happening. A whole-board mirror/rotation physically
   relocates every line the player can see.
2. **The generator's own orientation hill-climb** on top
   (`CurvedLevelGenerator.reshuffle`), for per-arrow direction variety and to
   land on a solvable board.

The transform is free in solver terms: mirroring or rotating the *entire*
scene at once is an isometry, so every pairwise blocking relationship
between arrows survives unchanged - a solved arrangement stays solved after
being transformed, without a second search to prove it. Regenerating
genuinely new curves was also considered and rejected: the curve packer fills
as many arrows as fit a region rather than a target count, so it could not
promise the exact arrow count a shuffle has to preserve.

### Making it a movement, not a jump cut

A straight state swap looked like a rendering bug: every arrowhead teleported
to the far end of its own curve on a single frame. The fix is a fold.

`CurvedGameViewModel.playShuffle` animates a phase 1 -> 0 -> 1. The board
folds every arrow flat towards its own centre, the new arrangement is swapped
in at the pinch point when *nothing is visible*, and then it unfolds. The
stagger (`ShuffleMotion.shuffleScaleFor`) is keyed to each arrow's distance
from the board centre, so the change travels outwards as a ripple.

The stagger is deliberately squeezed into the first part of each half rather
than spread evenly: every arrow must be fully folded *before* the swap, or a
late one would show exactly the head-teleport the animation exists to hide.

## One a day, and only today's

- **Today's puzzle, once.** Solving it closes the day. The Home button goes
  disabled rather than merely changing its label, and `DailyScreen` itself
  refuses to deal a board - a gate on the button alone is a gate with a back
  door (deep links, the back stack).
- **A skipped day is gone.** There is no backfill. `DailyProgress.canPlay`
  only ever returns true for today, and `DailyStatus` names the other states
  (`SOLVED`, `EXPIRED`, `LOCKED`) so no caller has to re-derive the rule.
- **The streak survives on its own merit.** Clearing a day that does not
  directly follow the last one restarts the run at 1. Skipping is allowed; it
  just costs you the streak, which is the only thing that makes "50 in a row"
  mean anything.

## Deliberately not doing

- Leaderboards or any server. Offline-first is the whole personality.
- A separate generator. If the curved one cannot make these boards, the fix
  belongs in the curved one.
- ~~Timers. Pressure plus density is just stress.~~ **Reversed.** The shuffle
  clock above is a timer, and the original objection still stands against the
  kind it warned about: a *deadline* you can fail. This one cannot be failed.
  It reshuffles the problem instead of ending the game, which is pressure
  without stress.
- Decorative themes on this screen. The point is that there is nothing to look
  at except the problem.
