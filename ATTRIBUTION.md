# Third-party data and attribution

This app bundles data produced by others. The licences are share-alike, so any
redistribution of this repository or of an APK built from it must keep this
attribution.

## KanjiVG — stroke-order diagrams

`app/src/main/assets/kanjivg/*.svg` are unmodified files from
[KanjiVG](https://kanjivg.tagaini.net/), © 2009–2024 Ulrich Apel and
contributors, licensed under
[Creative Commons Attribution-ShareAlike 3.0](https://creativecommons.org/licenses/by-sa/3.0/).
Each file carries its own copyright header. They are redistributed here under the
same licence.

KanjiVG covers kana as well as kanji, which is why the hiragana and katakana sets
get the same stroke-order diagrams as the kanji.

## Kanjidic2 — meanings, readings, JLPT levels

The `meaning`, `onyomi`, `kunyomi` and `jlpt` fields of
`app/src/main/assets/kanji.json` — the 2136 Jōyō kanji — derive from
**KANJIDIC2**, © Electronic Dictionary Research and Development Group, licensed under
[Creative Commons Attribution-ShareAlike 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
They were extracted from [davidluzgouveia/kanji-data](https://github.com/davidluzgouveia/kanji-data)
(MIT licence, © 2019 David Gouveia), which is a JSON repackaging of KANJIDIC2.

## Kana

`app/src/main/assets/kana.json` is not derived from an external source: the
gojūon table, its romanisation and the voiced and semi-voiced forms are written
out in `tools/build_assets.py`. The romanisation follows Hepburn, except that
ぢ/づ and ヂ/ヅ are spelled `di`/`du` so that every prompt has exactly one answer.

## JLPT vocabulary — example words

The `exampleWord` field is derived from the vocabulary lists in
[jamsinclair/open-anki-jlpt-decks](https://github.com/jamsinclair/open-anki-jlpt-decks)
(N5 through N1), MIT licence, © 2020 Jamie Sinclair.

## FSRS — scheduling algorithm

`fsrs/` is an independent Kotlin port of **FSRS v6**, written from the algorithm
specification and the reference implementation at
[open-spaced-repetition/fsrs-rs](https://github.com/open-spaced-repetition/fsrs-rs)
(MIT licence) and the
[FSRS wiki](https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm).
The port itself is original Kotlin code; the published default parameters and the
numeric test vectors come from that reference implementation.

## Chinese character data

Three sources, all shipped in the `chinese` flavour.

**Stroke order** — `app/src/chinese/assets/strokes/*.svg` are generated from
[Make Me a Hanzi](https://github.com/skishore/makemeahanzi), whose stroke graphics
derive from the **Arphic PL KaitiM GB** and **Arphic PL UKai** fonts, © 1999
Arphic Technology Co., Ltd., under the
[Arphic Public License](https://github.com/skishore/makemeahanzi/blob/master/APL/english/ARPHICPL.TXT).
The APL requires its licence text to travel unaltered with any copy of the data,
so it is bundled at `app/src/chinese/assets/ARPHICPL.TXT` and checked by a test.
This data is *modified*: the published stroke medians have been converted to SVG
paths, which the APL permits provided the conversion is noted — see
`tools/build_assets_chinese.py`.

The APL's mere-aggregation clause means this does not reach the application code
around it.

**Meanings and pinyin** — derived from **Unihan**, © Unicode, Inc., under the
[Unicode License v3](https://www.unicode.org/license.txt), which is permissive.

**HSK levels and word frequencies** — from
[complete-hsk-vocabulary](https://github.com/drkameleon/complete-hsk-vocabulary),
MIT, © 2026 Yanis Zafirópulos.

## ML Kit Digital Ink Recognition and Google Play Services

Proprietary, provided by Google as ordinary Gradle dependencies and **not**
redistributed here. The relevant terms are the
[ML Kit Terms of Service](https://developers.google.com/ml-kit/terms), which
incorporate the [Google APIs Terms of Service](https://developers.google.com/terms),
and the [Android Software Development Kit License](https://developer.android.com/studio/terms).

Two consequences worth knowing:

* The Japanese recognition model is downloaded from Google on first use. It is
  "related software" under the ML Kit terms, which forbid reverse engineering or
  extracting it, so it can be neither bundled with the app nor self-hosted.
  Offline use after the first download is the intended design; independence from
  Google's servers is not available within ML Kit.
* ML Kit sends Google metrics about the performance and utilisation of the API in
  the app, and the terms make the app's publisher responsible for telling users
  about it. Handwriting itself never leaves the device.

Because ML Kit depends on Google Play Services, this app cannot be distributed
through the main F-Droid repository; see the README.

## Bundled software

Built against 119 resolved runtime artifacts. The overwhelming majority are
Apache-2.0:

| Component | Licence |
| --- | --- |
| AndroidX (Compose, Activity, Lifecycle, Navigation, Room, DataStore, …) | Apache License 2.0 |
| Kotlin standard library, kotlinx.coroutines | Apache License 2.0 |
| Dagger/Hilt | Apache License 2.0 |
| OkHttp, Guava `listenablefuture`, `javax.inject` | Apache License 2.0 |
| Google Play Services (base, basement, tasks) | Android Software Development Kit License |
| ML Kit (common, digital-ink-common, digital-ink-recognition) | ML Kit Terms of Service |
| Firebase annotations/components, Google data transport | Apache License 2.0 |

Three artifacts declare no licence in their POMs — `com.google.guava:listenablefuture:1.0`,
`com.squareup.okhttp3:okhttp:3.12.1` and `javax.inject:javax.inject:1` — but all
three are Apache-2.0 upstream.

Attribution for all of the above is shown in the app, under Settings → Licences.

## Regenerating the bundled data

`tools/build_assets.py` rebuilds `kanji.json`, `kana.json` and the bundled SVGs
for the Japanese sets. It expects them fetched into a working directory as
`kanji-data.json`, `n5.csv` through `n1.csv`, and `kanjivg-master/kanji/`.

`tools/build_assets_chinese.py` rebuilds `hanzi.json`, the stroke SVGs and
`ARPHICPL.TXT` for the Chinese sets. It expects `hsk.json`, `Unihan.zip`
(unpacked to `unihan/`), `graphics.txt` and `arphic.txt`.

Both read `KANJI_SOURCES` for the source directory and take the destination
assets directory as their only argument.
