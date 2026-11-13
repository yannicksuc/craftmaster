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
| `/craftMasterTimer stop` | cfti | Arrête le chrono (et libère les joueurs figés) |
| `/craftMasterTimer go` | cfti | Lance tout de suite le chrono d'une manche qui attend sa lettre ou son bouton, libère les joueurs figés |
| `/craftMasterPublish [saison]` | cfpu | Publie les scores : busts, étiquettes, écrans et classement des joueurs se mettent à jour |
| `/craftMasterReveal start [saison]` | cfrv | Révélation mise en scène ([Émission](Emission.md)) |
| `/craftMasterReveal stop` | | Interrompt la révélation (rideau rouvert) |
| `/craftMasterReveal timing [rideau tambour joueur podium]` | | Durées en secondes (sans argument : les affiche) |
| `/craftMasterStage curtain open\|close [nom]` | cfsg | Ouvre / ferme un rideau nommé (défaut : `stage`) |
| `/craftMasterStage scene place <décor>` | | Pose un décor à l'ancre de scène |
| `/craftMasterUndo [nombre]` | cfun | Annule la ou les dernières modifications (50 gardées depuis le démarrage) |
| `/craftMasterRedo [nombre]` | cfrf | Refait ce qui vient d'être annulé (toute nouvelle modification vide le « refaire ») |
| `/craftMasterScores [saison]` | cfsc | Écran de saisie des points de l'arbitre (aussi touche **U**, [Saisie](Saisie.md)) |
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
| `/craftMasterManche depart <manche> <lancement\|lettre\|bouton>` | Quand le chrono démarre : quand un joueur de la manche ouvre la lettre de la tâche (défaut ; une manche sans tâche écrite démarre au lancement, et `/cfti go` le lance avant) ; au lancement ; ou au bouton « Commencer l'épreuve » de la lettre : la lettre s'ouvre seule devant chaque joueur au lancement, il reste figé (ni marcher, ni casser, ni poser, ni utiliser, sauf la lettre) jusqu'à son clic, le premier clic lance le chrono. Le même bouton est dans le chat (jeu sans le mod), J rouvre la lettre |
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
| `/craftMasterCurtain <open\|close\|toggle> <x y z>` | cfcu | Ouvre / ferme le rideau qui contient ce bloc (le tissu glisse en continu chez tous les joueurs, [Rideaux](Rideaux.md)) |

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
| `/cfpt hoist <saison> <blocs\|auto> [secondes]` · `/cfpt set [cibles] hoist <blocs\|auto> [secondes]` | Monte ou descend les tableaux (toute une galerie, ou ceux visés) jusqu'à cette longueur de ficelle, en douceur (1 bloc/s sans durée) |

## Épreuves (admin)
Détail et usage : [Épreuves](Epreuves.md). Ce qui touche aux données (feuille, chronos notés) passe par
l'exécuteur des actions : **annulable** et identique à la voix.

| Commande | Alias | Effet |
|---|---|---|
| `/craftMasterResult` | cfre | Ouvre la feuille de résultats de la manche en cours (touche **R**) ; `open <manche>` pour une autre |
| `/craftMasterResult set <joueur> <valeur> [temps\|nombre\|distance\|note\|poids]` | | Note une valeur (`42`, `1:05`, `12,5`) ; le type choisit la mesure si la feuille est vide |
| `/craftMasterResult dq\|undq <joueur>` | | Disqualifie / réintègre |
| `/craftMasterResult remove <joueur>` · `clear` | | Efface une ligne / la feuille |
| `/craftMasterResult kind <type>` · `order <lower\|higher\|auto>` · `scale <5,4,3,2,1>` | | Réglages de la feuille |
| `/craftMasterResult apply [barème]` | | Applique le barème : score de chaque candidat dans la manche (une seule annulation) |
| `/craftMasterResult show` | | La feuille dans le chat (places, points proposés) |
| `/craftMasterChrono start\|stop <joueur> [noter]` · `reset [joueur]` · `list` | cfch | Chronos des candidats ; l'arrêt note le temps (sauf `false`) |
| `/craftMasterBuzzer reset\|status` | cfbz | Rallume la partie de buzzers / qui a buzzé et dans quel ordre |
| `/craftMasterZone create <nom> [<de> <à>]` | cfzo | Zone d'épreuve (sans coordonnées : les deux derniers points du mètre ruban) |
| `/craftMasterZone players <nom> <joueurs…\|all>` · `enable <nom> <true\|false>` · `show` · `remove` · `list` | | Réglages des zones |
| `/craftMasterTrial log` · `reset` · `prop` | cftr | Journal (boutons, buzzers, premiers, sorties de zone) / remise à zéro / réglages de l'objet regardé |
| `/craftMasterBoard size <l> <h>` · `clear` · `lock <true\|false>` · `text <texte>` | cfbd | Tableau à dessin regardé (`\n` = retour à la ligne) |

