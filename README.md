# Character Practice — recall-first SRS for kanji and hanzi

**Two apps, one codebase.** The same review engine ships as:

| App | Characters | Sets | Recogniser |
| --- | --- | --- | --- |
| **Kanji Practice** (`japanese`) | 2136 Jōyō kanji + 142 kana | 9 | ML Kit `ja` |
| **Hanzi Practice** (`chinese`) | 3033 simplified Chinese characters | 7, by HSK level | ML Kit `zh-Hani-CN` |

They install side by side and keep separate databases. Everything else — the
review state machine, FSRS, the canvas, the drawing comparison, the hint popup,
undo, the settings — is shared code. Only the card data, the language tag, the
reading labels and the deck list differ, and those live in `app/src/japanese`
and `app/src/chinese`.

The sections below describe the review engine, which is common to both; the
Japanese app was the original and most of the written history refers to it.

A native Android app (Kotlin + Jetpack Compose) for practising kanji **production
from memory**. The screen shows only the meaning and readings; you draw the
character on a touch canvas; ML Kit recognises the whole character and tells you
whether you got it. FSRS v6 schedules the reviews. Everything works offline once
the recognition model has been downloaded once, and there are no ads.

It ships the **2136 Jōyō kanji** and **hiragana and katakana**, in nine card sets
you choose between in settings. Stroke-order diagrams — for kana as well as kanji
— come from KanjiVG.

It began as an implementation of `../planning/kanji_app.txt` and has been revised
after each round of hands-on testing on a real phone (see "What changed in 1.1"
through "2.0").

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

        "Give up" (in the hint popup)  ->  the rating screen, Again selected.
                                     The way out when the recogniser will not
                                     accept a drawing the user cannot improve.

        "Undo review" (top bar)  ->  the last committed review is taken back,
                                     including the queue copy a lapse appended
```

## Card sets

Nine sets, chosen in settings, all off by default except the kanji:

| Set | Cards |
| --- | --- |
| Hiragana | 71 (46 basic + 20 voiced + 5 semi-voiced) |
| Katakana | 71 |
| Kanji 1–6 | 300 each, commonest first |
| Kanji 7 | 336, the rest of the 2136 Jōyō kanji |

Sessions draw only from the selected sets. The kanji are ordered by KANJIDIC's
newspaper frequency rank, which is why set 1 opens with 日, 一, 国, 会, 人 rather
than with whatever a textbook happens to start on.

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

## Building

The Android SDK and Gradle caches already present in the parent directory are
reused, so no toolchain download is needed beyond the app's own dependencies.

```bash
export GRADLE_USER_HOME=/common/cr/programming/mobile/video_player/.buildcache/gradle-user-home
export ANDROID_USER_HOME=/common/cr/programming/mobile/video_player/.buildcache/android-user-home

