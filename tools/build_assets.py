#!/usr/bin/env python3
"""Build the bundled card data and copy the KanjiVG SVGs it needs.

Inputs (fetched into .buildcache/recon):
  kanji-data.json          KANJIDIC-derived per-kanji data (meanings, readings, grade, JLPT)
  n5.csv .. n1.csv         JLPT vocabulary lists (used only to pick an example word)
  kanjivg-master/kanji/    KanjiVG stroke-order SVGs, kanji *and* kana

Outputs, into the app's assets directory:
  kanji.json   2136 Joyo kanji, split into 7 sets
  kana.json    71 hiragana + 71 katakana
  kanjivg/     one SVG per card, named by code point
"""
import csv, json, os, re, shutil, sys

# Where the downloaded upstream sources live. Override with KANJI_SOURCES when
# running from a checkout that does not have them beside the script.
RECON = os.environ.get('KANJI_SOURCES', os.path.dirname(os.path.abspath(__file__)))
OUT_ASSETS = sys.argv[1]

KANA = re.compile(r'^[\u3040-\u309f\u30a0-\u30ff]+$')
CJK = re.compile(r'^[\u4e00-\u9fff]+$')
HIRA_START, HIRA_END, KATA_OFFSET = 0x3041, 0x3096, 0x60

KANJI_SET_SIZE = 300
# Six full sets of 300, then everything that is left in the seventh: 2136 Joyo
# kanji become 6 x 300 + 336.
KANJI_SET_COUNT = 7


def to_katakana(s):
    return ''.join(chr(ord(c) + KATA_OFFSET) if HIRA_START <= ord(c) <= HIRA_END else c for c in s)


def clean_meaning(raw):
    out = []
    for m in raw:
        if '(no.' in m or 'Radical' in m:
            continue
        out.append(m.strip().lower())
        if len(out) == 2:
            break
    return ', '.join(out)


def load_vocab():
    words = []
    for level, path in ((5, 'n5.csv'), (4, 'n4.csv'), (3, 'n3.csv'), (2, 'n2.csv'), (1, 'n1.csv')):
        full = os.path.join(RECON, path)
        if not os.path.exists(full):
            continue
        with open(full, encoding='utf-8') as fh:
            for row in csv.DictReader(fh):
                words.append((level, row['expression']))
    return words


def pick_example(kanji, words):
    """A short common compound containing *kanji* exactly once."""
    best, best_key = None, None
    for level, expr in words:
        if kanji not in expr or expr.count(kanji) != 1:
            continue
        if not (2 <= len(expr) <= 3):
            continue
        if not all(CJK.match(c) or KANA.match(c) for c in expr):
            continue
        if not any(CJK.match(c) for c in expr if c != kanji):
            continue  # okurigana, not a compound
        all_kanji = all(CJK.match(c) for c in expr)
        leads = expr.index(kanji) == 0
        tier = 0 if (all_kanji and leads and len(expr) == 2) else \
               1 if (all_kanji and leads) else \
               2 if all_kanji else 3
        key = (tier, len(expr), -level)
        if best_key is None or key < best_key:
            best, best_key = expr, key
    return best