## Pour tous
| Commande | Alias | Effet |
|---|---|---|
| `/craftMasterName [nom]` | cfnm | Nomme l'objet tenu (codes `&6`, `&l`…) ; sans nom : retire le nom |

## Réalisation TV (organisateur ou permission `craftmaster:camera`)
Détail : [Réalisation](Realisation.md). `<caméra>` = numéro, nom ou rôle (`large`, `trone`, `podium`) ;
`[transition]` = `coupe`, `fondu`, `enchaine`, `miroir`, `flash` ou `surimpression`.
| Commande | Alias | Effet |
|---|---|---|
| `/craftMasterCamera [list]` | cam | Les caméras (● à l'antenne), le plan en cours, la réalisation auto |
| `/cam here <n> [nom]` | | Pose (ou déplace) la caméra n à la place de tes yeux, regardant là où tu regardes |
| `/cam add [nom]` | | Idem avec le numéro libre suivant |
| `/cam aim <n>` · `/cam goto <n>` | | Elle regarde là où tu regardes · tu vois ce qu'elle voit |
| `/cam name <n> <nom>` · `/cam role <n> <rôle\|aucun>` | | Renomme · rôle (un seul par rôle) |
| `/cam zoom <n> <15..110\|reset>` · `/cam motion <n> <fixe\|respiration\|travelling\|panoramique>` | | Champ de vision · mouvement à l'antenne |
| `/cam remove <n>` | | Retire la caméra et son trépied |
| `/cam cut <caméra> [transition]` · `/cam wide` · `/cam next` · `/cam prev` | | Change de plan |
| `/cam follow <joueur> [transition]` | | Plan qui suit un joueur |
| `/cam auto <on\|off>` · `/cam transition <transition>` | | Réalisation automatique · transition par défaut |
| `/cam lieu <caméra> <studio\|manche> [zone]` | | Range la caméra dans un lieu (et une zone : nom de zone d'épreuve ou candidat) |
| `/cam focus [<studio\|manche\|auto> [zone]]` · `/cam zone <zone\|joueur\|suivante\|precedente\|tout>` | | Lieu / zone que visent les touches 1–9 et « caméra 2 » (sans argument : où on en est) |
| `/cam list tout` | | Les caméras de tous les lieux, par lieu et zone |
| `/cam mosaic [lieu\|off]` · `/cam pov <joueur>` | | Écran partagé des zones · vue d'un joueur |
| `/cam trajet <nom> point [s]` · `duree` · `pause` · `courbe` · `zoom` · `suppr` · `remplacer` · `aller` · `apercu` · `montrer` · `jouer` · `objet` · `lieu` · `effacer` | | Enregistre et règle un travelling (voir [Réalisation](Realisation.md)) |
| `/cam jouer <trajet> [transition] [effet]` · `/cam trajets` | | Travelling à l'antenne · liste |
| `/tv intro <manche> [jouer\|voir\|ajouter …\|plan <n> …\|auto\|musique\|titre\|effacer]` | | Intro animée d'une manche |
| `/craftMasterTv lowerthird player <joueur>` | tv | Synthé avec le nom (et le rang publié) d'un joueur |
| `/tv lowerthird text <titre> [\| sous-titre]` | | Synthé libre |
| `/tv task [secondes]` · `/tv scores [show\|auto\|on\|off]` · `/tv letterbox <on\|off>` | | Carte de la tâche · bandeau des scores · bandes cinéma |
| `/tv intro` · `/tv outro` · `/tv stop` | | Générique d'ouverture · de fin · l'arrêter |
| `/tv spotlight <joueur> [raison]` | | « Tous les yeux sur lui » : la réalisation auto coupe dessus |
| `/tv broadcast <joueur> <on\|off>` | | Active / coupe le mode diffusion de ce jeu à distance |

## Effets d'écran ([Effets](Effets.md))
| Commande | Alias | Rôle |
|---|---|---|
| `/craftMasterFx diffusion <effet[+effet]\|aucun> [secondes]` | `/fx` | Effet sur l'image de la diffusion (organisateur ou `craftmaster:camera`) |
| `/fx <joueur\|tous\|candidats> <effet[+effet]\|aucun> [secondes]` | | Effet imposé sur la vue de joueurs (organisateur) |
| `/fx aucun` | | Plus aucun effet (diffusion et joueurs) |
| `/fx porter <joueurs> <objet> [verrouille]` · `/fx retirer <joueurs>` | | Met / enlève des lunettes, un masque (verrouillé : malédiction du lien) |
| `/fx etat` · `/fx liste` | | Qui a quel effet · les effets et combinaisons |
| `/essaifx <effet\|aucun> [secondes]` | `/craftMasterFxTry` | Commande du jeu : essai sur sa propre vue, images par seconde |

## Mode diffusion (tous, commande du jeu)
| Commande | Effet |
|---|---|
| `/craftMasterBroadcast [on\|off]` (alias `/diffusion`) ou **F8** | Mode diffusion (image TV) |
| `/diffusion chat <on\|off>` · `/diffusion status` | Chat visible en diffusion · état |
| `/diffusion camera <n, nom ou clé>` (ou touches 1–9, molette) | Demande ce plan (réalisation seulement) |
| `/diffusion zone <zone\|next\|prev>` (ou Tab / Maj+Tab, Maj+molette) · touche **0** | Zone suivante de la manche · écran partagé |

## Tâches et enveloppes
`/craftMasterTask` (alias `/tache`, `/cftk`) — détail dans [Tâches](Taches.md). Sans argument (**tous**) : la tâche
du moment (comme la touche J). Le reste est admin et passe par l'exécuteur d'actions (textes annulables).
| Commande | Effet |
|---|---|
| `/tache` | Ouvre la tâche du moment (secrète : l'enveloppe scellée ; admin : même secrète) |
| `/tache edit [manche]` · `/tache seal` | Éditeur de tâches · écran du sceau à cire |
| `/tache view <manche>` | Lire la tâche d'une manche (admin, même secrète) |
| `/tache apercu <manche>` (`preview`) | Jouer la lettre (sceau, ouverture) sur ton écran seulement : rien n'est révélé |
| `/tache list` | Les manches, leur tâche, secrète ou révélée (quand, par qui) |
| `/tache set <manche> title <texte>` | Titre |
| `/tache set <manche> text <texte>` · `append <texte>` | Texte (`\n` = retour à la ligne) · ajouter un paragraphe |
| `/tache set <manche> rules [r1 \| r2 \| r3]` | Règles en puces (vide = aucune) |
| `/tache set <manche> seconds <n>` | Durée affichée (0 = chrono de la manche) |
| `/tache set <manche> wax <couleur>` | Cire : rouge, or, noir, blanc, bleu, vert, violet, rose, orange, cyan, marron, gris |
| `/tache set <manche> revealOnStart <true\|false>` | Révélée au lancement de la manche, ou par enveloppe seulement |
| `/tache clear <manche>` | Efface la tâche |
| `/tache reveal [manche]` · `/tache hide [manche]` | Révèle à tous (annoncé) · remet au secret (défaut : manche en cours) |
| `/tache suite [manche]` | Révèle la partie `[secret]` suivante du texte (« révèle la suite ») |
| `/tache show [manche] [secondes]` | La carte à l'écran de tous (12 s), révélée d'abord |
| `/tache envelope <manche> [joueurs\|tous]` | Enveloppes scellées à leur nom (défaut : candidats connectés) |
| `/tache close [joueurs]` | Ferme les cartes ouvertes |
| `/tache resetReveals` | Toutes les tâches de la saison active redeviennent secrètes (répétition) |
| `/tache tools` | Donne le sceau à cire et la lampe UV |

## Notes cachées (admin)
`/craftMasterNote` (alias `/cfno`) — détail dans [Tâches](Taches.md#notes-cachées).
| Commande | Effet |
|---|---|
| `/cfno list` · `/cfno info <note>` | Les notes · cachettes et trouvailles d'une note |
| `/cfno create [titre]` | Nouvelle note (sans titre : l'éditeur) ; donne son outil de cachette |
| `/cfno edit <note>` · `/cfno text <note> <texte>` | Éditeur · texte |
| `/cfno set <note> look\|readers\|players\|title\|once\|ink\|pieces <valeur>` | Un réglage (aspect, lecteurs, prise unique, encre invisible, morceaux) |
| `/cfno give <note>` · `/cfno bottle <note>` | Outil de cachette · bouteille à la mer |
| `/cfno reset <note>` | Oublie les trouvailles, remet les notes prises en place |
| `/cfno unhide <note>` · `/cfno delete <note>` | Retire toutes les cachettes · supprime la note |

## Client (chacun dans son jeu)
| Commande | Effet |
|---|---|
| `/craftMasterTwitch` · `reload` · `test` | État de la lecture du chat Twitch · relire `config/craftmaster-client.json` · simuler un `!tache` |

## Décor du studio
Détail dans [Plateau](Plateau.md). Sans cible, une commande agit sur l'écran, le projecteur ou le pupitre regardé.
| Commande | Alias | Effet |
|---|---|---|
| `/craftMasterImage <URL> [largeur] [hauteur]` | `image`, `cfim` | Image sur l'écran regardé, ou nouvel écran (5 × 3 par défaut) sur le mur regardé ; permission `craftmaster:image` ou admin |
| `/image clear\|remove` · `/image size <l> <h>` · `/image fit <true\|false>` | | Mire / retirer / taille / image entière ou étirée |
| `/craftMasterSpotlight [all] follow <joueur>\|stop\|on\|off\|color <teinture>\|here` | `cfsp` | Projecteur regardé (ou tous avec `all`) : suivre, arrêter, allumer, éteindre, couleur, pointer vers soi (admin) |
| `/cfsp list` | | Projecteurs chargés |
| `/craftMasterDesk name <nom\|->` | `cfdk` | Nom sur la plaque du pupitre regardé (admin) |
| `/craftMasterGong` | `cfgo` | Coup du gong le plus proche (admin) |

