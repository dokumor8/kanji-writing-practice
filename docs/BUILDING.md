# Building, signing and releasing

Everything needed to produce an APK, sign it so it can be installed over an
existing one, and publish it.

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
./gradlew :app:assembleJapaneseRelease :app:assembleChineseRelease
# -> app/build/outputs/apk/japanese/release/app-japanese-release.apk
# -> app/build/outputs/apk/chinese/release/app-chinese-release.apk
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

Releases are built and signed by `.github/workflows/release.yml`, so the key never
leaves your machine except as an encrypted repository secret.

**Once, to set it up.** Add four repository secrets — *Settings → Secrets and
variables → Actions → New repository secret*, one at a time:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the whole keystore file, base64 encoded (see below) |
| `KEYSTORE_PASSWORD` | the `storePassword` from `keystore.properties` |
| `KEY_ALIAS` | the `keyAlias` from `keystore.properties` |
| `KEY_PASSWORD` | the `keyPassword` from `keystore.properties` |

```bash
base64 -w0 /path/to/kanji-release.jks | wl-copy    # Linux, straight to clipboard
base64 -i /path/to/kanji-release.jks               # macOS
```

`-w0` matters: the default `base64` wraps its output at 76 columns, and a wrapped
value fails to decode in the workflow.

**Every release.** Bump `versionCode` and `versionName` in `app/build.gradle.kts`,
commit, then push a tag matching the version:

```bash
git tag v2.4.0
git push origin v2.4.0
```

The workflow runs both test suites, restores the key, builds both release APKs,
checks with `apksigner` that they really are signed, and attaches them to a GitHub
Release named after the tag. It takes about five minutes; watch it under the
*Actions* tab, and the result under *Releases*.

Two things worth knowing:

- It deliberately **fails rather than falling back** to another key when
  `KEYSTORE_BASE64` is missing, because a release signed with the wrong key
  cannot be installed over the previous one and is worse than no release at all.
- Only the tag publishes. A commit on its own triggers `ci.yml`, which runs the
  tests and uploads debug APKs as workflow artifacts, and publishes nothing.

To rebuild a release for a tag that already exists — after fixing a build, say —
use *Actions → Release → Run workflow* and give it the tag.

The release build does **not** enable R8 shrinking: it needs keep rules for ML Kit,
Room and Hilt, and a sideloaded app gains little from the size win against the
risk of a rule missing something at runtime.

## Project layout

| Path | Purpose |
| --- | --- |
| `app/src/japanese/assets/kanji.json` | The Japanese deck: 2136 Jōyō kanji, with readings and example words. |
| `app/src/japanese/assets/kana.json` | Hiragana and katakana. |
| `app/src/japanese/assets/kanjivg/` | KanjiVG stroke-order SVGs, one per card, named by code point. |
| `app/src/chinese/assets/hanzi.json` | The Chinese deck: 3033 characters, by HSK level. |
| `app/src/chinese/assets/strokes/` | Stroke medians from Make Me a Hanzi, in the same shape as the KanjiVG files. |
| `.../domain/session/StudyQueueBuilder.kt` | What a session contains: due reviews first, then capped new cards. |
| `.../domain/settings/` | The daily new-card limit, how many recogniser candidates count, and the shape threshold. |
| `.../domain/util/DayBoundary.kt` | Where one study day ends, in the user's time zone. |
| `.../domain/scheduler/` | FSRS wrapper: memory state in, next due date out. |
| `.../domain/recognition/` | ML Kit digital ink and coordinate normalisation. |
| `.../domain/stroke/` | KanjiVG parser, SVG path-data parser, and `StrokeSimilarity` — how a drawing is judged. |
| `.../ui/review/` | The review state machine and screen. |
| `.../ui/components/` | `DrawingCanvas`, `StrokeOrderView`, `StrokeHintDialog`. |
| `fsrs/` | Vendored FSRS v6, a dependency-free Kotlin JVM module. |
| `.../domain/deck/DeckCatalog.kt` | The `ScriptProfile` interface and, per flavour, the card sets. |
| `.../ui/settings/` | Card-set selection, the daily limit and the model. |
| `tools/build_assets.py` | Regenerates the card data and the bundled SVGs from their upstream sources. |
| `.github/workflows/` | CI on every push; a signed APK on every `v*` tag. |

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
