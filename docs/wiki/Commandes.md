# Commandes

**Admin** = permission `craftmaster:admin` (LuckPerms…) ou opérateur niveau 2. Chaque commande existe aussi en
minuscules et avec son alias. Noms de manches et pseudos : accents acceptés, guillemets si le nom contient un espace
(`"Le Pont"`). La touche Tab propose saisons, parties, manches, joueurs, décors et rideaux.

Les commandes v3 agissent sur la **saison active** et sa **partie active** (créée toute seule, « Partie 1 ») : on
n'a jamais besoin de parler de partie s'il n'y en a qu'une. Tout ce qui modifie saisons, manches, joueurs et scores
passe par le même exécuteur que la régie vocale : même comportement, et **tout est annulable** (`/cfun`).

## Le direct (admin)
| Commande | Alias | Effet |
|---|---|---|
| `/craftMasterManche start <manche\|next>` | cfma | Lance la manche : titre, son, TP des joueurs de la saison, chrono, décor si sur scène |
| `/craftMasterManche next` | | Lance la manche suivante |
| `/craftMasterManche end` | | Fin de la manche en cours (titre et son) |
| `/craftMasterGive <pseudo> <points> [manche]` | cfg | Points pour la manche en cours (ou nommée), annoncés « LordFinn +3 (Le Pont) » ; pseudo ou surnom |
| `/craftMasterTimer stop` | cfti | Arrête le chrono |
| `/craftMasterPublish [saison]` | cfpu | Publie les scores : busts, étiquettes, écrans et classement des joueurs se mettent à jour |
| `/craftMasterReveal start [saison]` | cfrv | Révélation mise en scène ([Émission](Emission.md)) |
| `/craftMasterReveal stop` | | Interrompt la révélation (rideau rouvert) |
| `/craftMasterReveal timing [rideau tambour joueur podium]` | | Durées en secondes (sans argument : les affiche) |
| `/craftMasterStage curtain open\|close [nom]` | cfsg | Ouvre / ferme un rideau nommé (défaut : `stage`) |
| `/craftMasterStage scene place <décor>` | | Pose un décor à l'ancre de scène |
| `/craftMasterUndo [nombre]` | cfun | Annule la ou les dernières modifications (50 gardées depuis le démarrage) |
| `/craftMasterHistory` | cfhi | Les 10 dernières modifications |
| `/craftMasterStatus` | cfst | Saison, manche en cours, dernier score, scores non publiés, chrono, révélation |
| `/craftMasterStatus context` | | Le contexte envoyé à la régie vocale, en JSON (vérification) |

## Saisons (admin)
| Commande | Alias | Effet |
|---|---|---|
| `/craftMasterSeason list` | cfse | Les saisons (★ active) |
| `/craftMasterSeason create [nom]` | | Nouvelle saison (numéro suivant), devient active |
| `/craftMasterSeason activate <n>` | | Change de saison active |
| `/craftMasterSeason rename <n> <nom>` | | Renomme |
| `/craftMasterSeason archive <n> <true\|false>` | | Range (lecture seule, reste dans les classements) |
| `/craftMasterSeason delete <n>` | | Supprime (annulable) ; les busts restent enregistrés |
| `/craftMasterHideRanking <n> <true\|false>` | cfhr | Cache / révèle ; révéler publie aussi les scores |

## Parties (admin, facultatif)
`/craftMasterPartie` (cfpa) : `list`, `create [nom]` (devient active), `rename <partie> <nom>`,
`status <partie> <preparation|en_cours|terminee>`, `archive <partie> <true|false>`, `delete <partie>`,
`activate <partie>`. Une partie se désigne par son numéro ou son nom.

## Manches (admin)
| Commande | Effet |
|---|---|
| `/craftMasterManche list` | Manches de la partie active (▶ en cours, notée, scène, décor, chrono, TP, surnoms) |
| `/craftMasterManche create <nom> [chrono en s]` | Ajoute à la fin |
| `/craftMasterManche rename <manche> <nom>` | Renomme (les points suivent : une manche garde son identifiant) |
| `/craftMasterManche aliases <manche> [surnoms…]` | Surnoms pour la voix (séparés par des virgules ou espaces ; rien = aucun) |
| `/craftMasterManche order <manche> <position>` | Change l'ordre |
| `/craftMasterManche timer <manche> <secondes>` | Chrono (0 = aucun) |
| `/craftMasterManche stage <manche> <true\|false>` | Se joue sur scène |
| `/craftMasterManche scene <manche> <décor\|aucun>` | Décor posé au lancement (si sur scène) |
| `/craftMasterManche tp add <manche>` | Ajoute l'endroit où l'on se tient (et la direction du regard) comme point de TP |
| `/craftMasterManche tp clear <manche>` | Retire ses points de TP |
| `/craftMasterManche archive <manche> <true\|false>` | Range (plus listée, ses points comptent) |
| `/craftMasterManche delete <manche>` | Supprime avec ses points (annulable) |

