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
- `WordArgumentTest` : accents sans guillemets, noms avec espaces, aller-retour des suggestions.
- `RegieTest` (régie vocale) : en-tête WAV 16 kHz mono, lecture d'un WAV avec bloc LIST, niveau du micro, JSON des
  actions (entiers gardés entiers), retrait des `say`, réponses du service (dont malformées, service absent),
  aller-retour des 5 paquets (contexte de 100 000 caractères accentués).

Service de la régie vocale (`tools/voice-regie`, Python) : `python -m pytest tests` — 50 tests, faux client
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

Non vérifié : compréhension par le vrai Claude (pas de clé `ANTHROPIC_API_KEY` sur le PC de test), vrai micro et
touches, sons (jeu muet), clics dans les écrans (réglages des tableaux, éditeur de poses, flèches de l'écran K).
