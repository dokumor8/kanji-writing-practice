#!/usr/bin/env python3
"""Build the Chinese card data and the stroke SVGs it needs.

Inputs (fetched into the directory named by KANJI_SOURCES):
  hsk.json                 complete-hsk-vocabulary, MIT (words, HSK level, frequency)
  Unihan_Readings.txt      from Unihan.zip, Unicode License v3 (pinyin, definitions)
  graphics.txt             Make Me a Hanzi, Arphic Public License (stroke medians)

Outputs, into the assets directory:
  hanzi.json    one entry per character, grouped into HSK sets
  strokes/      one SVG per character, same shape the KanjiVG parser already reads
  ARPHICPL.TXT  the licence the stroke data is distributed under, which the APL
                requires to travel unaltered with any copy
"""
import collections, json, os, re, shutil, sys

RECON = os.environ.get('KANJI_SOURCES', os.path.dirname(os.path.abspath(__file__)))
OUT = sys.argv[1]

HANZI = re.compile(r'^[\u4e00-\u9fff]+$')
EM_BOX = 1024


def load_hsk():
    """character -> (best HSK level, best word frequency), plus the word list."""
    words = json.load(open(os.path.join(RECON, 'hsk.json'), encoding='utf-8'))
    level_of, freq_of = {}, {}
    for w in words:
        nums = [int(m.group(1)) for m in
                (re.search(r'(\d+)', str(l)) for l in (w.get('level') or [])) if m]
        if not nums:
            continue
        level = min(nums)
        freq = w.get('frequency') or 10 ** 9
        for ch in w.get('simplified', ''):
            if not HANZI.match(ch):
                continue
            if ch not in level_of or level < level_of[ch]:
                level_of[ch] = level
            if ch not in freq_of or freq < freq_of[ch]:
                freq_of[ch] = freq
    return level_of, freq_of, words


def load_unihan():
    pinyin, definition = {}, {}
    path = os.path.join(RECON, 'unihan', 'Unihan_Readings.txt')
    for line in open(path, encoding='utf-8'):
        if line.startswith('#') or not line.strip():
            continue
        cp, field, value = line.rstrip('\n').split('\t', 2)
        ch = chr(int(cp[2:], 16))
        if field == 'kMandarin':
            pinyin[ch] = value
        elif field == 'kDefinition':
            definition[ch] = value
    return pinyin, definition


def clean_definition(raw):
    """Unihan glosses are 'a, b; c' - keep the first couple, learner-facing."""
    parts = [p.strip() for p in re.split(r'[;,]', raw) if p.strip()]
    kept = []
    for p in parts:
        if p.startswith('(') or 'variant of' in p or 'surname' in p:
            continue
        kept.append(p.lower())
        if len(kept) == 3:
            break
    return ', '.join(kept) or parts[0].lower() if parts else ''


def load_medians(characters):
    medians = {}
    for line in open(os.path.join(RECON, 'graphics.txt'), encoding='utf-8'):
        if not line.strip():
            continue
        g = json.loads(line)
        if g['character'] in characters and g.get('medians'):
            medians[g['character']] = g['medians']
    return medians


def smooth(median_pts):
    """Median polyline, already flipped and shifted, -> a smooth cubic path."""
    pts = median_pts
    if len(pts) < 2:
        x, y = pts[0]
        return f'M {x:.0f} {y:.0f} L {x + 1:.0f} {y:.0f}'
    d = f'M {pts[0][0]:.0f} {pts[0][1]:.0f}'
    if len(pts) == 2:
        return d + f' L {pts[1][0]:.0f} {pts[1][1]:.0f}'
    # Catmull-Rom through the points, expressed as cubic Beziers.
    for i in range(len(pts) - 1):
        p0 = pts[i - 1] if i > 0 else pts[i]
        p1, p2 = pts[i], pts[i + 1]
        p3 = pts[i + 2] if i + 2 < len(pts) else p2
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += (f' C {c1[0]:.1f} {c1[1]:.1f} {c2[0]:.1f} {c2[1]:.1f}'
              f' {p2[0]:.1f} {p2[1]:.1f}')
    return d


