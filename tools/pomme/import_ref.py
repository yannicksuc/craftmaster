"""Brings LordFinn's changes to the reference room back into the source (modifs.json, applied by gen.py over pomme.schem).

The reference room (minecraft:pomme, corner 0 60 0) is read from the server's region files and compared with what
gen.py builds: every block that differs, the contents of the containers that differ, and the decor entities (item
frames, paintings, block and item displays) as they are now. The entities the pack makes itself (villagers,
mannequins and their seats, the hens, the room's marker) and the cameras are left out.

    python -I import_ref.py gen.py pomme.schem <region/r.0.0.mca> <entities/r.0.0.mca> modifs.json

(the two files from craftmaster/dimensions/minecraft/pomme/ on the server, after a save-all flush; then gen.py and
verif give 0 on the reference)
"""
import io, json, math, re, struct, sys, zlib
import nbtlib
from nbtlib import serialize_tag

GEN, SCHEM, MCA, EMCA, OUT = sys.argv[1:6]


def _read_string(fileobj, byteorder="big"):
    """Java's modified UTF-8: an emoji is two 3-byte surrogates (nbtlib would turn them into replacement characters)."""
    n = nbtlib.tag.read_numeric(nbtlib.tag.USHORT, fileobj, byteorder)
    return fileobj.read(n).decode('utf-8', 'surrogatepass').encode('utf-16', 'surrogatepass').decode('utf-16')


nbtlib.tag.read_string = _read_string
REF = (0, 60, 0)
src = open(GEN, encoding='utf-8').read()
head = src[:src.index('MIN = (')].replace('SCHEM, OUT = sys.argv[1], sys.argv[2]', 'SCHEM = %r' % SCHEM)
# the changes already imported are not part of what is compared against
head = head.replace('MODIFS = load_modifs()', 'MODIFS = {}')
g = {'__file__': GEN}
exec(compile(head, GEN, 'exec'), g)
sch, W, H, L = g['sch'], g['W'], g['H'], g['L']
base_blk = g['blk']


def chunks(path):
    data = open(path, 'rb').read()
    for i in range(1024):
        off = int.from_bytes(data[4 * i:4 * i + 3], 'big') * 4096
        if not off:
            continue
        ln = struct.unpack('>I', data[off:off + 4])[0]
        raw = data[off + 5:off + 4 + ln]
        yield nbtlib.File.parse(io.BytesIO(zlib.decompress(raw) if data[off + 4] == 2 else raw))


def state_str(p):
    name = str(p['Name'])
    props = p.get('Properties')
    if props:
        name += '[' + ','.join('%s=%s' % (k, str(v)) for k, v in sorted(props.items())) + ']'
    return name


def norm(b):
    m = re.match(r'([^\[]+)(\[(.*)\])?$', b)
    n = m.group(1) if ':' in m.group(1) else 'minecraft:' + m.group(1)
    return n + ('[' + ','.join(sorted(m.group(3).split(','))) + ']' if m.group(3) else '')


sections, bes = {}, {}
for c in chunks(MCA):
    for s in c.get('sections', []):
        if 'block_states' in s:
            sections[(int(c['xPos']), int(s['Y']), int(c['zPos']))] = s['block_states']
    for be in c.get('block_entities', []):
        bes[(int(be['x']), int(be['y']), int(be['z']))] = be


