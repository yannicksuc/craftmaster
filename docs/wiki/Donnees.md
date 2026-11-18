# Fichier de données

`config/craftmaster/config.yml` (dans le dossier du serveur, ou de l'instance en solo), **format 2**. Enregistré
après chaque modification (écriture dans un fichier temporaire puis remplacement : pas de fichier à moitié écrit
en cas de crash).

```yaml
version: 2
live:                          # état du direct
  season: 4                    # saison active
  manche: {season: 4, partie: 1, manche: le-pont}   # manche en cours
  lastScoreAt: 1762722000000   # heure du dernier score (ms)
  scene: foret                 # dernier décor posé
seasons:
  '4':
    name: Saison 4
    hidden: true               # classement caché (facultatif)
    archived: true             # rangée, lecture seule (facultatif)
    activePartie: 1
    players:                   # joueurs de la saison, dans l'ordre (départage des égalités)
      LordFinn: {aliases: [Finn]}
      Ensuri: {}
    parties:
      '1':
        name: Partie 1
        status: en_cours       # preparation | en_cours | terminee
        manches:               # dans l'ordre
        - id: le-pont          # identifiant stable (renommer ne le change pas)
          name: Le Pont
          aliases: [pont]
          onStage: true
          scene: foret
          timer: 180
          timerStart: bouton   # facultatif : lettre (défaut, au lancement si pas de tâche écrite) | lancement | bouton (joueurs figés jusqu'au clic)
          tp: [{world: minecraft:overworld, x: 10.5, y: 64, z: -3.5, yaw: 90, pitch: 0}]
          archived: true       # facultatif
          task:                # facultatif (v4) : la tâche lue sur la carte de l'enveloppe
            title: Le pont le plus élégant
            text: "Construisez un pont…\n\nLe plus élégant gagne."
            rules: [Pas de TNT]
            seconds: 0         # durée affichée, 0 = le chrono de la manche
            wax: red           # red gold black white blue green purple pink orange cyan brown gray
            revealOnStart: false   # écrit seulement si false
        scores:                # manche → joueur → points (en direct)
          le-pont: {LordFinn: 3, Ensuri: 5}
    published:                 # copie figée à la dernière publication
      at: 1762722000000
      scores:
        LordFinn: {Le Pont: 3}
busts:
  '4':
    '1': {world: minecraft:overworld, x: 10, y: 64, z: -3}
stage:
  curtains: {stage: {world: minecraft:overworld, x: 0, y: 64, z: 9}}
  anchor: {world: minecraft:overworld, x: 0, y: 64, z: 3}
  scenes: {foret: {x: 9, y: 6, z: 7}}       # taille ; les blocs sont dans la structure craftmaster:scene/foret
  reveal: {curtain: 3.0, drum: 4.0, step: 3.0, podium: 5.0}
```

Les décors sont des structures vanilla : `<monde>/generated/craftmaster/structures/scene/<nom>.nbt`.

## Ancien format (plugin, mod 2.0)
Un fichier sans `version` est converti au chargement après une copie dans `config.yml.v1` : les tâches deviennent
les manches de « Partie 1 » (identifiants tirés des noms), les scores de tâches retirées deviennent des manches
archivées (les totaux ne changent pas), les joueurs notés forment la liste des joueurs, busts et `hidden` sont
gardés et les scores sont publiés.

## Bon à savoir
- Modifier à la main : serveur allumé, éditer puis `/cm scores recharger` (sinon la prochaine commande écrasera
  la modification).
- Un fichier illisible (YAML cassé) n'empêche pas le serveur de démarrer : le mod le copie dans
  `config.yml.broken`, part d'un état vide et l'écrit dans les logs. Corriger la copie, la remettre à la place de
  `config.yml`, puis `/cm scores recharger`.
- Le journal d'annulation n'est pas dans le fichier (mémoire seulement).
- Sauvegarde : copier ce fichier (et les structures des décors).

## Tâches révélées et notes cachées (`tasks.json`)
`config/craftmaster/tasks.json` (JSON) : `revealed` (`"<saison>:<id manche>": {at, by}`), `lastRevealed`, et
`notes` (`id → {title, text, look, readers, players, once, ink, pieces, spots[], finds[]}` ; une cachette :
`{world, x, y, z, entity?, method, piece, takenBy?, face?}` ; une trouvaille : `{player, at, piece, method}`).
Séparé de `config.yml` exprès : une révélation ou une trouvaille est un événement du direct, `/cm annuler`
ne l'annule pas. Un fichier illisible est gardé en `tasks.json.broken`.
