# Tests

## 1. Tests unitaires (automatiques, à chaque build)
`.\gradlew.bat test` — `src/test/java` :
- `toy/ToyLogicTest` : objets à manipuler (physique, œuf fragile, golf, couleurs, décomptes, ballon, verre gradué).
- `CraftMasterDataTest` : import du fichier du plugin (clés entre guillemets, nombres en texte, monde Bukkit),
  ajout / retrait / renommage de tâches (les scores suivent, fusion), scores (fixer, ajouter, retirer), classement
  et égalités, saisons cachées, busts, format 2 du fichier écrit, score sur une tâche inconnue, fichier illisible.
- `MigrationTest` : fichier du plugin et fichier du mod 2.0 → format 2 (Partie 1, ids, manches archivées pour les
  tâches retirées, joueurs, busts, saisons cachées, scores publiés identiques), copie `config.yml.v1`, pas de
  seconde conversion.
- `UndoJournalTest` : annuler une / plusieurs modifications, refaire (dans l'ordre, vidé par une nouvelle
  modification), modifications imbriquées = une entrée, rien de changé
  = rien de journalisé, échec = données intactes, limite de 50, état du direct annulé aussi.
- `ScoreBoardTest` : écran de saisie de l'arbitre — la place donne le score de base, le +1/-1 suit le joueur, échange,
  en tête, joueur déplacé puis +1/-1 (placé d'abord), banc (ordre d'arrivée, insertion, ex aequo dernier, rien envoyé
  pour le banc), défaire / refaire de chaque geste, bornes -10..+10 sans réécrire un score déjà hors bornes,
  barème d'une épreuve sans points, liste des changements à envoyer. `ScoreEntryBoardTest` : le JSON de l'écran.
- `TabletClockTest` : tablette chrono — minuteur (pause, reprise, jamais sous 0), chrono, formats (25.7, 3:07, 1:07.42,
  dixièmes des 10 dernières secondes, arrondi vers le haut d'un compte à rebours), heure, durées tapées, réglages JSON.
- `PublishedScoresTest` : rien de publié avant la première publication, copie figée, écart non publié, plusieurs
  parties additionnées, persistance.
- `ActionsTest` : le schéma d'actions de la régie (préparation, manche suivante / en cours, scores par surnom,
  liste invalide annulée en entier, messages, `undo` en tête de liste, `say`, publication et révélation, joueurs,
  ordre des manches, parties, saison archivée en lecture seule, rideaux et décors, points de TP).
- `TaskActionsTest` (chantier D) : `task.set` (champs partiels, texte nettoyé, règles, cire en français, durée),
  dictée en plusieurs fois (`append`), valeurs invalides sans effet, fichier écrit / relu, ancien fichier sans
  tâche, annulation des textes, révélation hors journal d'annulation, révélée au lancement ou par enveloppe seulement,
  enveloppes pour des joueurs nommés ou tous.