def at(x, y, z):
    bs = sections.get((x >> 4, y >> 4, z >> 4))
    if bs is None:
        return 'minecraft:air'
    pal = bs['palette']
    if len(pal) == 1 or 'data' not in bs:
        return state_str(pal[0])
    bits = max(4, math.ceil(math.log2(len(pal))))
    per = 64 // bits
    idx = ((y & 15) * 16 + (z & 15)) * 16 + (x & 15)
    v = (int(bs['data'][idx // per]) & ((1 << 64) - 1)) >> ((idx % per) * bits) & ((1 << bits) - 1)
    return state_str(pal[v])


def items(lst):
    return sorted((str(i['id']), int(i.get('count', 1)), int(i.get('Slot', 0))) for i in (lst or []))


blocks, contents = {}, {}
sch_be = {tuple(int(v) for v in e['Pos']): e['Data'] for e in sch['Blocks']['BlockEntities']}
for y in range(H):
    for z in range(L):
        for x in range(W):
            want, have = norm(base_blk(x, y, z)), norm(at(REF[0] + x, REF[1] + y, REF[2] + z))
            if want != have:
                blocks['%d,%d,%d' % (x, y, z)] = have
            live = bes.get((REF[0] + x, REF[1] + y, REF[2] + z))
            if live is not None and 'Items' in live:
                ref = sch_be.get((x, y, z), {}).get('Items')
                if (x, y, z) == (28, 4, 27):
                    ref = [i for i in (ref or []) if str(i['id']) != 'minecraft:sugar_cane']
                if items(live['Items']) != items(ref):
                    contents['%d,%d,%d' % (x, y, z)] = serialize_tag(live['Items'], compact=True)

# The block entities of what changed (signs and their text, new chests, barrels...), whole
def sign_text(t):
    return [serialize_tag(t[k], compact=True) if k in t else '' for k in ('front_text', 'back_text')]


be_data = {}
for (x, y, z), live in bes.items():
    p = (x - REF[0], y - REF[1], z - REF[2])
    if not (0 <= p[0] < W and 0 <= p[1] < H and 0 <= p[2] < L):
        continue
    k = '%d,%d,%d' % p
    old = sch_be.get(p)
    if k in blocks or ('sign' in str(live['id']) and (old is None or sign_text(old) != sign_text(live))):
        be_data[k] = serialize_tag(nbtlib.Compound({a: v for a, v in live.items() if a not in ('id', 'x', 'y', 'z', 'keepPacked')}), compact=True)

DECOR = {'minecraft:item_frame', 'minecraft:glow_item_frame', 'minecraft:painting', 'minecraft:block_display', 'minecraft:item_display'}
DROP = {'UUID', 'Motion', 'Paper.Origin', 'Paper.OriginWorld', 'WorldUUIDMost', 'WorldUUIDLeast', 'FallFlying', 'fall_distance',
        'OnGround', 'Air', 'PortalCooldown', 'Fire', 'HurtTime', 'DeathTime', 'HurtByTimestamp'}
def clean(e):
    """An entity's NBT for a summon: without its id, place, UUID and motion; its passengers (an item display riding
    another one) kept, cleaned the same way."""
    nb = nbtlib.Compound({k: v for k, v in e.items() if k not in DROP and k not in ('Pos', 'Passengers')})
    if 'Passengers' in e:
        nb['Passengers'] = nbtlib.List[nbtlib.Compound]([nbtlib.Compound({**clean(q), 'id': q['id']}) for q in e['Passengers']])
    nb.pop('id', None)
    return nb


decor = []
for c in chunks(EMCA):
    for e in c.get('Entities', []):
        eid = str(e['id'])
        p = [float(v) for v in e['Pos']]
        if eid not in DECOR or not (REF[0] <= p[0] < REF[0] + W and REF[1] <= p[1] < REF[1] + H and REF[2] <= p[2] < REF[2] + L):
            continue
        tags = [str(t) for t in e.get('Tags', [])]
        if 'pomme_siege' in tags:
            continue                     # a mannequin's seat: made by gen.py
        nb = clean(e)
        if 'block_pos' in e:
            bp = [int(e['block_pos'][k]) - REF[k] for k in range(3)]
            nb['block_pos'] = nbtlib.IntArray(bp)
            pos = [bp[0] + 0.5, bp[1] + 0.5, bp[2] + 0.5]
        else:
            pos = [p[0] - REF[0], p[1] - REF[1], p[2] - REF[2]]
        decor.append({'id': eid, 'pos': [round(v, 4) for v in pos], 'nbt': serialize_tag(nb, compact=True)})
decor.sort(key=lambda d: (d['id'], d['pos']))
json.dump({'note': "Changements de LordFinn dans la salle de référence, relevés par import_ref.py : appliqués par gen.py par-dessus pomme.schem.",
           'blocks': blocks, 'contents': contents, 'be': be_data, 'decor': decor}, open(OUT, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('%d blocs, %d conteneurs, %d décors' % (len(blocks), len(contents), len(decor)))
for k, v in blocks.items():
    print(' bloc', k, base_blk(*map(int, k.split(','))).replace('minecraft:', ''), '->', v.replace('minecraft:', ''))
for k in contents:
    print(' conteneur', k)
