# Replays des manches (Server Replay)

Si **Server Replay** (senseiwells, MIT) est installé sur le serveur, CraftMaster enregistre tout seul chaque manche,
ou chaque partie : un fichier par candidat (ce que son jeu a reçu : sa vue, son HUD) et un fichier pour **la zone de
l'épreuve** (des chunks fixes : tous les joueurs qui y sont, vus de n'importe où). Sans Server Replay, rien ne change
et rien ne casse.

## Installer
Sur le serveur seulement : `ServerReplay-3.5.2+26.2.jar` et sa dépendance `fabric-language-kotlin` (Modrinth).
Les joueurs n'ont rien à installer. Au démarrage, le journal du serveur dit
« [Replay] Server Replay trouvé : enregistrement automatique des manches possible ».

## Ce qui est enregistré
- **Quand** : `/cmreplay mode manche` (défaut) : du lancement de chaque manche à sa fin (`/cfma end`, ou la manche
  suivante). `mode partie` : de la première manche d'une partie jusqu'à ce qu'une autre partie devienne active ou
  qu'elle soit terminée (`/craftMasterPartie status 1 terminee`), entre les manches compris. `mode off` : rien
  d'automatique, `/cmreplay start` et `/cmreplay stop` à la main.
- **Qui** : les candidats de la saison (joueurs non archivés) connectés. Un candidat qui arrive en cours de route est
  ajouté ; s'il se déconnecte, Server Replay ferme son fichier, et un nouveau commence (même dossier) à son retour.
  Un joueur déjà enregistré par `/replay start players` est laissé tel quel.
- **La zone** : les caméras de la manche (et les zones d'épreuve qu'elles regardent), ses points de TP ; sans rien de
  tout ça, autour des candidats au lancement. Plus une marge de 2 chunks (`/cmreplay marge <n>`) ; 40 chunks de côté
  au plus. La caméra du replay démarre sur le plan large de la manche.
- **En plus de Minecraft** : l'état de l'émission (plan à l'antenne, caméras, chrono, scores publiés, réalisation
  automatique…) au début de chaque fichier puis à chaque changement, les habillages (synthés, points en direct…) et
  la caméra libre. Et des **repères** sur la timeline : début et fin de manche, points donnés (« Ensuri +3 »),
  enveloppes ouvertes, projecteur, buzzers, tâche révélée, scores publiés, révélation ; `/cmreplay repere <texte>`
  en ajoute un.

## Les fichiers
Dans les dossiers de Server Replay (`recordings/` du serveur par défaut), un dossier par enregistrement :

| Dossier | Contenu |
|---|---|
| `recordings/chunks/cm-s1-p3-m2-le-pont/<date-heure>.mcpr` | La zone de la manche 2 « Le Pont », partie 3, saison 1 |
| `recordings/players/cm-s1-p3-m2-le-pont-ensuri/<date-heure>.mcpr` | La vue d'Ensuri pendant cette manche |
| `recordings/chunks/cm-s1-p3/…`, `players/cm-s1-p3-ensuri/…` | Mode partie : toute la partie 3 |
| `recordings/craftmaster.json` | L'index : chaque enregistrement, ses dossiers, sa zone, ses repères (secondes) |

Format ReplayMod (`.mcpr`) par défaut ; le format se change dans la configuration de Server Replay
(`config/server-replay/config.json`, `default_encoding`), comme les dossiers, les paquets ignorés, la taille maximale.

## Regarder
- **Sur le serveur, sans rien installer** : `/replay view chunks cm-s1-p3-m2-le-pont <date-heure>` (ou `players …`)
  rejoue le fichier dans ton jeu (`/replay view pause`, `unpause`, `speed 2`, `restart`, `close`). Avec CraftMaster
  sur ton jeu, **F8** (mode diffusion) montre l'habillage et les plans choisis en direct, tels qu'ils étaient.
- **ReplayMod** : copier le `.mcpr` dans `replay_recordings/` du jeu. Les repères apparaissent sur sa timeline.
- Flashback lit aussi ces fichiers si on choisit son format, mais son FFmpeg casse WaterMedia (écrans vidéo du
  studio) : pas les deux dans le même jeu.

## Commandes (`/craftMasterReplay`, alias `/cmreplay`, organisateurs)
| Commande | Effet |
|---|---|
| `/cmreplay` | Server Replay trouvé ?, mode, ce qui s'enregistre en ce moment |
| `/cmreplay mode manche\|partie\|off` | Enregistrement automatique de chaque manche, chaque partie, ou jamais |
| `/cmreplay start` · `stop` | À la main : la manche en cours, sinon la partie active ; `stop` sauvegarde et n'est pas relancé avant la manche suivante |
| `/cmreplay joueurs on\|off` · `zone on\|off` | Un fichier par candidat / pour la zone (défaut : les deux) |
| `/cmreplay marge <0..8>` | Chunks ajoutés autour de la zone |
| `/cmreplay repere <texte>` | Un repère maintenant, dans tous les fichiers en cours |

Réglages : `config/craftmaster/replays.json`.

## Limites connues
- Les fichiers des joueurs et de la zone démarrent au même tick, mais leur temps 0 peut différer de quelques
  dixièmes de seconde.
- Un fichier joueur pèse environ 4 Mo pour 30 s (le monde à la distance de vue au départ) ; la zone, bien moins.
- Server Replay ne garde dans la zone que ce que le serveur envoie à tout le monde : barres de boss propres à un
  joueur, titres envoyés à un seul joueur… ne sont que dans le fichier de ce joueur.
- Le chrono du bandeau, rejoué, se recale sur l'heure du replay : sa justesse n'a pas encore été mesurée.
