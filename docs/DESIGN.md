# How it works

The reasoning behind the app: what it is trying to make you do, how a drawing is
judged, and where the implementation knowingly departs from the original plan.

The short version of the drawing rule is that a drawing is accepted only when the
recogniser names the character **and** a separate geometric comparison says the
drawing reproduces it. That second comparison is the unusual part, and
[How a drawing is judged](#how-a-drawing-is-judged) explains why it exists and
where its threshold comes from.

## The design in one paragraph

**Nothing on the prompt screen may hint at the shape of the character.** There is
no faded outline, no first-stroke hint, and no diagram on screen while you draw.
If you are stuck you press **I don't know**, which opens the stroke diagram as a
**popup**: you look at it, close it, and then draw. Because the popup covers the
canvas, the character has to survive a few seconds in working memory before it can
be drawn — a peek, not a reference to trace. The result screen then opens with
**Again** selected, so peeking is honest about what it cost, but you can override
it if you genuinely recalled the character after the glance.

The result screen also shows **your drawing next to the target**, because seeing
only the correct character is not enough to judge your own attempt.

## The review loop

```
        PROMPT  (meaning + readings, blank square canvas)
          |  Submit
          v
        CHECK  -- not the model's first choice --> PROMPT again
          |                                          (drawing kept, retry counted)
          |  recognised (top-1 only)
          v
        SUCCESS  (your drawing + stroke diagram + a suggested rating)
          |  Next            Again / Hard / Good / Easy, suggested as:
          |                  Again after a hint, Hard after a retry, Good first time
          v
        FSRS schedules the card; next card appears
          |
          +-- Again --> the card is put back into THIS session's queue

        "I don't know"  ->  stroke-hint POPUP  ->  close  ->  draw
                            (repeatable; a hint suggests Again on SUCCESS)

        CHECK is two questions: is the target among the recogniser's top few
        candidates, AND does the drawing match the reference diagram? A correct
        drawing can lose the first; a wrong one can win it.

        "Give up" (in the hint popup)  ->  the rating screen, Again selected.
                                     The way out when the recogniser will not
                                     accept a drawing the user cannot improve.

        "Draw it again" (rating screen)  ->  back to the canvas with the diagram
                                     showing, for deliberate practice. The next
                                     rating is suggested Again, since the answer
                                     was on screen.

        "Undo review" (top bar)  ->  the last committed review is taken back,
                                     including the queue copy a lapse appended
```

## Card sets

Nine sets, chosen in settings, all off by default except the kanji:

| Set | Cards |
| --- | --- |
| Hiragana | 71 (46 basic + 20 voiced + 5 semi-voiced) |
| Katakana | 71 |
| Grade 1–6 | 80, 160, 200, 200, 185, 181 — the primary-school set |
| Secondary 1–4 | 283, 283, 283, 281 — the secondary grade, in frequency bands |

Sessions draw only from the selected sets. The Japanese sets are **school
grades**, the order Japanese children learn them, with frequency ordering the
characters *within* each grade. Chunks of a frequency list felt arbitrary --
frequency puts 議 before 義 -- whereas grade order is a progression a learner can
recognise.

Changing the sets is a re-seed, and a re-seed does three jobs against one rule:
**progress is never rewritten, everything else is.** Cards that are not there yet
are inserted with a plain INSERT that skips existing rows, so a re-seed can never
overwrite FSRS state. Set membership, order, and the card's own text are written
separately, so they *are* refreshed on rows that already exist. Keeping the two
halves apart is the whole point: skipping is right for progress and silently wrong
for text, and 2.3.1 exists because 2.3.0 added two example-word columns that
stayed null on every card an existing install already had while a fresh install
looked perfect. `CardContentRefreshTest` fails if a column of the card is left
out of all three jobs.

## How a drawing is judged

Two separate questions, and the app asks both.

**Did the recogniser name the character?** ML Kit answers this, and how many of
its candidates count is a setting (five by default). A correct drawing can lose
to a similar character, so one candidate is too few.

**Did the drawing actually reproduce it?** The recogniser cannot answer this: it
picks the nearest character, which it will happily do from a drawing with strokes
missing. So the drawing is also compared against the bundled stroke diagram, and
scored on four separate things:

| Question | What it catches |
| --- | --- |
| **shape** — is this the right stroke? | a straight line drawn for an L-shaped stroke |
| **length** — is it as long as it should be? | a stroke that stops before it reaches |
| **position** — is it in the right place? | 大 drawn as 犬; 又 sitting too low |
| **topology** — do the strokes that cross still cross? | 羊's tail stopping at the bar instead of running through it |

Each is measured where it means something. Shape removes the stroke's own position
and size first, so a correctly drawn stroke in the wrong place still scores as the
right shape and pays only on position. Position is deliberately the most forgiving
term. Topology is worth only 10%: crossings are derived geometry, and a millimetre
of finger noise can invent one, so it registers without being allowed to decide.

### Why it is built this way

The first version scored a stroke purely by how close its points were to the
reference's, in a frame normalised from the drawing's own bounding box. That made
one mistake in one stroke move the frame, and therefore change every *other*
stroke's score. Two failures were reported from real use:

- **取 with 又 a little low** scored 45%. Two of eight strokes fell outside
  tolerance, and the worst-stroke weighting did the rest — for a drawing that was
  correct in shape.
- **二 with the bottom bar drawn short** scored 26%. Shortening the bar shrank the
  bounding box width that everything was measured against, so a gap of 0.442 of
  the drawing area — the reference is 0.440 — was measured as 0.82 against a
  reference of 0.57, and a top bar of correct length was measured as too long. One
  error in one stroke became four apparent errors in the other.

So the frame is no longer computed from a formula. A formula has to be fed
something, and whatever it is fed moves when a single stroke changes. Instead the
scale is chosen by search: a range is tried and whichever makes the drawing look
most like the reference is kept. One wrong stroke can then only make the frame
worse for itself.

Two other things stop the measurement inventing errors. The drawing is
**smoothed before it is resampled** — a raw finger trace zigzags, a zigzag is
longer than the line it was meant to be, and resampling by arc length spreads its
samples according to that inflated length, scoring a correct stroke as a length
error. And shape is normalised per stroke **with a floor**, so finger noise on a
two-unit dot is not magnified into a completely different shape.

### Where the threshold comes from

`StrokeSimilarityCalibrationTest` scores synthetic perturbations of real reference
diagrams, so the number in settings is measured rather than chosen. Every row
below is that test's output.

| Drawing | Score |
| --- | --- |
| exact copy | 1.00 |
| a correct 場 with a small wobble | 0.88 |
| 取 with 又 moved low | 0.93 |
| 二 with the bottom bar 31% short | 0.89 |
| 羊's tail stopping before the bar it should cross | 0.83 |
| a stroke displaced across the character | 0.76 |
| a different character (月 for 日) | 0.64 |
| drawing a bent stroke straight (こ) | 0.53 |
| one stroke missing from 曜 (18 strokes) | 0.41 |
| 主 drawn in 玉's stroke order | 0.35 |
| 目 drawn as 日 | 0.31 |
| one stroke missing from 日 (4 strokes) | 0.11 |

Correct drawings land at 0.88 or above even with a wobble, and every measured way
of getting one wrong lands at 0.76 or below, so 70% sits in the gap. Both this and
the candidate count are adjustable in settings; at 0% the shape check is off.

Two limits are worth stating. KanjiVG carries no stroke width, so this cannot tell
a confident stroke from a tentative one. And it says nothing about where on the
canvas you wrote or how large: drawing in the corner at a third of the size is
free, and a test checks that.

## Study limits

New cards are **not** dumped in all at once. A session is built as:

1. every card due before the **end of the current study day**, then
2. never-seen cards from the selected sets, up to whatever is left of the
   **daily new-card allowance** (20/day by default, adjustable in settings).

A study day runs from **03:00 to 03:00**, so a session started at 00:30 still
belongs to the previous day rather than starting a fresh one mid-sitting. Cards
that become due later in the day are already waiting at breakfast.

The allowance is derived from the review log rather than from a counter — it
counts cards whose *first ever* review happened today — which means undoing a
review automatically gives the card back.

## Where this deviates from the plan, and why

Everything in the plan is implemented, except where testing changed the design
(items 3–5 above) or where the plan could not be followed literally.

1. **FSRS is vendored, not a dependency.** The plan suggests
   `com.github.open-spaced-repetition:fsrs-kotlin:v6.0.0` from JitPack. Those
   coordinates do not exist (404 on both JitPack and Maven Central), so FSRS-6 is
   implemented in `fsrs/` as a plain Kotlin JVM module, ported from the reference
   implementation (`open-spaced-repetition/fsrs-rs`, `src/model_v6.rs`) and pinned
   by its test vectors.

2. **ML Kit 19.0.0's API differs from the plan's sample code.**
   `DigitalInkRecognizer.recognize()` returns `Task<RecognitionResult>` rather
   than `Task<List<RecognitionCandidate>>`, and the classes live in
   `com.google.mlkit.vision.digitalink.recognition` rather than `...digitalink`.

3. **"Again" gets a short relearning step.** FSRS produces a *stability*; turning
   that into a due date is scheduler policy FSRS deliberately does not specify. A
   lapse is due 10 minutes later and everything else gets at least a day. Both
   constants are named and commented in `ReviewScheduler`.

4. **Full retention is not enforced by the recogniser alone.** "Again is locked"
   from the plan's section 12 is now "Again is pre-selected after a hint", by
   request. The rating is the user's call.

5. **Card state is an enum stored as an Int**, with the numbering pinned by a test
   because it is part of the persistence contract.

6. **Timestamps are UTC**, and the study-day boundary is the user's local
   midnight. `DayBoundary` translates between the two.

7. **The example word is stored already blanked** (`"＿前"` for 駅前), as the plan's
   schema shows. The UI blanks it again defensively. 547 of the 612 cards have
   one; 駅 does not, because the JLPT vocabulary lists used to pick compounds
   contain no N5–N3 word beginning with it.

8. **UI strings are inline in Compose**, not in `strings.xml`; `strings.xml` holds
   only `app_name`.

9. **The browse screen and diagram animation are implemented** — both were marked
   optional or low priority in the plan.

## Not verified here

There is no KVM in this environment and no emulator or system image installed, so
the app cannot be launched here. What **is** verified is that it compiles,
packages into a debug APK, and that all 147 unit tests pass. Untested at runtime:
ML Kit model download and recognition accuracy on real handwriting (including
Compose layout at real screen sizes, Room persistence on device, and the 1 → 2
auto-migration (the SQL is derived and checked by Room at build time, but it has
never actually run against a populated database here).