def kanji_cards(data, words):
    joyo = {k: v for k, v in data.items() if v.get('grade') in range(1, 9)}
    # Most common first: KANJIDIC's newspaper frequency rank. The ~100 Joyo kanji
    # without a rank go last, ordered by school grade.
    ordered = sorted(
        joyo.items(),
        key=lambda kv: (kv[1].get('freq') or 10 ** 6, kv[1].get('grade') or 9, kv[0]),
    )

    cards = []
    for index, (kanji, info) in enumerate(ordered):
        meaning = clean_meaning(info.get('meanings') or [])
        if not meaning:
            continue
        kun = [r for r in (info.get('readings_kun') or []) if r]
        example = pick_example(kanji, words)
        cards.append({
            'character': kanji,
            'meaning': meaning,
            'onyomi': ', '.join(to_katakana(r) for r in (info.get('readings_on') or [])) or None,
            'kunyomi': ', '.join(kun) or None,
            'exampleWord': example.replace(kanji, '\uFF3F') if example else None,
            'jlpt': info.get('jlpt_new') or 0,
            'deck': 'kanji-%d' % min(index // KANJI_SET_SIZE + 1, KANJI_SET_COUNT),
            'sortKey': index,
        })
    return cards


HIRAGANA_BASIC = [
    ('\u3042', 'a'), ('\u3044', 'i'), ('\u3046', 'u'), ('\u3048', 'e'), ('\u304a', 'o'),
    ('\u304b', 'ka'), ('\u304d', 'ki'), ('\u304f', 'ku'), ('\u3051', 'ke'), ('\u3053', 'ko'),
    ('\u3055', 'sa'), ('\u3057', 'shi'), ('\u3059', 'su'), ('\u305b', 'se'), ('\u305d', 'so'),
    ('\u305f', 'ta'), ('\u3061', 'chi'), ('\u3064', 'tsu'), ('\u3066', 'te'), ('\u3068', 'to'),
    ('\u306a', 'na'), ('\u306b', 'ni'), ('\u306c', 'nu'), ('\u306d', 'ne'), ('\u306e', 'no'),
    ('\u306f', 'ha'), ('\u3072', 'hi'), ('\u3075', 'fu'), ('\u3078', 'he'), ('\u307b', 'ho'),
    ('\u307e', 'ma'), ('\u307f', 'mi'), ('\u3080', 'mu'), ('\u3081', 'me'), ('\u3082', 'mo'),
    ('\u3084', 'ya'), ('\u3086', 'yu'), ('\u3088', 'yo'),
    ('\u3089', 'ra'), ('\u308a', 'ri'), ('\u308b', 'ru'), ('\u308c', 're'), ('\u308d', 'ro'),
    ('\u308f', 'wa'), ('\u3092', 'wo'), ('\u3093', 'n'),
]

# Voiced and semi-voiced kana. The two ambiguous pairs take the distinct Hepburn
# spellings di/du rather than ji/zu, so a prompt has exactly one answer.
HIRAGANA_VOICED = [
    ('\u304c', 'ga'), ('\u304e', 'gi'), ('\u3050', 'gu'), ('\u3052', 'ge'), ('\u3054', 'go'),
    ('\u3056', 'za'), ('\u3058', 'ji'), ('\u305a', 'zu'), ('\u305c', 'ze'), ('\u305e', 'zo'),
    ('\u3060', 'da'), ('\u3062', 'di'), ('\u3065', 'du'), ('\u3067', 'de'), ('\u3069', 'do'),
    ('\u3070', 'ba'), ('\u3073', 'bi'), ('\u3076', 'bu'), ('\u3079', 'be'), ('\u307c', 'bo'),
    ('\u3071', 'pa'), ('\u3074', 'pi'), ('\u3077', 'pu'), ('\u307a', 'pe'), ('\u307d', 'po'),
]


def kana_cards():
    cards = []
    for deck, convert in (('hiragana', False), ('katakana', True)):
        for index, (char, romaji) in enumerate(HIRAGANA_BASIC + HIRAGANA_VOICED):
            cards.append({
                'character': to_katakana(char) if convert else char,
                'meaning': romaji,
                'onyomi': None,
                'kunyomi': None,
                'exampleWord': None,
                'jlpt': 0,
                'deck': deck,
                'sortKey': index,
            })
    return cards


def write_cards(path, cards):
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(cards, fh, ensure_ascii=False, indent=1)


def copy_svg(character, out_dir):
    code = '%05x' % ord(character)
    src = os.path.join(RECON, 'kanjivg-master', 'kanji', code + '.svg')
    if not os.path.exists(src):
        return False
    shutil.copyfile(src, os.path.join(out_dir, code + '.svg'))
    return True


def main():
    data = json.load(open(os.path.join(RECON, 'kanji-data.json'), encoding='utf-8'))
    words = load_vocab()

    kanji = kanji_cards(data, words)
    kana = kana_cards()

    svg_dir = os.path.join(OUT_ASSETS, 'kanjivg')
    os.makedirs(svg_dir, exist_ok=True)
    missing = [c['character'] for c in kanji + kana if not copy_svg(c['character'], svg_dir)]

    write_cards(os.path.join(OUT_ASSETS, 'kanji.json'), kanji)
    write_cards(os.path.join(OUT_ASSETS, 'kana.json'), kana)

    decks = {}
    for card in kanji + kana:
        decks[card['deck']] = decks.get(card['deck'], 0) + 1
    print('kanji:', len(kanji), ' kana:', len(kana))
    print('decks:', dict(sorted(decks.items())))
    print('with example word:', sum(1 for c in kanji if c['exampleWord']), '/', len(kanji))
    print('missing svg:', missing)


main()