- `TaskStoreTest` (chantier D) : révélations (la première gagne, par saison, sauvegarde, remise au secret),
  notes (ids, cachettes de bloc et d'entité, une trouvaille par joueur, réinitialisation), lecteurs, morceaux cachés
  à tour de rôle, texte déchiré qui se recolle (1 à 6 morceaux, équilibré), fichier illisible gardé, lignes IRC
  Twitch (chaîne, PING, PRIVMSG avec ou sans tags, `!tâche`, délai anti-spam).
- `WordArgumentTest` : accents sans guillemets, noms avec espaces, aller-retour des suggestions.
- `RegieTest` (régie vocale) : en-tête WAV 16 kHz mono, lecture d'un WAV avec bloc LIST, niveau du micro, JSON des
  actions (entiers gardés entiers), retrait des `say`, réponses du service (dont malformées, service absent),
  aller-retour des 5 paquets (contexte de 100 000 caractères accentués).

Service de la régie vocale (`tools/voice-regie`, Python) : `python -m pytest tests` — 62 tests (dont les phrases de réalisation TV du chantier E), faux client
Claude, aucun réseau : contexte (pseudos approchés et phonétiques, ambiguïté, manches par nom / numéro, règle
d'inactivité, contexte absent ou faux), validation des actions, forme de la requête Claude (préfixe mis en cache
identique quel que soit le contexte, outils stricts, repli), erreurs (pas de clé, Claude injoignable, refus),
serveur HTTP, mode hors ligne sur de vraies transcriptions. `tests/whisper_check.py` mesure Whisper sur des
phrases synthétisées (voix Windows française) : avec les pseudos en vocabulaire, 0,2 s par phrase sur la RTX 3070
(large-v3-turbo float16), pseudos et surnoms bien écrits sauf « arrobaz » / « l'or fine », rattrapés ensuite.
- `GalleryPlannerTest` : colonnes par rang, un tableau emporte son joueur, plans avant / arrière, jamais deux
  tableaux d'un même plan qui se croisent (500 ordres aléatoires), joueurs fixes, nouveaux venus, tableaux vides.
- `PortraitGeometryTest` : proportions bébé de vanilla, centre de gravité pondéré par les volumes, nœud de la
  ficelle à la verticale du centre de gravité (cadre incliné, bras tendu, cadre porté), coupe par la toile, effet
  fenêtre sans boîte.
- `PoseLibraryTest` : poses fournies, aller-retour JSON, lecture tolérante, fichier créé puis conservé, fichier
  illisible mis de côté.
- `PortraitAnimationTest` : les trois phases du réarrangement, progression monotone, durée bornée.
- Réalisation TV (chantier E) — `CameraListTest` : « 2 », « caméra deux », « cam 3 », noms, rôles (« plan large »,
  « plan sur le trône », « podium »), plan large par défaut, molette (suivante / précédente en boucle), numéro libre,
  champs normalisés. `CameraMotionTest` : caméra fixe immobile, respiration discrète et continue d'une image à
  l'autre, travelling qui avance puis s'arrête, panoramique des deux côtés, angles, plan qui suit devant le joueur.
  `FollowFramingTest` : azimut 0 devant le joueur, glissement par le plus court, départ et arrêt en douceur,
  mouvements lents et bornés, jamais deux fois le même cadrage d'affilée, seulement les cadrages qui ont la place
  (sinon le plus dégagé), préférence (devant quand il marche).
  `BroadcastActionsTest` : actions `camera.*` / `broadcast.*` / `show.*` (transitions, champs manquants ou faux,
  valeurs par défaut), effets sans modifier les données ni le journal, une action fausse annule toute la liste.
- Lieux, travellings, intros (chantier E, suite) — `CameraLieuxTest` : groupes et clés (studio, manche, zone),
  numéros par groupe, références relatives au lieu actif (jamais le n° d'un autre lieu, rôle de la manche puis du
  studio), clés explicites, lieux dits en mots (« caméra 2 du studio », « caméra 1 d'Ensuri », « de l'équipe
  bleue »), molette dans une zone, changement de lieu. `CameraPathTest` : durée et arrivées, étapes atteintes,
  pauses immobiles, départ / arrivée adoucis, pas de saut (échantillons à 120 i/s), vitesse continue aux étapes,
  yaw par le plus court, zoom, points répétés. `MancheIntroTest` : minutage (durée des trajets), coupes sur les temps de la musique (tempo, temps par plan, premier temps), lancement enchaîné gardé dans le fichier, titre automatique,
  actions `intro.*` (fichier, annulation, refus), `intro.play` en effet, nouvelles actions `camera.*`.
  Serveur : bloc « TV direction » de `scripts/server_smoke_test.py <hôte> <port rcon>` (avec LordFinn connecté).
- `StudioLogicTest` (décor du studio) : angles des projecteurs (conventions de Minecraft, aller-retour direction ↔
  angles, plus court chemin à travers ±180°, approche douce sans dépassement), chiffres à 7 segments et format du
  chrono, arguments de `/image` (5 × 3 par défaut, hauteur en 5:3, limites 16 × 9, adresses refusées), balancement
  amorti du gong.
- `StudioActionsTest` : `spotlight.follow` (surnom → pseudo, invité gardé tel quel, rien dans le journal),
  `spotlight.stop / on / off`, joueur manquant refusé.
- `CurtainClothTest` (chantier G) : rideau fermé = le modèle de bloc (plis de 4 px, z 6..8 / 8..10), rideau ouvert
  rassemblé contre les deux côtés, plis toujours jointifs et dans l'ordre pendant le glissement, moitiés symétriques,
  mouvement continu (moins de 3 px par quart de tick) et adouci, durée alignée sur les colonnes du serveur.
- `PoseEditingTest` (chantier G) : pile annuler / refaire (un glisser entier = une étape, pause ou relâché = nouvelle
  étape, refaire effacé par une nouvelle modification, limite), miroir (gauche ↔ droite, deux fois = identique, et
  vraie réflexion géométrique de chaque coin de chaque partie), copie symétrique d'un membre, remise à zéro, sens
  des glissers (ce qui est sous la souris la suit), angles bornés, et le **clic sur l'aperçu** : chaque partie
  trouvée sous son dessin (vue de face, tournée, vue de l'éditeur), la plus proche gagne, parties cachées
  ignorées, cadre seulement hors du personnage.
- `SkinFormatTest` (chantier G) : tailles acceptées (64, 128, 256, 512, 1024, multiples de 64, anciens formats
  2:1 HD) et refusées, sondes « bras fins » et copies de l'ancien format à l'échelle.

## 2. Test de bout en bout sur serveur (script)
Serveur de dev (`run-server/server.properties`) : `online-mode=false`, `enable-rcon=true`, `rcon.port=25692`,
`rcon.password=cmtest`, `level-type=minecraft:flat`, `pause-when-empty-seconds=0` (sinon le serveur vide se met en
pause et l'animation des rideaux attend), `difficulty=peaceful`.

```
.\gradlew.bat runServer
python3 scripts/server_smoke_test.py <ip de Windows vue depuis WSL>
```
79 vérifications, relançable : commandes du plugin (tâches, scores, accents, guillemets, alias, minuscules),
classement, saison cachée, rideaux (commande, redstone), chaise ; puis la v3 sur une saison « Smoke » recréée à
chaque fois : saison, joueurs et surnoms, manches (chrono, ordre, surnoms), rideau nommé, lancer / suivante / fin,
points par surnom, non publié puis publié, annulation, historique, chrono, décor enregistré et posé à l'ancre,
parties, contexte de la régie vocale (JSON), révélation (courte) puis rideau rouvert. La saison active d'avant est remise à la fin.
Un fichier de l'ancien format dans `run-server/config/craftmaster/` est converti au démarrage (vérifié).

## 3. En jeu (client de dev piloté par MCP Fabric)
`scripts/dev-client.ps1` lance le client sans voler le focus, sans son, fenêtre derrière les autres, connecté au
serveur de dev ; `-Second` lance Joueur2 (non admin, en français). MCP Fabric (dev uniquement, dans
`run/mods` et `run2/mods`, ignorés par git) donne captures d'écran et contrôle du joueur.

Vérifié en jeu (version 2.0.0, deux joueurs : LordFinn admin en anglais, Joueur2 non admin en français) :

| Fonction | Résultat |
|---|---|
| Modèles et textures des chaises (chêne, cerisier) et des rideaux (rouge, violet) | ✔ |
| S'asseoir (clic droit), hauteur des yeux assis (~1,43 bloc), se lever en s'accroupissant (devant la chaise) | ✔ |
| Busts enregistrés en visant la tête ; skins chargés (Notch, LordFinn, Dream) ; étiquettes « #1 Notch / 16 pts » | ✔ |
| Écran de classement : podium, têtes, barres, survol avec détail, sa ligne surlignée | ✔ |
| Mise à jour en direct de l'écran et des busts quand un score change | ✔ |
| Rideau : ouverture depuis le milieu, fermeture depuis les côtés (captures image par image) | ✔ |
| Télécommande : liaison par clic sur le rideau, ouverture / fermeture à 34 blocs | ✔ |
| Saison cachée : Joueur2 n'a ni écran ni classement chat, busts « #1 ? » ; révélation annoncée, skins revenus | ✔ |
| Commandes admin refusées à Joueur2 ; textes français | ✔ |

Vérifié en jeu (version 3, LordFinn admin en anglais puis Joueur2 non admin en français, captures MCP) :

| Fonction | Résultat |
|---|---|
| Lancer une manche : TP sur son point, titre « Round 1 » / « Manche 2 » + nom en sous-titre | ✔ |
| Chrono : barre de boss « Le Pont — 0:13 » qui se vide, rouge et décompte en grand les 10 dernières secondes | ✔ |
| Score : « LordFinn +4 (Finale) » dans la barre d'action, « +4 » en grand chez le joueur | ✔ |
| Révélation : titre, rideau, « 3rd — Ensuri / 2 pts » avec particules sur le bust, « 1st — Cyllu », feux d'artifice, rideau rouvert, busts mis à jour | ✔ |
| Écran admin : « · live scores », « +2 not published » ; écran joueur : classement publié, sans direct | ✔ |

Non vérifiés en jeu : les sons (le jeu de test est muet), le décor posé vu à l'écran (vérifié bloc par bloc dans le
test serveur), la touche K et Échap. Corrigé grâce à ces essais : le titre « Manche 1 — Le Pont » dépassait de l'écran (le nom passe en sous-titre), le
sablier ⌛ n'existe pas dans la police du jeu.

Bugs trouvés et corrigés grâce à ces tests : le siège refusé par le serveur (entité non sauvegardable), le clic
créatif sur un rideau qui empêchait de lier la télécommande, les points de tâches retirées absents du détail,
le format 26.3 des loot tables et des recettes, les accents refusés sans guillemets.

Pas automatisable avec les outils actuels (aucun outil MCP pour les touches ni les clics dans un écran) : la
touche K, les boutons ◀ ▶, la molette et Échap dans l'écran de classement, le survol de l'infobulle de la
télécommande. À vérifier à la main.

**Clics et touches simulés dans un écran (dev uniquement, chantier G)** : dans l'environnement de dev, le client
lit `run/dev-input.txt` (ou `run2/`) à chaque tick et envoie à l'écran ouvert les lignes `click x y [bouton]
[mods]`, `drag x y dx dy [bouton] [mods]`, `release x y`, `scroll x y n`, `key NOM [mods]` (coordonnées de
l'interface, bouton 0 gauche / 1 droit, mods 1 Maj, 2 Ctrl, 4 Alt ; `DevInput`). `scripts/mcp_session.py` a l'étape
`["!input", "click 351 106\nkey Z 2", délai]`. Hors dev, rien n'est lu. Le survol (infobulles) suit la vraie
souris et reste à voir à la main. Aussi : `hold NOM secondes` (touche tenue, ex. `hold SPACE 2` pour arracher le
sceau d'une lettre), `slowmo 4` (animations 4 fois plus lentes, pour capturer chaque phase ; `slowmo 1` remet),
`quit` (ferme le client de test proprement), `ears` (écrit dans le journal du client d'où écoutent le jeu et Simple
Voice Chat, la caméra, les yeux et le mode de `PlayerEars`), `control x y z yaw pitch` (regarder par une caméra posée
là, comme son bouton ; un écran ouvert en sort), `screenshot` (capture dans `screenshots/` comme F2, même sur l'écran
titre où l'outil `screenshot` de MCP Fabric refuse faute de monde), `hover x y` (position de souris lue par les
écrans qui la suivent, ex. la parallaxe de l'[écran d'accueil](Accueil.md) ; `hover` seul rend la vraie), `use secondes` (tient la touche « utiliser » dans le monde, même sans
écran ouvert : charger un lancer, gonfler un ballon, tirer à l'arc ; `stop_using_item` de MCP Fabric ne relâche que
côté client, le serveur ne voit pas le relâchement).

**Lettre de tâche** : `/tache apercu <manche>` puis, pour le sceau, `click` dessus puis `drag` vers le haut (il saute
au-delà de 26 pixels de lettre), ou `hold SPACE 2` ; captures avec `slowmo 3`. Tests JUnit : `TaskMarkupTest`,
`LetterMotionTest`.

### Chantier G : rideau fluide, éditeur de poses, skins HD (vérifié en jeu, captures MCP)

| Fonction | Résultat |
|---|---|
| Rideau 15 × 5 : ouverture et fermeture en continu (9 et 13 images à 0,1 s), plis rassemblés sur les côtés, bas qui suit le haut en penchant, se balance à l'arrêt | ✔ |
| Plis plus marqués sur les 16 couleurs (planche ×8), rideau fermé identique au modèle de bloc | ✔ |
| Deux joueurs voient le même glissement (LordFinn et Joueur2) | voir ci-dessous |
| Éditeur de poses : aperçu 3D (même modèle, skin, cadre), clic sur le bras gauche → « L. arm » choisi et teinté, glisser vers le haut → X −20° → −44°, molette +3°, C copie symétrique, Ctrl+Z, R remise à zéro, H cache le bras, Ctrl+glisser déplace le personnage, clic droit glissé tourne la vue, M miroir, Ctrl+Z / Ctrl+Y | ✔ (entrées simulées) |
| Curseurs qui suivent les gestes et l'annulation | ✔ |
| Skins HD par adresse : 256×256 (Steve, rayures fines et bandeau), 128×128 bras fins (Alex), ancien format 128×64 | ✔ |
| Écran du tableau : infobulles, libellés raccourcis | ✔ |

Non vérifiés en jeu : la cape (le serveur de test est hors ligne, aucun joueur n'a de cape), un rideau posé avant
cette version (son bloc-entité créé au chargement du tronçon), les infobulles de survol de l'aperçu, la vraie
souris et le vrai clavier AZERTY (Ctrl+Z est lu par la lettre tapée).

## 4. Régie vocale en jeu (sans micro ni clavier)
Personne ne parle pendant un test automatique : le client lancé avec `-PregieTrigger` surveille `run/regie-trigger/`.
Un `.wav` (16 kHz mono 16 bits, ex. `tools/voice-regie/tests/audio/*.wav`) qui y est déposé est « dit » : la régie
se comporte comme si la touche était tenue pendant sa durée (panneau d'écoute compris), puis le fichier devient
`.done`. Avec MCP Fabric pour les captures et RCON pour l'état du serveur.

Vérifié (client LordFinn, serveur de dev, service en mode `offline` puis `claude` sans clé, ActionExecutor provisoire) :

| Cas | Résultat |
|---|---|
| Touche `N` enregistrée (`key_key.craftmaster.regie:key.keyboard.n`), textes français | ✔ |
| Non-opérateur : « La régie vocale est réservée aux organisateurs », rien n'est envoyé | ✔ |
| Après `op` : le statut admin arrive tout seul (sans reconnexion) | ✔ |
| « Trois points pour Juste pour le pont » → +3 JustEdible (Pont), panneau vert, 0,65 s après le relâchement | ✔ |
| « Annule » → score revenu | ✔ |
| « Ouvrez les rideaux » → rideau ouvert ; « Manche suivante » → titre « Manche 2 — Tour » puis 3 | ✔ |
| Panneau : écoute (point rouge, durée, niveau), « ⏳ Compréhension.. », résultat 9 s | ✔ |
| Ordre non compris → rien fait, explication ; service arrêté → « injoignable » ; pas de clé → message clair | ✔ |

Non vérifié automatiquement : le vrai micro (personne ne parle), la touche physique, et la compréhension par le
vrai Claude tant que `ANTHROPIC_API_KEY` n'est pas définie sur la machine de test.

### Tableaux suspendus (vérifié en jeu, client de dev piloté par MCP Fabric)
| Fonction | Résultat |
|---|---|
| Cadre doré, fond velours rouge, personnage aux proportions bébé avec le skin du joueur ; étiquettes « #1 Notch / 16 pts » | ✔ |
| Poses fournies (face, trois quarts) ; jambes « dans la peinture » cachées par la coupe ; sortie par le dos vue de derrière | ✔ |
| Dos des tableaux : toile blanche, aucun membre qui dépasse ; de côté : cadre mince | ✔ |
| Ficelle droite et verticale, hauteurs automatiques différentes, balancement | ✔ |
| Effet Antichamber : personnage et pièce de velours vus par l'ouverture, parallaxe en trois quarts, profondeur et taille réglables (16 px / 140 %, 40 px / 200 %, 8 px / 100 %), rien de l'intérieur vu de côté, de dessous ni de dos | ✔ |
| Parties qui dépassent sous le cadre vues de dos : seules les parties hors du contour du cadre restent, sans tranche ni étiquette vue à l'envers | ✔ |
| Réarrangement à un changement de score (captures image par image) : avancer / reculer, glisser, revenir, ordre final | ✔ |
| Écran de réglages et éditeur de poses ouverts par commande (affichage) | ✔ |

Non vérifiable avec les outils actuels (pas de clic dans un écran) : modifier un réglage ou une pose à la souris,
enregistrer / écraser / supprimer depuis l'éditeur, le clic droit et le coup qui casse le tableau, le skin par
adresse (pas de serveur d'images de test). À vérifier à la main.

### Réalisation TV (chantier E, vérifié en jeu : 2 clients, le 2e en 1920 × 1080 et en mode diffusion)
Client de diffusion = `Joueur2` en spectateur, non admin (`dev-client.ps1 -Second -Size 1920x1080`), mode diffusion
activé à distance (`/tv broadcast Joueur2 on`) ; LordFinn admin pose les caméras par `/cam here` (via RCON
`execute as LordFinn`). Les captures sont exactement l'image envoyée à OBS.

| Fonction | Résultat |
|---|---|
| Trépieds (bois, laiton, corps noir, objectif, viseur, voyant rouge allumé à l'antenne, numéro sur les flancs) | ✔ |
| Mode diffusion : plus de main, barre, viseur, chat, toasts ; trépieds, joueur caméra et spectateurs invisibles | ✔ |
| Coupe sèche, fondu (noir), fondu enchaîné (ancienne image qui se dissout, captures image par image) | ✔ |
| Mouvement « travelling » / respiration | ✔ (sur plusieurs images) |
| Plan qui suit LordFinn, synthé automatique « LordFinn — Maître du jeu » avec visage | ✔ |
| Bandeau de manche (n°, nom, chrono, ligne d'or), rouge dans les 10 dernières secondes, retiré sans manche | ✔ |
| Points en direct « Ensuri +3 », bandeau des scores publiés (visages, rangs or / argent / bronze), bandes cinéma, synthé texte | ✔ |
| Générique d'ouverture (titre + sceau, saison et partie, fondus entre caméras, carte « CANDIDAT 1/4 ») et de fin (crédits, classement publié) | ✔ |
| Réalisation auto : coupe sur le marqueur puis retour au plan large, projecteur (« ouvre son enveloppe »), plan large au lancement de manche, caméra podium pendant toute la révélation | ✔ |
| `/diffusion camera 3` refusé à un non-admin, accepté une fois op | ✔ |
| Joueur caméra spectateur téléporté près du plan (envoyé à 200 blocs, ramené sous la caméra) | ✔ |

Non vérifié (outils sans touches ni clics, jeu muet) : touche F8, touches 1–9 et molette (même chemin que
`/diffusion camera`, vérifié), clic droit et coup sur un trépied, pose avec l'objet Caméra de studio (même code que
`/cam here`), le thème à l'oreille (fichier généré, durée 37 s vérifiée), la carte de la tâche (bouchon du chantier D :
l'appel `CurrentTask.showCard` est fait, la carte est dessinée par D).

## 5. Test d'ensemble après fusion des trois chantiers (v3)
Serveur et client de dev avec la version fusionnée (données + direct, tableaux, régie vocale) :

| Cas | Résultat |
|---|---|
| Test serveur `server_smoke_test.py` complet | ✔ 0 échec |
| Galerie de 4 tableaux (saison 3) : classement **publié** affiché, proportions bébé, cadres dorés | ✔ |
| Score donné sans publier : les tableaux ne bougent pas | ✔ |
| Publication : le tableau qui monte avance, glisse vers sa colonne, les autres se décalent | ✔ (captures image par image) |
| Révélation : titre court (« 9e » + « Steve — 3 pts »), halo doré sur le tableau du joueur annoncé | ✔ |
| Service vocal (mode hors ligne) avec le vrai contexte du serveur : « lord fine » → LordFinn, « annule », « ouvrez les rideaux », « manche suivante » | ✔ |
| Chaises vues de dos (dossier), rideau haut de plusieurs blocs (ourlet seulement en bas) | ✔ |

Bugs trouvés à cette étape et corrigés : le rafraîchissement général remettait chaque tableau sur le joueur de son
rang avant l'animation de publication (plus d'animation) ; le titre de la révélation débordait de l'écran avec un
long pseudo.

Compréhension par le vrai Claude vérifiée ensuite en mode abonnement (Claude Code, sans clé) : 4 ordres sur 4
justes, ~3,5 s chacun. Non vérifié : vrai micro et
touches, sons (jeu muet), clics dans les écrans (réglages des tableaux, éditeur de poses, flèches de l'écran K).

### Épreuves (chantier H, vérifié en jeu, serveur + 2 clients de dev pilotés par MCP Fabric)
Tests unitaires : `trial/ResultSheetTest` (classement, ex-aequo, barèmes, disqualifiés, valeurs tapées ou dites,
stockage), `trial/TrialToolsTest` (chronos, zones, buzzers, réglages et mémoire des props, poids),
`trial/CanvasPixelsTest` (traits, pinceau, pot de peinture, encodage), `show/JudgingActionsTest` (actions
`result.*`, `results.*`, `stopwatch.*` : barème appliqué en une annulation, feuille enregistrée et renommée).
Régie : `tests/test_offline.py` (phrases d'arbitrage hors ligne), `test_validate.py` (schéma).

En jeu (vérifié) : rendu des props (taille, rotation, rotation continue, nom), des trois tableaux (dessin, texte,
cadres), du socle (objet flottant + nom coloré), des buzzers allumés / éteints, du gros bouton enfoncé, de la
balance, de la malle ; dessin continu à la teinture (maintenir le clic) ; écran d'écriture du tableau ; écran des
réglages d'un prop (onglet Apparence) ; feuille de résultats (valeurs, DQ, places ex-aequo, points proposés,
rafraîchissement en direct après « apply » et « undo ») ; chrono en barre de boss ; essai complet d'un candidat
(prop « Départ » → chrono, prop « Arrivée » → arrêt, temps noté, objet donné, note, annonce du premier, signal
redstone) ; sortie / retour de zone ; buzzer (premier, second appui ignoré) ; carton rouge ; mètre ruban et
compteur (sur un coffre vanilla) avec les boutons « Noter pour » ; `/craftMasterName` avec couleurs.
Non vérifié en jeu (pas d'outil de clic dans les écrans) : changer d'onglet, boutons des écrans (Appliquer,
Enregistrer, Tester, DQ…) — leurs actions sont celles des commandes, vérifiées ; régie vocale réelle.

### Tâches, enveloppes, notes (chantier D, vérifié en jeu, MCP Fabric, LordFinn admin + Joueur2 non admin en français)
- Ouverture d'une enveloppe : arrivée, tremblement puis craquelure de la cire, moitiés qui tombent et miettes, rabat
  qui s'ouvre sur la doublure rouge, carte qui sort et se déplie, puis la carte lisible (en-tête, titre, règles en
  puces, durée, sceau) ; la tâche est notée « révélée par … ».
- Tâche secrète : Joueur2 voit l'enveloppe scellée « Tâche encore secrète » ; LordFinn la lit avec « Révéler à
  tous / À l'écran / Modifier ».
- Éditeur de tâches, sceau à cire, éditeur de notes : affichés (les clics dans les écrans ne sont pas pilotables
  par MCP : non vérifiés).
- Enveloppes posées sur des tables (cire rouge et or), ramassée par Joueur2 (message), ouverte, carte « Pour Joueur2 ».
- Carte en surimpression : `/tache show` (glisse, disparaît après la durée), `/craftMasterTwitch test`.
- Notes : sous un tapis (rien sans s'accroupir, trouvée accroupi), dans une bibliothèque, épinglée sur une face,
  double fond d'un coffre ; note déchirée en 2 à l'encre invisible et prise unique : vierge sans lampe, violette
  avec la lampe UV (morceau 2 manquant), recollée après le 2e morceau ; cachettes « prise par Joueur2 ».

### Décor du studio (chantier F, vérifié en jeu, client de dev piloté par MCP Fabric, serveur 25721)
Captures : dossier de travail de l'agent (non versionnées).

| Fonction | Résultat |
|---|---|
| Modèles et textures : trône, fauteuil, canapé (3 places jointes, accoudoirs aux bouts), lampadaire, lampe de bureau, suspension, enseigne « EN DIRECT » (éteinte / allumée), pupitres, sonnette, gong, table des prix, podium 1-2-3, trophée, poteaux à cordon, icônes dans la barre | ✔ |
| S'asseoir sur le trône (clic droit, entité `craftmaster:seat`) | ✔ |
| Canapé : les places se joignent / se séparent quand on ajoute ou retire un voisin | ✔ |
| Suspension qui tombe quand le plafond disparaît | ✔ |
| Projecteurs : faisceau, tache de lumière, couleur, `cfsp all here`, suivi de Joueur2 en mouvement (captures successives), bloc de lumière posé sous le joueur | ✔ |
| Chrono géant : durée avant le départ, temps qui défile, rouge les 10 dernières secondes, « 0:00 » rouge à la fin, bandeau « 1 · Le Pont » | ✔ |
| `/image <URL> 4 3` sur un mur : écran posé ; adresse refusée par le site → mire + « Image unavailable: HTTP 400 » ; `/image <URL>` en regardant l'écran : photo JPEG (2,5 Mo) affichée, image entière avec bandes | ✔ |
| Écran de réglages ouvert par clic droit (adresse complète, taille, image entière / étirée, mire) | ✔ |
| Pupitre nommé par une étiquette renommée ; nom « Ensuri / JustEdible / Cyllu » sur la plaque | ✔ |
| Table des prix : objet posé dans le quart visé, affiché à plat | ✔ |
| Sonnette : « ♪ LordFinn rang the bell », impulsion (état `powered`) ; gong : balancement image par image, `/cfgo` ; enseigne et gong par redstone | ✔ |
| Joueur2 (non admin) : `/image` et `/cfsp` inconnus | ✔ |

Non vérifiés en jeu : les sons (jeu muet), les clics accroupi (viser un projecteur, finir la manche au gong : pas
d'outil MCP pour s'accroupir), les clics dans l'écran de réglages (Enregistrer), les ordres vocaux des projecteurs
(tests unitaires et pytest seulement), la réduction des images de plus de 2048 px (vérifiée seulement par la photo
de 2,5 Mo), le cache disque entre deux sessions.


### Effets d'écran (vérifié en jeu : serveur 25821, LordFinn + Joueur2 dont un en 1920 × 1080 en mode diffusion)
- Les 16 effets un par un sur la vue de LordFinn (`/fx LordFinn <effet>`), puis des combinaisons (`judas`,
  `negatif+vignette`, six effets à la fois) : rendus, fondus (captures pendant le fondu : iris qui se ferme).
- Diffusion : `/fx diffusion fisheye+masque_rond+sepia`, `citrouille`, `vhs+vignette` sur le plan d'une caméra, avec
  un synthé et le logo : l'habillage reste net par-dessus.
- `citrouille` = la superposition vanille `textures/misc/pumpkinblur.png` (même chemin de rendu que la citrouille
  portée) ; le masque citrouille porté l'affiche par le jeu lui-même.
- Objets portés : les 12 sur des porte-armures et sur LordFinn vus par Joueur2 (face, côté, trois-quarts) ;
  `/fx porter … verrouille` (malédiction du lien sans reflet), `/fx retirer`.
- Images par seconde (`/essaifx`) : 30 / s avec ou sans six effets sur la fenêtre 1080p (fenêtre en arrière-plan,
  limitée à 30), 60 / s (vsync) avec sept effets sur le client 854 × 480.
- `scripts/server_smoke_test.py <hôte> [port RCON]` : section « fx » (commandes, refus, objets, composant).
- Non vérifié en jeu : clic droit pour mettre les lunettes (pas de clic par MCP ; testé par `/fx porter` et
  l'équipement), la régie vocale de bout en bout (pytest seulement), les recettes à l'établi.

## 6. Test d'ensemble après fusion des cinq chantiers (v4)
Serveur de dev + LordFinn (admin, créatif) + Joueur2 en spectateur et **en mode diffusion à 1920 × 1080** (l'image
que capte OBS), plateau monté par RCON : trône, canapés, pupitres, table des prix, trophée, lampadaire, chrono géant,
gong, enseigne EN DIRECT, buzzer, gros bouton, projecteur, rideau de scène, trois caméras (`/cam here`).
- Diffusion : logo, EN DIRECT, bandeau de la manche avec chrono, plan large / trône / suivi, fondu.
- Enveloppe (D → E) : LordFinn ouvre son enveloppe → animation complète puis carte ; la réalisation automatique
  coupe sur lui avec le synthé « ouvre son enveloppe » ; le bandeau de la manche prend le titre de la tâche révélée.
- Rideau de scène ouvert à l'antenne (G) : glissement continu, faisceaux et trône découverts.
- Points en direct « Joueur2 +3 », publication → bandeau des scores, carte de la tâche (`/tv task`, en français
  sur le jeu français), générique d'ouverture (titre, puis présentation des candidats).
- Projecteur qui suit LordFinn (F), chrono géant lisible vu de près et depuis la diffusion.
- `scripts/server_smoke_test.py` : 0 échec. JUnit : 200 tests. pytest (régie) : 78 tests.

Corrigé pendant ce test :
- les pseudos flottaient au-dessus des têtes à l'antenne (en double avec le synthé) : masqués en mode diffusion ;
- les chiffres du chrono géant ne s'affichaient pas sur certains jeux : dessinés comme le panneau (passe opaque) ;
- le rideau s'ouvrait en moins d'une seconde : 8 ticks par colonne (environ 2 s pour 9 blocs) ;
- le bandeau des scores coupait des pseudos courts (« Dr… ») avec 6 candidats : le nom rapetisse avant d'être coupé ;
- un lien de skin de plus de 128 caractères était coupé à la réouverture des réglages d'un tableau ;
- la touche de la feuille de résultats (N) était déjà celle de la régie vocale : passée sur R ;
- « montre la tâche » : carte pour tout le monde ; « montre la tâche sur le stream » : sur la diffusion seulement.

Non vérifié (pas d'outil pour) : clics dans les écrans, touches (J, R, F8, 1–9, N), sons, vraie connexion Twitch,
régie vocale avec la vraie compréhension Claude (pas de clé API sur le PC).

## 7. Mobilier du serveur (modèles ItemsAdder de LordFinn)
Vérifié en jeu (serveur de dev, deux clients) : trône, chaise de candidat, 3 inclinaisons de lumière de studio,
softbox au sol et au mur, cadre au mur, trépied + barre, carrelage, paroi du globe ; un joueur assis sur le trône et
sur la chaise (bonne hauteur, vu d'un second client) ; clic droit sur une lumière (angle 3 → 4) ; caméra posée avec
`/cam here`, son nouveau trépied, coupe `/cam cut` en mode diffusion. Test serveur 0 échec, JUnit 200 tests.
Non vérifié : icônes dans l'inventaire / l'onglet créatif, softbox et cadre au plafond, rotation accroupi + clic.

### Tablette chrono (vérifié en jeu, serveur de dev + client LordFinn piloté par MCP Fabric)
| Fonction | Résultat |
|---|---|
| 4 positions (debout, chevalet, à plat, au mur) × 4 modes (minuteur 25.7, chrono, heure, chrono de l'émission) | ✔ (captures) |
| Mode Émission : suit le chrono de la manche (« 1:28 », « 3 · Château ») | ✔ |
| Écran de réglages (clic droit) : aperçu en direct, Relancer, changement de cadre vu tout de suite | ✔ |
| Redstone (front montant : relance un minuteur fini), `/craftMasterTablet`, StreamDeck `/tablet/start`, `?group=` | ✔ |
| Matières (chêne, noyer, laque, laiton, acier), dos et pied par matière, chevalet, chiffres sur la grille de pixels | ✔ (captures de face et de dos) |
| Sons de fin, comparateur, clic des joueurs non organisateurs | non vérifiés en jeu (jeu muet, pas de second client) |

### Écran de saisie, phase 1 (vérifié en jeu, clics et touches simulés, captures)
| Fonction | Résultat |
|---|---|
| Épreuve jamais notée : tout le monde sur le banc, marches au barème, rien en brouillon | ✔ |
| 5 clics sur le banc dans l'ordre d'arrivée → 5 cartes sur les marches 5-4-3-2-1 | ✔ |
| Glisser le 5e en 1er (repère, insertion), molette +1 (jeton sous la carte), Ctrl+Z / Ctrl+Y | ✔ |
| Échap puis réouverture : brouillon retrouvé (cases hachurées) | ✔ |
| Entrée : une seule modification « Steve 5 ; LordFinn 5 ; Notch 3 ; Alice 2 ; Bob 1 », bouton « Annuler : … » | ✔ |
| Survol (▲ ▼, infobulle), sons | non vérifiés (la vraie souris, jeu muet) |

## 8. Minecraft 26.2 (portage, vérifié en jeu le 12/11/2026)
Serveur `:26.2:runServer` (`run-server-26.2/`, jeu 25901, RCON 25902) + client `scripts\dev-client.ps1 -Version 26.2`
(`run-26.2/`, MCP Fabric 26.2 dans `run-26.2/mods/`, pont 25941), Fabric Loader 0.20.1, Fabric API 0.161.0+26.2.
- `.\gradlew.bat build` : 274 tests sur chaque version, deux jars.
- Serveur 26.2 : démarre, commandes (`/craftMasterHistory`, `/craftMasterRanking`, `/fx`, `/cam`, `/tv`, `/cfpt`),
  blocs du studio posés avec leurs données (trône, projecteur, chrono géant, enveloppe).
- Client 26.2 (captures MCP) : studio dessiné (trône, projecteur, chrono géant avec son bandeau, enveloppe) ; les 16
  effets d'écran compilent et s'affichent, HUD net par-dessus ; mode diffusion (vue de la caméra, HUD et mains
  masqués, « EN DIRECT »), coupe, fondu au noir, fondu enchaîné, écran partagé à deux tuiles ; tableau suspendu
  avec son étiquette. Fenêtre ouverte sans prendre le focus.
- Client 26.3 de la même branche : démarre jusqu'à l'écran titre sans erreur de mixin.
- Pas encore revérifié en 26.2 : écrans à clavier / souris (saisie des points, lettres, éditeur de poses : la lettre
  d'une touche passe par GLFW en 26.2), régie vocale, épreuves, rideaux, tablette chrono, StreamDeck, deux joueurs.

### Lancement enchaîné d'une manche (intro en rythme, noir, TP, titre, révélation)
Serveur `runServer` + client de dev (LordFinn), manche « Château » (chrono 90 s, un point de TP), intro de 3 plans
du studio (sépia, noir et blanc, vignette) sur le thème, `rythme theme`, `auto on`. Vérifié avec captures MCP :
- vue joueur : pendant l'intro le joueur reste au studio ; noir ; « MANCHE 3 » et « Château » tapés sur le noir (le
  cadre joueur se retire pendant le noir) ; l'image s'ouvre sur le point de TP, chrono lancé ;
- vue diffusion (F8) : plans coupés en rythme avec leurs effets, noir, titre, ouverture sur le plan large avec le
  bandeau de la manche (caché pendant la descente au noir) ; `/tv stop` pendant l'intro saute au noir ;
- `/tv intro <m> voir` donne « 3,2 s (6 temps) » par plan.
Pas vérifié : un joueur sans le mod (cécité + titre), plusieurs joueurs et plusieurs points de TP, le son des
touches de la machine à écrire et le calage à l'oreille sur le thème.

## 9. Retours de LordFinn sur le mobilier du studio (vérifié en 26.2 le 12/11/2026)
Serveur `:26.2:runServer` (jeu 25951, RCON 25952) + client 26.2 (pont MCP 25991), WaterMedia 3.0.1 et WaterMedia
Binaries 3.0.1 dans `run-26.2/mods/`. Plateau posé par RCON, captures MCP.

| Vérification | Résultat |
|---|---|
| Gong : cadre en chêne noir du jeu (bûche écorcée, planches), plus de bout jaune | ✔ |
| Lampe de bureau : socle et tige en laiton, abat-jour vert ombré normalement, lumière dessous | ✔ |
| Suspension : rosace, chaîne dorée continue jusqu'à l'abat-jour à deux étages, ampoule | ✔ |
| Enseigne « EN DIRECT » : côtés et dessus dorés ; icône entière dans l'inventaire | ✔ |
| Lampadaire : icône entière dans l'inventaire | ✔ |
| Chrono géant : « CraftMaster » dans son bandeau (tailles 1 et 4), plus de support qui dépasse ni de scintillement en bas | ✔ |
| 3 pupitres côte à côte : un seul long pupitre ; clic droit : « Tiroir du pupitre » (9 cases) | ✔ |
| 3 tables des prix côte à côte : une seule table sous un drapé ; clic sur le drapé : étagère (27 cases) ; clic sur le dessus : le prix se pose (données du bloc vérifiées) | ✔ |
| `craftmaster:trophy` (alias) : donne une tête de joueur | ✔ |
| Icônes clap, caméra, télécommande, tableau dans la barre | ✔ |
| Écran du studio + MP4 direct : la vidéo joue ; `/image pause` fige l'image, `/image play` repart | ✔ |
| Écran du studio + lien YouTube (WaterMedia) : la vidéo joue | ✔ |
| Adresse en 404 : « Vidéo indisponible : HTTP 404… » sur la mire | ✔ |
| Son des vidéos selon la distance, deux joueurs synchronisés | non vérifié (jeu muet, un seul client) |
| `podium_first` donné : bloc de quartz (alias) ; anciennes lunettes floues données : deviennent `craftmaster:glasses` (effet « flou », verres bleus) | ✔ |
| Cordon des poteaux : une face par côté, plus de scintillement ; poteau en laiton sans taches d'ombre | ✔ |
| Buzzers en textures vanilla ; appui : allumé ; bloc de redstone à côté : remis à zéro ; mode `free` : clignote 1 s puis s'éteint ; menu `/craftMasterBuzzer mode` dans le chat | ✔ |
| Compteur sur un wagonnet à coffre et un porte-armure (API de transfert, entités) | ✔ (utilisé ; total en barre d'action) |
| Mètre ruban entre deux points visés au sol : « 5,1 m » au milieu, ruban jaune 10 s | ✔ |
| Ardoise, tableau blanc, toile : cadres en bois / fer vanilla de 2 px, auget ; ardoise mate sans poussière | ✔ |
| Socle d'exposition en quartz et laine rouge ; pupitre en sapin, laine rouge, baguette dorée | ✔ |
| Chaises chêne / cerisier / biscornue en 16 directions (vue de dessus), modèle validé par LordFinn | ✔ |
| Icônes : porte-bloc, lampe torche UV, cachet, chronomètre rond, mètre, compteur, tableaux, enveloppe et lettre de LordFinn | ✔ |

## 10. Garde-robe (vérifiée en jeu 26.2 le 12/11/2026)
Serveur `:26.2:runServer` (jeu 26061, RCON 26062) + client 26.2 (pont MCP 26071), Selfexpression
2.21.4-fabric.1+26.2 dans les `mods/` des deux, clics par `run-26.2/dev-input.txt` (`click`, `type`).
- Unitaires : `DressingTest` (tenue entière, pièce seule, mélange, pièce enlevée, reprise, rangement), `OutfitSearchTest`
  (accents, mots français → noms anglais, mots entiers) ; `gradlew build` : 307 tests sur chaque version (`OutfitColorsTest` : couleurs des pixels, contour ignoré ;
  `OutfitTagsTest` : métiers, thèmes, couleurs, mots entiers, parties du corps, tags
  de l'organisateur).
- En jeu : armoire posée (deux moitiés, portes ouvertes pendant l'écran), 265 tenues (20 prêtes + ensembles du jeu),
  11 tenues complètes enfilées une à une (médecin, policier, chevalier, roi, bouffon, mafieux, capitaine, mineur,
  cowboy, pilote spatial, forgeron : pièces vérifiées par `data get entity … equipment`, copies marquées), chapeau
  seul par-dessus le médecin (« Tenue mélangée »), favori (★ en tête), Mes affaires, recherche « chapeau noir »,
  « policier », cases des pièces portées (clic droit : chapeau enlevé, le casque d'avant revient ; clic : défile
  jusqu'à Capitaine, recherche vidée), ⇄ (policier → mes affaires → policier tête nue, comme avant), poubelle (vêtements rendus), Maj + poubelle
  (confirmation : Non revient à l'écran, Oui supprime tout), panneau des tags (Métier, Thème… avec leurs
  nombres ; « Médiéval » : 11 tenues), tenue supprimée restée en gris puis remise d'un clic, tags des 265 tenues relus dans `wardrobe-tags.txt`
  (toutes ont au moins une couleur ; Ouvrier du BTP : Jaune, Bleu, Marron), infobulle à 3 tags colorés + « Maj », mode Modifier (renommer → liste propre, Remettre toutes les tenues), type rangement (vraie armure
  rangée puis remise).
- Pas vérifié en jeu : Emotecraft / Player Animation Library et Straw Statues (pas dans les mods de test ; aucune
  mixin de rendu, les vêtements sont de l'armure ordinaire), deux joueurs à la même garde-robe, 26.3 en jeu.

## 11. Objets à manipuler (vérifié en jeu 26.2 le 12/11/2026)
Tests unitaires : `toy/ToyLogicTest` (rebond, hauteur de chute équivalente, œuf qui casse ou non, charge d'un
lancer, trou de golf rapide / lent / à côté, couleurs et accords, décomptes par joueur, ballon et limite, verre
gradué : verser, reprendre, déborder, trait).

En jeu (serveur + client 26.2 de ce worktree, jeu 25961, RCON 25962, pont MCP 25981, captures MCP) :
| Fonction | Résultat |
|---|---|
| Canards de 7 couleurs posés sur l'eau : ils flottent à plat ; sur une dalle, dans un pot, derrière un bloc | ✔ |
| Canard dans un canal : le courant l'emmène au bout (x 14,5 → 20,8) | ✔ |
| Candidat (non op) qui ramasse deux canards cachés : dans son inventaire, `/craftMasterToys` « trouvé(s) 2 : 1 canard en plastique, 1 canard en plastique rouge », 7 encore cachés | ✔ |
| `/craftMasterToys list` (coordonnées cliquables) et `glow` (contour à travers les murs) | ✔ |
| Lancer chargé (`use 1.5`) : distance notée au repos, record | ✔ |
| Sachet lancé dans le seau-cible : « Dans le seau ! (3,4 blocs · 3) », 3 prises notées à LordFinn | ✔ |
| Objet tombé, boule de neige et œuf du jeu dans la poubelle et la tasse | ✔ |
| Compteur sur le seau-cible (« 3 objet(s) ») | ✔ |
| Ballon gonflé en main (taille affichée, modèle qui grossit), une pile n'en gonfle qu'un, attaché sur un bloc, crevé d'un coup : confettis bleus, bloc de redstone deux blocs dessous | ✔ |
| Œuf fragile : survit à 1,5 bloc, casse à 5 ; coquille fine : casse à 0,7, tient posé | ✔ |
| Golf : putt « force 100 % », balle au trou, « dans le trou en 1 coup » | ✔ |
| Verre doseur : 2 fioles = 500 mL ; cuve : seau = 1 000 mL, eau au-dessus = + 300 mL en 3 s ; niveau visible | ✔ |
| Eau qui coule sur le verre : il reste (récipients forcés « solides », l'eau emportait le verre) et se remplit (400 mL) | ✔ |
| Trait tracé accroupi à 400 mL, + une fiole : « 650 mL / 1 000 mL · trait 400 mL (+250 mL) » | ✔ |
| Distributeur chargé de balles + signal : il en lance une, qui roule loin | ✔ |

Non vérifiés en jeu : l'onglet créatif (pas d'inventaire par MCP), les recettes à l'établi, une flèche sur un ballon, deux joueurs (messages d'un candidat vus par un
organisateur : vérifiés par le journal `/craftMasterTrial log`), le client 26.3 en jeu (seulement compilé et testé).

## 12. Portes de garage et WorldEdit (vérifié en jeu sur 26.2 le 13/11/2026)
Serveur `:26.2:runServer` (jeu 25931, RCON 25932) avec WorldEdit 7.4.5 dans `run-server-26.2/mods/` + client
LordFinn `scripts\dev-client.ps1 -Version 26.2 -Server localhost:25931` (pont MCP 25971). Porte de 3 × 3 en béton
rouge entre deux piliers de quartz, moteur au milieu du linteau.
- Détection : « Porte trouvée : 3 × 3 (9 blocs) » ; glissement continu et adouci, chaque rangée rentre dans le
  linteau (captures à 0,6 et 1,2 s par bloc) ; même teinte et même ombrage que les vrais blocs.
- Joueur dans l'embrasure à la fermeture : poussé hors du plan juste avant l'arrivée du panneau (z 5,6 → 6,4).
- Bouton sur un pilier : ouvre et la porte reste ouverte ; levier : ferme, le couper (> 2 s) rouvre.
- `/cfga nom`, `/cfga porte atelier close`, Stream Deck `/garage/toggle?name=atelier` et `/garage/close`.
- Moteur camouflé en quartz : invisible dans le linteau.
- Serveur arrêté en pleine ouverture : au redémarrage la porte finit de s'ouvrir, plus aucune barrière.
- WorldEdit : `/cfwe` montre la session « neighbors off, update off » dès la connexion ; `//set sand` en l'air laisse
  le sable flotter (un bloc posé à côté le fait tomber, comme prévu).
- Non vérifié : client 26.3 en jeu (compilé et testé unitairement), deux joueurs, très grande porte, `/cfwe perf on`
  suivi d'une reconnexion.

### Show gelé après un crash (vérifié en jeu sur 26.2, le 13/11/2026)
Serveur `:26.2:runServer` (jeu 26201, RCON 26202) + LordFinn (admin et candidat) et Joueur2 (`dev-client.ps1
-Version 26.2`, MCP Fabric). Saison de test avec les deux candidats, manche « Pont » (chrono 10:00), une tablette
chrono de 5:00, un chrono candidat pour Joueur2, une vache.
- Manche lancée, `save-all` (comme la sauvegarde auto), 15 s plus tard serveur tué (`taskkill /F`) : dernier
  `reprise.json` à moins d'une seconde du crash (9 819 ticks restants, 108,95 s pour Joueur2, tablette 32,2 s).
- Redémarrage : « Show gelé après le redémarrage » ; `tick query` : jeu gelé ; statut « Chrono de manche : 8:11 (en
  pause) », « Joueur2 1:49,0 », « Manquent : LordFinn, Joueur2 » ; `reprendre` refusé.
- LordFinn revient : statut dans son chat, sprint de 3 s sans bouger, vache immobile, barre blanche figée,
  « ⏸ Pause » en gris ; tablette 4:28 (= 300 − 32,2 s). Joueur2 revient : « tout le monde est là [Reprendre] ».
- 10 s plus tard, rien n'a bougé ; `reprendre` : 10 s après, 8:11 → 8:01, tablette 4:28 → 4:18, Joueur2
  1:49,0 → 1:59,3 ; la vache et les joueurs bougent.
- Tablette hors des chunks chargés (plus personne à côté) : gardée dans `reprise.json` et qui compte ; crash, puis
  redémarrage : « 1 hors des zones chargées », puis 4:34 exactement quand LordFinn revient (le monde, lui, avait
  gardé un temps faux). `reprendre force` avec Joueur2 absent : accepté.
- Arrêt propre (`stop`) en pleine manche : état écrit à l'arrêt, show gelé au redémarrage.
- Corrigé grâce à ces essais : le joueur qui se connecte se comptait parmi les absents ; une tablette dont le chunk
  se déchargeait disparaissait de la sauvegarde.
Pas vérifié en jeu : le Stream Deck `/resume`, les buzzers (testés en unitaire, `RepriseTest`), les dégâts bloqués.

## 13. Régie IA (vérifiée en jeu sur 26.2 le 14/11/2026)
Serveur `:26.2:runServer` (jeu 26301, RCON 26302) avec la scénographie du plateau (`docs/design/plateau-emission/config`)
+ client LordFinn `scripts\dev-client.ps1 -Version 26.2 -Server localhost:26301 -RegieTrigger` (pont MCP 26341)
+ service `python -m regie.service` (Whisper GPU, abonnement Claude via Claude Code dans WSL). Phrases dites par la
voix Windows (Paul), déposées dans `run-26.2/regie-trigger/`.

| Dit | Résultat | |
|---|---|---|
| « Rends l'ambiance tension plus rouge et ajoute un plan serré sur la galerie dans la révélation » | 7 `/ambiance modifier tension …` (contre #FF3030, rampe, face, table) ; révélation non touchée car la caméra 13 y est déjà à 7 s : il le dit et propose ; réponse en 16 s | ✔ |
| « Oui, ajoute le travelling galerie juste après, vers dix secondes » | clé caméra `travelling:galerie` à 10 s, en enchaîné, dans `revelation` (seules `tension` et `revelation` changées) | ✔ |
| « Supprime l'ambiance pause » puis « Oui, je confirme » | 1re demande : rien de fait, [Confirmer] [Refuser] dans le chat ; 2e : supprimée | ✔ |
| « Annule ce que l'agent vient de faire » | `pause` revenue à l'identique | ✔ |
| Clic [Annuler] (`/regieia annuler <demande>`) | déjà annulée : « Rien à annuler » ; sinon état d'avant remis | ✔ |
| « Ajoute une caméra 15 qui filme le trône de près, un peu en plongée, et appelle-la trône serré » | `/cam placer 15 …` (5 blocs, 2 plus haut, zoom 30), nommée | ✔ |

Icône et bouton (Simple Voice Chat 2.6.27+26.2 sur le client et le serveur de dev, menu ouvert par
`open de.maxhenkel.voicechat.gui.VoiceChatScreen` dans `run-26.2/dev-input.txt`, captures MCP) : bouton à sa place
après « volumes », clic → coupé (barre rouge, `regieAgent: false`), une demande dite alors n'est pas envoyée à
l'agent et aucune icône ne s'affiche ; activé : étincelle + barres pendant la parole, étincelle clignotante
pendant le travail, à côté de la place de l'icône de Voice Chat. ✔

Tests automatiques : `AgentBridgeTest` (noms entre guillemets, lignes de conduite refusées, adresses masquées) ;
`tools/voice-regie/tests/test_agent.py` (relais aller-retour, outil inconnu, jeu parti, routage `agent.request`).

## 14. Halos et faisceaux des projecteurs sur le sol (vérifiés en jeu sur 26.2 le 14/11/2026)
Serveur `:26.2:runServer` (jeu 26401, RCON 26402) + client LordFinn `scripts\dev-client.ps1 -Version 26.2 -Server
localhost:26401` (pont MCP 26341). Décor : sol de tapis rouge, lit, dalles, planches, marches, mur, six projecteurs
suspendus (cônes 3 à 12°, inclinaisons 25 à 89°, trois couleurs). « Vidéos » : rafales de captures MCP le long de
trajets de caméra (`tp` par RCON entre deux captures), regardées en planche-contact.

| Cas | Résultat | |
|---|---|---|
| À hauteur d'homme en longeant le lit, halo au pied du lit | halo lisse, faisceau éteint avant le tapis et le lit | ✔ |
| Dans le faisceau vertical, en montant et en tournant | pas de dents ni de coupure, que l'on soit dedans ou dehors | ✔ |
| Au ras du tapis (œil à 0,35 bloc) en reculant de 2 à 30 blocs | halo stable à toutes les distances | ✔ |
| Tour du décor à 32 blocs, en montant | halos et faisceaux propres | ✔ |
| Travelling de 5 cm par image à 20 blocs, angle rasant, différence image à image | seuls les bords des blocs bougent, aucun grésillement sur les halos | ✔ |

Corrigé : le halo flottait d'un ou deux centièmes de bloc au-dessus du sol, trop peu selon la distance et l'angle
(il se battait avec le tapis) : il est désormais avancé dans le test de profondeur comme les fissures de bloc
(`SpotLightRenderType.pool()`). Le faisceau était coupé net par le sol, une marche ou un lit, en dents de scie selon
le point de vue : chaque facette du cône est tracée jusqu'au bloc qu'elle rencontre et s'éteint juste avant
(`BeamEdges`). Non vérifié : avec un pack de shaders Iris (le halo garde alors son petit décalage).

## 15. Écoute depuis le joueur dans les vues de caméra (vérifiée en jeu sur 26.2 le 14/11/2026)
Serveur `:26.2:runServer` (jeu 26601, RCON 26602) + LordFinn (`dev-client.ps1 -Version 26.2`, pont MCP 26641) et
Joueur2 (`runClient2`, pont MCP 26642), Simple Voice Chat 2.6.27+26.2 dans les `mods/` des trois (enlevé après).
LordFinn en (0,5 ; 100 ; 0,5), Joueur2 à 2 blocs, caméra 1 à 40 blocs (`/cam placer 1 40.5 105 0.5 vers …`). Mesure
par la ligne `ears` de `dev-input.txt` : écoute du jeu (`SoundManager.getListenerTransform()`), état de la caméra de
Voice Chat (`ClientManager.getCameraState()`, qui sert au volume, à la direction OpenAL et à la stéréo des voix).

| Vue | Caméra | Écoute du jeu | Voice Chat | |
|---|---|---|---|---|
| Normale | yeux | yeux | yeux | ✔ |
| F7 imposé (`/tv flux forcer`), caméra 1 à l'antenne | (40,5 ; 105 ; 0,5) | yeux (0,5 ; 101,6 ; 0,5) | yeux, vers +Z | ✔ |
| F7, le joueur tourne à 90° | caméra 1 | yeux, vers −X | yeux, vers −X, yRot 90 | ✔ |
| Regarder par une caméra (`control 40.5 105 0.5 90 10`) | (40,5 ; 105 ; 0,5) | yeux | yeux, orientation d'avant (tangage 0, pas 10) | ✔ |
| Aperçu d'un travelling de 20 s (`/cam trajet t1 apercu`) | sur le trajet, ~31 blocs | yeux | yeux | ✔ |
| Mode diffusion F8 (`/tv broadcast LordFinn on`) | caméra 1 | caméra 1 | caméra 1 | ✔ (voulu) |

Non mesuré en jeu : une vraie voix (les clients de test n'ont pas de micro : Voice Chat « désactivé », la position
mesurée est celle qu'il utilise pour toutes les voix), le son lui-même (jeux de test muets), et l'image d'après une
prise de vignette (l'écoute y garde sa place ; ça ne dure qu'une image).

## 16. Projecteurs sous shaders, sur une copie du vrai plateau (vérifiés en jeu sur 26.2 le 14/11/2026)
Installation réutilisable :
- **Serveur** `:26.2:runServer` : jeu 26401, RCON 26402 (mot de passe `cmtest`), `online-mode=false`.
- **Monde** `level-name=plateau` : copie du monde `craftmaster` du vrai serveur (`save-all flush`, puis tar de
  `craftmaster/` et `config/craftmaster/`).
- **Configuration** : celle du vrai serveur, **sans `site.json`**, sinon le serveur de test se brancherait sur la vraie
  régie du site. La configuration de dev d'avant est gardée dans `config/craftmaster.dev-sauvegarde`.
- **Client** `scripts\dev-client.ps1 -Version 26.2 -Server localhost:26401` (pont MCP 26341), avec dans
  `run-26.2/mods` les mêmes Sodium et Iris que l'instance, et Complementary Reimagined r5.9.3 activé
  (`run-26.2/config/iris.properties`).

| Cas (ambiance de la salle, nuit, vues prises avec `tp`) | Avant | Après | |
|---|---|---|---|
| Plan large depuis la caméra 1 | faisceaux qui se masquent entre eux | faisceaux nets | ✔ |
| Plongée sur la table, 55° | halos découpés en croissants, dents de scie | halos doux en dégradé | ✔ |
| Au ras du tapis entre les chaises | éclats qui clignotent | rien | ✔ |
| Poursuite qui suit LordFinn | lumière derrière lui | flaque centrée sous ses pieds | ✔ |

Cause : sous un pack de shaders, la lumière était dessinée avec le type des éclairs de vanilla, qui écrit la
profondeur. Elle passe désormais par le type « yeux », émissif et sans écriture de profondeur. Le type du faisceau de
balise a aussi été essayé : Complementary en faisait des disques plats à bords nets. Les facettes du faisceau
s'arrêtent au plus tard là où le bord du cône touche le sol.

## 17. Plan qui suit un joueur (vérifié en jeu sur 26.2 le 14/11/2026)
Serveur `:26.2:runServer` (monde plat, décor fait au RCON : salle de 4 blocs de haut avec poteaux et fenêtre,
porte basse, couloir de 2 de large et 3 de haut, pièce de 2 blocs sous plafond, piliers dehors) ; LordFinn
(`dev-client.ps1 -Version 26.2`) marche au pathfinding MCP de dehors à la salle, au couloir puis à la pièce basse ;
Joueur2 en spectateur, `/diffusion on`, `/cam follow LordFinn`, captures MCP en rafale (40 images à 1,1 s).
Prise en main : `camturn <dx> <dy>` et `camwheel <crans>` dans `run2-26.2/dev-input.txt` (mêmes entrées que la
souris et la molette). Journal de dev `[Suivi]` : chaque cadrage choisi et pourquoi.

| Vérifié | |
|---|---|
| Joueur immobile dehors : trois-quarts → plongée → gros plan → trois-quarts gauche, glissés, jamais deux fois le même | ✔ |
| Salle, porte basse, couloir, pièce sous plafond bas : jamais dans un bloc ni derrière un mur, le corps reste visible | ✔ |
| Couloir : cadrages dans l'axe (face, de dos), la caméra se décale autour de lui quand un mur gêne | ✔ |
| Porte basse : plus d'image « dans la tête » (changement immédiat, en fondu enchaîné si le chemin traverse un mur) | ✔ |
| Souris : tourne autour du joueur, monte / descend ; molette : rapproche / éloigne ; reste là tant qu'on y touche | ✔ |
| 8 s sans y toucher : reprise automatique en glissant vers un cadrage | ✔ |

Corrigé grâce à ces essais : changements de cadrage en rafale dans les passages étroits (4 en 12 s, maintenant au plus
un toutes les 3 s, et seulement vers un cadrage qui a vraiment plus de place) ; gros plans de nuque à l'entrée d'un
couloir (esquive autour du joueur, puis changement immédiat sous 0,9 bloc) ; coin de mur cachant le corps (rayon
poitrine → objectif).
Caméras posées (3 caméras `/cam placer` : salle, dehors, bout du couloir) : `/cam voir LordFinn` prend la caméra
de la salle quand il y est, celle du couloir quand il y est, le suit quand aucune ne le voit (poteau devant, caméra
évitée) ✔ ; `/cam eviter 1`, `cadrage large|serre`, `rester`, `auto on` répondent ✔ ; le suivi coupe de lui-même
sur la caméra de la salle (2 fois en 75 s) puis revient aux cadrages ✔. Corrigé : la part de l'image comptée en
aire sous-estimait un joueur en pied (rien n'était choisi) → hauteur occupée ; un joueur au bord du cadre ne compte
plus. Pas vérifié : la coupe sur qui parle (pas de Simple Voice Chat sur le serveur de dev ; même code que `voir`,
`SpeakerCuts` inchangé). `ShotCoverageTest` : plus près = plus grand, derrière ou hors cadre = rien, collé =
tout ; poids qui monte puis retombe ; caché par un bloc ; tirage pondéré ; caméra qui voit le plus de joueurs.
Régie vocale : `test_offline.py` (« on va aller voir Ensuri pour voir comment ça se passe », « reste sur Juste »,
« plus large », « évite la caméra 3 », « repasse en auto »…), `test_validate.py`.
Écran partagé avec une tuile qui suit (branche de l'écran partagé fusionnée ; `camaction <json>` dans
`run-26.2/dev-input.txt` envoie l'action comme les touches de diffusion) : `camera.split` [follow:LordFinn, 1, 3]
en « principal » : LordFinn suivi en grand avec ses cadrages, les caméras 1 et 3 en petit ✔ ; [follow:LordFinn, 2]
« bord à bord » ✔. Pas vérifié en jeu : l'écran partagé automatique des zones d'une manche et « on va voir » en
manche (il faut deux candidats dans deux zones : un seul joueur sur le serveur de dev).
Changements plus vifs (retour de LordFinn « trop lent à changer ») : `/cam follow @maitre`, puis
`/plateau maitre Joueur2` et retour à LordFinn : coupe sèche sur le nouveau dès l'image suivante (avant : jusqu'à 1 s
puis un fondu enchaîné), et le synthé « Joueur2 » ne reste plus sur LordFinn ✔. `SpeakerCutsTest` : une voix passe à
l'antenne après ½ s, une toux de 0,35 s ne coupe pas, un plan tient 2 s (3 s en groupe). Pas vérifié en vrai micro.

Suivi commun caméras / projecteurs : `/cam follow @maitre` suit LordFinn, cadré sans retard en courant (gros plan
tenu en marchant) ✔ ; projecteur suspendu en poursuite, LordFinn marche 3 s dans un sens puis dans l'autre de nuit,
vu par la caméra 2 : la flaque reste centrée sous ses pieds (avant, elle traînait derrière) ✔ ; poursuite
`@antenne` : vide au départ, LordFinn dès « /cam voir LordFinn » ✔. `TargetTrackerTest` : pas de retard sur un
joueur qui marche (le lissage seul traîne de v × tau), avance plafonnée, arrêt sans dépassement, téléportation,
saut non anticipé. Régie vocale : « suis le premier du classement », « suis qui est sur scène ».
Pas vérifié : la vraie souris (le `@Redirect` de `MouseHandler.turnPlayer` s'applique, le jeu démarre ; mêmes
entrées que `camturn`), les replays et les intros (même code, `DirectorCamera.follow`).
