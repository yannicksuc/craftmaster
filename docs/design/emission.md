# Conception — CraftMaster v3 : outil d'émission

Objectif : tourner une émission complète CraftMaster (LordFinn + 4 créateurs, ex. JustEdible, Ensuri, Cyllu,
Arreaubaz) de façon fluide et spectaculaire, piloté à la voix ou à la souris, sans taper de commandes.

Référence de style : affiche CraftMaster (personnages qui sortent de cadres dorés, LordFinn sur un trône).
Mod Fabric 26.3, client + serveur. Code existant : voir docs/wiki/Developpement.md.

Ce document est la source de vérité partagée par les trois chantiers (A, B, C). Tout changement d'interface entre
chantiers se note ici.

---------------------------------------------------------------------------------------------------------------------

## 1. Modèle de données (chantier A)

```
Saison (id entier, nom, cachée, archivée)
 ├─ Joueurs de la saison : pseudo, surnoms[] (pour la voix), archivé
 ├─ Parties (id entier dans la saison, nom, archivée, statut préparation | en_cours | terminée)
 │   └─ Manches (ordre, id texte stable, nom, surnoms[], sur_scène (bool), scène (nom de décor ou rien),
 │               points de TP[], chrono en secondes (0 = aucun), archivée)
 ├─ Scores : saison → partie → manche → joueur → entier
 └─ Scores publiés : copie figée des scores au dernier « publier » (ce que voient joueurs, busts, tableaux)
État du direct (par serveur) : saison active, partie active, manche en cours, heure du dernier score donné.
Journal d'annulation : les 50 dernières actions avec leur inverse.
```

Règles :
- Total d'un joueur dans une saison = somme de tous ses scores de toutes les parties (archivées comprises :
  archiver = ranger / lecture seule, ne change pas les totaux). Supprimer = enlever pour de bon (annulable).
- **Scores publiés** : busts, tableaux, étiquettes, écran K des joueurs et classement chat des joueurs montrent les
  scores publiés. Les admins voient les scores en direct (avec l'écart non publié signalé). « Publier » copie les
  scores en direct vers les publiés et déclenche les animations (réarrangement des tableaux…).
  Les « +3 » dans la barre d'action sont annoncés en direct quand le score est donné (choix de LordFinn).
- Le fichier reste `config/craftmaster/config.yml` ; ajout d'une clé `version: 2`. Un fichier v1 (plugin ou mod
  2.0) est migré automatiquement au chargement après copie de sauvegarde `config.yml.v1` :
  les tâches d'une saison deviennent les manches de sa « Partie 1 », les scores suivent, busts et `hidden` gardés.
- Compatibilité des commandes existantes : une commande qui ne précise pas la partie agit sur la **partie active**
  de la saison (créée automatiquement « Partie 1 » si aucune). `AddTask` = ajouter une manche, etc. Le classement
  d'une saison reste la somme de toutes ses parties.

API Java (package `fr.lordfinn.craftmaster.data`) : la classe de données expose ces opérations ; chaque
modification passe par un point unique qui enregistre l'inverse dans le journal d'annulation et sauvegarde.

## 2. Déroulé du direct (chantier A)

Configuration du plateau (commandes admin, enregistrées dans le fichier) :
- rideau de scène nommé (`stage` par défaut) = une position de bloc rideau ; autres rideaux nommés possibles ;
- ancre de scène + décors : `scène enregistrer <nom> <de> <à>` sauvegarde une zone (structure vanilla) ;
  `scène poser <nom>` la pose à l'ancre (rideau fermé conseillé) ;
- points de TP par manche (ajouter la position où on se tient, ou une liste) ; points du public / des joueurs.

Actions de direct :
- **Lancer une manche** (nom ou « suivante ») : la manche devient la manche en cours ; si sur scène, son décor est
  posé ; TP des joueurs de la saison (en ligne) sur ses points (répartis à tour de rôle) ; grand titre chez tout le
  monde « Manche 3 — Le Pont » + son ; si chrono : barre de boss qui se vide, décompte en titre et son les 10
  dernières secondes, son de fin.
- **Fin de manche**, **manche suivante**, **arrêter le chrono**.
- **Score donné** : barre d'action pour tout le monde « LordFinn +3 (Le Pont) », petit son ; le joueur concerné
  voit en plus « +3 » en sous-titre.
- **Publier les scores** (« mettez à jour les scores ») : copie vers les publiés, busts / tableaux / écrans mis à jour
  avec animation.
