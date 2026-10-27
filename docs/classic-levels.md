# Classic mode: how levels are built

Classic mode is the grid game: a shape packed solid with bendable snake-arrows
that you tap off the board one at a time. This note covers how a level is
generated and what the newer mechanics do, because the generator is the part of
the codebase where "it works" and "it is fun" are two different questions.

## The pipeline

Four small pieces, wired together by `LevelGenerator`:

| Piece | Answers |
| --- | --- |
| `LevelDesigns` | What does the level look like? (the shape mask) |
| `LevelRecipe` | How hard does it play? (tangle, dots, locks, grid bump) |
| `DeflectorPlanner` | Where do the redirect dots go? |
| `SnakeCarver` | How is the mask cut into arrows? |
| `ColorLockPlanner` | Which arrows get welded into pairs? |

Looks and difficulty are deliberately separate knobs. Making a board bigger
just makes a bigger soup; the recipe attacks the part that makes a player
actually think.

Generation is a small contest rather than one roll: five candidate boards are
built per level and the most *chained* one wins. A level is only shipped once
`GameSolver` has played it to the end.

### What "closely chained" means here

`GameSolver` reports three numbers for a board:

| Measure | Meaning |
| --- | --- |
| `tightness` | Average lack of freedom. 1.0 = one legal move at every step. |
| `forcedRatio` | Share of steps that had exactly one legal move. |
| `longestChain` | The longest unbroken run of those forced moves. |

`challenge` folds them into one score (45/35/20) and that is what the
candidate contest ranks by. Tightness alone cannot tell a board that is
constantly forced from one that panics in bursts; the chain terms can. Both
numbers are shown in the dev bar (`tight 0.82 · chain 14`) so the curve can be
tuned by looking rather than by guessing.

## Carving: reverse-peeling, tightness first

The carver repeatedly picks a still-filled cell whose head has a clear route
off the board *through every cell that is still filled*, cuts a snake out
backwards from it, and removes it. Replaying that order is therefore a
guaranteed solution - a board can never be born dead.

Three levers decide how nasty the result is:

- **Tightness first.** The most *constrained* cell wins each peel: the one
  with the fewest ways out. Peeling the desperate cells first keeps the rest
  of the board jammed, so the player faces one or two legal moves per turn
  instead of twenty. `GameSolver` reports the resulting tightness (1.0 = a
  single legal move at every step).
- **Length tiers.** Lengths are handed out by *quota*, not dice: whichever
  tier is furthest behind its share goes next, so a board gets the promised
  mix. Four tiers - very long, long, medium and short. The short (two-cell)
  tier is not filler: a two-cell arrow plugs a corridor without taking up
  visual space, which makes it the most obstructive thing on the board per
  cell spent.
- **Tangle.** High tangle forces a bend every two cells and rejects moves that
  would let a body touch itself sideways, so long snakes come out as zig-zags
  rather than 2x2 blobs.

## Deflectors (redirect dots)

A dot sits on its own cell and sends any arrow crossing it off in a new
direction. Routes stop being straight lines, which is what makes a dense board
hard to read at a glance.

Dots need a cell of their own, so `DeflectorPlanner` *reserves* interior cells
before the carver runs rather than hunting for leftovers afterwards.

They are **permanent, not one-shot**. A one-shot dot (as in the generator this
port learned from) lets a player burn the only redirect a later arrow needed,
producing an unwinnable board. Permanent dots keep the rules monotone -
clearing an arrow can only ever help - which means no dead ends, and a greedy
solve is provably complete.

Cycles of dots are possible, and `ExitSimulator` treats an arrow that would
circle forever as blocked.

## Colour-locked pairs

Two arrows sharing a hue leave together or not at all, ignoring each other on
the way out. Cheapest difficulty in the game: the picture does not change one
pixel, but the player now has to spot two openings at once.

Pairs are safe by construction. Given a known solution order, arrows that are
*consecutive* in it get welded: the first can leave at that moment by
definition, and the second can leave the moment after with only the first in
its way - exactly the allowance a locked pair gets.

