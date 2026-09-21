# What changed, and why

A record of what each round of hands-on testing turned up. These are kept because
most of the entries are bugs that were invisible in review and obvious in use, and
the reasoning is usually more useful than the fix.

## What changed in 2.4.1

**A crossing no longer has to be a crossing.** The topology term asked for a
strict through-crossing, which is not something a fingertip on a phone can
guarantee. It flipped on and off between attempts, so 選 scored *worse* with less
wobble — 0.82 with a light wobble against 0.86 with a heavier one. A pair of
strokes now counts as still crossing if they cross **or** come within a hair of
each other. 選 with a light wobble went from 0.82 to 0.91, and 羊 with a tail that
stops well short still scores 0.72.

**The default shape threshold went from 70% to 50%.** It was rejecting correct
drawings: 選 took more than thirty attempts at 60%. Correct drawings land at 0.86
or above even on a fifteen-stroke character with a phone-sized wobble, and a
genuinely wrong one — wrong order, a missing stroke, a different character — lands
at 0.41 or below. The threshold exists to catch the second group, not to grade
penmanship, so it now sits well inside the gap.

## What changed in 2.4

**The drawing check was rebuilt, because it was failing correct drawings.**
[How a drawing is judged](../docs/DESIGN.md#how-a-drawing-is-judged) has the design;
this is the history.

It reported 45% for 取 with 又 a little low, 26% for 二 with the bottom bar drawn
short, and would not accept 場 at all. The common cause was that a stroke was
scored by how close its points were to the reference's, in a frame normalised from
the drawing's own bounding box — so one mistake in one stroke moved the frame and
changed every other stroke's score.

The replacement scores shape, length, position and crossings separately, chooses
the frame by search rather than by formula, smooths the drawing before resampling
it, and floors the shape normalisation so finger noise on a short stroke is not
magnified. The two failing drawings are now calibration fixtures: 取 with 又 moved
scores 0.93 and 二 with the bottom bar 31% short scores 0.89, while a missing
stroke still scores 0.11 and a different character 0.36.

**The README was split up.** It had grown to 600 lines of design notes. `README.md`
is now what the app is, how to get it and how to build it; the reasoning moved to
`docs/`.

## What changed in 1.1


Driven by testing the 1.0 build on a phone.

1. **"There was nothing to review" at first, then everything appeared.**
   Root cause found: new cards were seeded with `due = now`, and the deck screen
   queried them against a `now` captured when the ViewModel was created. Cards
   seeded a moment later were therefore born slightly in the future and stayed
   invisible until the app was restarted. Fixed twice over — new cards are no
   longer part of the "due" query at all (they enter through the allowance), and
   the counts are recomputed whenever the screen resumes.

2. **A daily new-card limit.** The plan did not mention one, and without it a
   612-card deck presents itself in full. Default 20/day, adjustable on the deck
   screen. See "Study limits" above.

3. **The hint is a popup, not a tracing screen.** The RELEARN screen is gone. The
   prompt and the learning step are now the same surface, as requested. Pressing
   "I don't know" opens an animated, stroke-numbered diagram over the canvas;
   closing it (button or tap outside) returns you to your drawing. It can be
   reopened as often as you like, and it no longer fails the card by itself —
   instead the result screen pre-selects **Again** and says why, which you may
   override.

4. **Only the recogniser's first choice counts.** Accepting the top three was
   letting 玉 pass for 主, because near-identical characters are ranked
   adjacently. Accepting a wrong character as correct is worse than occasionally
   asking the user to draw again.

5. **"Again" is now a rating on the result screen**, as the escape hatch for the
   false positives that still get through.

6. **Undo review.** A top-bar action that deletes the last review-log row,
   restores the card to its stored state, and returns you to that card's rating
   screen with the rating cleared. One level, like Anki. It also hands the card
   back to the daily new-card allowance.

7. **The model-download button no longer flashes on launch.** The screen used to
   render `Unknown` as "not downloaded" and offer a Download button until an
   asynchronous check came back. It now distinguishes *unchecked* from *checked
   and missing*, auto-checks on start and on resume, and only ever offers a
   download once a check has actually said the model is absent.

8. **The drawing canvas is a true square**, so the geometry the recogniser sees
   is consistent across devices.

## What changed in 1.2

1.1 broke recognition on a real device: the deck screen reported the model as
ready, but every Submit produced "Recognition is unavailable".

1. **Reverted the `RecognitionContext`/`WritingArea` change.** 1.1 passed a
   writing-area hint to `DigitalInkRecognizer.recognize(ink, context)`. That is
   the documented way to give the model more to work with, but it is the only
   difference between the working 1.0 call and the failing 1.1 one, so the plain
   `recognize(ink)` overload is back. Real touch timestamps were reverted with
   it, for the same reason: both were changes to a code path that worked, made
   without any way to measure them. The square canvas stays — that one is only
   about layout.

2. **Recognition failures now say what went wrong.** The message used to be a
   fixed string, which is not something a user (or a bug report) can act on. It
   now carries the underlying exception message, truncated to one line, and the
   full stack trace goes to logcat under the `ReviewViewModel` tag.

3. **"Rate this card myself".** If the recogniser throws, the prompt offers a
   self-grading path to the result screen. It is gated on the recogniser having
   actually failed — enforced in the state machine, not just by which button is
   rendered — so it cannot be used to skip drawing. Without it, a broken ML Kit
   install makes the entire deck unusable.

4. **"Reinstall" for the handwriting model.** ML Kit models live in app-private
   storage, so a model that reports as present but does not work could previously
   only be cleared by wiping the app and the review history with it. The deck
   screen now offers a confirmed delete-and-redownload.

### Recovering from a bad model install

The downloaded model is inside the app's private data directory
(`/data/data/com.example.kanjipractice/`), which is not browsable without root.
In order of preference:

* **Reinstall** on the deck screen — deletes and re-downloads the model, keeps
  your review history.
* `adb shell pm clear com.example.kanjipractice` — **wipes all progress too**.
* To find the files: `adb shell run-as com.example.kanjipractice ls -R files`
  (the app is debuggable).
* For diagnosis: `adb logcat -s ReviewViewModel MlKitRecognition`.

## What changed in 1.3

1. **Next did nothing after a hint.** The suggested rating was painted on the
   result screen from one field while `next()` read a second, private field that
   only a button press ever set. The highlight was therefore cosmetic: the user
   had to click the already-highlighted button before Next would respond. The
   displayed rating is now the only rating, so Next works immediately whatever
   suggested it.

2. **The result screen suggests a rating.** It used to suggest Again after a hint
   and nothing otherwise. Now: **Again** if the hint was opened, **Hard** if the
   drawing took more than one attempt, **Good** if it was right first time.
   **Easy is never suggested** — only the user can say a card was effortless. The
   suggestion is always overridable, and manual grading (after a recogniser
   failure) still suggests nothing, because nothing verified the drawing.

3. **A study day is one queue.** Two changes that together mean "reviews for
   today" are available in one sitting:
   * A session gathers cards due before the **end of the current study day**, not
     before the current instant, so a card due this evening is available in the
     morning. The deck screen counts the same window, so its number is what a
     session will actually contain.
   * A lapse is due **immediately** rather than ten minutes later, and the session
     re-appends it to its own queue. Failing a card sends it to the back of the
     current sitting instead of parking it in the future; because the queue holds
     the *updated* card, the next attempt starts from the memory state that lapse
     produced. Undoing such a review removes the copy it appended.

4. **The study day rolls over at 03:00, not midnight.** Studying at 00:30 is, to
   the person doing it, still the previous day — previously it started a fresh day
   of new cards and reset the daily allowance mid-session.

5. **The result screen shows your drawing next to the target.** Seeing only the
   correct character is not enough to judge your own attempt — and for the
   "I drew the wrong character and it was accepted" case it is the whole point.

## What changed in 2.0

1. **The whole Jōyō set, plus kana.** 2136 kanji where there were 612, and 71
   hiragana + 71 katakana (basic, voiced and semi-voiced). Kana prompts are
   romanisation. The two ambiguous pairs are spelled `di`/`du` rather than
   `ji`/`zu` so that a prompt has exactly one answer.

2. **Card sets, and a settings screen.** Nine sets: hiragana, katakana, and the
   kanji split into six sets of 300 with a seventh of 336. Kanji are ordered by
   frequency, so the commonest arrive first. Settings holds the set selection,
   the daily new-card limit (moved off the deck screen) and the handwriting model
   (also moved), which is where setup belongs.

3. **An upgrade keeps your progress.** Set membership is stored on the card but
   assigned separately from insertion, so re-seeding uses a plain INSERT that
   skips existing rows and can never overwrite FSRS state. The schema change is a
   Room auto-migration adding two nullable columns. Every one of the 612 cards in
   the old deck is a Jōyō kanji, so nothing studied became unreachable — they
   simply move into the new sets.

4. **Rating buttons that fit.** Four labels across a phone clipped "Again" to
   "Agai"; they are now two rows of two.

5. **Less explaining.** The app had a habit of narrating itself — "Daily
   new-card limit reached. This is what keeps the deck from burying you." Those
   asides are gone. What is left is either a fact ("Hint used"), an instruction
   ("Not quite - try again") or an error with its cause.

6. **A signed release build and CI.** `assembleRelease` produces a signed APK,
   and tagging a commit publishes it. See "Publishing a release".
