# Distribution and privacy

Where the card data comes from, what leaves the device, and why this app cannot be
published on F-Droid's main repository. Per-file attribution for the bundled data
is in [ATTRIBUTION.md](../ATTRIBUTION.md).

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
