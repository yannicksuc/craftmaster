# Saisie des points (arbitre), StreamDeck, cadre joueur

## L'écran de saisie (organisateurs)
Touche **U** (modifiable dans Options > Commandes > CraftMaster), `/craftMasterScores [saison]` (`/cfsc`) ou le
bouton `/menu` du StreamDeck. S'ouvre sur l'épreuve en cours, sinon sur le tableau de toutes les épreuves. Saison
active par défaut, ◀ ▶ en haut à gauche pour changer de saison (bloqué tant que des modifications ne sont pas
envoyées).

Rien n'est envoyé tant qu'on n'a pas cliqué **Mettre à jour** : tous les scores modifiés partent alors d'un coup
(une seule entrée du journal, donc un seul « Annuler »). L'écran reste ouvert et se met à jour sur place après
chaque modification, la sienne comme celles des autres (commandes, voix, autre arbitre). **Annuler** / **Refaire**
agissent sur le journal du serveur (comme `/cfun` et `/cfrf`).

### Vue « Épreuve »
Les joueurs côte à côte, du premier au dernier (« #1 » or, « #2 » argent, « #3 » bronze) : leur tête en 2D, leur
score dessous en **tête de chiffre** (de -10 à +10 ; positifs en tons froids, du cyan pâle au bleu-violet ;
négatifs en tons chauds, du jaune au rouge sombre ; 0 en gris).
- **Clic sur une tête** : le joueur est pris, sa tête suit la souris. **Clic sur une autre tête** : ils échangent
  leurs places. Clic sur la même tête ou **clic droit** : on le repose.
- **Maj-clic** sur une tête (ou pendant qu'un joueur est pris) : il passe en tête, les autres descendent d'un rang.
- **+1 / -1** au-dessus / en dessous d'un joueur : ne change que lui.
- Un joueur pris puis **+1 / -1 sur une autre place** : il y est d'abord placé (les autres se décalent), puis il
  reçoit le +1 / -1.

**La règle des places** (validée par LordFinn) : chaque place a un score de base (le score du joueur qui l'occupait
à l'ouverture), et chaque joueur garde son propre bonus / malus. Exemple : A 5, B 3, C 1 ; +1 à A (6) ; on échange A
et C → C 5, B 3, A 1 + 1 = 2. Le petit « +1 » doré au coin d'une tête est ce bonus personnel.

Une épreuve sans aucun point part du **barème** de sa feuille de résultats (5-4-3-2-1 par défaut) : il suffit de
mettre les joueurs dans l'ordre puis « Mettre à jour ». Une épreuve seulement regardée n'est pas envoyée.

### Vue « Toutes les épreuves »
Épreuves en lignes × joueurs en colonnes (8 joueurs au plus ; au-delà, « 8 joueurs max : N non affichés »).
**Clic gauche** sur une case : +1, **clic droit** : -1 ; une case modifiée est entourée d'or, une case vide (« · »)
n'a pas encore de score. Le **numéro** doré d'une épreuve ouvre sa vue « Épreuve ». Molette : défiler. L'épreuve en
cours est surlignée en vert.

## API StreamDeck
Une petite API HTTP dans le jeu de l'organisateur, sur **127.0.0.1** seulement (rien n'est accessible depuis un
autre PC). Désactivée par défaut : dans `config/craftmaster-client.json` (dossier de l'instance), section
`streamDeck` :

```json
"streamDeck": {"enabled": true, "port": 25710, "token": ""}
```

Redémarrer le jeu après modification. `token` facultatif : s'il est rempli, ajouter `?token=…` à chaque adresse
(ou l'en-tête `X-Token`). Un bouton StreamDeck = une adresse (plugin « API Request » ou « Website » en GET) :

| Adresse | Effet |
|---|---|
| `http://127.0.0.1:25710/menu` | Ouvre l'écran de saisie (le ferme s'il est ouvert) |
| `/ranking` | Ouvre le classement (le ferme s'il est ouvert) |
| `/undo` · `/redo` | Annule / refait la dernière modification (organisateurs : le serveur vérifie) |
| `/season/next` · `/season/prev` | Saison suivante / précédente dans l'écran de saisie ou de classement ouvert |
| `/tablet/start` · `/pause` · `/toggle` · `/reset` · `/restart` | Tablettes chrono (`?group=nom` : seulement ce groupe ; [Plateau](Plateau.md#tablette-chrono)) |
| `/status` | JSON : connecté, organisateur, écran ouvert, saison affichée, annulations / refaire possibles |

Réponse toujours en JSON `{"ok": true}` ou `{"ok": false, "error": "…"}` (409 : pas connecté ou aucun écran ouvert,
403 : mauvais jeton, 404 : adresse inconnue).

## Cadre joueur (HUD)
En haut à droite : sa propre tête et son total dessous (tête de chiffre de -10 à +10, nombre au-delà) pour **la
saison active** du serveur (ou une saison choisie dans les réglages),
mis à jour tout seul à chaque changement (les joueurs voient leur total **publié**, les organisateurs le direct) ;
un « +3 » apparaît quelques secondes à côté quand il change. Caché sans score, dans un écran, en mode diffusion et
avec F1, et pour les joueurs quand la saison est cachée. Réglages, section `playerFrame` de
`config/craftmaster-client.json` : `enabled` (true par défaut), `corner` (`top_left`, `top_right`, `bottom_left`,
`bottom_right`), `season` (`"active"` par défaut : suit la saison active, même quand elle change ; ou un numéro,
ex. `"4"`, pour rester sur une saison).

```json
"playerFrame": {"enabled": true, "corner": "top_right", "season": "active"}
```
