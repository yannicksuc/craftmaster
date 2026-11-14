"""
Écrit les travellings du plateau principal (config/craftmaster/trajets.json), rattachés aux caméras du studio.
Usage : python gen_trajets.py <fichier trajets.json>  (le serveur arrêté : il relit le fichier au démarrage).
"""
import json, math, sys

WORLD = "minecraft:overworld"


def look(eye, target):
    dx, dy, dz = target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]
    yaw = math.degrees(math.atan2(-dx, dz))
    pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
    return round(yaw, 1), round(pitch, 1)


def step(eye, target=None, travel=0.0, hold=0.0, curve=None, fov=0.0, cible=""):
    yaw, pitch = look(eye, target) if target else (0.0, 0.0)
    s = {"x": round(eye[0], 3), "y": round(eye[1], 3), "z": round(eye[2], 3), "yaw": yaw, "pitch": pitch, "travel": travel}
    if fov: s["fov"] = fov
    if hold: s["hold"] = hold
    if curve: s["curve"] = curve
    if cible: s["cible"] = cible
    return s


def path(name, camera, steps):
    return {"name": name, "lieu": "studio", "world": WORLD, "camera": camera, "steps": steps}


SCENE = (64.0, -52.0, 0.5)
TABLE = (81.5, -58.3, 0.5)
TRONE = (86.5, -57.3, 0.5)

paths = [
    # Grue : du fond de la salle, par-dessus le trône et les candidats, jusqu'à la scène
    path("survol", "studio:1", [
        step((93.5, -52.4, 0.5), (71.0, -56.5, 0.5)),
        step((87.5, -53.2, 0.5), (70.0, -55.5, 0.5), travel=4),
        step((78.5, -54.6, 0.5), SCENE, travel=4, hold=1, curve="sortie"),
    ]),
    # Travelling latéral devant la galerie, d'un bout à l'autre du classement
    path("galerie", "studio:8", [
        step((70.8, -53.4, -5.0), (63.5, -51.6, -3.5), fov=70),
        step((70.8, -53.4, 6.0), (63.5, -51.6, 4.5), travel=9, fov=70),
    ]),
    # Arc autour des candidats, côté cour, toujours tourné vers la table
    path("tour-de-table", "studio:12", [
        step((81.5 + 6.5 * math.cos(math.radians(a)), -56.7, 0.5 + 6.5 * math.sin(math.radians(a))), TABLE,
             travel=0 if i == 0 else 3, cible="@point:table")
        for i, a in enumerate((-150, -115, -80, -45))
    ]),
    # Avancée lente sur le CraftMaster (annonce, verdict)
    path("approche-trone", "studio:2", [
        step((80.3, -56.9, 0.5), TRONE, fov=50),
        step((83.2, -57.15, 0.5), TRONE, travel=5, curve="sortie", fov=38),
    ]),
    # Recul : de la scène vers le fond de la salle (fin, retour d'épreuve)
    path("recul", "studio:1", [
        step((80.0, -54.4, 0.5), SCENE),
        step((93.5, -51.6, 0.5), (71.0, -56.5, 0.5), travel=7, curve="entree"),
    ]),
]

out = sys.argv[1] if len(sys.argv) > 1 else "trajets.json"
json.dump({"version": 1, "paths": paths}, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print(len(paths), "travellings ->", out)
