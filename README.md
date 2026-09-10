# Kanji Practice — recall-first kanji SRS for Android

A native Android app (Kotlin + Jetpack Compose) for practising kanji **production
from memory**. The screen shows only the meaning and readings; you draw the
character on a touch canvas; ML Kit recognises the whole character and tells you
whether you got it. FSRS v6 schedules the reviews. Everything works offline once
the recognition model has been downloaded once, and there are no ads.

It is an implementation of `../planning/kanji_app.txt`.

## The design in one paragraph

**Nothing on the prompt screen may hint at the shape of the character.** There is
no faded outline, no first-stroke hint, no "show me" button, and no stroke-order
animation before the answer is committed. The only way to see the diagram before
succeeding is to press **I don't know**, which fails the card, shows the diagram,
and forces you to trace the character before the card can be dismissed. A
successful drawing reveals the diagram for self-check and asks you to rate the
recall Hard / Good / Easy. "Again" can only ever be produced by the "I don't
know" path, and the success screen cannot upgrade it — that is what keeps the
schedule honest.

```
        PROMPT  (meaning + readings, blank canvas)
          |  Submit
          v
        CHECK  ---- not recognised ----> PROMPT again (drawing kept, retry counted)
          |  recognised (top-3)
          v
        SUCCESS (stroke diagram, Hard/Good/Easy)  -- Next --> FSRS, next card

        PROMPT -- "I don't know" --> RELEARN (diagram + tracing canvas)
                                        |  Submit (any non-empty trace passes)
                                        v
                                     rating locked to AGAIN, FSRS, next card
```

## Building

The Android SDK and Gradle caches already present in the parent directory are
reused, so no download of the toolchain is needed beyond the app's own
dependencies.

```bash
export GRADLE_USER_HOME=/common/cr/programming/mobile/video_player/.buildcache/gradle-user-home
export ANDROID_USER_HOME=/common/cr/programming/mobile/video_player/.buildcache/android-user-home

./gradlew :app:assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest :fsrs:test
```

`local.properties` points `sdk.dir` at
`/common/cr/programming/mobile/video_player/.buildcache/android-sdk` (compileSdk 35,
build-tools 34/35, both already installed). It is git-ignored, as usual.

To run it: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. The debug
APK is ~42 MB, almost all of it ML Kit's on-device recognition engine.

## Project layout

| Path | Purpose |
| --- | --- |
| `app/src/main/assets/kanji.json` | The bundled deck: 612 kanji, JLPT N5–N3. |
| `app/src/main/assets/kanjivg/` | KanjiVG stroke-order SVGs, one per card, named by code point. |
| `app/src/main/java/.../domain/scheduler/` | FSRS wrapper: memory state in, next due date out. |
| `app/src/main/java/.../domain/recognition/` | ML Kit digital ink, coordinate normalisation, the top-3 acceptance rule. |
| `app/src/main/java/.../domain/stroke/` | KanjiVG parser and SVG path-data parser. |
| `app/src/main/java/.../ui/review/` | The state machine and the review screen. |
| `app/src/main/java/.../ui/components/` | `DrawingCanvas` and `StrokeOrderView`. |
| `fsrs/` | Vendored FSRS v6, a dependency-free Kotlin JVM module. |
| `tools/build_assets.py` | Regenerates `kanji.json` and the bundled SVGs from their upstream sources. |

## Tests

84 tests, no device required (`./gradlew :fsrs:test :app:testDebugUnitTest`).

* **FSRS (17)** — replays published reference vectors from the fsrs-rs
  implementation's own test suite (retrievability, initial stability and
  difficulty, next difficulty including mean reversion, all three stability
  formulas), then asserts the invariants the algorithm is supposed to have:
  `R(S, S) = 90%`, higher ratings produce longer intervals, a lapse shrinks
  stability, reviewing late is rewarded, and every state stays inside its legal
  range across a long simulated history.
* **Review state machine (19)** — the whole of plan section 6 driven through
  fakes: drawing persistence across failed attempts, `retryCount` bookkeeping,
  "I don't know" clearing the canvas and locking the rating to Again, lenient
  tracing, `Next` refusing to commit without a rating, and a recognition outage
  not being counted as a failed attempt.
