"""Temps des musiques du thème (catalogue data/IntroMusic.java) : pour chaque partition, le tempo, le début de
l'« appel », l'accord final, la fin (FIN) et la durée du .ogg rendu à côté. Usage :
python temps.py ambiances/*.py univers/m*.py   (les .ogg à côté des .py : Downloads/fanfare-craftmaster chez LordFinn)
"""
import os, re, sys, json, subprocess
INSTR0 = {"harp": ("harp", 66), "bit": ("bit", 66), "pling": ("pling", 66), "banjo": ("banjo", 66),
    "iron_xylophone": ("iron_xylophone", 66), "bass": ("bass", 42), "didgeridoo": ("didgeridoo", 42),
    "guitar": ("guitar", 54), "flute": ("flute", 78), "cow_bell": ("cow_bell", 78),
    "bell": ("bell", 90), "chime": ("icechime", 90), "xylophone": ("xylobone", 90),
    "basedrum": ("bd", None), "snare": ("snare", None), "hat": ("hat", None)}
out = {}
for PART in sys.argv[1:]:
    src = open(PART, encoding='utf-8').read()
    notes = []
    def n(voice, inst, t, d, m, v=1.0, pan=0.0): notes.append((voice, t))
    g = {'os': os, 'PART': PART, 'INSTR': INSTR0, 'n': n, '__name__': 'p'}
    m = re.search(r"^BPM\s*=\s*([0-9.]+)", src, re.M)
    g['BPM'] = float(m.group(1)) if m else 112
    exec(compile(src, PART, 'exec'), g)
    bpm = g['BPM']; beat = 60.0 / bpm
    ap = [t for v, t in notes if v == 'appel']
    name = os.path.splitext(os.path.basename(PART))[0]
    dur = float(subprocess.run(['ffprobe','-v','error','-show_entries','format=duration','-of','csv=p=0',PART[:-3]+'.ogg'],capture_output=True,text=True).stdout)
    out[name] = {'bpm': bpm, 'appel': round(min(ap) * beat, 3), 'accord': round(max(ap) * beat, 3), 'fin': round(g['FIN'] * beat, 3), 'duree': round(dur, 3),
                 'desc': src.splitlines()[0].lstrip('# ').strip()}
    print(name, out[name])
json.dump(out, open('temps.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
