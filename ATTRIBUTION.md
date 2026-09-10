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

## Kanjidic2 — meanings, readings, JLPT levels

The `meaning`, `onyomi`, `kunyomi` and `jlpt` fields of
`app/src/main/assets/kanji.json` derive from **KANJIDIC2**, © Electronic
Dictionary Research and Development Group, licensed under
[Creative Commons Attribution-ShareAlike 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
They were extracted from [davidluzgouveia/kanji-data](https://github.com/davidluzgouveia/kanji-data)
(MIT licence, © 2019 David Gouveia), which is a JSON repackaging of KANJIDIC2.

## JLPT vocabulary — example words

The `exampleWord` field is derived from the vocabulary lists in
[jamsinclair/open-anki-jlpt-decks](https://github.com/jamsinclair/open-anki-jlpt-decks),
MIT licence, © 2020 Jamie Sinclair.

## FSRS — scheduling algorithm

`fsrs/` is an independent Kotlin port of **FSRS v6**, written from the algorithm
specification and the reference implementation at
[open-spaced-repetition/fsrs-rs](https://github.com/open-spaced-repetition/fsrs-rs)
(MIT licence) and the
[FSRS wiki](https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm).
The port itself is original Kotlin code; the published default parameters and the
numeric test vectors come from that reference implementation.

## ML Kit Digital Ink Recognition

Provided by Google as a normal Gradle dependency
(`com.google.mlkit:digital-ink-recognition:19.0.0`); its own terms apply to the
downloaded Japanese recognition model.

## Regenerating the bundled data

`tools/build_assets.py` rebuilds `kanji.json` and the bundled SVGs from the three
sources above. It expects them fetched into a working directory as
`kanji-data.json`, `n5.csv`/`n4.csv`/`n3.csv` and `kanjivg-master/kanji/`, and
takes the assets directory as its only argument.
