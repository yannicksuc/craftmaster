"""Translations of the stage timeline editor (client/stage/TimelineScreen) and of the players' publish announcement.

Called by gen_assets.py; can also run alone (python scripts/gen_timeline.py) to merge these keys into the lang files
without regenerating everything else.
"""
import json
import os

P = "screen.craftmaster.timeline."
LANG = {
    "screen.craftmaster.timeline": ("Stage timeline", "Timeline de scène"),
    P + "prev": ("Previous timeline", "Séquence précédente"),
    P + "next": ("Next timeline", "Séquence suivante"),
    P + "rename": ("Click to rename", "Clic pour renommer"),
    P + "new": ("New timeline", "Nouvelle séquence"),
    P + "copy_seq": ("Copy", "Copier"),
    P + "copy_seq_tip": ("Duplicate this whole timeline", "Dupliquer toute cette séquence"),
    P + "delete_seq": ("Delete this timeline (click twice)", "Supprimer cette séquence (deux clics)"),
    P + "delete_sure": ("Sure?", "Sûr ?"),
    P + "length": ("Length %s", "Durée %s"),
    P + "length_tip": ("Wheel: ±1 s (Shift ±10) · click: type it", "Molette : ±1 s (Maj ±10) · clic : la taper"),
    P + "loop": ("Loop", "Boucle"),
    P + "loop_tip": ("Starts again at the end until stopped", "Recommence à la fin jusqu'à l'arrêt"),
    P + "play": ("▶ Play", "▶ Lire"),
    P + "stop": ("■ Stop", "■ Stop"),
    P + "play_tip": ("Plays on stage from the playhead (Space)", "Joue sur scène depuis la tête de lecture (Espace)"),
    P + "start": ("Back to the start (Home)", "Retour au début (Origine)"),
    P + "capture": ("● Capture", "● Capturer"),
    P + "capture_tip": ("The stage as it is now (set by hand, /cfsp…) into the selected keypoint, or a new one at the playhead",
                        "La scène telle qu'elle est (réglée à la main, /cfsp…) dans le keypoint choisi, ou un nouveau à la tête de lecture"),
    P + "mirror": ("Mirror", "Miroir"),
    P + "mirror_tip": ("The other side: a copy of the track for the other side (gauche ↔ droite, cour ↔ jardin), or its angles mirrored",
                       "L'autre côté : une copie de la piste pour l'autre côté (gauche ↔ droite, cour ↔ jardin), ou ses angles en miroir"),
    P + "reverse": ("Reverse", "Inverser"),
    P + "reverse_tip": ("Plays the selected keypoints (or the whole timeline) backwards", "Joue les keypoints choisis (ou toute la séquence) à l'envers"),
    P + "dup": ("Duplicate", "Dupliquer"),
    P + "dup_tip": ("Copies the selected keypoints right after them (Ctrl+D)", "Recopie les keypoints choisis juste après eux (Ctrl+D)"),
    P + "unsaved": ("new", "nouvelle"),
    P + "tracks": ("Tracks", "Pistes"),
    P + "type.lumiere": ("Light", "Lumière"),
    P + "type.rideau": ("Curtain", "Rideau"),
    P + "type.tableaux": ("Portraits", "Tableaux"),
    P + "type.ecran": ("Screen", "Écran"),
    P + "type.commande": ("Command", "Commande"),
    P + "add_track": ("Add a %s track", "Ajouter une piste %s"),
    P + "target_tip": ("What it drives: click / wheel to change, right-click to type it", "Ce qu'elle pilote : clic / molette pour changer, clic droit pour le taper"),
    P + "mute_tip": ("Mute: kept but not played", "Muette : gardée mais pas jouée"),
    P + "remove_track_tip": ("Remove this track", "Retirer cette piste"),
    P + "empty": ("No track yet: add one below (+ Light, + Curtain…)", "Aucune piste : ajoute-en une en dessous (+ Lumière, + Rideau…)"),
    P + "hint_add": ("Double-click a lane to add a keypoint", "Double-clic sur une ligne pour ajouter un keypoint"),
    P + "no_track": ("Add a track to start", "Ajoute une piste pour commencer"),
    P + "no_key": ("Select a keypoint", "Choisis un keypoint"),
    P + "many_keys": ("%s keypoints selected", "%s keypoints choisis"),
    P + "keys_count": ("%s keypoint(s)", "%s keypoint(s)"),
    P + "key_title": ("Keypoint · %s", "Keypoint · %s"),
    P + "number_tip": ("Drag or wheel to change (Shift: faster) · right-click: unchanged", "Glisser ou molette pour changer (Maj : plus vite) · clic droit : inchangé"),
    P + "cycle_tip": ("Click / wheel to change · right-click: unchanged", "Clic / molette pour changer · clic droit : inchangé"),
    P + "text_tip": ("Click to type", "Clic pour taper"),
    P + "text_hint": ("Enter: OK · Esc: cancel", "Entrée : OK · Échap : annuler"),
    P + "footer": ("Double-click: keypoint · drag: move (Ctrl: free) · Shift+click: several · Del · Space: play · Ctrl+C/V/D/Z · wheel: scroll, Ctrl: zoom",
                   "Double-clic : keypoint · glisser : déplacer (Ctrl : libre) · Maj+clic : plusieurs · Suppr · Espace : lire · Ctrl+C/V/D/Z · molette : défiler, Ctrl : zoom"),
    P + "field.temps": ("Time", "Temps"),
    P + "field.fondu": ("Fade", "Fondu"),
    P + "field.ouvert": ("Curtain", "Rideau"),
    P + "field.hauteur": ("String", "Ficelle"),
    P + "field.cascade": ("Cascade", "Cascade"),
    P + "field.montre": ("Shows", "Montre"),
    P + "field.montre_texte": ("Or", "Ou"),
    P + "field.commande": ("Command", "Commande"),
    P + "field.allume": ("On", "Allumé"),
    P + "field.couleur": ("Colour", "Couleur"),
    P + "field.intensite": ("Intensity", "Intensité"),
    P + "field.cone": ("Cone", "Cône"),
    P + "field.suit": ("Follows", "Suit"),
    P + "field.vise": ("Aims at", "Vise"),
    P + "field.effet": ("Effect", "Effet"),
    P + "field.vitesse": ("Speed", "Vitesse"),
    P + "field.vague": ("Wave", "Vague"),
    # The score entry screen's publish button
    "screen.craftmaster.scores.publish.count": ("Publish (%s)", "Publier (%s)"),
    "screen.craftmaster.scores.publish.none": ("Published", "Publié"),
    "screen.craftmaster.scores.publish.tip": ("Shows the validated scores to the players: portraits, busts, labels, Tab list, ranking, and each one's changes in the chat",
                                                   "Montre aux joueurs les scores validés : tableaux, têtes, étiquettes, liste Tab, classement, et ses changements à chacun dans le chat"),
    "screen.craftmaster.scores.publish.draft": ("Validate the round first", "Valide d'abord la manche"),
    # The spotlight's menu (client/studio/SpotlightScreen)
    "screen.craftmaster.spotlight": ("Spotlight", "Projecteur"),
    "screen.craftmaster.spotlight.title": ("Spotlight", "Projecteur"),
    "screen.craftmaster.spotlight.name": ("Name", "Nom"),
    "screen.craftmaster.spotlight.name_hint": ("face1… (Enter)", "face1… (Entrée)"),
    "screen.craftmaster.spotlight.lit": ("Light", "Lumière"),
    "screen.craftmaster.spotlight.on": ("On", "Allumé"),
    "screen.craftmaster.spotlight.off": ("Off", "Éteint"),
    "screen.craftmaster.spotlight.color": ("Colour", "Couleur"),
    "screen.craftmaster.spotlight.intensity": ("Intensity", "Intensité"),
    "screen.craftmaster.spotlight.cone": ("Cone", "Cône"),
    "screen.craftmaster.spotlight.direction": ("Aim", "Direction"),
    "screen.craftmaster.spotlight.follows_now": ("follows someone", "suit quelqu'un"),
    "screen.craftmaster.spotlight.turn_left": ("Turn left 15°", "Tourner à gauche de 15°"),
    "screen.craftmaster.spotlight.turn_right": ("Turn right 15°", "Tourner à droite de 15°"),
    "screen.craftmaster.spotlight.tilt_up": ("Raise 10°", "Lever de 10°"),
    "screen.craftmaster.spotlight.tilt_down": ("Lower 10°", "Baisser de 10°"),
    "screen.craftmaster.spotlight.flip": ("The other way round", "Dans l'autre sens"),
    "screen.craftmaster.spotlight.look": ("Where I look", "Où je regarde"),
    "screen.craftmaster.spotlight.look_tip": ("Points at the block under your crosshair", "Pointe le bloc sous ton viseur"),
    "screen.craftmaster.spotlight.me": ("At me", "Vers moi"),
    "screen.craftmaster.spotlight.me_tip": ("Points at you", "Pointe vers toi"),
    "screen.craftmaster.spotlight.follow": ("Follows", "Suit"),
    "screen.craftmaster.spotlight.nobody": ("nobody (fixed)", "personne (fixe)"),
    "screen.craftmaster.spotlight.effect": ("Effect", "Effet"),
    "screen.craftmaster.spotlight.speed": ("Speed", "Vitesse"),
    "screen.craftmaster.spotlight.angle_tip": ("Click to type it: horizontal vertical (e.g. 35 20), Enter", "Clic pour le taper : horizontal vertical (ex. 35 20), Entrée"),
    "screen.craftmaster.spotlight.hint": ("Wheel on a value · click outside: close", "Molette sur une valeur · clic dehors : fermer"),
    "screen.craftmaster.spotlight.control": ("Take control", "Prendre le contrôle"),
    "screen.craftmaster.spotlight.control_tip": ("Look through the spotlight and aim it with the mouse; click to keep, Esc to give up",
                                                 "Regarder par le projecteur et le viser à la souris ; clic pour garder, Échap pour annuler"),
    # The camera's menu (client/studio/CameraScreen) and taking control (ControlView)
    "screen.craftmaster.camera": ("Camera", "Caméra"),
    "screen.craftmaster.camera.title": ("Camera %s · %s", "Caméra %s · %s"),
    "screen.craftmaster.camera.name": ("Name", "Nom"),
    "screen.craftmaster.camera.role": ("Role", "Rôle"),
    "screen.craftmaster.camera.role_none": ("none", "aucun"),
    "screen.craftmaster.camera.role.large": ("wide shot", "plan large"),
    "screen.craftmaster.camera.role.trone": ("throne", "trône"),
    "screen.craftmaster.camera.role.podium": ("podium", "podium"),
    "screen.craftmaster.camera.role_tip": ("What the show uses it for: the wide shot, the throne, the podium", "À quoi le show la destine : plan large, trône, podium"),
    "screen.craftmaster.camera.zoom": ("Zoom", "Zoom"),
    "screen.craftmaster.camera.zoom_auto": ("auto", "auto"),
    "screen.craftmaster.camera.zoom_tip": ("Field of view: drag or wheel · right-click: the viewer's own", "Champ de vision : glisser ou molette · clic droit : celui du spectateur"),
    "screen.craftmaster.camera.motion": ("Motion", "Mouvement"),
    "screen.craftmaster.camera.motion.fixe": ("fixed", "fixe"),
    "screen.craftmaster.camera.motion.respiration": ("breathing", "respiration"),
    "screen.craftmaster.camera.motion.travelling": ("slow push-in", "avancée lente"),
    "screen.craftmaster.camera.motion.panoramique": ("slow pan", "panoramique"),
    "screen.craftmaster.camera.motion_tip": ("The small move it makes on air", "Le petit mouvement qu'elle fait à l'antenne"),
    "screen.craftmaster.camera.visibility": ("Prop", "Caméra"),
    "screen.craftmaster.camera.visible": ("Visible", "Visible"),
    "screen.craftmaster.camera.hidden": ("Hidden", "Cachée"),
    "screen.craftmaster.camera.hidden_tip": ("Hidden: no one sees it, except organisers holding a camera or the travelling marker",
                       "Cachée : personne ne la voit, sauf les organisateurs qui tiennent une caméra ou le clap"),
    "screen.craftmaster.camera.direction": ("Aim", "Direction"),
    "screen.craftmaster.camera.angle_tip": ("Click to type it: horizontal vertical (e.g. 35 20), Enter", "Clic pour la taper : horizontal vertical (ex. 35 20), Entrée"),
    "screen.craftmaster.camera.look": ("Where I look", "Là où je regarde"),
    "screen.craftmaster.camera.look_tip": ("It looks where you look (it stays in place)", "Elle regarde là où tu regardes (elle reste en place)"),
    "screen.craftmaster.camera.control": ("Take control", "Prendre le contrôle"),
    "screen.craftmaster.camera.control_tip": ("See what it sees and frame it with the mouse; click to keep, Esc to give up",
                        "Voir ce qu'elle voit et cadrer à la souris ; clic pour garder, Échap pour annuler"),
    "screen.craftmaster.camera.cut": ("On air", "À l'antenne"),
    "screen.craftmaster.camera.cut_tip": ("The broadcast cuts to this camera", "La diffusion passe sur cette caméra"),
    "screen.craftmaster.camera.hint": ("Wheel on a value · click outside: close", "Molette sur une valeur · clic dehors : fermer"),
    "hud.craftmaster.control.camera": ("Camera « %s »", "Caméra « %s »"),
    "hud.craftmaster.control.spot": ("Spotlight", "Projecteur"),
    "hud.craftmaster.control.hint": ("Mouse: frame · click or Sneak: keep · Esc: give up", "Souris : cadrer · clic ou Maj : garder · Échap : annuler"),
    # The players' publish announcement (Show.announcePublished)
    "show.craftmaster.published.you": ("Scores published, what changed for you:", "Scores publiés, ce qui change pour toi :"),
    "show.craftmaster.published.total": ("Total %s pts", "Total %s pts"),
}


def generate(g):
    for k, (e, f) in LANG.items():
        g.en[k] = e
        g.fr[k] = f


if __name__ == "__main__":
    lang = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "craftmaster", "lang")
    for file, i in (("en_us.json", 0), ("fr_fr.json", 1)):
        path = os.path.join(lang, file)
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
        for k, v in LANG.items():
            data[k] = v[i]
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(data, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
    print("ok", len(LANG))