def write_svg(path, character, flipped, box):
    """Same element layout the KanjiVG parser reads: paths, then stroke numbers."""
    parts = [f'<?xml version="1.0" encoding="UTF-8"?>',
             f'<!-- Generated from Make Me a Hanzi stroke medians. See ARPHICPL.TXT. -->',
             f'<svg xmlns="http://www.w3.org/2000/svg" '
             f'viewBox="0 0 {box["width"]:.0f} {box["height"]:.0f}">',
             f'<g id="StrokePaths_{ord(character):05x}">']
    for median in flipped:
        parts.append(f'  <path d="{smooth(median)}"/>')
    parts.append('</g>')
    parts.append(f'<g id="StrokeNumbers_{ord(character):05x}">')
    for i, median in enumerate(flipped, start=1):
        x, y = median[0]
        nx = min(max(x + 18, 20), box['width'] - 40)
        ny = min(max(y - 30, 40), box['height'] - 20)
        parts.append(f'  <text transform="matrix(1 0 0 1 {nx:.1f} {ny:.1f})">{i}</text>')
    parts.append('</g>')
    parts.append('</svg>')
    open(path, 'w', encoding='utf-8').write('\n'.join(parts) + '\n')


def pick_example(character, words):
    """A short common word containing the character exactly once, blanked."""
    best, best_key = None, None
    for w in words:
        expr = w.get('simplified', '')
        if character not in expr or expr.count(character) != 1:
            continue
        if not (2 <= len(expr) <= 3) or not HANZI.match(expr):
            continue
        nums = [int(m.group(1)) for m in
                (re.search(r'(\d+)', str(l)) for l in (w.get('level') or [])) if m]
        key = (len(expr), min(nums) if nums else 99, w.get('frequency') or 10 ** 9)
        if best_key is None or key < best_key:
            best, best_key = expr, key
    return best


def main():
    level_of, freq_of, words = load_hsk()
    pinyin, definition = load_unihan()
    medians = load_medians(set(level_of))

    usable = [c for c in level_of if c in medians and c in pinyin]
    ordered = sorted(usable, key=lambda c: (level_of[c], freq_of.get(c, 10 ** 9), c))

    # One fixed view box for every character, so relative proportions are kept.
    # Make Me a Hanzi works in a font box with y increasing upward; SVG has y
    # increasing downward. Flip here, and normalise the origin to (0, 0), which
    # is what the renderer assumes and what KanjiVG already does.
    xs = [x for c in ordered for m in medians[c] for x, _ in m]
    ys = [y for c in ordered for m in medians[c] for _, y in m]
    pad = 40
    top = max(ys) + pad
    left = min(xs) - pad
    flipped = {
        c: [[(x - left, top - y) for x, y in median] for median in medians[c]]
        for c in ordered
    }
    points = [p for c in ordered for median in flipped[c] for p in median]
    box = {
        'width': max(p[0] for p in points),
        'height': max(p[1] for p in points),
    }

    strokes_dir = os.path.join(OUT, 'strokes')
    os.makedirs(strokes_dir, exist_ok=True)

    cards = []
    for index, ch in enumerate(ordered):
        meaning = clean_definition(definition.get(ch, ''))
        if not meaning:
            continue
        example = pick_example(ch, words)
        cards.append({
            'character': ch,
            'meaning': meaning,
            'reading1': pinyin[ch],
            'reading2': None,
            'exampleWord': example.replace(ch, '\uFF3F') if example else None,
            'level': level_of[ch],
            'deck': 'hsk-%d' % level_of[ch],
            'sortKey': index,
        })
        write_svg(os.path.join(strokes_dir, '%05x.svg' % ord(ch)), ch, flipped[ch], box)

    json.dump(cards, open(os.path.join(OUT, 'hanzi.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)

    # The Arphic Public License requires this file to travel unaltered with any
    # copy of the data derived from the Arphic fonts.
    source = os.path.join(RECON, 'arphic.txt')
    if os.path.exists(source):
        shutil.copyfile(source, os.path.join(OUT, 'ARPHICPL.TXT'))

    print('cards:', len(cards))
    print('sets:', dict(sorted(collections.Counter(c['deck'] for c in cards).items())))
    print('with example word:', sum(1 for c in cards if c['exampleWord']))
    print('dropped (no definition):', len(ordered) - len(cards))
    print('viewBox: 0 0 %.0f %.0f' % (box['width'], box['height']))
    print('sample:', json.dumps(cards[:2], ensure_ascii=False))


main()
