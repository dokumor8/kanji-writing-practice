#!/usr/bin/env python3
"""Build the kanji deck JSON and copy the KanjiVG SVGs it needs.

Inputs (fetched into .buildcache/recon):
  kanji-data.json          KANJIDIC-derived per-kanji data (meanings, readings, JLPT)
  n5.csv / n4.csv / n3.csv JLPT vocabulary lists (used only to pick an example word)
  kanjivg-master/kanji/    KanjiVG stroke-order SVGs
"""
import csv, glob, json, os, re, sys, shutil

RECON = os.path.dirname(os.path.abspath(__file__))
OUT_ASSETS = sys.argv[1]

KANA = re.compile(r'^[\u3040-\u309f\u30a0-\u30ff]+$')
CJK = re.compile(r'^[\u4e00-\u9fff]+$')

HIRA_START, HIRA_END, KATA_OFFSET = 0x3041, 0x3096, 0x60


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
    for level, path in ((5, 'n5.csv'), (4, 'n4.csv'), (3, 'n3.csv')):
        with open(os.path.join(RECON, path), encoding='utf-8') as fh:
            for row in csv.DictReader(fh):
                expr = row['expression']
                words.append((level, expr, row['reading'], row['meaning']))
    return words


def pick_example(kanji, words):
    """Pick a short common compound containing *kanji* exactly once.

    A good prompt word is a real compound (so it needs at least one *other*
    kanji), short enough to read at a glance, and ideally led by the kanji
    being tested -- which is what the ＿ blank in the UI stands for.
    """
    best, best_key = None, None
    for level, expr, reading, _meaning in words:
        if kanji not in expr or expr.count(kanji) != 1:
            continue
        if not (2 <= len(expr) <= 3):
            continue
        if not all(CJK.match(c) or KANA.match(c) for c in expr):
            continue
        others = [c for c in expr if c != kanji]
        if not any(CJK.match(c) for c in others):
            continue  # e.g. 七つ -- okurigana, not a compound
        all_kanji = all(CJK.match(c) for c in expr)
        leads = expr.index(kanji) == 0
        # Tier first (pure-kanji compound led by the target is ideal), then
        # shorter words, then easier vocabulary.
        tier = 0 if (all_kanji and leads and len(expr) == 2) else \
               1 if (all_kanji and leads) else \
               2 if all_kanji else 3
        key = (tier, len(expr), -level)
        if best_key is None or key < best_key:
            best, best_key = expr, key
    return best


def main():
    data = json.load(open(os.path.join(RECON, 'kanji-data.json'), encoding='utf-8'))
    words = load_vocab()

    deck, missing_svg = [], []
    for kanji, info in data.items():
        level = info.get('jlpt_new')
        if level not in (5, 4, 3):
            continue
        meaning = clean_meaning(info.get('meanings') or [])
        if not meaning:
            continue
        on = info.get('readings_on') or []
        kun = [r for r in (info.get('readings_kun') or []) if r]
        example = pick_example(kanji, words)
        deck.append({
            'character': kanji,
            'meaning': meaning,
            'onyomi': ', '.join(to_katakana(r) for r in on) or None,
            'kunyomi': ', '.join(kun) or None,
            'exampleWord': example.replace(kanji, '＿') if example else None,
            'jlpt': level,
        })

    # Easy levels first, then by stroke count so a session feels coherent.
    deck.sort(key=lambda c: (-c['jlpt'], len(c['character']), c['character']))

    os.makedirs(os.path.join(OUT_ASSETS, 'kanjivg'), exist_ok=True)
    for card in deck:
        src = os.path.join(RECON, 'kanjivg-master', 'kanji', '%05x.svg' % ord(card['character']))
        if not os.path.exists(src):
            missing_svg.append(card['character'])
            continue
        shutil.copyfile(src, os.path.join(OUT_ASSETS, 'kanjivg', '%05x.svg' % ord(card['character'])))

    with open(os.path.join(OUT_ASSETS, 'kanji.json'), 'w', encoding='utf-8') as fh:
        json.dump(deck, fh, ensure_ascii=False, indent=1)

    stats = {}
    for c in deck:
        stats[c['jlpt']] = stats.get(c['jlpt'], 0) + 1
    with_example = sum(1 for c in deck if c['exampleWord'])
    print('deck size:', len(deck), 'by jlpt:', stats)
    print('with example word:', with_example, '/', len(deck))
    print('missing svg:', missing_svg[:20], len(missing_svg))
    print('sample:', json.dumps(deck[:3], ensure_ascii=False))


main()
