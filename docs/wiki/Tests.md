# Tests

## 1. Tests unitaires (automatiques, à chaque build)
`.\gradlew.bat test` — `src/test/java` :
- `CraftMasterDataTest` : import du fichier du plugin (clés entre guillemets, nombres en texte, monde Bukkit),
  ajout / retrait / renommage de tâches (les scores suivent, fusion), scores (fixer, ajouter, retirer), classement
  et égalités, saisons cachées, busts, format 2 du fichier écrit, score sur une tâche inconnue, fichier illisible.
- `MigrationTest` : fichier du plugin et fichier du mod 2.0 → format 2 (Partie 1, ids, manches archivées pour les
  tâches retirées, joueurs, busts, saisons cachées, scores publiés identiques), copie `config.yml.v1`, pas de
  seconde conversion.
- `UndoJournalTest` : annuler une / plusieurs modifications, modifications imbriquées = une entrée, rien de changé
  = rien de journalisé, échec = données intactes, limite de 50, état du direct annulé aussi.
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
  `BroadcastActionsTest` : actions `camera.*` / `broadcast.*` / `show.*` (transitions, champs manquants ou faux,
  valeurs par défaut), effets sans modifier les données ni le journal, une action fausse annule toute la liste.
- Lieux, travellings, intros (chantier E, suite) — `CameraLieuxTest` : groupes et clés (studio, manche, zone),
  numéros par groupe, références relatives au lieu actif (jamais le n° d'un autre lieu, rôle de la manche puis du
  studio), clés explicites, lieux dits en mots (« caméra 2 du studio », « caméra 1 d'Ensuri », « de l'équipe
  bleue »), molette dans une zone, changement de lieu. `CameraPathTest` : durée et arrivées, étapes atteintes,
  pauses immobiles, départ / arrivée adoucis, pas de saut (échantillons à 120 i/s), vitesse continue aux étapes,
  yaw par le plus court, zoom, points répétés. `MancheIntroTest` : minutage (durée des trajets), titre automatique,
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
souris et reste à voir à la main.

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
