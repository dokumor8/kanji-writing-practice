
import re, glob, collections
cmds = collections.Counter()
other = collections.Counter()
pat = re.compile(r'\sd="([^"]*)"')
files = glob.glob('kanjivg-master/kanji/*.svg')
print('files', len(files))
for f in files[:4000]:
    s = open(f, encoding='utf-8').read()
    for d in pat.findall(s):
        for ch in re.findall(r'[A-Za-z]', d):
            cmds[ch] += 1
print('letters in path data:', dict(cmds))
# viewBox
vb = collections.Counter()
for f in files[:4000]:
    s = open(f, encoding='utf-8').read()
    m = re.search(r'viewBox="([^"]*)"', s)
    vb[m.group(1) if m else None] += 1
print('viewBox:', vb.most_common(5))