* **Bundled data (22)** — parses every one of the 612 bundled SVGs and asserts
  each is well formed, has one stroke number per stroke, and belongs to a card;
  and validates the deck itself (unique single-character entries, no example word
  leaking its own answer, on-yomi in katakana).
* **Parsers and helpers (26)** — SVG path-data commands including implicit
  repeats and smooth-curve reflection, KanjiVG extraction, stroke normalisation
  (aspect ratio preserved, canvas-size independent, no division by zero).

## Where this deviates from the plan, and why

Everything in the plan is implemented. These are the places where the plan could
not be followed literally, or where a decision had to be supplied.

1. **FSRS is vendored, not a dependency.** The plan suggests
   `com.github.open-spaced-repetition:fsrs-kotlin:v6.0.0` from JitPack. Those
   coordinates do not exist (404 on both JitPack and Maven Central), so FSRS-6 is
   implemented in `fsrs/` as a plain Kotlin JVM module, ported from the
   reference implementation (`open-spaced-repetition/fsrs-rs`, `src/model_v6.rs`)
   and pinned by its test vectors. It is ~200 lines, has no dependencies, and
   makes the build reproducible and offline.

2. **ML Kit 19.0.0's API differs from the plan's sample code.**
   `DigitalInkRecognizer.recognize()` returns `Task<RecognitionResult>` rather
   than `Task<List<RecognitionCandidate>>`, and the classes live in
   `com.google.mlkit.vision.digitalink.recognition` rather than
   `...digitalink`. The plan's snippet is written against 18.x.

3. **"Again" gets a short relearning step.** FSRS produces a *stability*, and
   turning that into a due date is scheduler policy that FSRS deliberately does
   not specify. A lapse here is due 10 minutes later (so the card comes back in
   the same sitting, which is what makes the forced tracing step worth doing) and
   everything else gets at least a day. Both constants are named and commented in
   `ReviewScheduler`.

4. **Card state is an enum stored as an Int.** Same column, same values as the
   plan's `state: Int`, but type-safe in Kotlin.

5. **Timestamps are UTC.** The plan types the FSRS fields as `LocalDateTime`;
   they are stored as epoch millis interpreted as UTC, and the app's `Clock` is
   UTC, so due-date arithmetic cannot shift when the device's time zone changes.

6. **The example word is stored already blanked** (`"＿前"` for 駅前), exactly as
   the plan's schema shows. The UI blanks it again defensively, so a deck that
   ships the unblanked form still cannot leak the answer. 547 of the 612 cards
   have one; 駅, for instance, does not, because the JLPT vocabulary lists used
   to pick compounds contain no N5–N3 word beginning with it. The field is
   nullable and the UI omits it.

7. **UI strings are inline in Compose, not in `strings.xml`.** `strings.xml`
   holds only `app_name`. The plan has no localisation requirement; adding one
   would be a mechanical change.

8. **There is no session size limit.** The plan does not specify one, so a
   session queues every due card and the user leaves when they are done.

9. **Small additions the plan marks optional.** The browse screen (§9.3, "useful
   for debugging") is implemented, and the stroke diagrams animate on the success
   and relearn screens (§5.4, "a polish item") — the static diagram is what
   carries the information.

## Data sources and attribution

See `ATTRIBUTION.md`. In short: the stroke diagrams are KanjiVG (CC BY-SA 3.0),
and the meanings, readings and JLPT levels derive from KANJIDIC2
(© EDRDG, CC BY-SA 4.0) via `davidluzgouveia/kanji-data`; the example words come
from `jamsinclair/open-anki-jlpt-decks` (MIT).

## Not verified here

There is no KVM in this environment and no emulator or system image installed, so
the app could not be launched. What **is** verified is that it compiles, packages
into a debug APK, and that all 84 unit tests pass. Untested at runtime: ML Kit
model download and recognition accuracy on real handwriting, Compose layout at
real screen sizes, and Room persistence on device.

If you want runtime verification, the practical option is a physical device
(`adb install`). Installing the emulator plus a system image would work, but
without `/dev/kvm` it would run under full software emulation — say the word if
you want it anyway.