# Everything: both apps, both test suites
./gradlew :fsrs:test :app:testJapaneseDebugUnitTest :app:testChineseDebugUnitTest
./gradlew :app:assembleJapaneseDebug :app:assembleChineseDebug
```

Task names are prefixed with the flavour (`Japanese`/`Chinese`), and the APKs
land in `app/build/outputs/apk/<flavour>/<buildType>/`.

`local.properties` points `sdk.dir` at
`/common/cr/programming/mobile/video_player/.buildcache/android-sdk` (compileSdk 35,
build-tools 34/35, both already installed). It is git-ignored, as usual.

To install: `adb install -r <apk>`. The release APK is ~24 MB; see "App size".

**The APKs are built for `arm64-v8a` and `armeabi-v7a` only.** ML Kit's
recognition engine is a native library and Google ships one copy per CPU
architecture; x86 and x86_64 exist for emulators, which this app is not
distributed for. That one filter is most of the size reduction. If you ever want
to run it on an emulator, add the ABI back in `app/build.gradle.kts`.

The database schema changes in 2.0, through a Room auto-migration that adds two
columns; installing over an earlier build keeps your existing progress.

## App size

The release APKs are around 24 MB, down from 42 MB. Where it goes:

| | Chinese | Japanese |
| --- | --- | --- |
| `libdigitalink.so` (arm64-v8a) | 6.9 MB | 6.9 MB |
| `libdigitalink.so` (armeabi-v7a) | 4.5 MB | 4.5 MB |
| dex (code) | 8.6 MB | 8.6 MB |
| stroke diagrams | 2.9 MB | 3.7 MB |
| resources and everything else | ~1.1 MB | ~1.1 MB |

Nearly 30 MB of the original APK was **four copies of ML Kit's native recognition
library**, one per CPU architecture. Two of those were for emulators, so
`abiFilters` drops them; that alone is a 42% cut with no runtime risk at all.

Two further reductions are available, neither taken here:

* **Drop `armeabi-v7a`** as well, worth about 4.5 MB. Every phone from roughly
  2017 on is 64-bit, so this is usually safe — check with
  `adb shell getprop ro.product.cpu.abi`. It is left in so the app cannot fail
  to install on an older device.
* **Enable R8** (`isMinifyEnabled = true`), worth perhaps 3 MB of the 8.6 MB of
  dex. It has been left off deliberately: it needs working keep rules for ML Kit
  (which parses its model manifest with Gson and reflection), Room and Hilt, and
  none of that can be verified without a device. A release that crashes on
  recognition is a worse trade than 3 MB.

Rounding the stroke path coordinates to whole units was tried as well. It is
worth about 0.5 MB and is in, but the decimal places were a small fraction of the
data; the diagrams are mostly irreducible geometry.

**The Japanese stroke SVGs are left byte-for-byte as KanjiVG publishes them.**
Editing them would make them an adaptation rather than a collection, which
changes what the CC BY-SA licence requires of the project.

## Publishing a release

```bash
./gradlew :app:assembleRelease      # -> app/build/outputs/apk/release/app-release.apk
```

### The signing key

Release builds are signed with a key that is **not in this repository**. It is
read from `keystore.properties` (git-ignored) locally, or from the environment in
CI:

| Property | Environment variable |
| --- | --- |
| `storeFile` | `KEYSTORE_FILE` |
| `storePassword` | `KEYSTORE_PASSWORD` |
| `keyAlias` | `KEY_ALIAS` |
| `keyPassword` | `KEY_PASSWORD` |

Without them the build still runs but produces an **unsigned** APK that cannot be
installed.

**Android will not install an update signed with a different key than the
installed app.** Losing the keystore means the next release cannot be installed
over the previous one — the only way out is to uninstall, which takes the review
history with it. Back up the keystore and its passwords somewhere you will still
have them in a year.

A key was generated for this project at
`../.buildcache/kanji-release.jks` (outside the repository) and its passwords are
in `keystore.properties`. If you would rather use your own, replace both.

### Cutting a release on GitHub

Add the four values above as repository secrets, then:

```bash
base64 -w0 /path/to/kanji-release.jks     # -> KEYSTORE_BASE64
git tag v2.0.0 && git push origin v2.0.0
```

`.github/workflows/release.yml` runs the tests, restores the key, builds, verifies
the APK really is signed, and attaches it to a GitHub Release. It deliberately
**fails rather than falling back** to another key when `KEYSTORE_BASE64` is
missing, because a release signed with the wrong key is worse than no release.
`.github/workflows/ci.yml` runs the tests and builds a debug APK on every push.

The release build does **not** enable R8 shrinking: it needs keep rules for ML Kit,
Room and Hilt, and a sideloaded app gains little from the size win against the
risk of a rule missing something at runtime.

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
| `.../domain/deck/DeckCatalog.kt` | The nine card sets, their ids and names. |
| `.../ui/settings/` | Card-set selection, the daily limit and the model. |
| `tools/build_assets.py` | Regenerates the card data and the bundled SVGs from their upstream sources. |
| `.github/workflows/` | CI on every push; a signed APK on every `v*` tag. |

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

147 tests, no device required (`./gradlew :fsrs:test :app:testDebugUnitTest`).

* **FSRS (17)** — replays published reference vectors from the fsrs-rs
  implementation's own test suite, then asserts the invariants the algorithm is
  supposed to have: `R(S, S) = 90%`, higher ratings produce longer intervals, a
  lapse shrinks stability, reviewing late is rewarded, and every state stays
  inside its legal range across a long simulated history, and that the vendored
  port still passes its own reference vectors.
* **Review state machine (42)** — the daily new-card cap (including that a spent
  allowance produces no new cards, and that due reviews are never capped), drawing
  persistence across failed attempts, top-1 rejection of a second-rank match, the
  hint opening without committing anything and being reopenable, Next committing
  the suggested rating without a second click, the suggestion rules (Good / Hard /
  Again, never Easy), a lapse returning to the same session and its copy being
  removed on undo, the day-wide due window, undo restoring the card, the log and
  the daily allowance, and the recognition-failure path.
* **Deck screen (12)** — the regression test for the "nothing to review" bug (a
  deck seeded *after* the screen loads must be visible without a restart), the
  allowance arithmetic, and that only cards from selected sets are counted, with
  "nothing selected" reported rather than silently ignored.
* **Bundled data and diagrams (20)** — parses all 2278 bundled SVGs and asserts
  each is well formed, has one stroke number per stroke, and belongs to a card;
  and validates the card data itself: 2136 Jōyō kanji in 7 sets, 71 + 71 kana,
  unique characters and sort keys, set ids that exist in the catalog, kana
  romanisations that are unambiguous, and example words that never leak their own
  answer.
* **Parsers, scheduling and settings (57)** — SVG path-data commands, KanjiVG
  extraction, stroke normalisation, the FSRS-to-due-date policy (8, including that
  a lapse is due immediately and a success is not), study-day boundaries across
  time zones and the 03:00 rollover, and the settings arithmetic.

## Data sources and attribution

See `ATTRIBUTION.md`, and Settings → Licences in the app itself. In short: the
stroke diagrams are KanjiVG (CC BY-SA 3.0), the meanings, readings and JLPT levels
derive from KANJIDIC2 (© EDRDG, CC BY-SA 4.0), the example words come from
`jamsinclair/open-anki-jlpt-decks` (MIT), and the scheduling follows FSRS v6 from
the open-spaced-repetition project (MIT).

**Credit is required, not optional.** Both CC BY-SA licences oblige anyone
distributing this app to pass on attribution, and CC BY-SA also obliges
adaptations to stay under the same licence. The stroke SVGs ship unmodified and
keep their own copyright headers inside the APK; the in-app licences screen is
what makes the attribution reachable without unzipping it. If you ever edit the
SVGs — recolouring them, converting them to another format — the result is an
adaptation and inherits CC BY-SA.

**The project's own code is public domain.** `LICENSE` is the
[Unlicense](https://unlicense.org/): the application source, the vendored FSRS
module, the generated kana table, the build scripts and the documentation are all
dedicated to the public domain. The Unlicense is used rather than CC0 because
Creative Commons themselves recommend against CC licences for software, because
the Unlicense is purpose-built for code and OSI-approved where CC0 is not, and
because it carries a permissive fallback licence for jurisdictions where a
public-domain dedication may not take effect.

That dedication covers **only** what was written for this project. The stroke
diagrams (CC BY-SA 3.0) and the kanji data (CC BY-SA 4.0) keep their own licences
and are not relicensed by it; `LICENSE` spells out the split, and
`ATTRIBUTION.md` has the detail.

## Distribution and privacy

Three constraints to be aware of before publishing this anywhere.

**F-Droid is not available with ML Kit.** The
[inclusion policy](https://f-droid.org/en/docs/Inclusion_Policy/) says that
"proprietary tracking or advertising libraries and analytics tools such as Google
Play Services and Firebase and Crashlytics ... are strictly forbidden in all
applications". ML Kit depends on Google Play Services and pulls in Google data
transport, so the app cannot go into the main F-Droid repository. The policy does
allow a separate repository, but that does not remove the dependency.
Replacing ML Kit with a bundled TensorFlow Lite handwriting model would be the
only route to F-Droid, and would also solve the dependency on Google's servers
below.

**The recognition model is Google's and stays on Google's servers.** Under the
ML Kit terms a model is "related software" and may not be reverse engineered or
extracted, so it can be neither bundled in the APK nor mirrored elsewhere. If
Google stops serving it, recognition in this app stops working. Digital Ink
Recognition is still actively maintained (19.0.0, last published August 2025, and
it appears in current release notes), so this is a long-term risk rather than an
imminent one — but it is not a risk the app can mitigate on its own.

**ML Kit phones home.** Handwriting never leaves the device and is never sent to
Google, but the terms say the APIs "send metrics about the performance and
utilisation of the APIs in your app to Google", and that the publisher "is
responsible for informing users of your app about Google's processing of ML Kit
metrics data as required by applicable law". This is disclosed in the in-app
licences screen under "What leaves your device", and it is what a Google Play
Data Safety declaration would need to cover.

## Not verified here

There is no KVM in this environment and no emulator or system image installed, so
the app cannot be launched here. What **is** verified is that it compiles,
packages into a debug APK, and that all 147 unit tests pass. Untested at runtime:
ML Kit model download and recognition accuracy on real handwriting (including
Compose layout at real screen sizes, Room persistence on device, and the 1 → 2
auto-migration (the SQL is derived and checked by Room at build time, but it has
never actually run against a populated database here).