- **Révélation mise en scène** : rideau de scène fermé ; roulement de tambour ; publication ; révélation du dernier
  au premier (titre « 4e — Ensuri — 12 pts », son, le tableau / bust concerné s'illumine) ; feux d'artifice sur le
  podium (3 premiers) ; rideau ouvert. Durées réglables, interrompable.
- **Rideaux** : ouvrir / fermer un rideau nommé (défaut : scène).

## 3. Tableaux suspendus (chantier B)

Entité `craftmaster:portrait` (sauvegardée) : un cadre doré avec un personnage posé dedans / en sortant, suspendu
par une ficelle à un rail au plafond.
- Réglages par tableau (écran admin en clic droit) : saison (galerie), joueur suivi = « rang N » (défaut) ou pseudo
  fixe, pseudo affiché, skin (celui du joueur par défaut, ou URL d'une image de skin), pose, hauteur.
- Hauteurs : chaque tableau pend à une hauteur légèrement différente (réglable, par défaut variation douce).
- Balancement : rotation lente autour de l'axe vertical, quelques degrés, période ~8–12 s, phase propre à chaque
  tableau (calcul côté client, pas de trafic réseau).
- **Point d'accroche automatique** : centre de gravité du tableau (cadre + personnage dans sa pose, volumes des
  pièces) ; la ficelle s'accroche au-dessus de ce point pour que le tableau pende droit.
- **Réarrangement à la publication** : les tableaux d'une galerie occupent des colonnes ordonnées par rang publié.
  Quand l'ordre change : 1) ceux qui vont vers la gauche avancent, ceux qui vont vers la droite reculent (plans
  différents), 2) glissement latéral jusqu'à la nouvelle colonne, le haut de la ficelle suit le rail, 3) retour dans
  le plan. Les ficelles ne se croisent jamais. Animation interpolée côté client à partir d'un état synchronisé
  (colonne de départ, d'arrivée, heure de début).
- **Poses** : bibliothèque côté serveur (`config/craftmaster/poses.json`), synchronisée aux clients. Une pose =
  rotations de tête, corps, bras, jambes + décalage du personnage par rapport au cadre + inclinaison du cadre.
  Poses fournies inspirées de l'affiche : classique (buste), sort du cadre, assis dans le cadre, porte le cadre,
  se penche dehors, salut, bras croisés, trône. **Éditeur de poses** en jeu (admin) : choisir une partie du corps,
  régler rotations et décalages avec aperçu 3D en direct, enregistrer sous un nouveau nom, écraser, supprimer.

## 4. Régie vocale (chantier C)

- Côté client (admin uniquement) : touche à maintenir (défaut : `N`), enregistrement du micro (16 kHz mono),
  indicateur « ● Régie à l'écoute » ; au relâchement, envoi au **service local** avec le contexte de la partie.
- Contexte : le client le demande au serveur (paquet) : saisons / parties / manches (nom, surnoms, statut, ordre),
  manche en cours, heure du dernier score donné, joueurs et surnoms, scores en direct et publiés, rideaux et
  scènes, 10 dernières actions.
- **Service local** : `tools/voice-regie/` (Python, sur le PC de LordFinn ; faster-whisper, Claude via le SDK
  `anthropic`, clé dans la variable d'environnement `ANTHROPIC_API_KEY`). `POST /command` (audio WAV + contexte
  JSON) → transcription (vocabulaire du contexte donné à Whisper : pseudos, surnoms, manches) → Claude avec des
  outils correspondant au schéma d'actions ci-dessous → réponse `{transcript, actions[], reply}`.
  Règles données à Claude : pseudos et surnoms approximatifs ou mal transcrits → joueur le plus proche ; manche
  non nommée → manche en cours ; si aucune note donnée depuis longtemps (seuil réglable, défaut 4 min) et qu'une
  nouvelle note arrive sans manche → c'est la manche suivante (la lancer d'abord) ; en cas de doute réel, ne rien
  faire et demander (`say`).
- Le client envoie les actions au serveur (paquet), le serveur vérifie que c'est un admin, les exécute, renvoie le
  compte rendu ; affichage : ce qui a été compris + fait, avec annulation possible (« annule »).

### Schéma d'actions (JSON, liste) — contrat entre C (produit) et A (exécute)

Champs `season`, `partie` optionnels partout (défaut : actifs). `manche` = id, nom ou « current » / « next ».
`player` = pseudo exact (le service résout surnoms et fautes avec le contexte).

```
score.set {manche, player, value}        score.add {manche, player, points}     score.remove {manche, player}
scores.publish {}                         reveal.start {}                        undo {count}
player.add {pseudo, aliases[]}            player.update {pseudo, newPseudo?, aliases[]?}   player.remove {pseudo}
season.create {name}  season.update {name?, hidden?}  season.archive {archived}  season.delete {}  season.activate {}
partie.create {name}  partie.update {name?, status?}  partie.archive {archived}  partie.delete {}  partie.activate {}
manche.create {name, aliases[]?, onStage?, scene?, timer?, after?}
manche.update {manche, name?, aliases[]?, onStage?, scene?, timer?, order?}
manche.archive {manche, archived}  manche.delete {manche}
manche.start {manche}   manche.end {}   timer.stop {}
curtain.open {name?}    curtain.close {name?}    scene.set {name}
say {text}                               (réponse orale/écrite seulement, aucune modification)
```
Exécution côté serveur : `fr.lordfinn.craftmaster.show.ActionExecutor#execute(ServerPlayer admin, List<Map<String,Object>> actions)`
→ `Result(List<String> lignes, boolean ok)`. Toute la liste est annulable d'un coup.

## 5. Qualité (tous les chantiers)

- Code commenté (anglais dans le code comme l'existant), propre, cohérent avec l'existant (Mojang names, 26.3).
- Tests unitaires pour la logique pure (données, migration, calcul de centre de gravité, ordre des colonnes,
  interprétation du schéma d'actions…). Le build doit passer : `cmd.exe /c ".\gradlew.bat build"`.
- Textes joueurs en fr_fr et en_us via `scripts/gen_assets.py` (ne pas éditer les JSON générés).
- Wiki `docs/wiki/` mis à jour (pages organisateurs + développeurs).
