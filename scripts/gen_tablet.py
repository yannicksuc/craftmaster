"""Timer tablet assets: blockstate, models (the block itself is drawn by TimerTabletRenderer; the item is a little
3D picture frame showing "25.7"), textures, loot, recipe and translations (block + settings screen).
Called by gen_assets.py."""
import os

import gen_studio

RED = (255, 54, 40)
DIM = (40, 10, 8)

# 3 × 5 digit shapes (rows top to bottom)
DIGITS = {
    "2": ["###", "..#", "###", "#..", "###"],
    "5": ["###", "#..", "###", "..#", "###"],
    "7": ["###", "..#", "..#", "..#", "..#"],
}


def generate(g):
    A, D = g.A, g.D
    wj = g.write_json

    # ---- texture: black frame, white passe-partout, dark screen, red "25.7"
    t = gen_studio.Img(32, 24)
    t.rect(0, 0, 32, 24, (20, 20, 22))
    t.rect(0, 0, 32, 1, (48, 48, 52))
    t.rect(3, 3, 29, 21, (242, 239, 230))
    t.rect(6, 6, 26, 18, (10, 10, 10))
    x = 9
    for ch in "25.7":
        if ch == ".":
            t.set(x, 14, RED)
            x += 2
            continue
        for row, line in enumerate(DIGITS[ch]):
            for col, c in enumerate(line):
                t.set(x + col, 9 + row, RED if c == "#" else DIM)
        x += 4
    t.save(g, "block/studio/timer_tablet")

    tex = "craftmaster:block/studio/timer_tablet"
    # Block: nothing drawn by the model (the renderer draws it), particles from the texture
    wj(os.path.join(A, "models/block/timer_tablet.json"), {"textures": {"particle": tex}, "elements": []})
    wj(os.path.join(A, "blockstates/timer_tablet.json"), {"variants": {"": {"model": "craftmaster:block/timer_tablet"}}})
    # Item: a 14 × 10.5 px frame, 1.2 deep, the face on the south side (towards the viewer in the inventory)
    face = {"texture": "#face", "uv": [0, 0, 16, 12]}
    edge = {"texture": "#face", "uv": [0, 0, 1, 1]}
    wj(os.path.join(A, "models/item/timer_tablet.json"), {
        "parent": "minecraft:block/block",
        "textures": {"face": tex, "particle": tex},
        "elements": [{"from": [1, 2.75, 7.4], "to": [15, 13.25, 8.6],
                      "faces": {"south": face, "north": edge, "east": edge, "west": edge, "up": edge, "down": edge}}],
        "display": {
            "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.1, 1.1, 1.1]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
            # Held up in front, face towards the others (like Taskmaster's assistant)
            "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 2.5, 1], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_lefthand": {"rotation": [0, 180, 0], "translation": [0, 2.5, 1], "scale": [0.6, 0.6, 0.6]},
            "firstperson_righthand": {"rotation": [0, 160, 0], "translation": [1, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "firstperson_lefthand": {"rotation": [0, 200, 0], "translation": [-1, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 4, -6], "scale": [1, 1, 1]},
        },
    })
    wj(os.path.join(A, "items/timer_tablet.json"), {"model": {"type": "minecraft:model", "model": "craftmaster:item/timer_tablet"}})
    wj(os.path.join(D, "craftmaster/loot_table/blocks/timer_tablet.json"), g.self_drop("timer_tablet"))
    g.shaped("timer_tablet", ["PCP", "PGP"], {"P": "minecraft:black_concrete", "C": "minecraft:clock", "G": "minecraft:glass_pane"},
             "timer_tablet", 1, "redstone")

    T = "screen.craftmaster.tablet."
    texts = {
        "block.craftmaster.timer_tablet": ("Timer Tablet", "Tablette chrono"),
        T + "title": ("Timer tablet", "Tablette chrono"),
        T + "tab.mode": ("Mode", "Mode"),
        T + "tab.look": ("Look", "Apparence"),
        T + "tab.triggers": ("Triggers", "Déclencheurs"),
        T + "tab.pose": ("Position", "Position"),
        T + "start": ("▶ Start", "▶ Démarrer"),
        T + "start.tip": ("Starts (the whole group). A finished timer starts again from the top",
                          "Démarre (tout le groupe). Un minuteur fini repart du début"),
        T + "pause": ("⏸ Pause", "⏸ Pause"),
        T + "reset": ("Reset", "Remettre"),
        T + "reset.tip": ("Back to the start, stopped", "Retour au début, à l'arrêt"),
        T + "restart": ("Restart", "Relancer"),
        T + "restart.tip": ("Back to the start and running", "Retour au début et c'est parti"),
        T + "state.running": ("● running", "● en marche"),
        T + "state.stopped": ("■ stopped", "■ à l'arrêt"),
        T + "in_group": ("group « %s »", "groupe « %s »"),
        T + "seconds": ("last %s s", "%s dernières s"),
        T + "mode": ("Shows", "Affiche"),
        T + "mode.tip": ("Timer: counts down · Stopwatch: counts up · Clock: time of day · Show: the round's timer",
                         "Minuteur : compte à rebours · Chrono : compte · Heure : l'heure · Émission : le chrono de la manche"),
        T + "mode.timer": ("Timer", "Minuteur"),
        T + "mode.stopwatch": ("Stopwatch", "Chrono"),
        T + "mode.clock": ("Clock", "Heure"),
        T + "mode.show": ("Show's timer", "Chrono de l'émission"),
        T + "duration": ("Duration", "Durée"),
        T + "duration.tip": ("1:30, 90 or 1:00:00", "1:30, 90 ou 1:00:00"),
        T + "format": ("Format", "Format"),
        T + "format.tip": ("Auto: 3:07, tenths under 10 s (9.4) · Tenths: 25.7 · Hundredths: 1:07.42",
                           "Auto : 3:07, dixièmes sous 10 s (9.4) · Dixièmes : 25.7 · Centièmes : 1:07.42"),
        T + "format.auto": ("Auto", "Auto"),
        T + "format.minutes": ("3:07", "3:07"),
        T + "format.tenths": ("25.7", "25.7"),
        T + "format.hundredths": ("1:07.42", "1:07.42"),
        T + "format.seconds": ("187", "187"),
        T + "warn": ("Warning", "Alerte"),
        T + "warn.tip": ("The last seconds blink in the warning colour", "Les dernières secondes clignotent dans la couleur d'alerte"),
        T + "warn_color": ("Warning colour", "Couleur d'alerte"),
        T + "color": ("Digits", "Chiffres"),
        T + "digitcolor.red": ("Red", "Rouge"),
        T + "digitcolor.amber": ("Amber", "Ambre"),
        T + "digitcolor.green": ("Green", "Vert"),
        T + "digitcolor.blue": ("Blue", "Bleu"),
        T + "digitcolor.white": ("White", "Blanc"),
        T + "digitcolor.gold": ("Gold", "Or"),
        T + "ghost": ("Unlit segments", "Segments éteints"),
        T + "ghost.tip": ("Faintly visible, like a real display", "Visibles en sombre, comme un vrai afficheur"),
        T + "frame": ("Frame", "Cadre"),
        T + "framecolor.black": ("Black", "Noir"),
        T + "framecolor.walnut": ("Walnut", "Noyer"),
        T + "framecolor.gold": ("Gold", "Doré"),
        T + "framecolor.white": ("White", "Blanc"),
        T + "framecolor.red": ("Red", "Rouge"),
        T + "mat": ("Passe-partout", "Passe-partout"),
        T + "mat.white": ("White", "Blanc"),
        T + "mat.cream": ("Cream", "Crème"),
        T + "mat.black": ("Black", "Noir"),
        T + "mat.none": ("None", "Aucun"),
        T + "size": ("Size", "Taille"),
        T + "label": ("Text", "Texte"),
        T + "label.tip": ("Under the digits (empty in Show mode: the round's name)", "Sous les chiffres (vide en mode Émission : le nom de la manche)"),
        T + "show_label": ("Show the text", "Afficher le texte"),
        T + "click": ("Players' click", "Clic des joueurs"),
        T + "click.tip": ("Anyone's right click starts / pauses it (organisers: Sneak + right click changes the pose)",
                          "Le clic droit de n'importe qui démarre / met en pause (organisateurs : accroupi + clic droit change la position)"),
        T + "redstone": ("Redstone", "Redstone"),
        T + "redstone.tip": ("What a redstone signal on the tablet does", "Ce que fait un signal redstone sur la tablette"),
        T + "redstone.off": ("Nothing", "Rien"),
        T + "redstone.toggle": ("Start / pause", "Démarre / pause"),
        T + "redstone.run_while_powered": ("Runs while powered", "Tourne tant qu'alimentée"),
        T + "redstone.restart": ("Restarts", "Relance"),
        T + "round_start": ("Starts with the round", "Démarre avec la manche"),
        T + "round_start.tip": ("Restarts when a round starts (/craftMasterManche start, the voice)",
                                "Repart de zéro au lancement d'une manche (/craftMasterManche start, la voix)"),
        T + "round_end": ("Stops at round end", "S'arrête en fin de manche"),
        T + "end_sound": ("End sound", "Son de fin"),
        T + "endsound.none": ("None", "Aucun"),
        T + "endsound.beep": ("Beep", "Bip"),
        T + "endsound.bell": ("Bell", "Cloche"),
        T + "endsound.gong": ("Gong", "Gong"),
        T + "endsound.buzzer": ("Buzzer", "Buzzer"),
        T + "blink": ("Blinks at the end", "Clignote à la fin"),
        T + "signal": ("Comparator at the end", "Comparateur à la fin"),
        T + "signal.tip": ("A comparator reads 15 once the timer is over (doors, lamps, a gong…)",
                           "Un comparateur lit 15 quand le minuteur est fini (portes, lampes, gong…)"),
        T + "group": ("Group", "Groupe"),
        T + "group.tip": ("Tablets of the same group start and stop together; /craftMasterTablet start <group>",
                          "Les tablettes d'un même groupe démarrent et s'arrêtent ensemble ; /craftMasterTablet start <groupe>"),
        T + "pose": ("Position", "Position"),
        T + "pose.stand": ("Standing", "Debout"),
        T + "pose.easel": ("On an easel", "Sur chevalet"),
        T + "pose.flat": ("Lying flat", "À plat"),
        T + "pose.wall": ("On the wall", "Au mur"),
        T + "tilt": ("Easel tilt", "Inclinaison"),
        T + "wall": ("Hung on a wall (place it on another face to change)", "Accrochée au mur (la reposer ailleurs pour changer)"),
        T + "pose_hint": ("Placing: on a wall = hung, on the ground = standing, sneaking = flat. Facing you, 16 directions.",
                          "Pose : sur un mur = accrochée, au sol = debout, accroupi = à plat. Tournée vers vous, 16 directions."),
    }
    for k, (e, f) in texts.items():
        g.en[k] = e
        g.fr[k] = f
