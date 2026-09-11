# Kanji Practice — recall-first kanji SRS for Android

A native Android app (Kotlin + Jetpack Compose) for practising kanji **production
from memory**. The screen shows only the meaning and readings; you draw the
character on a touch canvas; ML Kit recognises the whole character and tells you
whether you got it. FSRS v6 schedules the reviews. Everything works offline once
the recognition model has been downloaded once, and there are no ads.

It is an implementation of `../planning/kanji_app.txt`, revised after a round of
hands-on testing on a real phone (see "What changed in 1.1" and "What changed
in 1.2").

## The design in one paragraph

**Nothing on the prompt screen may hint at the shape of the character.** There is
no faded outline, no first-stroke hint, and no diagram on screen while you draw.
If you are stuck you press **I don't know**, which opens the stroke diagram as a
**popup**: you look at it, close it, and then draw. Because the popup covers the
canvas, the character has to survive a few seconds in working memory before it can
be drawn — a peek, not a reference to trace. The result screen then opens with
**Again** selected, so peeking is honest about what it cost, but you can override
it if you genuinely recalled the character after the glance.

## The review loop

```
        PROMPT  (meaning + readings, blank square canvas)
          |  Submit
          v
        CHECK  -- not the model's first choice --> PROMPT again
          |                                          (drawing kept, retry counted)
          |  recognised (top-1 only)
          v
        SUCCESS  (stroke diagram + Again / Hard / Good / Easy)
          |  Next
          v
        FSRS schedules the card, next card appears

        "I don't know"  ->  stroke-hint POPUP  ->  close  ->  draw
                            (repeatable; a hint pre-selects Again on SUCCESS)

        "Undo review" (top bar)  ->  the last committed review is taken back and
                                     you are returned to its rating screen
```

## Study limits

New cards are **not** dumped in all at once. A session is built as:

1. every card the scheduler says is due, then
2. never-seen cards, up to whatever is left of the **daily new-card allowance**
   (20/day by default, adjustable on the deck screen in steps of 5).

The allowance is derived from the review log rather than from a counter — it
counts cards whose *first ever* review happened today — which means undoing a
review automatically gives the card back.

## Building

The Android SDK and Gradle caches already present in the parent directory are
reused, so no toolchain download is needed beyond the app's own dependencies.

```bash
export GRADLE_USER_HOME=/common/cr/programming/mobile/video_player/.buildcache/gradle-user-home
export ANDROID_USER_HOME=/common/cr/programming/mobile/video_player/.buildcache/android-user-home

./gradlew :app:assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest :fsrs:test
```

`local.properties` points `sdk.dir` at
`/common/cr/programming/mobile/video_player/.buildcache/android-sdk` (compileSdk 35,
build-tools 34/35, both already installed). It is git-ignored, as usual.

To install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. The debug
APK is ~44 MB, almost all of it ML Kit's on-device recognition engine. The database
schema has not changed since 1.0, so installing over an earlier build keeps your
existing progress.

## Project layout

| Path | Purpose |
| --- | --- |
| `app/src/main/assets/kanji.json` | The bundled deck: 612 kanji, JLPT N5–N3. |
| `app/src/main/assets/kanjivg/` | KanjiVG stroke-order SVGs, one per card, named by code point. |
| `.../domain/session/StudyQueueBuilder.kt` | What a session contains: due reviews first, then capped new cards. |
| `.../domain/settings/` | The daily new-card limit and how it is persisted. |
| `.../domain/util/DayBoundary.kt` | Where one study day ends, in the user's time zone. |
| `.../domain/scheduler/` | FSRS wrapper: memory state in, next due date out. |
| `.../domain/recognition/` | ML Kit digital ink, coordinate normalisation, top-1 acceptance. |
| `.../domain/stroke/` | KanjiVG parser and SVG path-data parser. |
| `.../ui/review/` | The review state machine and screen. |
| `.../ui/components/` | `DrawingCanvas`, `StrokeOrderView`, `StrokeHintDialog`. |
| `fsrs/` | Vendored FSRS v6, a dependency-free Kotlin JVM module. |
| `tools/build_assets.py` | Regenerates `kanji.json` and the bundled SVGs from their upstream sources. |

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

## Tests

129 tests, no device required (`./gradlew :fsrs:test :app:testDebugUnitTest`).

* **FSRS (17)** — replays published reference vectors from the fsrs-rs
  implementation's own test suite, then asserts the invariants the algorithm is
  supposed to have: `R(S, S) = 90%`, higher ratings produce longer intervals, a
  lapse shrinks stability, reviewing late is rewarded, and every state stays
  inside its legal range across a long simulated history, and that the vendored
  port still passes its own reference vectors.
* **Review state machine (33)** — the daily new-card cap (including that a spent
  allowance produces no new cards, and that due reviews are never capped), drawing
  persistence across failed attempts, top-1 rejection of a second-rank match, the
  hint opening without committing anything and being reopenable, Again being
  pre-selected after a hint but overridable, undo restoring the card, the log and
  the daily allowance, and the recognition-failure path (the error names its
  cause, manual grading becomes available, and it is refused when the recogniser
  has not actually failed).
* **Deck screen (12)** — the regression test for the "nothing to review" bug (a
  deck seeded *after* the screen loads must be visible without a restart), the
  allowance arithmetic, and the settings stepper's clamps.
* **Bundled data (13)** — parses every one of the 612 bundled SVGs and asserts
  each is well formed, has one stroke number per stroke, and belongs to a card;
  and validates the deck itself.
* **Parsers, scheduling and settings (54)** — SVG path-data commands, KanjiVG
  extraction, stroke normalisation, the FSRS-to-due-date policy (8), day
  boundaries across time zones, and the settings arithmetic.

## Data sources and attribution

See `ATTRIBUTION.md`. In short: the stroke diagrams are KanjiVG (CC BY-SA 3.0),
and the meanings, readings and JLPT levels derive from KANJIDIC2
(© EDRDG, CC BY-SA 4.0) via `davidluzgouveia/kanji-data`; the example words come
from `jamsinclair/open-anki-jlpt-decks` (MIT).

## Not verified here

There is no KVM in this environment and no emulator or system image installed, so
the app cannot be launched here. What **is** verified is that it compiles,
packages into a debug APK, and that all 129 unit tests pass. Untested at runtime:
ML Kit model download and recognition accuracy on real handwriting (including
whether recognition is working again on your device — that is the whole point of
this build), Compose layout at real screen sizes, and Room persistence on device.
