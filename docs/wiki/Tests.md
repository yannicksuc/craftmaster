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
- `StudioLogicTest` (décor du studio) : angles des projecteurs (conventions de Minecraft, aller-retour direction ↔
  angles, plus court chemin à travers ±180°, approche douce sans dépassement), chiffres à 7 segments et format du
  chrono, arguments de `/image` (5 × 3 par défaut, hauteur en 5:3, limites 16 × 9, adresses refusées), balancement
  amorti du gong.
- `StudioActionsTest` : `spotlight.follow` (surnom → pseudo, invité gardé tel quel, rien dans le journal),
  `spotlight.stop / on / off`, joueur manquant refusé.

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