The hues come from `BoardFurniture.LockPalette` and are deliberately **not**
taken from the active theme. They used to be borrowed from the theme's four
directional arrow colours, which broke the mechanic outright in Ink: that
palette is monochrome on purpose, so every pair came out a slightly different
grey and nothing said which two belonged together. Owning four fixed hues
fixes it everywhere, and in Ink it becomes the nicest version of the feature -
the locked pairs are the only colour on an otherwise black-and-white board.

## Level kinds

`LevelRecipe.kindOf` marks every 5th level a **Boss** and every 20th a **God**
level: bigger grid, longer snakes, more tangle, more dots and more locks
(boss boards always get at least one locked pair, god boards at least two).
The kind is folded into the difficulty label the HUD shows. Levels 1-3 are
tutorial boards with no furniture at all, dots start at level 8 and locks at
level 16, so each mechanic gets introduced on its own - the reference
generator calls this the strict mechanic gate, and it is the difference
between "hard" and "baffling".

Tangle is front-loaded on purpose (0.50 by level 4, 1.0 from level 21, plus a
boss/god boost): a board that looks like a woven knot from the first real
level sets the expectation that this is a thinking game, not a tapping one.

## Reading the board

Three things exist purely so a player can follow what the rules are doing:

- **Pinch to zoom, drag to pan.** Shared with curved mode via `BoardViewport`,
  including the tap-radius compensation so a zoomed-in finger still hits what
  it is pointing at. The view re-centres on every new level.
- **Hold an arrow to preview its route.** A marching dashed line follows the
  *actual traced route*, bends and all, ending in a ring where the arrow
  leaves the board. Releasing after a preview does not fire the arrow.
- **A rejected tap explains itself.** The same dashed line appears in the
  error colour and stops dead on a cross marking the cell that blocked it.

The dots themselves are drawn as turn pads - a tile with a curved arrow
sweeping into the outgoing direction plus a short fading exit lane. The first
version was a plain chevron, which read as "something points that way" and
taught nobody anything.

## Where the rules live

`ExitSimulator` is the single definition of "can it get out?". The collision
checker, the solver, the carver and the escape animation all call it, so none
of them can drift apart on the rules - the animation literally follows the
route the engine traced, deflector corners included.

## Why a level appears instantly

Several things keep the loading spinner off the screen, in order of how much
they matter:

1. **The sweep allocates nothing.** The orientation search runs the solver
   thousands of times per level. It used to build a `HashMap`, an `ArrayList`
   per arrow and a `TreeSet` on *every call*, and walked blocker sets as
   boxed `Set<Int>`. Blockers are now stored as flat `IntArray` slot indices
   and the scratch buffers live in a reusable workspace, so a search step
   costs arithmetic instead of garbage.
2. **The climb stops when it stops climbing.** The high difficulty bands ask
   for a tightness the scoring formula can barely reach, so the hill-climb
   used to run its full budget on every late level. It now bails after
   `STALL_LIMIT` non-improving steps - but only once it already holds a
   *solvable* board, so this can never trade correctness for speed.
3. **The search aims instead of guessing.** A candidate move used to be a
   uniformly random flip, so most were rejected and progress needed thousands
   of full evaluations. `pickSlot` now samples a few arrows and flips the one
   that would gain the most blockers - an arrow with an empty lane ahead is
   free on turn one and contributes nothing, so turning it to face a crowd is
   the move most likely to tighten the board. Weighing moves costs array
   lengths, not sweeps. It deliberately only applies *once a solvable board
   exists*: while still hunting for one, "more blocked" points straight at the
   deadlocks the search is trying to escape, so plain exploration wins there.
4. **The search never builds the clear order.** It reads only whether the
   board cleared and how constrained it was, so it calls `score()` which
   returns `SolveStats` and writes the order into a reusable buffer. The
   `List<Int>` of boxed ids is materialised once, for the winner, instead of
   on every candidate.
5. **Neighbours are built before they are asked for.** While the player reads
   the current board, the levels either side are generated in the background
   and land in the same cache a real load reads from. A real load cancels the
   speculative one first, so guessing never delays the level actually being
   waited on.

On the drawing side the same instinct applies: the canvas repaints on every
frame of the hint pulse, so anything that groups or partitions the arrow list
is hoisted into a `remember` rather than run sixty times a second.