## Joueurs de la saison (admin)
`/craftMasterPlayer` (cfpl) : `list`, `add <pseudo> [surnoms…]`, `aliases <pseudo> [surnoms…]`,
`rename <pseudo> <nouveau>` (scores en direct et publiés suivent), `remove <pseudo>` (archivé s'il a des points,
pour rester au classement). Un joueur qui reçoit des points sans être dans la liste y est ajouté.

## Plateau (admin)
| Commande | Effet |
|---|---|
| `/craftMasterStage info` | Rideaux, ancre, décors, durées de la révélation |
| `/craftMasterStage curtain set <nom> [x y z]` | Enregistre le rideau (bloc visé ou position) ; `stage` = rideau de scène |
| `/craftMasterStage curtain remove <nom>` | L'oublie |
| `/craftMasterStage anchor [x y z]` | Ancre de scène (bloc visé ou position) : coin bas nord-ouest des décors |
| `/craftMasterStage scene save <nom> <de> <à>` | Enregistre la zone comme décor (structure vanilla, 64 blocs max par côté) |
| `/craftMasterStage scene delete <nom>` | L'oublie (le fichier de structure reste) |

## Commandes du plugin (admin, toujours là)
Elles agissent sur la partie active de la saison donnée ; « tâche » = manche.

| Commande | Alias | Effet |
|---|---|---|
| `/craftMasterAddTask <saison> <tâche>` | cfat | Ajoute la manche (une manche retirée revient avec ses points) |
| `/craftMasterRemoveTask <saison> <tâche>` | cfrt | Archive la manche ; ses points restent dans les totaux |
| `/craftMasterRenameTask <saison> <ancien> <nouveau>` | cfrn | Renomme ; fusionne si le nouveau nom existe |
| `/craftMasterSetScore <saison> <tâche> <pseudo> <score>` | cfss | Fixe le score |
| `/craftMasterAddScore <saison> <tâche> <pseudo> <points>` | cfas | Ajoute des points (négatif pour en retirer) |
| `/craftMasterRemoveScore <saison> <tâche> <pseudo>` | cfds | Efface ce score |
| `/craftMasterReloadScores` | cfrs | Relit `config/craftmaster/config.yml` (vide le journal d'annulation) |
| `/craftMasterAddBust <saison> <rang>` | cfab | En regardant une tête de joueur : en fait le bust de ce rang |
| `/craftMasterRemoveBust <saison> <rang>` | cfdb | Oublie le bust |
| `/craftMasterCurtain <open\|close\|toggle> <x y z>` | cfcu | Ouvre / ferme le rideau qui contient ce bloc |

Un score sur une tâche qui n'est pas dans la liste est accepté avec un avertissement (la manche est créée archivée).
Les scores changés ici sont en direct : les joueurs les voient après `/craftMasterPublish` ([Classement](Classement.md)).

## Classement (tous)
| Commande | Alias | Effet |
|---|---|---|
| `/craftMasterRanking [saison]` | cfrk | Ouvre l'écran (dernière saison) ; dans le chat si le jeu n'a pas le mod |
| `/craftMasterRanking chat [saison]` | | Classement dans le chat (joueurs : publié ; admins : en direct + « non publié ») |

## Tableaux suspendus (admin)
`/craftMasterPortrait` (alias `cfpt`) — détail dans [Tableaux](Tableaux.md). Sans cible, agit sur le tableau regardé.
| Commande | Effet |
|---|---|
| `/cfpt place <saison> <rang> [<bloc du plafond> <north\|south\|east\|west>]` | Accroche un tableau |
| `/cfpt set [cibles] season\|rank\|player\|name\|skin\|pose\|window\|windowDepth\|windowSize\|height <valeur>` | Change un réglage |
| `/cfpt edit [cibles]` / `/cfpt poseEditor [cibles]` | Ouvre l'écran de réglages / l'éditeur de poses |
| `/cfpt remove [cibles]` | Retire le tableau |
| `/cfpt poses` | Liste les poses |
| `/cfpt publish <saison>` | Réarrange les galeries de la saison selon le classement |
| `/cfpt highlight <saison> <rang>` | Illumine le tableau de ce rang (5 s) |
