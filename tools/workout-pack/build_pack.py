"""Build a workout resource pack from a wger exerciseinfo dump (CC-BY-SA / CC0 per item)."""
import json, os, re, sys, io, time, zipfile, html, urllib.request
from PIL import Image

SRC, OUT, CACHE = sys.argv[1], sys.argv[2], sys.argv[3]
MAXPX, QUALITY = 480, 72
os.makedirs(os.path.join(OUT, 'media'), exist_ok=True); os.makedirs(CACHE, exist_ok=True)
data = json.load(open(SRC))
LANG = {2: 'en', 4: 'es'}

def text(h):
    h = re.sub(r'</(p|li)>', '\n', h or ''); h = re.sub(r'<[^>]+>', '', h)
    return '\n'.join(l.strip() for l in html.unescape(h).splitlines() if l.strip())

def authors(o):
    a = [o.get('license_author')] if o.get('license_author') else []
    return a + [x for x in o.get('author_history', []) if x not in a]

def fetch(u):
    p = os.path.join(CACHE, re.sub(r'\W', '_', u)[-120:])
    if not os.path.exists(p):
        open(p, 'wb').write(urllib.request.urlopen(u, timeout=60).read()); time.sleep(0.15)
    return p

exercises, size = [], 0
for e in data:
    names, descs, cred = {}, {}, set(authors(e))
    for t in e['translations']:
        l = LANG.get(t['language'])
        if l and t['name']:
            names[l] = t['name']; descs[l] = text(t['description']); cred.update(authors(t))
    if 'en' not in names: continue
    media = []
    for i in e['images']:
        if i.get('is_ai_generated'): ai = True
        try:
            im = Image.open(fetch(i['image'])).convert('RGB')
        except Exception as x:
            print('skip image', i['image'], x, file=sys.stderr); continue
        im.thumbnail((MAXPX, MAXPX))
        rel = f"media/{e['uuid']}_{i['id']}.webp"
        im.save(os.path.join(OUT, rel), 'WEBP', quality=QUALITY, method=6)
        size += os.path.getsize(os.path.join(OUT, rel))
        media.append({'file': rel, 'main': i['is_main'], 'ai': bool(i.get('is_ai_generated')),
                      'license': {1: 'CC-BY-SA-3.0', 2: 'CC-BY-SA-4.0', 3: 'CC0-1.0', 4: 'CC-BY-4.0'}.get(i['license'], str(i['license'])),
                      'authors': authors(i)})
    exercises.append({
        'id': e['uuid'], 'name': names, 'description': descs,
        'category': e['category']['name'],
        'muscles': [m['name_en'] or m['name'] for m in e['muscles']],
        'musclesSecondary': [m['name_en'] or m['name'] for m in e['muscles_secondary']],
        'equipment': [q['name'] for q in e['equipment']],
        'license': e['license']['short_name'].replace(' ', '-'), 'authors': sorted(cred), 'media': media})

json.dump({'schema': 1, 'source': 'wger.de', 'exercises': exercises}, open(os.path.join(OUT, 'exercises.json'), 'w'), ensure_ascii=False)
json.dump({'schema': 1, 'id': 'wger', 'version': time.strftime('%Y%m%d'), 'languages': ['en', 'es'],
           'exercises': len(exercises), 'withMedia': sum(1 for x in exercises if x['media']),
           'licenses': ['CC-BY-SA-4.0', 'CC-BY-SA-3.0', 'CC0-1.0'],
           'attribution': 'Exercise data and images from wger (https://wger.de), by their listed authors, under the licence stated per item.'},
          open(os.path.join(OUT, 'manifest.json'), 'w'), indent=1)
zp = OUT.rstrip('/') + '.zip'
with zipfile.ZipFile(zp, 'w', zipfile.ZIP_DEFLATED) as z:
    for r, _, fs in os.walk(OUT):
        for f in fs: z.write(os.path.join(r, f), os.path.relpath(os.path.join(r, f), OUT))
print(len(exercises), 'exercises; media', round(size / 1e6, 1), 'MB; zip', round(os.path.getsize(zp) / 1e6, 1), 'MB')
