# Fichier de données

`config/craftmaster/config.yml` (dans le dossier du serveur, ou de l'instance en solo). Même format que le
`config.yml` du plugin, enregistré après chaque modification (écriture dans un fichier temporaire puis
remplacement : pas de fichier à moitié écrit en cas de crash).

```yaml
seasons:
  '4':
    tasks: [Pont, Tour]
    hidden: true            # classement caché (optionnel)
scores:
  '4':
    LordFinn: {Pont: 3, Tour: 5}
busts:
  '4':
    '1': {world: minecraft:overworld, x: 10, y: 64, z: -3}
```

- Modifier à la main : serveur allumé, éditer puis `/craftMasterReloadScores` (sinon la prochaine commande écrasera
  la modification).
- Un fichier illisible (YAML cassé) n'empêche pas le serveur de démarrer : le mod le copie dans
  `config.yml.broken`, part d'un état vide et l'écrit dans les logs. Corriger la copie, la remettre à la place de
  `config.yml`, puis `/craftMasterReloadScores`.
- Sauvegarde : copier ce fichier suffit.
