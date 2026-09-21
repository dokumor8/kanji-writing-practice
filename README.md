# Character Practice

Two Android apps for practising **writing** kanji and hanzi from memory. The
screen shows the meaning and readings; you draw the character on a touch canvas;
it tells you whether you got it right; FSRS v6 schedules the reviews.

| App | Characters | Recogniser |
| --- | --- | --- |
| **Kanji Practice** | 2136 Jōyō kanji + 142 kana | ML Kit `ja` |
| **Hanzi Practice** | 3033 simplified Chinese characters | ML Kit `zh-Hani-CN` |

It is one codebase. The two apps install side by side and keep separate
databases. You choose which card sets are in play — school grades for Japanese,
HSK levels for Chinese — and a daily limit paces new characters. Everything works
offline once the recognition model has been downloaded once. There are no ads and
no accounts.

## Get the app

[**Releases**](https://github.com/dokumor8/kanji-writing-practice/releases) —
download the APK for your app and install it. Android will ask you to allow
installing from your browser or file manager the first time.

The APKs are signed with a stable key, so a later release installs straight over
an earlier one and your review history is kept.

- On first run the app downloads the ML Kit recognition model (a few MB, once).
  Until it finishes, the drawing is not checked.
- Requirements: Android 8.0 or newer.

## Build it

Requires a JDK 17+ and the Android SDK. Point `local.properties` at the SDK:

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleJapaneseDebug     # Kanji Practice
./gradlew :app:assembleChineseDebug      # Hanzi Practice
```

Run the tests with `./gradlew :fsrs:test :app:testJapaneseDebugUnitTest :app:testChineseDebugUnitTest`.

Signing a release, publishing it, and the project layout are in
[docs/BUILDING.md](docs/BUILDING.md).

## Documentation

- [How it works](docs/DESIGN.md) — the review loop, the card sets, and how a
  drawing is judged
- [Building and releasing](docs/BUILDING.md) — signing keys, releases, app size
- [What changed, and why](docs/CHANGELOG.md)
- [Distribution and privacy](docs/DISTRIBUTION.md) — data sources, and what
  leaves the device
- [Third-party attribution](ATTRIBUTION.md)

## Licence

Project code is released under the [Unlicense](LICENSE). The bundled character
data and stroke diagrams are third-party work under share-alike licences and are
**not** covered by it — see [ATTRIBUTION.md](ATTRIBUTION.md).
